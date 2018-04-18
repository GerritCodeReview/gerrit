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

import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
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
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.submit.IntegrationException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CherryPick
    extends RetryingRestModifyView<RevisionResource, CherryPickInput, ChangeInfo>
    implements UiAction<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(CherryPick.class);
  private final PermissionBackend permissionBackend;
  private final CherryPickChange cherryPickChange;
  private final ChangeJson.Factory json;
  private final ContributorAgreementsChecker contributorAgreements;
  private final ProjectAccessor.Factory projectAccessorFactory;

  @Inject
  CherryPick(
      PermissionBackend permissionBackend,
      RetryHelper retryHelper,
      CherryPickChange cherryPickChange,
      ChangeJson.Factory json,
      ContributorAgreementsChecker contributorAgreements,
      ProjectAccessor.Factory projectAccessorFactory) {
    super(retryHelper);
    this.permissionBackend = permissionBackend;
    this.cherryPickChange = cherryPickChange;
    this.json = json;
    this.contributorAgreements = contributorAgreements;
    this.projectAccessorFactory = projectAccessorFactory;
  }

  @Override
  public ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, RevisionResource rsrc, CherryPickInput input)
      throws OrmException, IOException, UpdateException, RestApiException,
          PermissionBackendException, ConfigInvalidException, NoSuchProjectException {
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
    projectAccessorFactory.create(rsrc.getProject()).checkStatePermitsWrite();

    try {
      Change.Id cherryPickedChangeId =
          cherryPickChange.cherryPick(
              updateFactory,
              rsrc.getChange(),
              rsrc.getPatchSet(),
              input,
              new Branch.NameKey(rsrc.getProject(), refName));
      return json.noOptions().format(rsrc.getProject(), cherryPickedChangeId);
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
      // TODO(dborowitz): Pretty sure this should be checkStatePermitsWrite().
      projectAccessorFactory.create(rsrc.getProject()).statePermitsWrite();
    } catch (IOException | NoSuchProjectException e) {
      log.error("Failed to check if project state permits write: " + rsrc.getProject(), e);
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
