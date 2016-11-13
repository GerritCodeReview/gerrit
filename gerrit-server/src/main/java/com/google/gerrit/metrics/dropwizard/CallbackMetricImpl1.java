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

package com.google.gerrit.metrics.dropwizard;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Function;
import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;

/** Optimized version of {@link BucketedCallback} for single dimension. */
class CallbackMetricImpl1<F1, V> extends BucketedCallback<V> {
  CallbackMetricImpl1(
      DropWizardMetricMaker metrics,
      MetricRegistry registry,
      String name,
      Class<V> valueClass,
      Description desc,
      Field<F1> field1) {
    super(metrics, registry, name, valueClass, desc, field1);
  }

  CallbackMetric1<F1, V> create() {
    return new Impl1();
  }

  private final class Impl1 extends CallbackMetric1<F1, V> implements CallbackMetricGlue {
    @Override
    public void beginSet() {
      doBeginSet();
    }

    @Override
    public void set(F1 field1, V value) {
      BucketedCallback<V>.ValueGauge cell = getOrCreate(field1);
      cell.value = value;
      cell.set = true;
    }

    @Override
    public void prune() {
      doPrune();
    }

    @Override
    public void endSet() {
      doEndSet();
    }

    @Override
    public void forceCreate(F1 field1) {
      getOrCreate(field1);
    }

    @Override
    public void register(Runnable t) {
      trigger = t;
    }

    @Override
    public void remove() {
      doRemove();
    }
  }

  @Override
  String name(Object field1) {
    @SuppressWarnings("unchecked")
    Function<Object, String> fmt = (Function<Object, String>) fields[0].formatter();

    return fmt.apply(field1).replace('/', '-');
  }
}
