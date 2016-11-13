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
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
class MetricsCollection implements ChildCollection<ConfigResource, MetricResource> {
  private final DynamicMap<RestView<MetricResource>> views;
  private final Provider<ListMetrics> list;
  private final Provider<CurrentUser> user;
  private final DropWizardMetricMaker metrics;

  @Inject
  MetricsCollection(
      DynamicMap<RestView<MetricResource>> views,
      Provider<ListMetrics> list,
      Provider<CurrentUser> user,
      DropWizardMetricMaker metrics) {
    this.views = views;
    this.list = list;
    this.user = user;
    this.metrics = metrics;
  }

  @Override
  public DynamicMap<RestView<MetricResource>> views() {
    return views;
  }

  @Override
  public RestView<ConfigResource> list() {
    return list.get();
  }

  @Override
  public MetricResource parse(ConfigResource parent, IdString id)
      throws ResourceNotFoundException, AuthException {
    if (!user.get().getCapabilities().canViewCaches()) {
      throw new AuthException("restricted to viewCaches");
    }

    Metric metric = metrics.getMetric(id.get());
    if (metric == null) {
      throw new ResourceNotFoundException(id.get());
    }
    return new MetricResource(id.get(), metric);
  }
}
