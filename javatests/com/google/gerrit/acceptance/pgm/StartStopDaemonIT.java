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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import org.junit.Test;
import org.junit.runner.Description;

@Sandboxed
public class StartStopDaemonIT extends AbstractDaemonTest {
  Description suiteDescription = Description.createSuiteDescription(StartStopDaemonIT.class);

  @Test
  public void sandboxedDaemonDoesNotLeakThreads() throws Exception {
    ThreadMXBean thbean = ManagementFactory.getThreadMXBean();
    int startThreads = thbean.getThreadCount();
    beforeTest(suiteDescription);
    afterTest();
    assertThat(Thread.activeCount()).isLessThan(startThreads);
  }
}
