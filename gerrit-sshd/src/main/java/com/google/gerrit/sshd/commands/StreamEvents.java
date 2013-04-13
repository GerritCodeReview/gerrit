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

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.ChangeListener;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.CancelableRunnable;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.StreamCommandExecutor;
import com.google.gson.Gson;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

@RequiresCapability(GlobalCapability.STREAM_EVENTS)
@CommandMetaData(name = "stream-events", descr = "Monitor events occurring in real time")
final class StreamEvents extends BaseCommand {
  /** Maximum number of events that may be queued up for each connection. */
  private static final int MAX_EVENTS = 128;

  /** Number of events to write before yielding off the thread. */
  private static final int BATCH_SIZE = 32;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ChangeHooks hooks;

  @Inject
  @StreamCommandExecutor
  private WorkQueue.Executor pool;

  /** Queue of events to stream to the connected user. */
  private final LinkedBlockingQueue<ChangeEvent> queue =
      new LinkedBlockingQueue<ChangeEvent>(MAX_EVENTS);

  private final Gson gson = new Gson();

  /** Special event to notify clients they missed other events. */
  private final Object droppedOutputEvent = new Object() {
    @SuppressWarnings("unused")
    final String type = "dropped-output";
  };

  private final ChangeListener listener = new ChangeListener() {
    @Override
    public void onChangeEvent(final ChangeEvent event) {
      offer(event);
    }
  };

  private final CancelableRunnable writer = new CancelableRunnable() {
    @Override
    public void run() {
      writeEvents();
    }

    @Override
    public void cancel() {
      onExit(0);
    }
  };

  /** True if {@link #droppedOutputEvent} needs to be sent. */
  private volatile boolean dropped;

  /** Lock to protect {@link #queue}, {@link #task}, {@link #done}. */
  private final Object taskLock = new Object();

  /** True if no more messages should be sent to the output. */
  private boolean done;

  /**
   * Currently scheduled task to spin out {@link #queue}.
   * <p>
   * This field is usually {@code null}, unless there is at least one object
   * present inside of {@link #queue} ready for delivery. Tasks are only started
   * when there are events to be sent.
   */
  private Future<?> task;

  private PrintWriter stdout;

  @Override
  public void start(final Environment env) throws IOException {
    try {
      parseCommandLine();
    } catch (UnloggedFailure e) {
      String msg = e.getMessage();
      if (!msg.endsWith("\n")) {
        msg += "\n";
      }
      err.write(msg.getBytes("UTF-8"));
      err.flush();
      onExit(1);
      return;
    }

    stdout = toPrintWriter(out);
    hooks.addChangeListener(listener, currentUser);
  }

  @Override
  protected void onExit(final int rc) {
    hooks.removeChangeListener(listener);

    synchronized (taskLock) {
      done = true;
    }

    super.onExit(rc);
  }

  @Override
  public void destroy() {
    hooks.removeChangeListener(listener);

    final boolean exit;
    synchronized (taskLock) {
      if (task != null) {
        task.cancel(true);
        exit = false; // onExit will be invoked by the task cancellation.
      } else {
        exit = !done;
      }
      done = true;
    }
    if (exit) {
      onExit(0);
    }
  }

  private void offer(final ChangeEvent event) {
    synchronized (taskLock) {
      if (!queue.offer(event)) {
        dropped = true;
      }

      if (task == null && !done) {
        task = pool.submit(writer);
      }
    }
  }

  private ChangeEvent poll() {
    synchronized (taskLock) {
      ChangeEvent event = queue.poll();
      if (event == null) {
        task = null;
      }
      return event;
    }
  }

  private void writeEvents() {
    int processed = 0;

    while (processed < BATCH_SIZE) {
      if (Thread.interrupted() || stdout.checkError()) {
        // The other side either requested a shutdown by calling our
        // destroy() above, or it closed the stream and is no longer
        // accepting output. Either way terminate this instance.
        //
        hooks.removeChangeListener(listener);
        flush();
        onExit(0);
        return;
      }

      if (dropped) {
        write(droppedOutputEvent);
        dropped = false;
      }

      final ChangeEvent event = poll();
      if (event == null) {
        break;
      }

      write(event);
      processed++;
    }

    flush();

    if (BATCH_SIZE <= processed) {
      // We processed the limit, but more might remain in the queue.
      // Schedule the write task again so we will come back here and
      // can process more events.
      //
      synchronized (taskLock) {
        task = pool.submit(writer);
      }
    }
  }

  private void write(final Object message) {
    final String msg = gson.toJson(message) + "\n";
    synchronized (stdout) {
      stdout.print(msg);
    }
  }

  private void flush() {
    synchronized (stdout) {
      stdout.flush();
    }
  }
}
