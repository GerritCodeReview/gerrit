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
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Histogram2;
import com.google.gerrit.metrics.Histogram3;

/** Generalized implementation of N-dimensional Histogram metrics. */
class HistogramImplN extends BucketedHistogram implements BucketedMetric {
  HistogramImplN(DropWizardMetricMaker metrics, String name, Description desc, Field<?>... fields) {
    super(metrics, name, desc, fields);
  }

  <F1, F2> Histogram2<F1, F2> histogram2() {
    return new Histogram2<F1, F2>() {
      @Override
      public void record(F1 field1, F2 field2, long value) {
        total.record(value);
        forceCreate(field1, field2).record(value);
      }

      @Override
      public void remove() {
        doRemove();
      }
    };
  }

  <F1, F2, F3> Histogram3<F1, F2, F3> histogram3() {
    return new Histogram3<F1, F2, F3>() {
      @Override
      public void record(F1 field1, F2 field2, F3 field3, long value) {
        total.record(value);
        forceCreate(field1, field2, field3).record(value);
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
