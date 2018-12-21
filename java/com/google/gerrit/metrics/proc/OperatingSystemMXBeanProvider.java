// Copyright (C) 2017 The Android Open Source Project
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
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.Arrays;

class OperatingSystemMXBeanProvider {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final OperatingSystemMXBean sys;
  private final Method getProcessCpuTime;
  private final Method getOpenFileDescriptorCount;

  static class Factory {
    static OperatingSystemMXBeanProvider create() {
      OperatingSystemMXBean sys = ManagementFactory.getOperatingSystemMXBean();
      for (String name :
          Arrays.asList(
              "com.sun.management.UnixOperatingSystemMXBean",
              "com.ibm.lang.management.UnixOperatingSystemMXBean")) {
        try {
          Class<?> impl = Class.forName(name);
          if (impl.isInstance(sys)) {
            return new OperatingSystemMXBeanProvider(sys);
          }
        } catch (ReflectiveOperationException e) {
          logger.atFine().withCause(e).log("No implementation for %s", name);
        }
      }
      logger.atWarning().log("No implementation of UnixOperatingSystemMXBean found");
      return null;
    }
  }

  private OperatingSystemMXBeanProvider(OperatingSystemMXBean sys)
      throws ReflectiveOperationException {
    this.sys = sys;
    getProcessCpuTime = sys.getClass().getMethod("getProcessCpuTime");
    getProcessCpuTime.setAccessible(true);
    getOpenFileDescriptorCount = sys.getClass().getMethod("getOpenFileDescriptorCount");
    getOpenFileDescriptorCount.setAccessible(true);
  }

  public long getProcessCpuTime() {
    try {
      return (long) getProcessCpuTime.invoke(sys, new Object[] {});
    } catch (ReflectiveOperationException e) {
      return -1;
    }
  }

  public long getOpenFileDescriptorCount() {
    try {
      return (long) getOpenFileDescriptorCount.invoke(sys, new Object[] {});
    } catch (ReflectiveOperationException e) {
      return -1;
    }
  }
}
