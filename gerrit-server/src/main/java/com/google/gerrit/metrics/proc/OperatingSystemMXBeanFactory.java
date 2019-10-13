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

import com.sun.management.UnixOperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("restriction")
class OperatingSystemMXBeanFactory {
  private static final Logger log = LoggerFactory.getLogger(OperatingSystemMXBeanFactory.class);

  static OperatingSystemMXBeanInterface create() {
    OperatingSystemMXBean sys = ManagementFactory.getOperatingSystemMXBean();
    if (sys instanceof UnixOperatingSystemMXBean) {
      return new OperatingSystemMXBeanUnixNative((UnixOperatingSystemMXBean) sys);
    }

    for (String name :
        Arrays.asList(
            "com.sun.management.UnixOperatingSystemMXBean",
            "com.ibm.lang.management.UnixOperatingSystemMXBean")) {
      try {
        Class<?> impl = Class.forName(name);
        if (impl.isInstance(sys)) {
          return new OperatingSystemMXBeanReflectionBased(sys);
        }
      } catch (ReflectiveOperationException e) {
        log.debug("No implementation for {}", name, e);
      }
    }
    log.warn("No implementation of UnixOperatingSystemMXBean found");
    return null;
  }
}
