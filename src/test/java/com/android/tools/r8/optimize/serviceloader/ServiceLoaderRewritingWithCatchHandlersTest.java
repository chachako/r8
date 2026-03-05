// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.serviceloader;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ServiceLoaderRewritingWithCatchHandlersTest extends ServiceLoaderTestBase {

  public interface Service {
    void print();
  }

  public static class MainRunner {

    public static void main(String[] args) {
      loadService();
    }

    @NeverInline
    private static void loadService() {
      try {
        ServiceLoader<Service> loader =
            ServiceLoader.load(Service.class, Service.class.getClassLoader());
        Iterator<Service> it = loader.iterator();
        if (it.hasNext()) {
          Service service = it.next();
          service.print();
        }
      } finally {
        System.out.println("Finally");
      }
    }
  }

  @Parameterized.Parameters(name = "{0}, enableRewriting: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public ServiceLoaderRewritingWithCatchHandlersTest(
      TestParameters parameters, boolean enableRewriting) {
    super(parameters, enableRewriting);
  }

  @Test
  public void testRewritingWithNoImpls() throws Exception {
    serviceLoaderTest(null)
        .addKeepRules("-assumenosideeffects class java.util.ServiceLoader { *** load(...); }")
        .addKeepMainRule(MainRunner.class)
        .enableInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), MainRunner.class)
        .assertSuccessWithOutputLines("Finally");
  }
}
