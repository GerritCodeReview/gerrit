// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

/**
 * Test command that reproduces the exception shape seen when an SSH client disconnects while the
 * worker thread is blocked in a library call.
 */
@CommandMetaData(
    name = "interrupted",
    description = "Test command that wraps an interrupt in an unchecked exception",
    runsAt = MASTER_OR_SLAVE)
public class InterruptedCommand extends SshCommand {
  /** Tripped once the command is running and ready to be interrupted. */
  public static final CyclicBarrier syncPoint = new CyclicBarrier(2);

  /** Counted down immediately before the wrapped exception is thrown. */
  public static final CountDownLatch threwWrapped = new CountDownLatch(1);

  @Override
  protected void run() throws Exception {
    syncPoint.await();
    try {
      Thread.sleep(Long.MAX_VALUE);
    } catch (InterruptedException e) {
      threwWrapped.countDown();
      throw new RuntimeException("thread waiting for the response was interrupted", e);
    }
  }
}
