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

package com.google.gerrit.server;

import com.google.common.flogger.FluentLogger;
import com.google.inject.servlet.RequestScoped;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/** Registers cleanup activities to be completed when a scope ends. */
@RequestScoped
public class RequestCleanup implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final List<Runnable> cleanup = new LinkedList<>();
  private boolean ran;

  /** Register a task to be completed after the request ends. */
  public void add(Runnable task) {
    synchronized (cleanup) {
      if (ran) {
        throw new IllegalStateException("Request has already been cleaned up");
      }
      cleanup.add(task);
    }
  }

  @Override
  public void run() {
    synchronized (cleanup) {
      ran = true;
      for (Iterator<Runnable> i = cleanup.iterator(); i.hasNext(); ) {
        try {
          i.next().run();
        } catch (Throwable err) {
          logger.atSevere().withCause(err).log("Failed to execute per-request cleanup");
        }
        i.remove();
      }
    }
  }
}
