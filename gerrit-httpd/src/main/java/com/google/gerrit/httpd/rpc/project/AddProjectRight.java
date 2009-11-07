// Copyright (C) 2009 The Android Open Source Project
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
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ProjectRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.account.NoSuchGroupException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;

class AddProjectRight extends Handler<ProjectDetail> {
  interface Factory {
    AddProjectRight create(@Assisted Project.NameKey projectName,
        @Assisted ApprovalCategory.Id categoryId, @Assisted String groupName,
        @Assisted("min") short min, @Assisted("max") short max);
  }

  private final ProjectDetailFactory.Factory projectDetailFactory;
  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final ReviewDb db;
  private final ApprovalTypes approvalTypes;

  private final Project.NameKey projectName;
  private final ApprovalCategory.Id categoryId;
  private final AccountGroup.NameKey groupName;
  private final short min;
  private final short max;

  @Inject
  AddProjectRight(final ProjectDetailFactory.Factory projectDetailFactory,
      final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final ReviewDb db,
      final ApprovalTypes approvalTypes,

      @Assisted final Project.NameKey projectName,
      @Assisted final ApprovalCategory.Id categoryId,
      @Assisted final String groupName, @Assisted("min") final short min,
      @Assisted("max") final short max) {
    this.projectDetailFactory = projectDetailFactory;
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.approvalTypes = approvalTypes;
    this.db = db;

    this.projectName = projectName;
    this.categoryId = categoryId;
    this.groupName = new AccountGroup.NameKey(groupName);

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
      NoSuchGroupException {
    final ProjectControl projectControl =
        projectControlFactory.ownerFor(projectName);

    if (projectControl.getProjectState().isSpecialWildProject()
        && ApprovalCategory.OWN.equals(categoryId)) {
      // Giving out control of the WILD_PROJECT to other groups beyond
      // Administrators is dangerous. Having control over WILD_PROJECT
      // is about the same as having Administrator access as users are
      // able to affect grants in all projects on the system.
      //
      throw new IllegalArgumentException("Cannot give " + categoryId.get()
          + " on " + projectName + " " + groupName);
    }

    final ApprovalType at = approvalTypes.getApprovalType(categoryId);
    if (at == null || at.getValue(min) == null || at.getValue(max) == null) {
      throw new IllegalArgumentException("Invalid category " + categoryId
          + " or range " + min + ".." + max);
    }

    final AccountGroup group = db.accountGroups().get(groupName);
    if (group == null) {
      throw new NoSuchGroupException(groupName);
    }

    final ProjectRight.Key key =
        new ProjectRight.Key(projectName, categoryId, group.getId());
    ProjectRight pr = db.projectRights().get(key);
    if (pr == null) {
      pr = new ProjectRight(key);
      pr.setMinValue(min);
      pr.setMaxValue(max);
      db.projectRights().insert(Collections.singleton(pr));
    } else {
      pr.setMinValue(min);
      pr.setMaxValue(max);
      db.projectRights().update(Collections.singleton(pr));
    }

    projectCache.evict(projectControl.getProject());
    return projectDetailFactory.create(projectName).call();
  }
}
