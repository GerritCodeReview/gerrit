// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.common.base.Strings;
import com.google.gerrit.entities.ProjectUtil;
import com.google.gerrit.exceptions.InvalidNameException;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CreateChange implements RestModifyView<ProjectResource, ChangeInput> {
  private final com.google.gerrit.server.restapi.change.CreateChange changeCreateChange;
  private final Provider<CurrentUser> user;
  private final BatchUpdate.Factory updateFactory;

  @Inject
  public CreateChange(
      Provider<CurrentUser> user,
      BatchUpdate.Factory updateFactory,
      com.google.gerrit.server.restapi.change.CreateChange changeCreateChange) {
    this.updateFactory = updateFactory;
    this.changeCreateChange = changeCreateChange;
    this.user = user;
  }

  @Override
  public Response<ChangeInfo> apply(ProjectResource rsrc, ChangeInput input)
      throws PermissionBackendException, IOException, ConfigInvalidException,
          InvalidChangeOperationException, InvalidNameException, UpdateException, RestApiException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    if (!Strings.isNullOrEmpty(input.project)
        && !rsrc.getName().equals(ProjectUtil.sanitizeProjectName(input.project))) {
      throw new BadRequestException("project must match URL");
    }

    input.project = rsrc.getName();
    return changeCreateChange.execute(updateFactory, input, rsrc);
  }
}
