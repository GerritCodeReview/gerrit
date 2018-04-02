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

package com.google.gerrit.httpd.rpc.project;

import com.google.common.base.Throwables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gerrit.server.restapi.change.PostReviewers;
import com.google.gerrit.server.restapi.project.SetParent;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class ReviewProjectAccess extends ProjectAccessHandler<Change.Id> {
  interface Factory {
    ReviewProjectAccess create(
        @Assisted("projectName") Project.NameKey projectName,
        @Nullable @Assisted ObjectId base,
        @Assisted List<AccessSection> sectionList,
        @Nullable @Assisted("parentProjectName") Project.NameKey parentProjectName,
        @Nullable @Assisted String message);
  }

  private final ReviewDb db;
  private final PermissionBackend permissionBackend;
  private final Sequences seq;
  private final Provider<PostReviewers> reviewersProvider;
  private final ProjectCache projectCache;
  private final ChangesCollection changes;
  private final ChangeInserter.Factory changeInserterFactory;
  private final BatchUpdate.Factory updateFactory;

  @Inject
  ReviewProjectAccess(
      PermissionBackend permissionBackend,
      GroupBackend groupBackend,
      MetaDataUpdate.User metaDataUpdateFactory,
      ReviewDb db,
      Provider<PostReviewers> reviewersProvider,
      ProjectCache projectCache,
      AllProjectsName allProjects,
      ChangesCollection changes,
      ChangeInserter.Factory changeInserterFactory,
      BatchUpdate.Factory updateFactory,
      Provider<SetParent> setParent,
      Sequences seq,
      ContributorAgreementsChecker contributorAgreements,
      Provider<CurrentUser> user,
      @Assisted("projectName") Project.NameKey projectName,
      @Nullable @Assisted ObjectId base,
      @Assisted List<AccessSection> sectionList,
      @Nullable @Assisted("parentProjectName") Project.NameKey parentProjectName,
      @Nullable @Assisted String message) {
    super(
        groupBackend,
        metaDataUpdateFactory,
        allProjects,
        setParent,
        user.get(),
        projectName,
        base,
        sectionList,
        parentProjectName,
        message,
        contributorAgreements,
        permissionBackend,
        false);
    this.db = db;
    this.permissionBackend = permissionBackend;
    this.seq = seq;
    this.reviewersProvider = reviewersProvider;
    this.projectCache = projectCache;
    this.changes = changes;
    this.changeInserterFactory = changeInserterFactory;
    this.updateFactory = updateFactory;
  }

  // TODO(dborowitz): Hack MetaDataUpdate so it can be created within a BatchUpdate and we can avoid
  // calling setUpdateRef(false).
  @SuppressWarnings("deprecation")
  @Override
  protected Change.Id updateProjectConfig(
      ProjectConfig config, MetaDataUpdate md, boolean parentProjectUpdate)
      throws IOException, OrmException, AuthException, PermissionBackendException,
          ConfigInvalidException, ResourceConflictException {
    PermissionBackend.ForProject perm = permissionBackend.user(user).project(config.getName());
    if (!check(perm, ProjectPermission.READ_CONFIG)) {
      throw new AuthException(RefNames.REFS_CONFIG + " not visible");
    }

    if (!check(perm, ProjectPermission.WRITE_CONFIG)
        && !check(perm.ref(RefNames.REFS_CONFIG), RefPermission.CREATE_CHANGE)) {
      throw new AuthException("cannot create change for " + RefNames.REFS_CONFIG);
    }

    projectCache.checkedGet(config.getName()).checkStatePermitsWrite();

    md.setInsertChangeId(true);
    Change.Id changeId = new Change.Id(seq.nextChangeId());
    RevCommit commit =
        config.commitToNewRef(
            md, new PatchSet.Id(changeId, Change.INITIAL_PATCH_SET_ID).toRefName());
    if (commit.getId().equals(base)) {
      return null;
    }

    try (ObjectInserter objInserter = md.getRepository().newObjectInserter();
        ObjectReader objReader = objInserter.newReader();
        RevWalk rw = new RevWalk(objReader);
        BatchUpdate bu =
            updateFactory.create(db, config.getProject().getNameKey(), user, TimeUtil.nowTs())) {
      bu.setRepository(md.getRepository(), rw, objInserter);
      bu.insertChange(
          changeInserterFactory
              .create(changeId, commit, RefNames.REFS_CONFIG)
              .setValidate(false)
              .setUpdateRef(false)); // Created by commitToNewRef.
      bu.execute();
    } catch (UpdateException | RestApiException e) {
      throw new IOException(e);
    }

    ChangeResource rsrc;
    try {
      rsrc = changes.parse(changeId);
    } catch (RestApiException e) {
      throw new IOException(e);
    }
    addProjectOwnersAsReviewers(rsrc);
    if (parentProjectUpdate) {
      addAdministratorsAsReviewers(rsrc);
    }
    return changeId;
  }

  private void addProjectOwnersAsReviewers(ChangeResource rsrc) {
    final String projectOwners = groupBackend.get(SystemGroupBackend.PROJECT_OWNERS).getName();
    try {
      AddReviewerInput input = new AddReviewerInput();
      input.reviewer = projectOwners;
      reviewersProvider.get().apply(rsrc, input);
    } catch (Exception e) {
      // one of the owner groups is not visible to the user and this it why it
      // can't be added as reviewer
      Throwables.throwIfUnchecked(e);
    }
  }

  private void addAdministratorsAsReviewers(ChangeResource rsrc) {
    List<PermissionRule> adminRules =
        projectCache
            .getAllProjects()
            .getConfig()
            .getAccessSection(AccessSection.GLOBAL_CAPABILITIES)
            .getPermission(GlobalCapability.ADMINISTRATE_SERVER)
            .getRules();
    for (PermissionRule r : adminRules) {
      try {
        AddReviewerInput input = new AddReviewerInput();
        input.reviewer = r.getGroup().getUUID().get();
        reviewersProvider.get().apply(rsrc, input);
      } catch (Exception e) {
        // ignore
        Throwables.throwIfUnchecked(e);
      }
    }
  }

  private boolean check(PermissionBackend.ForRef perm, RefPermission p)
      throws PermissionBackendException {
    try {
      perm.check(p);
      return true;
    } catch (AuthException denied) {
      return false;
    }
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
