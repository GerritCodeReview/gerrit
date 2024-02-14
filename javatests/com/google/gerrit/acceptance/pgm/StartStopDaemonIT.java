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
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

@Sandboxed
public class StartStopDaemonIT extends AbstractDaemonTest {
  Description suiteDescription = Description.createSuiteDescription(StartStopDaemonIT.class);

  @Override
  protected TestRule createTestRules() {
    TestRule innerRules = super.createTestRules();
    return RuleChain.outerRule(
            new TestRule() {
              @Override
              public Statement apply(Statement statement, Description description) {
                return new Statement() {
                  @Override
                  public void evaluate() throws Throwable {
                    ThreadMXBean thbean = ManagementFactory.getThreadMXBean();
                    int startThreads = thbean.getThreadCount();
                    statement.evaluate();
                    assertThat(Thread.activeCount()).isLessThan(startThreads);
                  }
                };
              }
            })
        .around(innerRules);
  }

  @Test
  public void sandboxedDaemonDoesNotLeakThreads_1() throws Exception {
    // dummy test - the sandboxed server will be started and then stopped
  }

  @Test
  public void sandboxedDaemonDoesNotLeakThreads_2() throws Exception {
    // dummy test - the sandboxed server will be started and then stopped
  }
}
