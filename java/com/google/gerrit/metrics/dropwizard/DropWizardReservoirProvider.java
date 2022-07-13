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

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.Config;

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.UniformReservoir;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class DropWizardReservoirProvider implements Provider<Reservoir> {
	private static final String METRICS_SECTION = "metrics";
	private static final String METRICS_RESERVOIR = "reservoir";

	private final Config gerritConfig;
	private final ReservoirType reservoir;

	public enum ReservoirType {
		ExponentiallyDecaying, SlidingTimeWindowArray, SlidingTimeWindowReservoir, SlidingWindowReservoir,
		UniformReservoir;
	}

	@Inject
	public DropWizardReservoirProvider(@GerritServerConfig Config gerritConfig) {
		this.gerritConfig = gerritConfig;
		this.reservoir = gerritConfig.getEnum(METRICS_SECTION, null, METRICS_RESERVOIR,
				ReservoirType.ExponentiallyDecaying);
	}

	@Override
	public Reservoir get() {
		Set<String> configSubsections = gerritConfig.getSubsections(METRICS_SECTION);
		boolean hasReservoirConfig = configSubsections.contains(reservoir.name());
		long window = ConfigUtil.getTimeUnit(gerritConfig, METRICS_SECTION, reservoir.name(), "window", 60L, TimeUnit.SECONDS);
		int size = gerritConfig.getInt(METRICS_SECTION, reservoir.name(), "size", 1028);
		double alpha = Double.parseDouble(
				Optional.ofNullable(gerritConfig.getString(METRICS_SECTION, reservoir.name(), "alpha"))
						.orElse("0.015"));

		switch (reservoir) {
		case ExponentiallyDecaying:
			return hasReservoirConfig ? new ExponentiallyDecayingReservoir(size, alpha) : new ExponentiallyDecayingReservoir();
		case SlidingTimeWindowArray:
			return new SlidingTimeWindowArrayReservoir(window, TimeUnit.SECONDS);
		case SlidingTimeWindowReservoir:
			return new SlidingTimeWindowReservoir(window, TimeUnit.SECONDS);
		case SlidingWindowReservoir:
			return new SlidingWindowReservoir(size);
		case UniformReservoir:
			return hasReservoirConfig ? new UniformReservoir(size) : new UniformReservoir();
			
		default:
			throw new IllegalArgumentException("Unsupported reservoir type " + reservoir);
		}
	}

}
