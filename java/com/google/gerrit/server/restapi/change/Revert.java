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
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class Revert
    implements RestModifyView<ChangeResource, RevertInput>, UiAction<ChangeResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;
  private final PatchSetUtil psUtil;
  private final ChangeJson.Factory json;
  private final ContributorAgreementsChecker contributorAgreements;
  private final ProjectCache projectCache;
  private final CommitUtil commitUtil;

  @Inject
  Revert(
      PermissionBackend permissionBackend,
      PatchSetUtil psUtil,
      ChangeJson.Factory json,
      ContributorAgreementsChecker contributorAgreements,
      ProjectCache projectCache,
      CommitUtil commitUtil) {
    this.permissionBackend = permissionBackend;
    this.psUtil = psUtil;
    this.json = json;
    this.contributorAgreements = contributorAgreements;
    this.projectCache = projectCache;
    this.commitUtil = commitUtil;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, RevertInput input)
      throws IOException, RestApiException, UpdateException, NoSuchChangeException,
          PermissionBackendException, NoSuchProjectException, ConfigInvalidException {
    Change change = rsrc.getChange();
    if (!change.isMerged()) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(change));
    }

    contributorAgreements.check(rsrc.getProject(), rsrc.getUser());
    permissionBackend.user(rsrc.getUser()).ref(change.getDest()).check(CREATE_CHANGE);
    rsrc.permissions().check(ChangePermission.REVERT);
    projectCache.checkedGet(rsrc.getProject()).checkStatePermitsWrite();
    ChangeNotes notes = rsrc.getNotes();
    Change.Id changeIdToRevert = notes.getChangeId();
    PatchSet.Id patchSetId = notes.getChange().currentPatchSetId();
    PatchSet patch = psUtil.get(notes, patchSetId);
    if (patch == null) {
      throw new ResourceNotFoundException(changeIdToRevert.toString());
    }
    Timestamp timestamp = TimeUtil.nowTs();
    return Response.ok(
        json.noOptions()
            .format(
                rsrc.getProject(),
                commitUtil.createRevertChange(notes, rsrc.getUser(), input, timestamp)));
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    boolean projectStatePermitsWrite = false;
    try {
      projectStatePermitsWrite = projectCache.checkedGet(rsrc.getProject()).statePermitsWrite();
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Failed to check if project state permits write: %s", rsrc.getProject());
    }
    return new UiAction.Description()
        .setLabel("Revert")
        .setTitle("Revert the change")
        .setVisible(
            and(
                and(
                    change.isMerged() && projectStatePermitsWrite,
                    permissionBackend
                        .user(rsrc.getUser())
                        .ref(change.getDest())
                        .testCond(CREATE_CHANGE)),
                permissionBackend
                    .user(rsrc.getUser())
                    .change(rsrc.getNotes())
                    .testCond(ChangePermission.REVERT)));
  }
}
