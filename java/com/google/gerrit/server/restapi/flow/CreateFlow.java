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

import com.google.gerrit.extensions.common.FlowInfo;
import com.google.gerrit.extensions.common.FlowInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowCreation;
import com.google.gerrit.server.flow.FlowPermissionDeniedException;
import com.google.gerrit.server.flow.FlowServiceUtil;
import com.google.gerrit.server.flow.InvalidFlowException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * REST endpoint to create a flow .
 *
 * <p>This REST endpoint handles {@code POST /change/<change-id>/flows} requests.
 */
@Singleton
public class CreateFlow
    implements RestCollectionModifyView<ChangeResource, FlowResource, FlowInput> {
  private final FlowServiceUtil flowServiceUtil;
  private final Provider<CurrentUser> self;

  @Inject
  CreateFlow(FlowServiceUtil flowServiceUtil, Provider<CurrentUser> self) {
    this.flowServiceUtil = flowServiceUtil;
    this.self = self;
  }

  @Override
  public Response<FlowInfo> apply(ChangeResource changeResource, FlowInput flowInput)
      throws AuthException, BadRequestException, MethodNotAllowedException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    if (flowInput == null) {
      flowInput = new FlowInput();
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
