// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.flow;

import com.google.gerrit.extensions.common.IsFlowsEnabledInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.flow.FlowServiceUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * REST endpoint to check if Flows is enabled for the user.
 *
 * <p>This REST endpoint handles {@code GET /change/<change-id>/isFlowsEnabled} requests.
 */
@Singleton
public class IsFlowsEnabled implements RestReadView<ChangeResource> {
  private final FlowServiceUtil flowServiceUtil;

  @Inject
  IsFlowsEnabled(FlowServiceUtil flowServiceUtil) {
    this.flowServiceUtil = flowServiceUtil;
  }

  @Override
  public Response<IsFlowsEnabledInfo> apply(ChangeResource changeResource) throws RestApiException {
    IsFlowsEnabledInfo enabledInfo =
        new IsFlowsEnabledInfo(
            flowServiceUtil
                .getFlowServiceOrThrow()
                .isFlowsEnabled(changeResource.getProject(), changeResource.getId()));
    return Response.ok(enabledInfo);
  }
}
