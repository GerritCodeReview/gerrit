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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Supplier;
import com.google.gerrit.common.UserScopedEventListener;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventTypes;
import com.google.gerrit.server.events.ProjectNameKeySerializer;
import com.google.gerrit.server.events.SupplierSerializer;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.CancelableRunnable;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.StreamCommandExecutor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiresCapability(GlobalCapability.STREAM_EVENTS)
@CommandMetaData(name = "stream-events", description = "Monitor events occurring in real time")
final class StreamEvents extends BaseCommand {
  private static final Logger log = LoggerFactory.getLogger(StreamEvents.class);

  /** Maximum number of events that may be queued up for each connection. */
  private static final int MAX_EVENTS = 128;

  /** Number of events to write before yielding off the thread. */
  private static final int BATCH_SIZE = 32;

  @Option(
    name = "--subscribe",
    aliases = {"-s"},
    metaVar = "SUBSCRIBE",
    usage = "subscribe to specific stream-events"
  )
  private List<String> subscribedToEvents = new ArrayList<>();

  @Inject private IdentifiedUser currentUser;

  @Inject private DynamicSet<UserScopedEventListener> eventListeners;

  @Inject @StreamCommandExecutor private WorkQueue.Executor pool;

  /** Queue of events to stream to the connected user. */
  private final LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>(MAX_EVENTS);

  private Gson gson;

  private RegistrationHandle eventListenerRegistration;

  /** Special event to notify clients they missed other events. */
  private static final class DroppedOutputEvent extends Event {
    private static final String TYPE = "dropped-output";

    DroppedOutputEvent() {
      super(TYPE);
    }
  }

  static {
    EventTypes.register(DroppedOutputEvent.TYPE, DroppedOutputEvent.class);
  }

  private final CancelableRunnable writer =
      new CancelableRunnable() {
        @Override
        public void run() {
          writeEvents();
        }

        @Override
        public void cancel() {
          onExit(0);
        }

        @Override
        public String toString() {
          return "Stream Events (" + currentUser.getAccount().getUserName() + ")";
        }
      };

  /** True if {@link DroppedOutputEvent} needs to be sent. */
  private volatile boolean dropped;

  /** Lock to protect {@link #queue}, {@link #task}, {@link #done}. */
  private final Object taskLock = new Object();

  /** True if no more messages should be sent to the output. */
  private boolean done;

  /**
   * Currently scheduled task to spin out {@link #queue}.
   *
   * <p>This field is usually {@code null}, unless there is at least one object present inside of
   * {@link #queue} ready for delivery. Tasks are only started when there are events to be sent.
   */
  private Future<?> task;

  private PrintWriter stdout;

  @Override
  public void start(Environment env) throws IOException {
    try {
      parseCommandLine();
    } catch (UnloggedFailure e) {
      String msg = e.getMessage();
      if (!msg.endsWith("\n")) {
        msg += "\n";
      }
      err.write(msg.getBytes(UTF_8));
      err.flush();
      onExit(1);
      return;
    }

    stdout = toPrintWriter(out);
    eventListenerRegistration =
        eventListeners.add(
            new UserScopedEventListener() {
              @Override
              public void onEvent(Event event) {
                if (subscribedToEvents.isEmpty() || subscribedToEvents.contains(event.getType())) {
                  offer(event);
                }
              }

              @Override
              public CurrentUser getUser() {
                return currentUser;
              }
            });

    gson =
        new GsonBuilder()
            .registerTypeAdapter(Supplier.class, new SupplierSerializer())
            .registerTypeAdapter(Project.NameKey.class, new ProjectNameKeySerializer())
            .create();
  }

  @Override
  protected void onExit(int rc) {
    eventListenerRegistration.remove();

    synchronized (taskLock) {
      done = true;
    }

    super.onExit(rc);
  }

  @Override
  public void destroy() {
    eventListenerRegistration.remove();

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

  private void offer(Event event) {
    synchronized (taskLock) {
      if (!queue.offer(event)) {
        dropped = true;
      }

      if (task == null && !done) {
        task = pool.submit(writer);
      }
    }
  }

  private Event poll() {
    synchronized (taskLock) {
      Event event = queue.poll();
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
        eventListenerRegistration.remove();
        flush();
        onExit(0);
        return;
      }

      if (dropped) {
        write(new DroppedOutputEvent());
        dropped = false;
      }

      final Event event = poll();
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

  private void write(Object message) {
    String msg = null;
    try {
      msg = gson.toJson(message) + "\n";
    } catch (Exception e) {
      log.warn("Could not deserialize the msg: ", e);
    }
    if (msg != null) {
      synchronized (stdout) {
        stdout.print(msg);
      }
    }
  }

  private void flush() {
    synchronized (stdout) {
      stdout.flush();
    }
  }
}
