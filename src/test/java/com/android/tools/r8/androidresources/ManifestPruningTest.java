// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ManifestPruningTest extends TestBase {

  @Parameterized.Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameter(1)
  public boolean enableManifestPruning;

  @Parameters(name = "{0}, pruneManifest: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  enum ComponentType {
    ACTIVITY,
    SERVICE,
    PROVIDER,
    RECEIVER;

    public String getSuperClass() {
      switch (this) {
        case ACTIVITY:
          return "Activity";
        case SERVICE:
          return "Service";
        case PROVIDER:
          return "ContentProvider";
        case RECEIVER:
          return "BroadcastReceiver";
        default:
          throw new RuntimeException();
      }
    }

    public String getTagName() {
      switch (this) {
        case ACTIVITY:
          return "activity";
        case SERVICE:
          return "service";
        case PROVIDER:
          return "provider";
        case RECEIVER:
          return "receiver";
        default:
          throw new RuntimeException();
      }
    }
  }

  static class Component {
    ComponentType type;
    boolean used;
    boolean hasIntentFilter;
    boolean exported;
    boolean relativeName;
    String name;

    public Component(
        ComponentType type,
        boolean used,
        boolean hasIntentFilter,
        boolean exported,
        boolean relativeName) {
      this.type = type;
      this.used = used;
      this.hasIntentFilter = hasIntentFilter;
      this.exported = exported;
      this.relativeName = relativeName;
      this.name =
          type.name()
              + (used ? "_Used" : "_Unused")
              + (hasIntentFilter ? "_WithIntentFilter" : "_NoIntentFilter")
              + (exported ? "_Exported" : "_NotExported")
              + (relativeName ? "_Relative" : "_Absolute");
    }
  }

  @Test
  public void testUnusedComponents() throws Exception {
    List<Component> components = new ArrayList<>();
    for (ComponentType type : ComponentType.values()) {
      for (boolean used : new boolean[] {true, false}) {
        for (boolean hasIntentFilter : new boolean[] {true, false}) {
          for (boolean exported : new boolean[] {true, false}) {
            for (boolean relativeName : new boolean[] {true, false}) {
              components.add(new Component(type, used, hasIntentFilter, exported, relativeName));
            }
          }
        }
      }
    }

    StringBuilder manifestBuilder = new StringBuilder();
    manifestBuilder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
    manifestBuilder.append(
        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
    manifestBuilder.append("    package=\"com.android.tools.r8.resourceshrinker\">\n");
    manifestBuilder.append("    <application>\n");

    for (Component component : components) {
      manifestBuilder.append("        <").append(component.type.getTagName());
      if (component.relativeName) {
        manifestBuilder.append(" android:name=\".Classes$").append(component.name).append("\"");
      } else {
        manifestBuilder
            .append(" android:name=\"com.android.tools.r8.resourceshrinker.Classes$")
            .append(component.name)
            .append("\"");
      }
      manifestBuilder.append(" android:exported=\"").append(component.exported).append("\">\n");
      if (component.hasIntentFilter) {
        manifestBuilder.append("            <intent-filter>\n");
        manifestBuilder.append(
            "                <action android:name=\"android.intent.action.INSERT\"/>\n");
        manifestBuilder.append(
            "                <category android:name=\"android.intent.category.DEFAULT\"/>\n");
        manifestBuilder.append("            </intent-filter>\n");
      }
      manifestBuilder.append("        </").append(component.type.getTagName()).append(">\n");
    }
    manifestBuilder.append("    </application>\n");
    manifestBuilder.append("</manifest>");

    StringBuilder javaSourceBuilder = new StringBuilder();
    javaSourceBuilder.append("package com.android.tools.r8.resourceshrinker;\n");
    javaSourceBuilder.append("import android.app.Activity;\n");
    javaSourceBuilder.append("import android.app.Service;\n");
    javaSourceBuilder.append("import android.content.BroadcastReceiver;\n");
    javaSourceBuilder.append("import android.content.ContentProvider;\n");
    javaSourceBuilder.append("import android.content.Context;\n");
    javaSourceBuilder.append("import android.content.ContentValues;\n");
    javaSourceBuilder.append("import android.content.Intent;\n");
    javaSourceBuilder.append("import android.database.Cursor;\n");
    javaSourceBuilder.append("import android.net.Uri;\n");
    javaSourceBuilder.append("import android.os.IBinder;\n");
    javaSourceBuilder.append("public class Classes {\n");
    javaSourceBuilder.append("  public static class Main {\n");
    javaSourceBuilder.append("    public static void main(Context context) {\n");

    for (Component component : components) {
      if (component.used) {
        javaSourceBuilder.append("      new ").append(component.name).append("();\n");
      }
    }

    javaSourceBuilder.append("    }\n");
    javaSourceBuilder.append("  }\n");

    for (Component component : components) {
      javaSourceBuilder
          .append("  public static class ")
          .append(component.name)
          .append(" extends ")
          .append(component.type.getSuperClass())
          .append(" {\n");
      if (component.type == ComponentType.SERVICE) {
        javaSourceBuilder.append("    @Override\n");
        javaSourceBuilder.append("    public IBinder onBind(Intent intent) { return null; }\n");
      } else if (component.type == ComponentType.PROVIDER) {
        javaSourceBuilder.append("    @Override public boolean onCreate() { return false; }\n");
        javaSourceBuilder.append(
            "    @Override public Cursor query(Uri uri, String[] projection, String selection,"
                + " String[] selectionArgs, String sortOrder) { return null; }\n");
        javaSourceBuilder.append("    @Override public String getType(Uri uri) { return null; }\n");
        javaSourceBuilder.append(
            "    @Override public Uri insert(Uri uri, ContentValues values) { return null; }\n");
        javaSourceBuilder.append(
            "    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {"
                + " return 0; }\n");
        javaSourceBuilder.append(
            "    @Override public int update(Uri uri, ContentValues values, String selection,"
                + " String[] selectionArgs) { return 0; }\n");
      } else if (component.type == ComponentType.RECEIVER) {
        javaSourceBuilder.append(
            "    @Override public void onReceive(Context context, Intent intent) { }\n");
      }
      javaSourceBuilder.append("  }\n");
    }
    javaSourceBuilder.append("}\n");

    Path classes = temp.newFolder().toPath();
    Path source = classes.resolve("Classes.java");
    FileUtils.writeTextFile(source, javaSourceBuilder.toString());

    Path jar =
        javac(TestRuntime.getCheckedInJdk11())
            .addSourceFiles(source)
            .addClasspathFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
            .compile();

    AndroidTestResource testResource =
        new AndroidTestResourceBuilder().withManifest(manifestBuilder.toString()).build(temp);

    AndroidApp app =
        (testForR8(parameters.getBackend())
                .addProgramFiles(jar)
                .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
                .addKeepRules(
                    "-keep class com.android.tools.r8.resourceshrinker.Classes$Main { public static"
                        + " void main(android.content.Context); }")
                .addAndroidResources(testResource)
                .enableOptimizedShrinking()
                .applyIf(
                    parameters.getPartialCompilationTestParameters().isSome(),
                    rr ->
                        rr.addR8PartialR8OptionsModification(
                            o -> {
                              o.enableManifestPruning = enableManifestPruning;
                            }),
                    rr ->
                        rr.addOptionsModification(
                            o -> {
                              o.enableManifestPruning = enableManifestPruning;
                            }))
                .setMinApi(parameters)
                .compile())
            .getApp();

    CodeInspector inspector = new CodeInspector(app);

    assertThat(inspector.clazz("com.android.tools.r8.resourceshrinker.Classes$Main"), isPresent());

    for (Component component : components) {
      String className = "com.android.tools.r8.resourceshrinker.Classes$" + component.name;
      if (component.used
          || component.hasIntentFilter
          || component.exported
          || !enableManifestPruning) {
        assertThat(className, inspector.clazz(className), isPresent());
      } else {
        assertThat(className, inspector.clazz(className), isAbsent());
      }
    }
  }
}
