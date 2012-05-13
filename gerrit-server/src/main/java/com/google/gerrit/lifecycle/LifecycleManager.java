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

package com.google.gerrit.lifecycle;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Tracks and executes registered {@link LifecycleListener}s. */
public class LifecycleManager {
  private final LinkedHashMap<LifecycleListener, Boolean> listeners =
      new LinkedHashMap<LifecycleListener, Boolean>();

  private boolean started;

  /** Add a single listener. */
  public void add(final LifecycleListener listener) {
    listeners.put(listener, true);
  }

  /** Add all {@link LifecycleListener}s registered in the Injector. */
  public void add(final Injector injector) {
    if (started) {
      throw new IllegalStateException("Already started");
    }
    for (final Binding<LifecycleListener> binding : get(injector)) {
      add(binding.getProvider().get());
    }
  }

  /** Add all {@link LifecycleListener}s registered in the Injectors. */
  public void add(final Injector... injectors) {
    for (final Injector i : injectors) {
      add(i);
    }
  }

  /** Start all listeners, in the order they were registered. */
  public void start() {
    if (!started) {
      started = true;
      for (LifecycleListener obj : listeners.keySet()) {
        obj.start();
      }
    }
  }

  /** Stop all listeners, in the reverse order they were registered. */
  public void stop() {
    if (started) {
      final List<LifecycleListener> t =
          new ArrayList<LifecycleListener>(listeners.keySet());

      for (int i = t.size() - 1; 0 <= i; i--) {
        final LifecycleListener obj = t.get(i);
        try {
          obj.stop();
        } catch (Throwable err) {
          LoggerFactory.getLogger(obj.getClass()).warn("Failed to stop", err);
        }
      }

      started = false;
    }
  }

  private static List<Binding<LifecycleListener>> get(Injector i) {
    return i.findBindingsByType(new TypeLiteral<LifecycleListener>() {});
  }
}
