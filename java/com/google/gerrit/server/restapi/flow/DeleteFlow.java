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
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.flow.FlowPermissionDeniedException;
import com.google.gerrit.server.flow.FlowServiceUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * REST endpoint to delete a flow .
 *
 * <p>This REST endpoint handles {@code DELETE /change/<change-id>/flows/<flow-id>} requests.
 */
@Singleton
public class DeleteFlow implements RestModifyView<FlowResource, Input> {
  private final FlowServiceUtil flowServiceUtil;
  private final Provider<CurrentUser> self;

  @Inject
  DeleteFlow(FlowServiceUtil flowServiceUtil, Provider<CurrentUser> self) {
    this.flowServiceUtil = flowServiceUtil;
    this.self = self;
  }

  @Override
  public Response<FlowInfo> apply(FlowResource flowResource, Input input)
      throws AuthException, MethodNotAllowedException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    try {
      flowServiceUtil.getFlowServiceOrThrow().deleteFlow(flowResource.getFlow().key());
    } catch (FlowPermissionDeniedException e) {
      throw new AuthException(e.getMessage(), e);
    }

    return Response.none();
  }
}
