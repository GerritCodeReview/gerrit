// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.ChangeListener;
import com.google.gerrit.common.ChangeHookRunner.AbsChangeEvent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.sshd.StreamCommand;
import com.google.gson.Gson;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;

import java.io.PrintWriter;
import java.util.concurrent.LinkedBlockingQueue;

@StreamCommand
final class StreamChangeEvents extends BaseCommand {
  /** Maximum number of events that may be queued up for each connection. */
  private static final int MAX_EVENTS = 16;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ChangeHookRunner hooks;

  /** Queue of events to stream to the connected user. */
  private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<Object>(MAX_EVENTS);

  private final Gson gson = new Gson();

  private final String droppedOutputEvent =
      gson.toJson(new Object() {
        @SuppressWarnings("unused")
        String type = "dropped-output";
      });

  private volatile boolean dropped = false;

  private ChangeListener listener = new ChangeListener() {
    @Override
    public void onChangeEvent(final AbsChangeEvent event) {
      if (!queue.offer(event)) {
        dropped = true;
      }
    }
  };

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        StreamChangeEvents.this.display();
      }
    });
  }

  private void display() throws Failure {
    final PrintWriter stdout = toPrintWriter(out);

    hooks.addChangeListener(listener, currentUser);

    try {
      while (!stdout.checkError()) {
        stdout.println(gson.toJson(queue.take()));
        stdout.flush();

        if (dropped) {
          stdout.println(droppedOutputEvent);
          stdout.flush();
          dropped = false;
        }
      }
    } catch (InterruptedException e) {
      // exit
    } finally {
      hooks.removeChangeListener(listener);
    }
  }
}
