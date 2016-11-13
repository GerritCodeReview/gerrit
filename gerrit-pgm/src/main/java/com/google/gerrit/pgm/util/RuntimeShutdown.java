// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.util;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimeShutdown {
  private static final ShutdownCallback cb = new ShutdownCallback();

  /** Add a task to be performed when graceful shutdown is requested. */
  public static void add(final Runnable task) {
    if (!cb.add(task)) {
      // If the shutdown has already begun we cannot enqueue a new
      // task. Instead trigger the task in the caller, without any
      // of our locks held.
      //
      task.run();
    }
  }

  /** Wait for the JVM shutdown to occur. */
  public static void waitFor() {
    cb.waitForShutdown();
  }

  public static void manualShutdown() {
    cb.manualShutdown();
  }

  private RuntimeShutdown() {}

  private static class ShutdownCallback extends Thread {
    private static final Logger log = LoggerFactory.getLogger(ShutdownCallback.class);

    private final List<Runnable> tasks = new ArrayList<>();
    private boolean shutdownStarted;
    private boolean shutdownComplete;

    ShutdownCallback() {
      setName("ShutdownCallback");
    }

    boolean add(final Runnable newTask) {
      synchronized (this) {
        if (!shutdownStarted && !shutdownComplete) {
          if (tasks.isEmpty()) {
            Runtime.getRuntime().addShutdownHook(this);
          }
          tasks.add(newTask);
          return true;
        }
        // We don't permit adding a task once shutdown has started.
        //
        return false;
      }
    }

    @Override
    public void run() {
      log.debug("Graceful shutdown requested");

      List<Runnable> taskList;
      synchronized (this) {
        shutdownStarted = true;
        taskList = tasks;
      }

      for (Runnable task : taskList) {
        try {
          task.run();
        } catch (Exception err) {
          log.error("Cleanup task failed", err);
        }
      }

      log.debug("Shutdown complete");

      synchronized (this) {
        shutdownComplete = true;
        notifyAll();
      }
    }

    void manualShutdown() {
      Runtime.getRuntime().removeShutdownHook(this);
      run();
    }

    void waitForShutdown() {
      synchronized (this) {
        while (!shutdownComplete) {
          try {
            wait();
          } catch (InterruptedException e) {
            return;
          }
        }
      }
    }
  }
}
