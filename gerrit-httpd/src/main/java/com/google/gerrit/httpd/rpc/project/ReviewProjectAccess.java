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
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.patch.AddReviewer;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
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
  private final AddReviewer.Factory addReviewerFactory;

  @Inject
  ReviewProjectAccess(final ProjectControl.Factory projectControlFactory,
      final GroupCache groupCache,
      final MetaDataUpdate.User metaDataUpdateFactory, final ReviewDb db,
      final IdentifiedUser user, final PatchSetInfoFactory patchSetInfoFactory,
      final AddReviewer.Factory addReviewerFactory,

      @Assisted final Project.NameKey projectName,
      @Nullable @Assisted final ObjectId base,
      @Assisted List<AccessSection> sectionList,
      @Nullable @Assisted String message) {
    super(projectControlFactory, groupCache, metaDataUpdateFactory,
        projectName, base, sectionList, message, false);
    this.db = db;
    this.user = user;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.addReviewerFactory = addReviewerFactory;
  }

  @Override
  protected Change.Id updateProjectConfig(ProjectConfig config, MetaDataUpdate md)
      throws IOException, NoSuchProjectException, ConfigInvalidException, OrmException {
    int nextChangeId = db.nextChangeId();
    PatchSet.Id patchSetId = new PatchSet.Id(new Change.Id(nextChangeId), 1);
    final PatchSet ps = new PatchSet(patchSetId);
    RevCommit commit = config.commitToNewRef(md, ps.getRefName());
    if (commit.getId().equals(base)) {
      return null;
    }
    Change.Key changeKey = new Change.Key("I" + commit.name());
    final Change change =
        new Change(changeKey, new Change.Id(nextChangeId), user.getAccountId(),
            new Branch.NameKey(config.getProject().getNameKey(),
                GitRepositoryManager.REF_CONFIG));
    change.nextPatchSetId();

    ps.setCreatedOn(change.getCreatedOn());
    ps.setUploader(user.getAccountId());
    ps.setRevision(new RevId(commit.name()));

    db.patchSets().insert(Collections.singleton(ps));

    final PatchSetInfo info = patchSetInfoFactory.get(commit, ps.getId());
    change.setCurrentPatchSet(info);
    ChangeUtil.updated(change);

    db.changes().insert(Collections.singleton(change));

    addProjectOwnersAsReviewers(change.getId());

    return change.getId();
  }

  private void addProjectOwnersAsReviewers(final Change.Id changeId) {
    final String projectOwners =
        groupCache.get(AccountGroup.PROJECT_OWNERS).getName();
    try {
      addReviewerFactory.create(changeId, Collections.singleton(projectOwners),
          false).call();
    } catch (Exception e) {
      // one of the owner groups is not visible to the user and this it why it
      // can't be added as reviewer
    }
  }
}
