// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk9.desugaredlib;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TimeZoneTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_OUTPUT = StringUtils.lines("9223372036854775807");
  private static final String EXPECTED_OUTPUT_NO_TZ_DATA = StringUtils.lines("1771120800000");
  private static final String EXPECTED_OUTPUT_NO_TZ_DATA_4_4_4 = StringUtils.lines("1774749600000");

  private static final Class<?> MAIN_CLASS = TimeZoneMain.class;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK11, JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public TimeZoneTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(MAIN_CLASS)
        .compile()
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(getExpectedOutput(true));
  }

  private String getExpectedOutput(boolean desugaring) {
    if (parameters.isDexRuntime()) {
      // On 4.4.4 the tzdata is missing and the behavior is different than art VMs.
      if (parameters.getDexRuntimeVersion() == Version.V4_4_4) {
        return EXPECTED_OUTPUT_NO_TZ_DATA_4_4_4;
      }
      // VMs newer than 12.0.0, the tz data is missing. The desugared library behavior uses
      // emulated tz data and is as if the tz data was present. The non desugared behavior is
      // different since the tz data is missing.
      if (parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V12_0_0)) {
        if (!desugaring || !libraryDesugaringSpecification.hasCompleteTimeDesugaring(parameters)) {
          return EXPECTED_OUTPUT_NO_TZ_DATA;
        }
      }
    }
    return EXPECTED_OUTPUT;
  }

  @Test
  public void testD8() throws Exception {
    Assume.assumeTrue(compilationSpecification == D8_L8DEBUG);
    Assume.assumeTrue(libraryDesugaringSpecification == JDK11);
    Assume.assumeTrue(
        parameters.isCfRuntime()
            || !libraryDesugaringSpecification.hasCompleteTimeDesugaring(parameters));
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .compile()
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(getExpectedOutput(false));
  }

  public static class TimeZoneMain {

    public static void main(String[] args) throws ParseException {
      TimeZone tz = TimeZone.getTimeZone("Africa/Casablanca");
      Long now = getTimeInMillisecond("02-13-2026 10:00:00:00", "Africa/Casablanca");
      Long nextTimeChange = findNextTimeChange(tz, now);
      System.out.println(nextTimeChange);
    }

    private static Long findNextTimeChange(TimeZone tz, Long startDate) {
      Long endDate = Long.MAX_VALUE;
      ZoneRules rules = ZoneId.of(tz.getID(), ZoneId.SHORT_IDS).getRules();

      ZoneOffsetTransition nextTransition = rules.nextTransition(Instant.ofEpochMilli(startDate));
      if (nextTransition != null) {
        return java.util.concurrent.TimeUnit.SECONDS.toMillis(nextTransition.toEpochSecond());
      }

      return Long.MAX_VALUE;
    }

    private static Long getTimeInMillisecond(String dateString, String zoneId)
        throws ParseException {
      SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss", Locale.US);
      sdf.setTimeZone(TimeZone.getTimeZone(zoneId));
      Date date = sdf.parse(dateString);
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(date);
      return calendar.getTimeInMillis();
    }
  }
}
