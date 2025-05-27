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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.common.FlowInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowServiceUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

/**
 * REST endpoint to list members of the {@link FlowCollection}.
 *
 * <p>This REST endpoint handles {@code GET /change/<change-id>/flows} requests.
 */
@Singleton
public class ListFlows implements RestReadView<ChangeResource> {
  private final FlowServiceUtil flowServiceUtil;

  @Inject
  ListFlows(FlowServiceUtil flowServiceUtil) {
    this.flowServiceUtil = flowServiceUtil;
  }

  @Override
  public Response<List<FlowInfo>> apply(ChangeResource changeResource)
      throws MethodNotAllowedException {
    ImmutableList<Flow> flows =
        flowServiceUtil
            .getFlowServiceOrThrow()
            .listFlows(changeResource.getProject(), changeResource.getId());
    return Response.ok(flows.stream().map(FlowJson::format).collect(toImmutableList()));
  }
}
