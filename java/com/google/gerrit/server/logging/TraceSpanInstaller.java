// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.logging;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Bridges the Guice-managed {@link DynamicSet} of {@link TraceSpanListener}s to {@link
 * TraceContext}.
 */
@Singleton
public class TraceSpanInstaller implements LifecycleListener {
  private final DynamicSet<TraceSpanListener> listeners;

  @Inject
  TraceSpanInstaller(DynamicSet<TraceSpanListener> listeners) {
    this.listeners = listeners;
    TraceContext.setTraceSpanListeners(listeners);
  }

  @Override
  public void start() {
    TraceContext.setTraceSpanListeners(listeners);
  }

  @Override
  public void stop() {
    TraceContext.setTraceSpanListeners(null);
  }
}
