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
import com.google.gerrit.metrics.CallbackMetric0;
import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Histogram0;
import com.google.gerrit.metrics.Histogram1;
import com.google.gerrit.metrics.Histogram2;
import com.google.gerrit.metrics.Histogram3;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.metrics.Timer2;
import com.google.gerrit.metrics.Timer3;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class PluginMetricMaker extends MetricMaker implements LifecycleListener {
  private final MetricMaker root;
  private final String prefix;
  private final Set<RegistrationHandle> cleanup;

  public PluginMetricMaker(MetricMaker root, String pluginName) {
    this.root = root;
    this.prefix = String.format("plugins/%s/", pluginName);
    cleanup = Collections.synchronizedSet(new HashSet<RegistrationHandle>());
  }

  @Override
  public Counter0 newCounter(String name, Description desc) {
    Counter0 m = root.newCounter(prefix + name, desc);
    cleanup.add(m);
    return m;
  }

  @Override
  public <F1> Counter1<F1> newCounter(String name, Description desc, Field<F1> field1) {
    Counter1<F1> m = root.newCounter(prefix + name, desc, field1);
    cleanup.add(m);
    return m;
  }

  @Override
  public <F1, F2> Counter2<F1, F2> newCounter(
      String name, Description desc, Field<F1> field1, Field<F2> field2) {
    Counter2<F1, F2> m = root.newCounter(prefix + name, desc, field1, field2);
    cleanup.add(m);
    return m;
  }

  @Override
  public <F1, F2, F3> Counter3<F1, F2, F3> newCounter(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    Counter3<F1, F2, F3> m = root.newCounter(prefix + name, desc, field1, field2, field3);
    cleanup.add(m);
    return m;
  }

  @Override
  public Timer0 newTimer(String name, Description desc) {
    Timer0 m = root.newTimer(prefix + name, desc);
    cleanup.add(m);
    return m;
  }

  @Override
  public <F1> Timer1<F1> newTimer(String name, Description desc, Field<F1> field1) {
    Timer1<F1> m = root.newTimer(prefix + name, desc, field1);
    cleanup.add(m);
    return m;
  }

  @Override
  public <F1, F2> Timer2<F1, F2> newTimer(
      String name, Description desc, Field<F1> field1, Field<F2> field2) {
    Timer2<F1, F2> m = root.newTimer(prefix + name, desc, field1, field2);
    cleanup.add(m);
    return m;
  }

  @Override
  public <F1, F2, F3> Timer3<F1, F2, F3> newTimer(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    Timer3<F1, F2, F3> m = root.newTimer(prefix + name, desc, field1, field2, field3);
    cleanup.add(m);
    return m;
  }

  @Override
  public Histogram0 newHistogram(String name, Description desc) {
    Histogram0 m = root.newHistogram(prefix + name, desc);
    cleanup.add(m);
    return m;
  }

  @Override
  public <F1> Histogram1<F1> newHistogram(String name, Description desc, Field<F1> field1) {
    Histogram1<F1> m = root.newHistogram(prefix + name, desc, field1);
    cleanup.add(m);
    return m;
  }

  @Override
  public <F1, F2> Histogram2<F1, F2> newHistogram(
      String name, Description desc, Field<F1> field1, Field<F2> field2) {
    Histogram2<F1, F2> m = root.newHistogram(prefix + name, desc, field1, field2);
    cleanup.add(m);
    return m;
  }

  @Override
  public <F1, F2, F3> Histogram3<F1, F2, F3> newHistogram(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    Histogram3<F1, F2, F3> m = root.newHistogram(prefix + name, desc, field1, field2, field3);
    cleanup.add(m);
    return m;
  }

  @Override
  public <V> CallbackMetric0<V> newCallbackMetric(
      String name, Class<V> valueClass, Description desc) {
    CallbackMetric0<V> m = root.newCallbackMetric(prefix + name, valueClass, desc);
    cleanup.add(m);
    return m;
  }

  @Override
  public <F1, V> CallbackMetric1<F1, V> newCallbackMetric(
      String name, Class<V> valueClass, Description desc, Field<F1> field1) {
    CallbackMetric1<F1, V> m = root.newCallbackMetric(prefix + name, valueClass, desc, field1);
    cleanup.add(m);
    return m;
  }

  @Override
  public RegistrationHandle newTrigger(Set<CallbackMetric<?>> metrics, Runnable trigger) {
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
  public void start() {}

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
