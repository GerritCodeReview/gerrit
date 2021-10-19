// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.util;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.fail;

import com.google.gerrit.acceptance.AbstractPluginLogFileTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseSsh;
import com.google.inject.Inject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Test;

@NoHttpd
@UseSsh
public class PluginLogFileIT extends AbstractPluginLogFileTest {
  @Inject private InvocationCounter invocationCounter;
  private static final int NUMBER_OF_THREADS = 5;

  @Test
  public void testMultiThreadedPluginLogFile() throws Exception {
    try (AutoCloseable ignored = installPlugin("my-plugin", TestModule.class)) {
      ExecutorService service = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
      CountDownLatch latch = new CountDownLatch(NUMBER_OF_THREADS);
      createChange();
      for (int i = 0; i < NUMBER_OF_THREADS; i++) {
        service.execute(
            () -> {
              try {
                adminSshSession.exec("gerrit query --format json status:open --my-plugin--opt");
                adminSshSession.assertSuccess();
              } catch (Exception e) {
                fail(e.getMessage());
              } finally {
                latch.countDown();
              }
            });
      }
      latch.await();
      assertEquals(1, invocationCounter.getCounter());
    }
  }
}
