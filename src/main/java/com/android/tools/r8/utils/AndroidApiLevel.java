// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** Android API level description */
public class AndroidApiLevel implements Ordered<AndroidApiLevel> {
  private static final List<AndroidApiLevel> valuesSorted;

  public static final AndroidApiLevel B;
  public static final AndroidApiLevel B_1_1;
  public static final AndroidApiLevel C;
  public static final AndroidApiLevel D;
  public static final AndroidApiLevel E;
  public static final AndroidApiLevel E_0_1;
  public static final AndroidApiLevel E_MR1;
  public static final AndroidApiLevel F;
  public static final AndroidApiLevel G;
  public static final AndroidApiLevel G_MR1;
  public static final AndroidApiLevel H;
  public static final AndroidApiLevel H_MR1;
  public static final AndroidApiLevel H_MR2;
  public static final AndroidApiLevel I;
  public static final AndroidApiLevel I_MR1;
  public static final AndroidApiLevel J;
  public static final AndroidApiLevel J_MR1;
  public static final AndroidApiLevel J_MR2;
  public static final AndroidApiLevel K;
  public static final AndroidApiLevel K_WATCH;
  public static final AndroidApiLevel L;
  public static final AndroidApiLevel L_MR1;
  public static final AndroidApiLevel M;
  public static final AndroidApiLevel N;
  public static final AndroidApiLevel N_MR1;
  public static final AndroidApiLevel O;
  public static final AndroidApiLevel O_MR1;
  public static final AndroidApiLevel P;
  public static final AndroidApiLevel Q;
  public static final AndroidApiLevel R;
  public static final AndroidApiLevel S;
  public static final AndroidApiLevel Sv2;
  public static final AndroidApiLevel T;
  public static final AndroidApiLevel U;
  public static final AndroidApiLevel V;
  public static final AndroidApiLevel BAKLAVA;
  public static final AndroidApiLevel MAIN;
  // Used for API modeling of Android extension APIs.
  public static final AndroidApiLevel EXTENSION;

  // When updating LATEST and a new version goes public, add a new api-versions.xml to third_party
  // and update the version and generated jar in AndroidApiDatabaseBuilderGeneratorTest. Together
  // with that update third_party/android_jar/libcore_latest/core-oj.jar and run
  // GenerateCovariantReturnTypeMethodsTest.
  public static final AndroidApiLevel LATEST;

  public static final AndroidApiLevel API_DATABASE_LEVEL;

  public static final AndroidApiLevel UNKNOWN;

  /** Constant used to signify some unknown min api when compiling platform. */
  public static final int ANDROID_PLATFORM_CONSTANT = 10000;

  static {
    ImmutableList.Builder<AndroidApiLevel> builder = ImmutableList.builder();
    builder.add(B = new AndroidApiLevel(1, "B"));
    builder.add(B_1_1 = new AndroidApiLevel(2, "B_1_1"));
    builder.add(C = new AndroidApiLevel(3, "C"));
    builder.add(D = new AndroidApiLevel(4, "D"));
    builder.add(E = new AndroidApiLevel(5, "E"));
    builder.add(E_0_1 = new AndroidApiLevel(6, "E_0_1"));
    builder.add(E_MR1 = new AndroidApiLevel(7, "E_MR1"));
    builder.add(F = new AndroidApiLevel(8, "F"));
    builder.add(G = new AndroidApiLevel(9, "G"));
    builder.add(G_MR1 = new AndroidApiLevel(10, "G_MR1"));
    builder.add(H = new AndroidApiLevel(11, "H"));
    builder.add(H_MR1 = new AndroidApiLevel(12, "H_MR1"));
    builder.add(H_MR2 = new AndroidApiLevel(13, "H_MR2"));
    builder.add(I = new AndroidApiLevel(14, "I"));
    builder.add(I_MR1 = new AndroidApiLevel(15, "I_MR1"));
    builder.add(J = new AndroidApiLevel(16, "J"));
    builder.add(J_MR1 = new AndroidApiLevel(17, "J_MR1"));
    builder.add(J_MR2 = new AndroidApiLevel(18, "J_MR2"));
    builder.add(K = new AndroidApiLevel(19, "K"));
    builder.add(K_WATCH = new AndroidApiLevel(20, "K_WATCH"));
    builder.add(L = new AndroidApiLevel(21, "L"));
    builder.add(L_MR1 = new AndroidApiLevel(22, "L_MR1"));
    builder.add(M = new AndroidApiLevel(23, "M"));
    builder.add(N = new AndroidApiLevel(24, "N"));
    builder.add(N_MR1 = new AndroidApiLevel(25, "N_MR1"));
    builder.add(O = new AndroidApiLevel(26, "O"));
    builder.add(O_MR1 = new AndroidApiLevel(27, "O_MR1"));
    builder.add(P = new AndroidApiLevel(28, "P"));
    builder.add(Q = new AndroidApiLevel(29, "Q"));
    builder.add(R = new AndroidApiLevel(30, "R"));
    builder.add(S = new AndroidApiLevel(31, "S"));
    builder.add(Sv2 = new AndroidApiLevel(32, "Sv2"));
    builder.add(T = new AndroidApiLevel(33, "T"));
    builder.add(U = new AndroidApiLevel(34, "U"));
    builder.add(V = new AndroidApiLevel(35, "V"));
    builder.add(BAKLAVA = new AndroidApiLevel(36, "BAKLAVA"));
    builder.add(MAIN = new AndroidApiLevel(37, "MAIN"));
    builder.add(EXTENSION = new AndroidApiLevel(Integer.MAX_VALUE, "EXTENSION"));
    valuesSorted = builder.build();
    assert valuesSorted.size() == 38;
    assert checkValuesSorted();

    LATEST = BAKLAVA;
    API_DATABASE_LEVEL = LATEST;
    UNKNOWN = MAIN;
  }

