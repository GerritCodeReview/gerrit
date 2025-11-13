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

package com.google.gerrit.server.config;

import com.google.gerrit.metrics.MetricsReservoirConfig;
import com.google.gerrit.metrics.ReservoirType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/** Define metrics reservoir settings based on gerrit.config */
@Singleton
public class MetricsReservoirConfigImpl implements MetricsReservoirConfig {
  private static final double RESERVOIR_ALPHA_DEFAULT = 0.015;
  private static final int METRICS_RESERVOIR_SIZE_DEFAULT = 1028;
  private static final long METRICS_RESERVOIR_WINDOW_MSEC_DEFAULT = 60000L;
  private static final String METRICS_SECTION = "metrics";
  private static final String METRICS_RESERVOIR = "reservoir";

  private final ReservoirType reservoirType;

  private final Duration reservoirWindow;
  private final int reservoirSize;
  private final double reservoirAlpha;

  @Inject
  MetricsReservoirConfigImpl(@GerritServerConfig Config gerritConfig) {
    this.reservoirType =
        gerritConfig.getEnum(
            METRICS_SECTION, null, METRICS_RESERVOIR, ReservoirType.ExponentiallyDecaying);

    reservoirWindow =
        Duration.ofMillis(
            ConfigUtil.getTimeUnit(
                gerritConfig,
                METRICS_SECTION,
                reservoirType.name(),
                "window",
                METRICS_RESERVOIR_WINDOW_MSEC_DEFAULT,
                TimeUnit.MILLISECONDS));
    reservoirSize =
        gerritConfig.getInt(
            METRICS_SECTION, reservoirType.name(), "size", METRICS_RESERVOIR_SIZE_DEFAULT);
    reservoirAlpha =
        Optional.ofNullable(gerritConfig.getString(METRICS_SECTION, reservoirType.name(), "alpha"))
            .map(Double::parseDouble)
            .orElse(RESERVOIR_ALPHA_DEFAULT);
  }

  // (non-Javadoc)
  // @see com.google.gerrit.server.config.MetricsConfig#reservoirType()
  //
  @Override
  public ReservoirType reservoirType() {
    return reservoirType;
  }

  // (non-Javadoc)
  // @see com.google.gerrit.server.config.MetricsConfig#reservoirWindow()
  //
  @Override
  public Duration reservoirWindow() {
    return reservoirWindow;
  }

  // (non-Javadoc)
  // @see com.google.gerrit.server.config.MetricsConfig#reservoirSize()
  //
  @Override
  public int reservoirSize() {
    return reservoirSize;
  }

  // (non-Javadoc)
  // @see com.google.gerrit.server.config.MetricsConfig#reservoirAlpha()
  //
  @Override
  public double reservoirAlpha() {
    return reservoirAlpha;
  }
}
