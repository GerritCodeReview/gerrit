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

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tracks and executes registered {@link LifecycleListener}s. */
public class LifecycleManager {
  private final List<Injector> injectors = new ArrayList<Injector>();
  private LifecycleListener[] listeners;

  /** Add all {@link LifecycleListener}s registered in the Injector. */
  public void add(final Injector injector) {
    if (listeners != null) {
      throw new IllegalStateException("Already started");
    }
    injectors.add(injector);
  }

  /** Add all {@link LifecycleListener}s registered in the Injectors. */
  public void add(final Injector... injectors) {
    for (final Injector i : injectors) {
      add(i);
    }
  }

  /** Start all listeners, in the order they were registered. */
  public void start() {
    if (listeners == null) {
      listeners = all();

      for (LifecycleListener obj : listeners) {
        obj.start();
      }
    }
  }

  /** Stop all listeners, in the reverse order they were registered. */
  public void stop() {
    if (listeners != null) {
      for (int i = listeners.length - 1; 0 <= i; i--) {
        final LifecycleListener obj = listeners[i];
        try {
          obj.stop();
        } catch (Throwable err) {
          LoggerFactory.getLogger(obj.getClass()).warn("Failed to stop", err);
        }
      }

      listeners = null;
    }
  }

  private LifecycleListener[] all() {
    final Map<LifecycleListener, Boolean> found =
        new LinkedHashMap<LifecycleListener, Boolean>();

    for (final Injector injector : injectors) {
      if (injector == null) {
        continue;
      }
      for (final Binding<LifecycleListener> binding : get(injector)) {
        found.put(binding.getProvider().get(), true);
      }
    }

    final LifecycleListener[] a = new LifecycleListener[found.size()];
    found.keySet().toArray(a);
    return a;
  }

  private static List<Binding<LifecycleListener>> get(Injector i) {
    return i.findBindingsByType(new TypeLiteral<LifecycleListener>() {});
  }
}
