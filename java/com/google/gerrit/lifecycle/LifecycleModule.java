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

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Singleton;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.UniqueAnnotations;
import java.lang.annotation.Annotation;

/** Module to support registering a unique LifecyleListener. */
public abstract class LifecycleModule extends FactoryModule {
  /**
   * @return a unique listener binding.
   *     <p>To create a listener binding use:
   *     <pre>
   * listener().to(MyListener.class);
   * </pre>
   *     where {@code MyListener} is a {@link Singleton} implementing the {@link LifecycleListener}
   *     interface.
   */
  protected LinkedBindingBuilder<LifecycleListener> listener() {
    final Annotation id = UniqueAnnotations.create();
    return bind(LifecycleListener.class).annotatedWith(id);
  }
}
