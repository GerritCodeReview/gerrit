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
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;

/** Optimized version of {@link BucketedCounter} for single dimension. */
class CounterImpl1<F1> extends BucketedCounter {
  CounterImpl1(DropWizardMetricMaker metrics, String name, Description desc, Field<F1> field1) {
    super(metrics, name, desc, field1);
  }

  Counter1<F1> counter() {
    return new Counter1<F1>() {
      @Override
      public void incrementBy(F1 field1, long value) {
        total.incrementBy(value);
        forceCreate(field1).incrementBy(value);
      }

      @Override
      public void remove() {
        doRemove();
      }
    };
  }

  @Override
  String name(Object field1) {
    @SuppressWarnings("unchecked")
    Function<Object, String> fmt = (Function<Object, String>) fields[0].formatter();

    return fmt.apply(field1).replace('/', '-');
  }
}
