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
import com.google.gerrit.server.config.MetricsConfig;
import com.google.gerrit.server.config.MetricsConfig.ReservoirType;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.concurrent.TimeUnit;

public class DropWizardReservoirProvider implements Provider<Reservoir> {
  private final MetricsConfig config;

  @Inject
  public DropWizardReservoirProvider(MetricsConfig config) {
    this.config = config;
  }

  @Override
  public Reservoir get() {
    ReservoirType reservoirType = config.reservoirType();
    switch (reservoirType) {
      case ExponentiallyDecaying:
        return config.hasCustomReservoirConfig()
            ? new ExponentiallyDecayingReservoir(config.reservoirSize(), config.reservoirAlpha())
            : new ExponentiallyDecayingReservoir();
      case SlidingTimeWindowArray:
        return new SlidingTimeWindowArrayReservoir(
            config.reservoirWindow().toMillis(), TimeUnit.MILLISECONDS);
      case SlidingTimeWindow:
        return new SlidingTimeWindowReservoir(
            config.reservoirWindow().toMillis(), TimeUnit.MILLISECONDS);
      case SlidingWindow:
        return new SlidingWindowReservoir(config.reservoirSize());
      case Uniform:
        return config.hasCustomReservoirConfig()
            ? new UniformReservoir(config.reservoirSize())
            : new UniformReservoir();

      default:
        throw new IllegalArgumentException(
            "Unsupported metrics reservoir type " + reservoirType.name());
    }
  }
}
