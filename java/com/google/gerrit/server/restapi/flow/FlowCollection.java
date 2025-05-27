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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowKey;
import com.google.gerrit.server.flow.FlowServiceUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** REST collection that serves requests to {@code /changes/<change-id>/flows}. */
@Singleton
public class FlowCollection implements ChildCollection<ChangeResource, FlowResource> {
  private final FlowServiceUtil flowServiceUtil;
  private final DynamicMap<RestView<FlowResource>> views;
  private final ListFlows listFlows;

  @Inject
  FlowCollection(
      FlowServiceUtil flowServiceUtil,
      ListFlows listFlows,
      DynamicMap<RestView<FlowResource>> views) {
    this.flowServiceUtil = flowServiceUtil;
    this.listFlows = listFlows;
    this.views = views;
  }

  @Override
  public RestView<ChangeResource> list() {
    return listFlows;
  }

  @Override
  public FlowResource parse(ChangeResource changeResource, IdString flowUuid)
      throws ResourceNotFoundException, MethodNotAllowedException {
    FlowKey flowKey =
        FlowKey.create(changeResource.getProject(), changeResource.getId(), flowUuid.get());
    Flow flow =
        flowServiceUtil
            .getFlowServiceOrThrow()
            .getFlow(flowKey)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        String.format("Flow %s not found.", flowUuid.get())));
    return new FlowResource(flow);
  }

  @Override
  public DynamicMap<RestView<FlowResource>> views() {
    return views;
  }
}
