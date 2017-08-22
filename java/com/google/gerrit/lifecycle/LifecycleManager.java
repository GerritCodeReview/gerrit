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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Tracks and executes registered {@link LifecycleListener}s. */
public class LifecycleManager {
  private final List<Provider<LifecycleListener>> listeners = newList();
  private final List<RegistrationHandle> handles = newList();

  /** Index of the last listener to start successfully; -1 when not started. */
  private int startedIndex = -1;

  /**
   * Add a handle that must be cleared during stop.
   *
   * @param handle the handle to add.
   */
  public void add(RegistrationHandle handle) {
    handles.add(handle);
  }

  /**
   * Add a single listener.
   *
   * @param listener the listener to add.
   */
  public void add(LifecycleListener listener) {
    listeners.add(Providers.of(listener));
  }

  /**
   * Add a single listener.
   *
   * @param listener the listener to add.
   */
  public void add(Provider<LifecycleListener> listener) {
    listeners.add(listener);
  }

  /**
   * Add all {@link LifecycleListener}s registered in the Injector.
   *
   * @param injector the injector to add.
   */
  public void add(Injector injector) {
    Preconditions.checkState(startedIndex < 0, "Already started");
    for (Binding<LifecycleListener> binding : get(injector)) {
      add(binding.getProvider());
    }
  }

  /**
   * Add all {@link LifecycleListener}s registered in the Injectors.
   *
   * @param injectors the injectors to add.
   */
  public void add(Injector... injectors) {
    for (Injector i : injectors) {
      add(i);
    }
  }

  /** Start all listeners, in the order they were registered. */
  public void start() {
    for (int i = startedIndex + 1; i < listeners.size(); i++) {
      LifecycleListener listener = listeners.get(i).get();
      startedIndex = i;
      listener.start();
    }
  }

  /** Stop all listeners, in the reverse order they were registered. */
  public void stop() {
    for (int i = handles.size() - 1; 0 <= i; i--) {
      handles.get(i).remove();
    }
    handles.clear();

    for (int i = startedIndex; 0 <= i; i--) {
      LifecycleListener obj = listeners.get(i).get();
      try {
        obj.stop();
      } catch (Throwable err) {
        LoggerFactory.getLogger(obj.getClass()).warn("Failed to stop", err);
      }
      startedIndex = i - 1;
    }
  }

  private static List<Binding<LifecycleListener>> get(Injector i) {
    return i.findBindingsByType(new TypeLiteral<LifecycleListener>() {});
  }

  private static <T> List<T> newList() {
    return Lists.newArrayListWithCapacity(4);
  }
}
