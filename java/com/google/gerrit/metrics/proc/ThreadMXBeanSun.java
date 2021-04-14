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

import com.sun.management.ThreadMXBean;

class ThreadMXBeanSun implements ThreadMXBeanInterface {
  private final ThreadMXBean sys;

  ThreadMXBeanSun(java.lang.management.ThreadMXBean sys) {
    this.sys = (ThreadMXBean) sys;
  }

  @Override
  public long getCurrentThreadCpuTime() {
    return sys.getCurrentThreadCpuTime();
  }

  @Override
  public long getCurrentThreadUserTime() {
    return sys.getCurrentThreadUserTime();
  }

  @Override
  public long getCurrentThreadAllocatedBytes() {
    // TODO(ms): call getCurrentThreadAllocatedBytes as soon as this is available in the patched
    // Java version used by bazel
    return sys.getThreadAllocatedBytes(Thread.currentThread().getId());
  }
}
