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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Timer2;

import java.util.concurrent.TimeUnit;

/** Generalized implementation of N-dimensional timer metrics. */
class TimerImplN extends BucketedTimer implements BucketedMetric {
  TimerImplN(DropWizardMetricMaker metrics, String name, Field<?>... fields) {
    super(metrics, name, fields);
  }

  <F1, F2> Timer2<F1, F2> timer2() {
    return new Timer2<F1, F2>() {
      @Override
      public void record(F1 field1, F2 field2, long value, TimeUnit unit) {
        total.record(value, unit);
        forceCreate(field1).record(value, unit);
      }

      @Override
      public void remove() {
        doRemove();
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Override
  String makeSuffix(Object key) {
    ImmutableList<Object> keyList = (ImmutableList<Object>) key;
    String[] parts = new String[fields.length];
    for (int i = 0; i < fields.length; i++) {
      Function<Object, String> fmt =
          (Function<Object, String>) fields[i].formatter();

      parts[i] = metrics.encode(fmt.apply(keyList.get(i)));
    }
    return Joiner.on('/').join(parts);
  }
}