  private final int level;
  private final String name;

  private AndroidApiLevel(int level, String name) {
    this.level = level;
    this.name = name;
  }

  private static boolean checkValuesSorted() {
    for (int i = 1; i < valuesSorted.size(); i++) {
      assert valuesSorted.get(i - 1).isLessThan(valuesSorted.get(i));
    }
    return true;
  }

  public int getLevel() {
    return level;
  }

  public String getName() {
    return "Android " + name;
  }

  public static AndroidApiLevel getDefault() {
    return AndroidApiLevel.B;
  }

  public AndroidApiLevel max(AndroidApiLevel other) {
    return Ordered.max(this, other);
  }

  public DexVersion getDexVersion() {
    return DexVersion.getDexVersion(this);
  }

  public AndroidApiLevel next() {
    return getAndroidApiLevel(getLevel() + 1);
  }

  public AndroidApiLevel verifyLevel(int expected) {
    assert level == expected;
    return this;
  }

  public static List<AndroidApiLevel> getAndroidApiLevelsSorted() {
    return valuesSorted;
  }

  public static AndroidApiLevel getMinAndroidApiLevel(DexVersion dexVersion) {
    switch (dexVersion) {
      case V35:
        return AndroidApiLevel.B;
      case V37:
        return AndroidApiLevel.N;
      case V38:
        return AndroidApiLevel.O;
      case V39:
        return AndroidApiLevel.P;
      case V40:
        return AndroidApiLevel.R;
      case V41:
        assert InternalOptions.containerDexApiLevel().isEqualTo(AndroidApiLevel.BAKLAVA);
        return AndroidApiLevel.BAKLAVA;
      default:
        throw new Unreachable();
    }
  }

  public static AndroidApiLevel getAndroidApiLevel(int apiLevel) {
    assert apiLevel > 0;
    assert BAKLAVA == LATEST; // This has to be updated when we add new api levels.
    assert UNKNOWN.isGreaterThan(LATEST);
    switch (apiLevel) {
      case 1:
        return B;
      case 2:
        return B_1_1;
      case 3:
        return C;
      case 4:
        return D;
      case 5:
        return E;
      case 6:
        return E_0_1;
      case 7:
        return E_MR1;
      case 8:
        return F;
      case 9:
        return G;
      case 10:
        return G_MR1;
      case 11:
        return H;
      case 12:
        return H_MR1;
      case 13:
        return H_MR2;
      case 14:
        return I;
      case 15:
        return I_MR1;
      case 16:
        return J;
      case 17:
        return J_MR1;
      case 18:
        return J_MR2;
      case 19:
        return K;
      case 20:
        return K_WATCH;
      case 21:
        return L;
      case 22:
        return L_MR1;
      case 23:
        return M;
      case 24:
        return N;
      case 25:
        return N_MR1;
      case 26:
        return O;
      case 27:
        return O_MR1;
      case 28:
        return P;
      case 29:
        return Q;
      case 30:
        return R;
      case 31:
        return S;
      case 32:
        return Sv2;
      case 33:
        return T;
      case 34:
        return U;
      case 35:
        return V;
      case 36:
        return BAKLAVA;
      default:
        return MAIN;
    }
  }

  public static AndroidApiLevel parseAndroidApiLevel(String apiLevel) {
    int dotPosition = apiLevel.indexOf('.');
    if (dotPosition == -1) {
      return AndroidApiLevel.getAndroidApiLevel(Integer.parseInt(apiLevel));
    } else {
      String majorApiLevel = apiLevel.substring(0, dotPosition);
      String minorApiLevel = apiLevel.substring(dotPosition + 1);
      assert Integer.parseInt(minorApiLevel) >= 0;
      return AndroidApiLevel.getAndroidApiLevel(Integer.parseInt(majorApiLevel));
    }
  }

  @Override
  public int compareTo(AndroidApiLevel other) {
    return level - other.level;
  }

  @Override
  public String toString() {
    return getName();
  }
}
