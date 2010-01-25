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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.WildProjectName;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Cached information on a project. */
public class ProjectState {
  public interface Factory {
    ProjectState create(Project project, Collection<RefRight> localRights,
        InheritedRights inheritedRights);
  }

  public interface InheritedRights {
    Collection<RefRight> get();
  }

  private final AnonymousUser anonymousUser;
  private final Project.NameKey wildProject;

  private final Project project;
  private final Collection<RefRight> localRights;
  private final InheritedRights inheritedRights;
  private final Set<AccountGroup.Id> owners;

  @Inject
  protected ProjectState(final AnonymousUser anonymousUser,
      @WildProjectName final Project.NameKey wildProject,
      @Assisted final Project project,
      @Assisted final Collection<RefRight> rights,
      @Assisted final InheritedRights inheritedRights) {
    this.anonymousUser = anonymousUser;
    this.wildProject = wildProject;

    this.project = project;
    this.localRights = rights;
    this.inheritedRights = inheritedRights;

    final HashSet<AccountGroup.Id> groups = new HashSet<AccountGroup.Id>();
    for (final RefRight right : rights) {
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

  /** Get the rights that pertain only to this project. */
  public Collection<RefRight> getLocalRights() {
    return localRights;
  }

  /** Get the rights this project inherits from the wild project. */
  public Collection<RefRight> getInheritedRights() {
    if (isSpecialWildProject()) {
      return Collections.emptyList();
    }
    return inheritedRights.get();
  }

  /** Is this the special wild project which manages inherited rights? */
  public boolean isSpecialWildProject() {
    return project.getNameKey().equals(wildProject);
  }

  public Set<AccountGroup.Id> getOwners() {
    return owners;
  }

  public ProjectControl controlForAnonymousUser() {
    return controlFor(anonymousUser);
  }

  public ProjectControl controlFor(final CurrentUser user) {
    return new ProjectControl(user, this);
  }
}
