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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.common.FlowInfo;
import com.google.gerrit.extensions.common.FlowInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowCreation;
import com.google.gerrit.server.flow.FlowPermissionDeniedException;
import com.google.gerrit.server.flow.FlowServiceUtil;
import com.google.gerrit.server.flow.InvalidFlowException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/**
 * REST endpoint to create a flow .
 *
 * <p>This REST endpoint handles {@code POST /change/<change-id>/flows} requests.
 */
@Singleton
public class CreateFlow
    implements RestCollectionModifyView<ChangeResource, FlowResource, FlowInput> {
  @VisibleForTesting public static final int DEFAULT_MAX_FLOWS_PER_CHANGE = 20;

  private final Provider<Config> cfgProvider;
  private final FlowServiceUtil flowServiceUtil;
  private final Provider<CurrentUser> self;

  @Inject
  CreateFlow(
      @GerritServerConfig Provider<Config> cfgProvider,
      FlowServiceUtil flowServiceUtil,
      Provider<CurrentUser> self) {
    this.cfgProvider = cfgProvider;
    this.flowServiceUtil = flowServiceUtil;
    this.self = self;
  }

  @Override
  public Response<FlowInfo> apply(ChangeResource changeResource, FlowInput flowInput)
      throws AuthException,
          BadRequestException,
          MethodNotAllowedException,
          ResourceConflictException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    if (!changeResource
        .getChangeData()
        .currentPatchSet()
        .realUploader()
        .equals(self.get().getAccountId())) {
      throw new AuthException(
          "Only latest uploader can create a flow, because actions are executed on behalf of"
              + " uploader.");
    }

    if (flowInput == null) {
      flowInput = new FlowInput();
    }

    int maxFlowsPerChange =
        cfgProvider.get().getInt("flows", "maxPerChange", DEFAULT_MAX_FLOWS_PER_CHANGE);
    if (maxFlowsPerChange > 0
        && flowServiceUtil
                .getFlowServiceOrThrow()
                .listFlows(changeResource.getProject(), changeResource.getId())
                .size()
            >= maxFlowsPerChange) {
      throw new ResourceConflictException(
          String.format("Too many flows (max %s flow allowed per change)", maxFlowsPerChange));
    }

    FlowCreation flowCreation =
        FlowJson.createFlowCreation(
            changeResource.getProject(),
            changeResource.getId(),
            self.get().asIdentifiedUser().getAccountId(),
            flowInput);

    try {
      Flow flow = flowServiceUtil.getFlowServiceOrThrow().createFlow(flowCreation);
      return Response.created(FlowJson.format(flow));
    } catch (FlowPermissionDeniedException e) {
      throw new AuthException(e.getMessage(), e);
    } catch (InvalidFlowException e) {
      throw new BadRequestException(e.getMessage(), e);
    }
  }
}
