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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeStatus;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.CreateChange;
import com.google.gerrit.server.project.ProjectCreateChange.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;

@Singleton
public class ProjectCreateChange implements RestModifyView<ProjectResource, Input>,
    UiAction<ProjectResource> {
  static class Input {
    public String subject;
    public String branch;
    public Boolean draft;
  }

  private final CreateChange createChange;
  private final Provider<CurrentUser> currentUser;

  @Inject
  ProjectCreateChange(CreateChange createChange,
      Provider<CurrentUser> currentUser) {
    this.createChange = createChange;
    this.currentUser = currentUser;
  }

  @Override
  public Response<ChangeJson.ChangeInfo> apply(ProjectResource rsrc, Input input)
      throws AuthException, BadRequestException, UnprocessableEntityException,
      OrmException, IOException, InvalidChangeOperationException {
    ChangeInfo in = new ChangeInfo();
    in.project = rsrc.getName();
    in.branch = input.branch;
    in.subject = input.subject;
    if (input.draft != null && input.draft) {
      in.status = ChangeStatus.DRAFT;
    }
    return createChange.apply(TopLevelResource.INSTANCE, in);
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
        .setLabel("Create change")
        .setTitle("Create change directly in browser")
        .setVisible(currentUser.get().isIdentifiedUser());
  }
}
