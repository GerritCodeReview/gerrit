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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

public class ReviewProjectAccess extends ProjectAccessHandler<Change.Id> {
  interface Factory {
    ReviewProjectAccess create(@Assisted Project.NameKey projectName,
        @Nullable @Assisted ObjectId base,
        @Assisted List<AccessSection> sectionList,
        @Nullable @Assisted String message);
  }

  private final ReviewDb db;
  private final IdentifiedUser user;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final Provider<PostReviewers> reviewersProvider;
  private final ChangeControl.GenericFactory changeFactory;

  @Inject
  ReviewProjectAccess(final ProjectControl.Factory projectControlFactory,
      final GroupBackend groupBackend,
      final MetaDataUpdate.User metaDataUpdateFactory, final ReviewDb db,
      final IdentifiedUser user, final PatchSetInfoFactory patchSetInfoFactory,
      final Provider<PostReviewers> reviewersProvider,
      final ChangeControl.GenericFactory changeFactory,

      @Assisted final Project.NameKey projectName,
      @Nullable @Assisted final ObjectId base,
      @Assisted List<AccessSection> sectionList,
      @Nullable @Assisted String message) {
    super(projectControlFactory, groupBackend, metaDataUpdateFactory,
        projectName, base, sectionList, message, false);
    this.db = db;
    this.user = user;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.reviewersProvider = reviewersProvider;
    this.changeFactory = changeFactory;
  }

  @Override
  protected Change.Id updateProjectConfig(ProjectConfig config, MetaDataUpdate md)
      throws IOException, OrmException {
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
            GitRepositoryManager.REF_CONFIG));

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
      addProjectOwnersAsReviewers(change);
      db.commit();
    } finally {
      db.rollback();
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

  private void addProjectOwnersAsReviewers(final Change change) {
    final String projectOwners =
        groupBackend.get(AccountGroup.PROJECT_OWNERS).getName();
    try {
      ChangeResource rsrc =
          new ChangeResource(changeFactory.controlFor(change, user));
      PostReviewers.Input input = new PostReviewers.Input();
      input.reviewer = projectOwners;
      reviewersProvider.get().apply(rsrc, input);
    } catch (Exception e) {
      // one of the owner groups is not visible to the user and this it why it
      // can't be added as reviewer
    }
  }
}
