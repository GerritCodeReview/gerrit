// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.ChangeIdUtil;

@Singleton
public class CreateAccessChange implements RestModifyView<ProjectResource, ProjectAccessInput> {
  private final PermissionBackend permissionBackend;
  private final Sequences seq;
  private final ChangeInserter.Factory changeInserterFactory;
  private final BatchUpdate.Factory updateFactory;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final Provider<ReviewDb> db;
  private final SetAccessUtil setAccess;
  private final Provider<IdentifiedUser> identifiedUser;
  private final ChangeJson.Factory jsonFactory;

  @Inject
  CreateAccessChange(
      PermissionBackend permissionBackend,
      ChangeInserter.Factory changeInserterFactory,
      BatchUpdate.Factory updateFactory,
      Sequences seq,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      Provider<ReviewDb> db,
      SetAccessUtil accessUtil,
      Provider<IdentifiedUser> identifiedUser,
      ChangeJson.Factory jsonFactory) {
    this.permissionBackend = permissionBackend;
    this.seq = seq;
    this.changeInserterFactory = changeInserterFactory;
    this.updateFactory = updateFactory;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.db = db;
    this.setAccess = accessUtil;
    this.identifiedUser = identifiedUser;
    this.jsonFactory = jsonFactory;
  }

  @Override
  public Response<ChangeInfo> apply(ProjectResource rsrc, ProjectAccessInput input)
      throws PermissionBackendException, PermissionDeniedException, IOException,
          ConfigInvalidException, OrmException, UnprocessableEntityException, BadRequestException,
          InvalidNameException, ResourceConflictException {
    ProjectControl projectControl = rsrc.getControl();
    MetaDataUpdate.User metaDataUpdateUser = metaDataUpdateFactory.get();
    List<AccessSection> removals = setAccess.getAccessSections(input.remove);
    List<AccessSection> additions = setAccess.getAccessSections(input.add);

    PermissionBackend.ForRef metaRef =
        permissionBackend
            .user(projectControl.getUser())
            .project(projectControl.getProject().getNameKey())
            .ref(RefNames.REFS_CONFIG);
    try {
      metaRef.check(RefPermission.READ);
    } catch (AuthException denied) {
      throw new PermissionDeniedException(RefNames.REFS_CONFIG + " not visible");
    }
    if (!projectControl.isOwner()) {
      try {
        metaRef.check(RefPermission.CREATE_CHANGE);
      } catch (AuthException denied) {
        throw new PermissionDeniedException("cannot create change for " + RefNames.REFS_CONFIG);
      }
    }

    Project.NameKey newParentProjectName =
        input.parent == null ? null : new Project.NameKey(input.parent);

    try (MetaDataUpdate md = metaDataUpdateUser.create(rsrc.getNameKey())) {
      ProjectConfig config = ProjectConfig.read(md);

      setAccess.validateChanges(config, removals, additions);
      setAccess.applyChanges(config, removals, additions);
      try {
        setAccess.setParentName(
            identifiedUser.get(), config, rsrc.getNameKey(), newParentProjectName, false);
      } catch (AuthException e) {
        throw new IllegalStateException(e);
      }

      md.setMessage("review access change");
      md.setInsertChangeId(true);
      Change.Id changeId = new Change.Id(seq.nextChangeId());
      RevCommit commit =
          config.commitToNewRef(
              md, new PatchSet.Id(changeId, Change.INITIAL_PATCH_SET_ID).toRefName());

      // TODO : check if this introduces a content change.
      try (ObjectInserter objInserter = md.getRepository().newObjectInserter();
          ObjectReader objReader = objInserter.newReader();
          RevWalk rw = new RevWalk(objReader);
          BatchUpdate bu =
              updateFactory.create(
                  db.get(),
                  config.getProject().getNameKey(),
                  projectControl.getUser(),
                  TimeUtil.nowTs())) {
        bu.setRepository(md.getRepository(), rw, objInserter);
        ChangeInserter ins =
            changeInserterFactory.create(changeId, commit, RefNames.REFS_CONFIG);
        ins.setMessage("First patchset")
          .setValidate(false)
          .setUpdateRef(false);
        bu.insertChange(ins);
        bu.execute();
        ChangeJson json = jsonFactory.noOptions();
        return Response.created(json.format(ins.getChange()));
      } catch (UpdateException | RestApiException e) {
        throw new IOException(e);
      }
    }
  }
}
