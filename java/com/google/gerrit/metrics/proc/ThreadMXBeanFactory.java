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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class ThreadMXBeanFactory {

  private ThreadMXBeanFactory() {}

  public static ThreadMXBeanInterface create() {
    ThreadMXBean sys = ManagementFactory.getThreadMXBean();
    if (sys instanceof com.sun.management.ThreadMXBean) {
      return new ThreadMXBeanSun(sys);
    }
    return new ThreadMXBeanJava(sys);
  }
}
