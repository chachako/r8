// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.blastradius.proto.BlastRadiusContainer;
import com.android.tools.r8.blastradius.proto.BlastRadiusSummary;
import com.android.tools.r8.blastradius.proto.BuildInfo;
import com.android.tools.r8.blastradius.proto.KeepConstraint;
import com.android.tools.r8.blastradius.proto.KeepConstraints;
import com.android.tools.r8.blastradius.proto.KeepInfoCollectionSummary;
import com.android.tools.r8.blastradius.proto.KeepRuleBlastRadius;
import com.android.tools.r8.blastradius.proto.KeepRuleBlastRadiusSummary;
import com.android.tools.r8.blastradius.proto.KeepRuleTag;
import com.android.tools.r8.blastradius.proto.KeptClassInfo;
import com.android.tools.r8.blastradius.proto.KeptFieldInfo;
import com.android.tools.r8.blastradius.proto.KeptMethodInfo;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.google.protobuf.AbstractMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@KeepForApi
public class BlastRadiusHtmlReportGenerator {

  /**
   * Convert a {@code blastradius.pb} file to HTML using {@code BlastRadiusHtmlReportGenerator
   * <blastradius.pb> <blastradius.html>}.
   *
   * <p>Convert all {@code blastradius.pb} files in a given directory to HTML and create a summary
   * using {@code BlastRadiusHtmlReportGenerator <blastradius dir> <out dir>}. </code>
   */
  public static void main(String[] args) throws IOException {
    // TODO(b/486097941): Remove.
    System.out.println("NOTE: Running experimental BlastRadiusHtmlReportGenerator.");

    Path input = Paths.get(args[0]);
    Path output = Paths.get(args[1]);
    List<Path> blastRadiusFiles = new ArrayList<>();
    List<Path> blastRadiusOutputFiles = new ArrayList<>();

    // If the first argument is a directory, find all *blastradius*.pb files inside the directory.
    boolean summarize = false;
    if (Files.isDirectory(input)) {
      if (Files.exists(output) && !Files.isDirectory(output)) {
        throw new IllegalArgumentException("Expected directory, but was: " + output);
      }
      try (var stream = Files.walk(input)) {
        stream
            .filter(Files::isRegularFile)
            .forEach(
                path -> {
                  String name = path.getFileName().toString();
                  if (name.endsWith(".pb") && name.contains("blastradius")) {
                    String htmlName = name.substring(0, name.lastIndexOf('.')) + ".html";
                    blastRadiusFiles.add(path);
                    blastRadiusOutputFiles.add(
                        output.resolve(input.relativize(path.resolveSibling(htmlName))));
                  }
                });
      }
      summarize = true;
    } else {
      blastRadiusFiles.add(input);
      blastRadiusOutputFiles.add(output);
    }

    // Generate HTML for each *blastradius*.pb file.
    List<BlastRadiusSummary> blastRadiusSummaries = summarize ? new ArrayList<>() : null;
    for (int i = 0; i < blastRadiusFiles.size(); i++) {
      Path blastRadiusFile = blastRadiusFiles.get(i);
      Path blastRadiusOutputFile = blastRadiusOutputFiles.get(i);
      BlastRadiusContainer blastRadius;
      try (InputStream is = Files.newInputStream(blastRadiusFile)) {
        blastRadius = BlastRadiusContainer.parseFrom(is);
      }
      Files.createDirectories(blastRadiusOutputFile.getParent());
      Files.write(blastRadiusOutputFile, generate(blastRadius).getBytes(StandardCharsets.UTF_8));
      if (summarize) {
        Path relPath = input.relativize(blastRadiusFile);
        String name = relPath.getParent().toString();
        String link = relPath.toString().replace(".pb", ".html");
        blastRadiusSummaries.add(getSummary(blastRadius, name, link));
      }
    }

    // Output summary.
    if (summarize) {
      Files.write(
          output.resolve("blastradius.html"),
          generateSummary(blastRadiusSummaries).getBytes(StandardCharsets.UTF_8));
    }
  }

  private static String generate(BlastRadiusContainer blastRadius) {
    String html = BlastRadiusHtmlReportTemplate.getHtmlTemplate();
    return html.replace(
        "<script id=\"blastradius-data\" type=\"application/octet-stream\"></script>",
        String.join(
            "",
            "<script id=\"blastradius-data\" type=\"application/octet-stream\">",
            encodeToString(blastRadius),
            "</script>"));
  }

