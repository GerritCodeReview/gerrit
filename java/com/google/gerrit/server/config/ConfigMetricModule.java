// Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.base.Supplier;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.proc.MetricModule;
import com.google.inject.Inject;

public class ConfigMetricModule extends MetricModule {
  private GerritServerConfigProvider gerritConfig;

  @Inject
  ConfigMetricModule(GerritServerConfigProvider provider) {
    this.gerritConfig = provider;
  }

  @Override
  protected void configure(MetricMaker metrics) {
    metrics.newCallbackMetric(
        "config/current",
        String.class,
        new Description("current gerrit.config"),
        new Supplier<String>() {
          @Override
          public String get() {
            return gerritConfig.get().toText();
          }
        });
  }
}
