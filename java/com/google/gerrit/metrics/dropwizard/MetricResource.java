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

import com.codahale.metrics.Metric;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.TypeLiteral;

class MetricResource extends ConfigResource {
  static final TypeLiteral<RestView<MetricResource>> METRIC_KIND =
      new TypeLiteral<RestView<MetricResource>>() {};

  private final String name;
  private final Metric metric;

  MetricResource(String name, Metric metric) {
    this.name = name;
    this.metric = metric;
  }

  String getName() {
    return name;
  }

  Metric getMetric() {
    return metric;
  }
}
