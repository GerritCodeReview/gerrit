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
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.server.cancellation.RequestCancelledException;
import com.google.gerrit.server.cancellation.RequestStateContext;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.server.channel.ChannelSession;

/**
 * Test command that, like commands which write data, is not cancelled when the client disconnects.
 *
 * <p>It waits until it was destroyed, records whether that interrupted or cancelled it, and then
 * waits until it is released or killed.
 */
@CommandMetaData(
    name = "blocking-write",
    description = "Test command that is not cancelled when the client disconnects",
    runsAt = MASTER_OR_SLAVE)
public class BlockingWriteCommand extends SshCommand {
  public static CountDownLatch running;
  public static CountDownLatch destroyed;
  public static CountDownLatch release;
  public static CountDownLatch killed;
  public static CountDownLatch finished;
  public static final AtomicBoolean interruptedOnDisconnect = new AtomicBoolean();
  public static final AtomicBoolean cancelledOnDisconnect = new AtomicBoolean();

  public static void reset() {
    running = new CountDownLatch(1);
    destroyed = new CountDownLatch(1);
    release = new CountDownLatch(1);
    killed = new CountDownLatch(1);
    finished = new CountDownLatch(1);
    interruptedOnDisconnect.set(false);
    cancelledOnDisconnect.set(false);
  }

  @Override
  public void destroy(ChannelSession channel) {
    super.destroy(channel);
    destroyed.countDown();
  }

  @Override
  protected void run() throws Exception {
    running.countDown();
    try {
      destroyed.await(30, SECONDS);
      interruptedOnDisconnect.set(Thread.currentThread().isInterrupted());
      try {
        RequestStateContext.abortIfCancelled();
      } catch (RequestCancelledException e) {
        cancelledOnDisconnect.set(true);
      }
      release.await(30, SECONDS);
    } catch (InterruptedException e) {
      killed.countDown();
      throw e;
    } finally {
      finished.countDown();
    }
  }
}
