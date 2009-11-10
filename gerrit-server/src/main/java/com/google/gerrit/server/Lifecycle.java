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

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.UniqueAnnotations;

import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Lifecycle {
  /** Start all listeners in the order of registration. */
  public static void start(final Injector... injectors) {
    for (final LifecycleListener obj : all(injectors)) {
      obj.start();
    }
  }

  /** Stop all listeners in the reverse order of registration. */
  public static void stop(final Injector... injectors) {
    final LifecycleListener[] all = all(injectors);
    for (int i = all.length - 1; 0 <= i; i--) {
      final LifecycleListener obj = all[i];
      try {
        obj.stop();
      } catch (Throwable err) {
        LoggerFactory.getLogger(obj.getClass()).warn("Failed to stop", err);
      }
    }
  }

  private static LifecycleListener[] all(final Injector... injectors) {
    final Map<LifecycleListener, Boolean> found =
        new LinkedHashMap<LifecycleListener, Boolean>();

    for (final Injector injector : injectors) {
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

  private Lifecycle() {
  }

  /** Module to support registering a unique LifecyleListener. */
  public static abstract class Module extends AbstractModule {
    /**
     * Create a unique listener binding.
     * <p>
     * To create a listener binding use:
     *
     * <pre>
     * listener().to(MyListener.class);
     * </pre>
     *
     * where {@code MyListener} is a {@link Singleton} implementing the
     * {@link LifecycleListener} interface.
     */
    protected LinkedBindingBuilder<LifecycleListener> listener() {
      final Annotation id = UniqueAnnotations.create();
      return bind(LifecycleListener.class).annotatedWith(id);
    }
  }
}
