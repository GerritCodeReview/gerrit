// Copyright (C) 2022 The Android Open Source Project
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

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.UniformReservoir;
import com.google.gerrit.metrics.MetricsReservoirConfig;
import com.google.gerrit.metrics.ReservoirType;
import java.util.concurrent.TimeUnit;

class DropWizardReservoirProvider {

  private DropWizardReservoirProvider() {}

  static Reservoir get(MetricsReservoirConfig config) {
    ReservoirType reservoirType = config.reservoirType();
    switch (reservoirType) {
      case ExponentiallyDecaying:
        return new ExponentiallyDecayingReservoir(config.reservoirSize(), config.reservoirAlpha());
      case SlidingTimeWindowArray:
        return new SlidingTimeWindowArrayReservoir(
            config.reservoirWindow().toMillis(), TimeUnit.MILLISECONDS);
      case SlidingTimeWindow:
        return new SlidingTimeWindowReservoir(
            config.reservoirWindow().toMillis(), TimeUnit.MILLISECONDS);
      case SlidingWindow:
        return new SlidingWindowReservoir(config.reservoirSize());
      case Uniform:
        return new UniformReservoir(config.reservoirSize());

      default:
        throw new IllegalArgumentException(
            "Unsupported metrics reservoir type " + reservoirType.name());
    }
  }
}
