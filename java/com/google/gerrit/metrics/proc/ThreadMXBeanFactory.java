// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.metrics.proc;

import com.google.common.flogger.FluentLogger;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class ThreadMXBeanFactory {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private ThreadMXBeanFactory() {}

  public static ThreadMXBeanInterface create() {
    ThreadMXBean sys = ManagementFactory.getThreadMXBean();
    try {
      Class<?> cls = Class.forName("com.sun.management.ThreadMXBean");
      if (cls.isInstance(sys)) {
        return new ThreadMXBeanSunReflective(sys);
      }
    } catch (ReflectiveOperationException e) {
      logger.atWarning().log("No implementation of ThreadMXBeanSun found");
    }
    return new ThreadMXBeanJava(sys);
  }
}
