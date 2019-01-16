// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.common.CherryPickChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.submit.IntegrationException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CherryPick
    extends RetryingRestModifyView<RevisionResource, CherryPickInput, CherryPickChangeInfo>
    implements UiAction<RevisionResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;
  private final CherryPickChange cherryPickChange;
  private final ChangeJson.Factory json;
  private final ContributorAgreementsChecker contributorAgreements;
  private final ProjectCache projectCache;

  @Inject
  CherryPick(
      PermissionBackend permissionBackend,
      RetryHelper retryHelper,
      CherryPickChange cherryPickChange,
      ChangeJson.Factory json,
      ContributorAgreementsChecker contributorAgreements,
      ProjectCache projectCache) {
    super(retryHelper);
    this.permissionBackend = permissionBackend;
    this.cherryPickChange = cherryPickChange;
    this.json = json;
    this.contributorAgreements = contributorAgreements;
    this.projectCache = projectCache;
  }

  @Override
  public CherryPickChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, RevisionResource rsrc, CherryPickInput input)
      throws IOException, UpdateException, RestApiException, PermissionBackendException,
          ConfigInvalidException, NoSuchProjectException {
    input.parent = input.parent == null ? 1 : input.parent;
    if (input.message == null || input.message.trim().isEmpty()) {
      throw new BadRequestException("message must be non-empty");
    } else if (input.destination == null || input.destination.trim().isEmpty()) {
      throw new BadRequestException("destination must be non-empty");
    }

    String refName = RefNames.fullName(input.destination);
    contributorAgreements.check(rsrc.getProject(), rsrc.getUser());

    permissionBackend
        .currentUser()
        .project(rsrc.getChange().getProject())
        .ref(refName)
        .check(RefPermission.CREATE_CHANGE);
    projectCache.checkedGet(rsrc.getProject()).checkStatePermitsWrite();

    try {
      CherryPickChange.Result cherryPickResult =
          cherryPickChange.cherryPick(
              updateFactory,
              rsrc.getChange(),
              rsrc.getPatchSet(),
              input,
              new Branch.NameKey(rsrc.getProject(), refName));
      CherryPickChangeInfo changeInfo =
          json.noOptions()
              .format(rsrc.getProject(), cherryPickResult.changeId(), CherryPickChangeInfo::new);
      changeInfo.containsGitConflicts =
          !cherryPickResult.filesWithGitConflicts().isEmpty() ? true : null;
      return changeInfo;
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } catch (IntegrationException | NoSuchChangeException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource rsrc) {
    boolean projectStatePermitsWrite = false;
    try {
      projectStatePermitsWrite = projectCache.checkedGet(rsrc.getProject()).statePermitsWrite();
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Failed to check if project state permits write: %s", rsrc.getProject());
    }
    return new UiAction.Description()
        .setLabel("Cherry Pick")
        .setTitle("Cherry pick change to a different branch")
        .setVisible(
            and(
                rsrc.isCurrent() && projectStatePermitsWrite,
                permissionBackend
                    .currentUser()
                    .project(rsrc.getProject())
                    .testCond(ProjectPermission.CREATE_CHANGE)));
  }
}
