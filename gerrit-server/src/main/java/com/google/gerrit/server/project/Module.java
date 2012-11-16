// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.inject.AbstractModule;

public class Module extends AbstractModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), ProjectResource.PROJECT_KIND);
    DynamicMap.mapOf(binder(), DashboardResource.DASHBOARD_KIND);

    bind(ProjectResource.PROJECT_KIND)
      .annotatedWith(Exports.named("GET./"))
      .to(GetProject.class);

    bind(ProjectResource.PROJECT_KIND)
      .annotatedWith(Exports.named("GET.description"))
      .to(GetDescription.class);
    bind(ProjectResource.PROJECT_KIND)
      .annotatedWith(Exports.named("PUT.description"))
      .to(SetDescription.class);
    bind(ProjectResource.PROJECT_KIND)
      .annotatedWith(Exports.named("DELETE.description"))
      .to(SetDescription.class);

    bind(ProjectResource.PROJECT_KIND)
      .annotatedWith(Exports.named("GET.parent"))
      .to(GetParent.class);
    bind(ProjectResource.PROJECT_KIND)
      .annotatedWith(Exports.named("PUT.parent"))
      .to(SetParent.class);

    bind(ProjectResource.PROJECT_KIND)
      .annotatedWith(Exports.named("GET.dashboards"))
      .to(DashboardsCollection.class);
    bind(DashboardResource.DASHBOARD_KIND)
      .annotatedWith(Exports.named("GET./"))
      .to(GetDashboard.class);
  }
}
