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
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Timer1;
import java.util.concurrent.TimeUnit;

/** Optimized version of {@link BucketedTimer} for single dimension. */
class TimerImpl1<F1> extends BucketedTimer implements BucketedMetric {
  TimerImpl1(DropWizardMetricMaker metrics, String name, Description desc, Field<F1> field1) {
    super(metrics, name, desc, field1);
  }

  Timer1<F1> timer() {
    return new Timer1<F1>() {
      @Override
      public void record(F1 field1, long value, TimeUnit unit) {
        total.record(value, unit);
        forceCreate(field1).record(value, unit);
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
