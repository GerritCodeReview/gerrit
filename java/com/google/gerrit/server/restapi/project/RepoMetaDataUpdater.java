// Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.InvalidNameException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.CreateGroupPermissionSyncer;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.MetaDataUpdate.User;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Updates repo refs/meta/config content. */
@Singleton
public class RepoMetaDataUpdater {
  private final CreateGroupPermissionSyncer createGroupPermissionSyncer;
  private final Provider<User> metaDataUpdateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;
  private final ChangeInserter.Factory changeInserterFactory;
  private final Sequences seq;

  private final BatchUpdate.Factory updateFactory;

  private final PermissionBackend permissionBackend;

  @Inject
  RepoMetaDataUpdater(
      CreateGroupPermissionSyncer createGroupPermissionSyncer,
      Provider<User> metaDataUpdateFactory,
      ProjectConfig.Factory projectConfigFactory,
      ProjectCache projectCache,
      ChangeInserter.Factory changeInserterFactory,
      Sequences seq,
      BatchUpdate.Factory updateFactory,
      PermissionBackend permissionBackend) {
    this.createGroupPermissionSyncer = createGroupPermissionSyncer;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectConfigFactory = projectConfigFactory;
    this.projectCache = projectCache;
    this.changeInserterFactory = changeInserterFactory;
    this.seq = seq;
    this.updateFactory = updateFactory;
    this.permissionBackend = permissionBackend;
  }

  public Change updateAndCreateChangeForReview(
      Project.NameKey projectName,
      CurrentUser user,
      String message,
      ProjectConfigUpdater projectConfigUpdater)
      throws ConfigInvalidException,
          IOException,
          RestApiException,
          UpdateException,
          InvalidNameException,
          PermissionBackendException {
    checkArgument(!message.isBlank(), "The message must not be empty");
    message = validateMessage(message);

    PermissionBackend.ForProject forProject = permissionBackend.user(user).project(projectName);
    if (!check(forProject, ProjectPermission.READ_CONFIG)) {
      throw new AuthException(RefNames.REFS_CONFIG + " not visible");
    }
    if (!check(forProject, ProjectPermission.WRITE_CONFIG)) {
      try {
        forProject.ref(RefNames.REFS_CONFIG).check(RefPermission.CREATE_CHANGE);
      } catch (AuthException denied) {
        throw new AuthException("cannot create change for " + RefNames.REFS_CONFIG, denied);
      }
    }
    projectCache.get(projectName).orElseThrow(illegalState(projectName)).checkStatePermitsWrite();

    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(projectName)) {
      ProjectConfig config = projectConfigFactory.read(md);
      ObjectId oldCommit = config.getRevision();
      String oldCommitSha1 = oldCommit == null ? null : oldCommit.getName();

      projectConfigUpdater.update(config);
      md.setMessage(message);
      md.setInsertChangeId(true);

      Change.Id changeId = Change.id(seq.nextChangeId());
      try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
        RevCommit commit =
            config.commitToNewRef(
                md, PatchSet.id(changeId, Change.INITIAL_PATCH_SET_ID).toRefName());

        if (commit.name().equals(oldCommitSha1)) {
          throw new BadRequestException("no change");
        }

        try (ObjectInserter objInserter = md.getRepository().newObjectInserter();
            ObjectReader objReader = objInserter.newReader();
            RevWalk rw = new RevWalk(objReader);
            BatchUpdate bu = updateFactory.create(projectName, user, TimeUtil.now())) {
          bu.setRepository(md.getRepository(), rw, objInserter);
          ChangeInserter ins = newInserter(changeId, commit);
          bu.insertChange(ins);
          bu.execute();
          return ins.getChange();
        }
      }
    }
  }

  public void updateWithoutReview(
      Project.NameKey projectName, String message, ProjectConfigUpdater projectConfigUpdater)
      throws ConfigInvalidException,
          IOException,
          PermissionBackendException,
          AuthException,
          ResourceConflictException,
          InvalidNameException,
          BadRequestException {
    updateWithoutReview(
        projectName, message, /* skipPermissionsCheck= */ false, projectConfigUpdater);
  }

  public void updateWithoutReview(
      Project.NameKey projectName,
      String message,
      boolean skipPermissionsCheck,
      ProjectConfigUpdater projectConfigUpdater)
      throws ConfigInvalidException,
          IOException,
          PermissionBackendException,
          AuthException,
          ResourceConflictException,
          InvalidNameException,
          BadRequestException {
    message = validateMessage(message);
    if (!skipPermissionsCheck) {
      permissionBackend.currentUser().project(projectName).check(ProjectPermission.WRITE_CONFIG);
    }

    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(projectName)) {
      ProjectConfig config = projectConfigFactory.read(md);

      projectConfigUpdater.update(config);
      md.setMessage(message);
      config.commit(md);
      projectCache.evictAndReindex(config.getProject());
      createGroupPermissionSyncer.syncIfNeeded();
    }
  }

  private String validateMessage(String message) {
    if (!message.endsWith("\n")) {
      return message + "\n";
    }
    return message;
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

  @FunctionalInterface
  public interface ProjectConfigUpdater {
    void update(ProjectConfig config)
        throws BadRequestException,
            InvalidNameException,
            PermissionBackendException,
            ResourceConflictException,
            AuthException;
  }
}
