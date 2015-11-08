// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.metrics.CallbackMetric;
import com.google.gerrit.metrics.Counter;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Rate;
import com.google.gerrit.metrics.Timer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class PluginMetricMaker extends MetricMaker implements LifecycleListener {
  private final MetricMaker root;
  private final String prefix;
  private final Set<RegistrationHandle> cleanup;

  PluginMetricMaker(MetricMaker root, String pluginName) {
    this.root = root;
    this.prefix = "plugins/" + pluginName;
    cleanup = Collections.synchronizedSet(new HashSet<RegistrationHandle>());
  }

  @Override
  public Counter newCounter(String name, Description desc) {
    Counter m = root.newCounter(prefix + name, desc);
    cleanup.add(m);
    return m;
  }

  @Override
  public Timer newTimer(String name, Description desc) {
    Timer m = root.newTimer(prefix + name, desc);
    cleanup.add(m);
    return m;
  }

  @Override
  public Rate newRate(String name, Description desc) {
    Rate m = root.newRate(prefix + name, desc);
    cleanup.add(m);
    return m;
  }

  @Override
  public <V> CallbackMetric<V> newCallbackMetric(String name,
      Class<V> valueClass, Description desc) {
    CallbackMetric<V> m = root.newCallbackMetric(prefix + name, valueClass, desc);
    cleanup.add(m);
    return m;
  }

  @Override
  public RegistrationHandle newTrigger(Set<CallbackMetric<?>> metrics,
      Runnable trigger) {
    final RegistrationHandle handle = root.newTrigger(metrics, trigger);
    cleanup.add(handle);
    return new RegistrationHandle() {
      @Override
      public void remove() {
        handle.remove();
        cleanup.remove(handle);
      }
    };
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
    synchronized (cleanup) {
      Iterator<RegistrationHandle> itr = cleanup.iterator();
      while (itr.hasNext()) {
        itr.next().remove();
        itr.remove();
      }
    }
  }
}
