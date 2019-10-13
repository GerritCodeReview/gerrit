// Copyright (C) 2019 The Android Open Source Project
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

import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

class OperatingSystemMXBeanReflectionBased implements OperatingSystemMXBeanInterface {
  private final OperatingSystemMXBean sys;
  private final Method getProcessCpuTime;
  private final Method getOpenFileDescriptorCount;

  OperatingSystemMXBeanReflectionBased(OperatingSystemMXBean sys)
      throws ReflectiveOperationException {
    this.sys = sys;
    getProcessCpuTime = sys.getClass().getMethod("getProcessCpuTime");
    getProcessCpuTime.setAccessible(true);
    getOpenFileDescriptorCount = sys.getClass().getMethod("getOpenFileDescriptorCount");
    getOpenFileDescriptorCount.setAccessible(true);
  }

  @Override
  public long getProcessCpuTime() {
    try {
      return (long) getProcessCpuTime.invoke(sys, new Object[] {});
    } catch (ReflectiveOperationException e) {
      return -1;
    }
  }

  @Override
  public long getOpenFileDescriptorCount() {
    try {
      return (long) getOpenFileDescriptorCount.invoke(sys, new Object[] {});
    } catch (ReflectiveOperationException e) {
      return -1;
    }
  }
}
