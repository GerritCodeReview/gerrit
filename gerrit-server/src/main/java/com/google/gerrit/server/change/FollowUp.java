// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.change.FollowUp.FollowUpInput;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;

@Singleton
class FollowUp implements RestModifyView<ChangeResource, FollowUpInput>,
    UiAction<ChangeResource> {
  static class FollowUpInput {}

  private final CreateChange createChange;

  @Inject
  FollowUp(CreateChange createChange) {
    this.createChange = createChange;
  }

  @Override
  public Response<ChangeJson.ChangeInfo> apply(ChangeResource rsrc,
      FollowUpInput input) throws AuthException, BadRequestException,
      ResourceNotFoundException, IOException, OrmException,
      UnprocessableEntityException {
    try {
      ChangeInfo info = new ChangeInfo();
      info.project = rsrc.getControl().getProject().getName();
      info.branch = rsrc.getChange().getDest().get();
      info.subject = rsrc.getChange().getSubject();
      info.baseChange = String.format("%s~%s~%s", info.project, info.branch,
          rsrc.getChange().getKey().get());
      return createChange.apply(TopLevelResource.INSTANCE, info);
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    return new UiAction.Description()
      .setLabel("Follow-up")
      .setTitle("Create follow-up change");
  }
}
