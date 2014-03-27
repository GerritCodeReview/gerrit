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

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.MergeabilityChecker;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.SetParent;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReviewProjectAccess extends ProjectAccessHandler<Change.Id> {
  private static final Logger log =
      LoggerFactory.getLogger(ReviewProjectAccess.class);

  interface Factory {
    ReviewProjectAccess create(
        @Assisted("projectName") Project.NameKey projectName,
        @Nullable @Assisted ObjectId base,
        @Assisted List<AccessSection> sectionList,
        @Nullable @Assisted("parentProjectName") Project.NameKey parentProjectName,
        @Nullable @Assisted String message);
  }

  private final ReviewDb db;
  private final IdentifiedUser user;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final Provider<PostReviewers> reviewersProvider;
  private final MergeabilityChecker mergeabilityChecker;
  private final ChangeHooks hooks;
  private final CreateChangeSender.Factory createChangeSenderFactory;
  private final ProjectCache projectCache;
  private final ChangesCollection changes;

  @Inject
  ReviewProjectAccess(final ProjectControl.Factory projectControlFactory,
      GroupBackend groupBackend,
      MetaDataUpdate.User metaDataUpdateFactory, ReviewDb db,
      IdentifiedUser user, PatchSetInfoFactory patchSetInfoFactory,
      Provider<PostReviewers> reviewersProvider,
      MergeabilityChecker mergeabilityChecker, ChangeHooks hooks,
      CreateChangeSender.Factory createChangeSenderFactory,
      ProjectCache projectCache,
      AllProjectsNameProvider allProjects,
      ChangesCollection changes,
      Provider<SetParent> setParent,

      @Assisted("projectName") Project.NameKey projectName,
      @Nullable @Assisted ObjectId base,
      @Assisted List<AccessSection> sectionList,
      @Nullable @Assisted("parentProjectName") Project.NameKey parentProjectName,
      @Nullable @Assisted String message) {
    super(projectControlFactory, groupBackend, metaDataUpdateFactory,
        allProjects, setParent, projectName, base, sectionList,
        parentProjectName, message, false);
    this.db = db;
    this.user = user;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.reviewersProvider = reviewersProvider;
    this.mergeabilityChecker = mergeabilityChecker;
    this.hooks = hooks;
    this.createChangeSenderFactory = createChangeSenderFactory;
    this.projectCache = projectCache;
    this.changes = changes;
  }

  @Override
  protected Change.Id updateProjectConfig(ProjectConfig config,
      MetaDataUpdate md, boolean parentProjectUpdate) throws IOException,
      OrmException {
    Change.Id changeId = new Change.Id(db.nextChangeId());
    PatchSet ps =
        new PatchSet(new PatchSet.Id(changeId, Change.INITIAL_PATCH_SET_ID));
    RevCommit commit = config.commitToNewRef(md, ps.getRefName());
    if (commit.getId().equals(base)) {
      return null;
    }

    Change change = new Change(
        new Change.Key("I" + commit.name()),
        changeId,
        user.getAccountId(),
        new Branch.NameKey(
            config.getProject().getNameKey(),
            RefNames.REFS_CONFIG),
        TimeUtil.nowTs());

    ps.setCreatedOn(change.getCreatedOn());
    ps.setUploader(change.getOwner());
    ps.setRevision(new RevId(commit.name()));

    PatchSetInfo info = patchSetInfoFactory.get(commit, ps.getId());
    change.setCurrentPatchSet(info);
    ChangeUtil.updated(change);

    db.changes().beginTransaction(changeId);
    try {
      insertAncestors(ps.getId(), commit);
      db.patchSets().insert(Collections.singleton(ps));
      db.changes().insert(Collections.singleton(change));
      db.commit();
    } finally {
      db.rollback();
    }
    mergeabilityChecker.newCheck().addChange(change).reindex().run();
    hooks.doPatchsetCreatedHook(change, ps, db);
    try {
      CreateChangeSender cm =
          createChangeSenderFactory.create(change);
      cm.setFrom(change.getOwner());
      cm.setPatchSet(ps, info);
      cm.send();
    } catch (Exception err) {
      log.error("Cannot send email for new change " + change.getId(), err);
    }
    ChangeResource rsrc;
    try {
      rsrc = changes.parse(changeId);
    } catch (ResourceNotFoundException e) {
      throw new IOException(e);
    }
    addProjectOwnersAsReviewers(rsrc);
    if (parentProjectUpdate) {
      addAdministratorsAsReviewers(rsrc);
    }
    return changeId;
  }

  private void insertAncestors(PatchSet.Id id, RevCommit src)
      throws OrmException {
    final int cnt = src.getParentCount();
    List<PatchSetAncestor> toInsert = new ArrayList<PatchSetAncestor>(cnt);
    for (int p = 0; p < cnt; p++) {
      PatchSetAncestor a;

      a = new PatchSetAncestor(new PatchSetAncestor.Id(id, p + 1));
      a.setAncestorRevision(new RevId(src.getParent(p).name()));
      toInsert.add(a);
    }
    db.patchSetAncestors().insert(toInsert);
  }

  private void addProjectOwnersAsReviewers(ChangeResource rsrc) {
    final String projectOwners =
        groupBackend.get(SystemGroupBackend.PROJECT_OWNERS).getName();
    try {
      AddReviewerInput input = new AddReviewerInput();
      input.reviewer = projectOwners;
      reviewersProvider.get().apply(rsrc, input);
    } catch (Exception e) {
      // one of the owner groups is not visible to the user and this it why it
      // can't be added as reviewer
    }
  }

  private void addAdministratorsAsReviewers(ChangeResource rsrc) {
    List<PermissionRule> adminRules =
        projectCache.getAllProjects().getConfig()
            .getAccessSection(AccessSection.GLOBAL_CAPABILITIES)
            .getPermission(GlobalCapability.ADMINISTRATE_SERVER).getRules();
    for (PermissionRule r : adminRules) {
      try {
        AddReviewerInput input = new AddReviewerInput();
        input.reviewer = r.getGroup().getUUID().get();
        reviewersProvider.get().apply(rsrc, input);
      } catch (Exception e) {
        // ignore
      }
    }
  }
}
