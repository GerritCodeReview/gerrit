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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Timer2;
import com.google.gerrit.metrics.Timer3;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Generalized implementation of N-dimensional timer metrics. */
class TimerImplN extends BucketedTimer implements BucketedMetric {
  TimerImplN(DropWizardMetricMaker metrics, String name, Description desc, Field<?>... fields) {
    super(metrics, name, desc, fields);
  }

  @SuppressWarnings("unchecked")
  <F1, F2> Timer2<F1, F2> timer2() {
    return new Timer2<F1, F2>(name, (Field<F1>) fields[0], (Field<F2>) fields[1]) {
      @Override
      protected void doRecord(F1 field1, F2 field2, long value, TimeUnit unit) {
        total.record(value, unit);
        forceCreate(field1, field2).record(value, unit);
      }

      @Override
      public void remove() {
        doRemove();
      }
    };
  }

  @SuppressWarnings("unchecked")
  <F1, F2, F3> Timer3<F1, F2, F3> timer3() {
    return new Timer3<F1, F2, F3>(
        name, (Field<F1>) fields[0], (Field<F2>) fields[1], (Field<F3>) fields[2]) {
      @Override
      protected void doRecord(F1 field1, F2 field2, F3 field3, long value, TimeUnit unit) {
        total.record(value, unit);
        forceCreate(field1, field2, field3).record(value, unit);
      }

      @Override
      public void remove() {
        doRemove();
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Override
  String name(Object key) {
    ImmutableList<Object> keyList = (ImmutableList<Object>) key;
    String[] parts = new String[fields.length];
    for (int i = 0; i < fields.length; i++) {
      Function<Object, String> fmt = (Function<Object, String>) fields[i].formatter();

      parts[i] = fmt.apply(keyList.get(i)).replace('/', '-');
    }
    return Joiner.on('/').join(parts);
  }
}