  private static String generateSummary(List<BlastRadiusSummary> blastRadiusSummaries) {
    String html = BlastRadiusHtmlReportTemplate.getSummaryHtmlTemplate();
    return html.replace(
        "<script id=\"blastradius-data\" type=\"application/json\"></script>",
        String.join(
            "",
            "<script id=\"blastradius-data\" type=\"application/json\">[",
            blastRadiusSummaries.stream()
                .map(BlastRadiusHtmlReportGenerator::encodeToString)
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(",")),
            "]</script>"));
  }

  private static String encodeToString(AbstractMessage message) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      message.writeTo(baos);
    } catch (IOException e) {
      // Should not happen.
      throw new UncheckedIOException(e);
    }
    return Base64.getEncoder().encodeToString(baos.toByteArray());
  }

  private static BlastRadiusSummary getSummary(
      BlastRadiusContainer blastRadius, String name, String link) {
    BlastRadiusSummary.Builder summaryBuilder =
        BlastRadiusSummary.newBuilder().setName(name).setLink(link);

    // Info about number of items.
    BuildInfo buildInfo = blastRadius.getBuildInfo();
    summaryBuilder.setClassCount(buildInfo.getClassCount());
    summaryBuilder.setFieldCount(buildInfo.getFieldCount());
    summaryBuilder.setMethodCount(buildInfo.getMethodCount());

    // Info about number of keep rules.
    summaryBuilder.setKeepRuleCount(blastRadius.getKeepRuleBlastRadiusTableCount());
    summaryBuilder.setKeepRulePackageWideCount(
        (int)
            blastRadius.getKeepRuleBlastRadiusTableList().stream()
                .filter(rule -> rule.getTagsList().contains(KeepRuleTag.PACKAGE_WIDE))
                .count());

    // Info about number of kept items.
    Map<Integer, KeepConstraints> constraintsById = new HashMap<>();
    for (KeepConstraints keepConstraints : blastRadius.getKeepConstraintsTableList()) {
      constraintsById.put(keepConstraints.getId(), keepConstraints);
    }
    Map<Integer, KeepConstraints> ruleIdToConstraints = new HashMap<>();
    for (KeepRuleBlastRadius rule : blastRadius.getKeepRuleBlastRadiusTableList()) {
      ruleIdToConstraints.put(rule.getId(), constraintsById.get(rule.getConstraintsId()));
    }
    summaryBuilder.setKeptClasses(
        getKeptItemsSummary(
            blastRadius.getKeptClassInfoTableList(),
            KeptClassInfo::getId,
            KeptClassInfo::getKeptByList,
            ruleIdToConstraints));
    summaryBuilder.setKeptFields(
        getKeptItemsSummary(
            blastRadius.getKeptFieldInfoTableList(),
            KeptFieldInfo::getId,
            KeptFieldInfo::getKeptByList,
            ruleIdToConstraints));
    summaryBuilder.setKeptMethods(
        getKeptItemsSummary(
            blastRadius.getKeptMethodInfoTableList(),
            KeptMethodInfo::getId,
            KeptMethodInfo::getKeptByList,
            ruleIdToConstraints));

    // Info about problematic rules.
    List<KeepRuleBlastRadius> rulesWithRadius =
        new ArrayList<>(blastRadius.getKeepRuleBlastRadiusTableList());
    rulesWithRadius.sort(
        (a, b) -> {
          int radiusA =
              a.getBlastRadius().getClassBlastRadiusCount()
                  + a.getBlastRadius().getFieldBlastRadiusCount()
                  + a.getBlastRadius().getMethodBlastRadiusCount();
          int radiusB =
              b.getBlastRadius().getClassBlastRadiusCount()
                  + b.getBlastRadius().getFieldBlastRadiusCount()
                  + b.getBlastRadius().getMethodBlastRadiusCount();
          return Integer.compare(radiusB, radiusA);
        });

    for (int i = 0; i < Math.min(10, rulesWithRadius.size()); i++) {
      KeepRuleBlastRadius rule = rulesWithRadius.get(i);
      int radius =
          rule.getBlastRadius().getClassBlastRadiusCount()
              + rule.getBlastRadius().getFieldBlastRadiusCount()
              + rule.getBlastRadius().getMethodBlastRadiusCount();
      if (radius == 0) {
        break;
      }
      summaryBuilder.addKeepRuleBlastRadius(
          KeepRuleBlastRadiusSummary.newBuilder().setSource(rule.getSource()).setItemCount(radius));
    }

    return summaryBuilder.build();
  }

  private static <T> KeepInfoCollectionSummary getKeptItemsSummary(
      List<T> items,
      Function<T, Integer> getId,
      Function<T, List<Integer>> getKeptBy,
      Map<Integer, KeepConstraints> ruleIdToConstraints) {
    Set<Integer> noObfuscation = new HashSet<>();
    Set<Integer> noOptimization = new HashSet<>();
    Set<Integer> noShrinking = new HashSet<>();
    for (T item : items) {
      int id = getId.apply(item);
      List<Integer> keptBy = getKeptBy.apply(item);
      for (int ruleId : keptBy) {
        KeepConstraints constraints = ruleIdToConstraints.get(ruleId);
        for (KeepConstraint constraint : constraints.getConstraintsList()) {
          switch (constraint) {
            case DONT_OBFUSCATE:
              noObfuscation.add(id);
              break;
            case DONT_OPTIMIZE:
              noOptimization.add(id);
              break;
            case DONT_SHRINK:
              noShrinking.add(id);
              break;
            default:
              throw new RuntimeException("Unexpected constraint: " + constraint);
          }
        }
      }
    }
    return KeepInfoCollectionSummary.newBuilder()
        .setItemCount(items.size())
        .setNoObfuscationCount(noObfuscation.size())
        .setNoOptimizationCount(noOptimization.size())
        .setNoShrinkingCount(noShrinking.size())
        .build();
  }
}
