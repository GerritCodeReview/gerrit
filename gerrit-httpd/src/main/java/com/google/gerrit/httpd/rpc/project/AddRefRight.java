// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.NoSuchGroupException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

import java.util.Collections;

import javax.annotation.Nullable;

class AddRefRight extends Handler<ProjectDetail> {
  interface Factory {
    AddRefRight create(@Assisted Project.NameKey projectName,
        @Assisted ApprovalCategory.Id categoryId,
        @Assisted("groupName") String groupName,
        @Nullable @Assisted("refPattern") String refPattern,
        @Assisted("min") short min, @Assisted("max") short max);
  }

  private final ProjectDetailFactory.Factory projectDetailFactory;
  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final GroupCache groupCache;
  private final ReviewDb db;
  private final ApprovalTypes approvalTypes;

  private final Project.NameKey projectName;
  private final ApprovalCategory.Id categoryId;
  private final AccountGroup.NameKey groupName;
  private final String refPattern;
  private final short min;
  private final short max;

  @Inject
  AddRefRight(final ProjectDetailFactory.Factory projectDetailFactory,
      final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final GroupCache groupCache,
      final ReviewDb db, final ApprovalTypes approvalTypes,

      @Assisted final Project.NameKey projectName,
      @Assisted final ApprovalCategory.Id categoryId,
      @Assisted("groupName") final String groupName,
      @Nullable @Assisted("refPattern") final String refPattern,
      @Assisted("min") final short min, @Assisted("max") final short max) {
    this.projectDetailFactory = projectDetailFactory;
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.groupCache = groupCache;
    this.approvalTypes = approvalTypes;
    this.db = db;

    this.projectName = projectName;
    this.categoryId = categoryId;
    this.groupName = new AccountGroup.NameKey(groupName);
    this.refPattern = refPattern;

    if (min <= max) {
      this.min = min;
      this.max = max;
    } else {
      this.min = max;
      this.max = min;
    }
  }

  @Override
  public ProjectDetail call() throws NoSuchProjectException, OrmException,
      NoSuchGroupException, InvalidNameException {
    final ProjectControl projectControl =
        projectControlFactory.ownerFor(projectName);

    final ApprovalType at = approvalTypes.getApprovalType(categoryId);
    if (at == null || at.getValue(min) == null || at.getValue(max) == null) {
      throw new IllegalArgumentException("Invalid category " + categoryId
          + " or range " + min + ".." + max);
    }

    String refPattern = this.refPattern;
    if (refPattern == null || refPattern.isEmpty()) {
      if (categoryId.equals(ApprovalCategory.SUBMIT)
          || categoryId.equals(ApprovalCategory.PUSH_HEAD)) {
        // Explicitly related to a branch head.
        refPattern = "refs/heads/*";

      } else if (!at.getCategory().isAction()) {
        // Non actions are approval votes on a change, assume these apply
        // to branch heads only.
        refPattern = "refs/heads/*";

      } else if (categoryId.equals(ApprovalCategory.PUSH_TAG)) {
        // Explicitly related to the tag namespace.
        refPattern = "refs/tags/*";

      } else if (categoryId.equals(ApprovalCategory.READ)
          || categoryId.equals(ApprovalCategory.OWN)) {
        // Currently these are project-wide rights, so apply that way.
        refPattern = "refs/*";

      } else {
        // Assume project wide for the default.
        refPattern = "refs/*";
      }
    }
    while (refPattern.startsWith("/")) {
      refPattern = refPattern.substring(1);
    }
    if (!refPattern.startsWith(Constants.R_REFS)) {
      refPattern = Constants.R_HEADS + refPattern;
    }
    if (refPattern.endsWith("/*")) {
      final String prefix = refPattern.substring(0, refPattern.length() - 2);
      if (!Repository.isValidRefName(prefix)) {
        throw new InvalidNameException();
      }
    } else {
      if (!Repository.isValidRefName(refPattern)) {
        throw new InvalidNameException();
      }
    }

    // TODO Support per-branch READ access.
    if (ApprovalCategory.READ.equals(categoryId)
        && !refPattern.equals("refs/*")) {
      throw new UnsupportedOperationException("READ on " + refPattern
          + " not yet supported.");
    }

    final AccountGroup group = groupCache.get(groupName);
    if (group == null) {
      throw new NoSuchGroupException(groupName);
    }
    final RefRight.Key key =
        new RefRight.Key(projectName, new RefRight.RefPattern(refPattern),
            categoryId, group.getId());
    RefRight rr = db.refRights().get(key);
    if (rr == null) {
      rr = new RefRight(key);
      rr.setMinValue(min);
      rr.setMaxValue(max);
      db.refRights().insert(Collections.singleton(rr));
    } else {
      rr.setMinValue(min);
      rr.setMaxValue(max);
      db.refRights().update(Collections.singleton(rr));
    }
    projectCache.evict(projectControl.getProject());
    return projectDetailFactory.create(projectName).call();
  }
}
