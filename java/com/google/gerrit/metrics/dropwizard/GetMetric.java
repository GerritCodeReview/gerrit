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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import org.kohsuke.args4j.Option;

class GetMetric implements RestReadView<MetricResource> {
  private final PermissionBackend permissionBackend;
  private final DropWizardMetricMaker metrics;

  @Option(name = "--data-only", usage = "return only values")
  boolean dataOnly;

  @Inject
  GetMetric(PermissionBackend permissionBackend, DropWizardMetricMaker metrics) {
    this.permissionBackend = permissionBackend;
    this.metrics = metrics;
  }

  @Override
  public Response<MetricJson> apply(MetricResource resource)
      throws AuthException, PermissionBackendException {
    permissionBackend.currentUser().check(GlobalPermission.VIEW_CACHES);
    return Response.ok(
        new MetricJson(resource.getMetric(), metrics.getAnnotations(resource.getName()), dataOnly));
  }
}
