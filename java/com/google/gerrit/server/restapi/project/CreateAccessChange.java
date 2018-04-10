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

package com.google.gerrit.server.restapi.project;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class CreateAccessChange implements RestModifyView<ProjectResource, ProjectAccessInput> {
  private final PermissionBackend permissionBackend;
  private final Sequences seq;
  private final ChangeInserter.Factory changeInserterFactory;
  private final BatchUpdate.Factory updateFactory;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final Provider<ReviewDb> db;
  private final SetAccessUtil setAccess;
  private final ChangeJson.Factory jsonFactory;
  private final ProjectCache projectCache;

  @Inject
  CreateAccessChange(
      PermissionBackend permissionBackend,
      ChangeInserter.Factory changeInserterFactory,
      BatchUpdate.Factory updateFactory,
      Sequences seq,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      Provider<ReviewDb> db,
      SetAccessUtil accessUtil,
      ChangeJson.Factory jsonFactory,
      ProjectCache projectCache) {
    this.permissionBackend = permissionBackend;
    this.seq = seq;
    this.changeInserterFactory = changeInserterFactory;
    this.updateFactory = updateFactory;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.db = db;
    this.setAccess = accessUtil;
    this.jsonFactory = jsonFactory;
    this.projectCache = projectCache;
  }

  @Override
  public Response<ChangeInfo> apply(ProjectResource rsrc, ProjectAccessInput input)
      throws PermissionBackendException, AuthException, IOException, ConfigInvalidException,
          OrmException, InvalidNameException, UpdateException, RestApiException {
    PermissionBackend.ForProject forProject =
        permissionBackend.user(rsrc.getUser()).project(rsrc.getNameKey());
    if (!check(forProject, ProjectPermission.READ_CONFIG)) {
      throw new AuthException(RefNames.REFS_CONFIG + " not visible");
    }
    if (!check(forProject, ProjectPermission.WRITE_CONFIG)) {
      try {
        forProject.ref(RefNames.REFS_CONFIG).check(RefPermission.CREATE_CHANGE);
      } catch (AuthException denied) {
        throw new AuthException("cannot create change for " + RefNames.REFS_CONFIG);
      }
    }
    projectCache.checkedGet(rsrc.getNameKey()).checkStatePermitsWrite();

    MetaDataUpdate.User metaDataUpdateUser = metaDataUpdateFactory.get();
    List<AccessSection> removals = setAccess.getAccessSections(input.remove);
    List<AccessSection> additions = setAccess.getAccessSections(input.add);

    Project.NameKey newParentProjectName =
        input.parent == null ? null : new Project.NameKey(input.parent);

    try (MetaDataUpdate md = metaDataUpdateUser.create(rsrc.getNameKey())) {
      ProjectConfig config = ProjectConfig.read(md);
      ObjectId oldCommit = config.getRevision();
      String oldCommitSha1 = oldCommit == null ? null : oldCommit.getName();

      setAccess.validateChanges(config, removals, additions);
      setAccess.applyChanges(config, removals, additions);
      try {
        setAccess.setParentName(
            rsrc.getUser().asIdentifiedUser(),
            config,
            rsrc.getNameKey(),
            newParentProjectName,
            false);
      } catch (AuthException e) {
        throw new IllegalStateException(e);
      }

      md.setMessage("Review access change");
      md.setInsertChangeId(true);
      Change.Id changeId = new Change.Id(seq.nextChangeId());

      RevCommit commit =
          config.commitToNewRef(
              md, new PatchSet.Id(changeId, Change.INITIAL_PATCH_SET_ID).toRefName());

      if (commit.name().equals(oldCommitSha1)) {
        throw new BadRequestException("no change");
      }

      try (ObjectInserter objInserter = md.getRepository().newObjectInserter();
          ObjectReader objReader = objInserter.newReader();
          RevWalk rw = new RevWalk(objReader);
          BatchUpdate bu =
              updateFactory.create(db.get(), rsrc.getNameKey(), rsrc.getUser(), TimeUtil.nowTs())) {
        bu.setRepository(md.getRepository(), rw, objInserter);
        ChangeInserter ins = newInserter(changeId, commit);
        bu.insertChange(ins);
        bu.execute();
        return Response.created(jsonFactory.noOptions().format(ins.getChange()));
      }
    }
  }

  // ProjectConfig doesn't currently support fusing into a BatchUpdate.
  @SuppressWarnings("deprecation")
  private ChangeInserter newInserter(Change.Id changeId, RevCommit commit) {
    return changeInserterFactory
        .create(changeId, commit, RefNames.REFS_CONFIG)
        .setMessage(
            // Same message as in ReceiveCommits.CreateRequest.
            ApprovalsUtil.renderMessageWithApprovals(1, ImmutableMap.of(), ImmutableMap.of()))
        .setValidate(false)
        .setUpdateRef(false);
  }

  private boolean check(PermissionBackend.ForProject perm, ProjectPermission p)
      throws PermissionBackendException {
    try {
      perm.check(p);
      return true;
    } catch (AuthException denied) {
      return false;
    }
  }
}
