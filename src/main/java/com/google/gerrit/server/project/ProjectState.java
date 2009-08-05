// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gwtorm.client.OrmException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Cached information on a project. */
public class ProjectState {
  final ProjectCache projectCache;
  private final Project project;
  private final Collection<ProjectRight> rights;
  private final Set<AccountGroup.Id> owners;

  protected ProjectState(final ProjectCache pc, final ReviewDb db,
      final Project p) throws OrmException {
    projectCache = pc;
    project = p;
    rights =
        Collections.unmodifiableCollection(db.projectRights().byProject(
            project.getNameKey()).toList());

    final HashSet<AccountGroup.Id> groups = new HashSet<AccountGroup.Id>();
    for (final ProjectRight right : rights) {
      if (ApprovalCategory.OWN.equals(right.getApprovalCategoryId())
          && right.getMaxValue() > 0) {
        groups.add(right.getAccountGroupId());
      }
    }
    owners = Collections.unmodifiableSet(groups);
  }

  public Project getProject() {
    return project;
  }

  public Collection<ProjectRight> getRights() {
    return rights;
  }

  public Set<AccountGroup.Id> getOwners() {
    return owners;
  }

  public ProjectControl controlForAnonymousUser() {
    return controlFor(projectCache.anonymousUser);
  }

  public ProjectControl controlFor(final CurrentUser user) {
    return new ProjectControl(user, this);
  }
}
