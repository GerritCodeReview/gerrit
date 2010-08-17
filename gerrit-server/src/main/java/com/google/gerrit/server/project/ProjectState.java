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

import com.google.gerrit.reviewdb.AccessCategory;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.NewRefRight;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.WildProjectName;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cached information on a project. */
public class ProjectState {
  public interface Factory {
    ProjectState create(Project project, Collection<RefRight> localRights,
        Collection<NewRefRight> localNewRights);
  }

  private final AnonymousUser anonymousUser;
  private final Project.NameKey wildProject;
  private final ProjectCache projectCache;

  private final Project project;
  private final Collection<RefRight> localRights;
  private final Collection<NewRefRight> localNewRights;
  private final Set<AccountGroup.Id> owners;

  private volatile Collection<RefRight> inheritedRights;
  private volatile Collection<NewRefRight> inheritedNewRights;

  @Inject
  protected ProjectState(final AnonymousUser anonymousUser,
      final ProjectCache projectCache,
      @WildProjectName final Project.NameKey wildProject,
      @Assisted final Project project,
      @Assisted final Collection<RefRight> rights,
      @Assisted final Collection<NewRefRight> newRights) {
    this.anonymousUser = anonymousUser;
    this.projectCache = projectCache;
    this.wildProject = wildProject;

    this.project = project;
    this.localRights = rights;
    this.localNewRights = newRights;

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

  /**
   * Get the rights that pertain only to this project.
   *
   * @param action the category requested.
   * @return immutable collection of rights for the requested category.
   */
  public Collection<RefRight> getLocalRights(ApprovalCategory.Id action) {
    return filter(getLocalRights(), action);
  }

  /** Get the rights this project inherits from the wild project. */
  public Collection<RefRight> getInheritedRights() {
    if (inheritedRights == null) {
      inheritedRights = computeInheritedRights();
    }
    return inheritedRights;
  }

  private Collection<RefRight> computeInheritedRights() {
    if (isSpecialWildProject()) {
      return Collections.emptyList();
    }

    List<RefRight> inherited = new ArrayList<RefRight>();
    Set<Project.NameKey> seen = new HashSet<Project.NameKey>();
    Project.NameKey parent = project.getParent();

    while (parent != null && seen.add(parent)) {
      ProjectState s = projectCache.get(parent);
      if (s != null) {
        inherited.addAll(s.getLocalRights());
        parent = s.getProject().getParent();
      } else {
        break;
      }
    }

    // Wild project is the parent, or the root of the tree
    if (parent == null) {
      inherited.addAll(getWildProjectRights());
    }

    return Collections.unmodifiableCollection(inherited);
  }

  private Collection<RefRight> getWildProjectRights() {
    final ProjectState s = projectCache.get(wildProject);
    return s != null ? s.getLocalRights() : Collections.<RefRight> emptyList();
  }

  /**
   * Get the rights this project inherits from the wild project.
   *
   * @param action the category requested.
   * @return immutable collection of rights for the requested category.
   */
  public Collection<RefRight> getInheritedRights(ApprovalCategory.Id action) {
    if (action.canInheritFromWildProject()) {
      return filter(getInheritedRights(), action);
    }
    return Collections.emptyList();
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

  private static Collection<RefRight> filter(Collection<RefRight> all,
      ApprovalCategory.Id actionId) {
    if (all.isEmpty()) {
      return Collections.emptyList();
    }
    final Collection<RefRight> mine = new ArrayList<RefRight>(all.size());
    for (final RefRight right : all) {
      if (right.getApprovalCategoryId().equals(actionId)) {
        mine.add(right);
      }
    }
    if (mine.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableCollection(mine);
  }

  public Collection<NewRefRight> getLocalNewRights() {
    return localNewRights;
  }

  public Collection<NewRefRight> getLocalNewRights(
      AccessCategory.Id accessCategory) {
    return filter(getLocalNewRights(), accessCategory);
  }

  public Collection<NewRefRight> getInheritedNewRights() {
    if (inheritedNewRights == null) {
      computeInheritedNewRights();
    }
    return inheritedNewRights;
  }

  private void computeInheritedNewRights() {
    if (isSpecialWildProject()) {
      inheritedNewRights = Collections.emptyList();
    } else {
      final List<NewRefRight> ir = new ArrayList<NewRefRight>();

      Set<Project.NameKey> seen = new HashSet<Project.NameKey>();
      Project.NameKey parent = project.getParent();

      while (parent != null && seen.add(parent)) {
        ProjectState s = projectCache.get(parent);
        if (s != null) {
          ir.addAll(s.getLocalNewRights());
          parent = s.getProject().getParent();
        } else {
          break;
        }
      }

      // Wild project is the parent, or the root of the tree
      if (parent == null) {
        ir.addAll(getWildProjectNewRights());
      }

      inheritedNewRights = Collections.unmodifiableCollection(ir);
    }
  }

  private Collection<NewRefRight> getWildProjectNewRights() {
    final ProjectState s = projectCache.get(wildProject);
    return s != null ? s.getLocalNewRights() : Collections
        .<NewRefRight> emptyList();
  }

  private static Collection<NewRefRight> filter(Collection<NewRefRight> all,
      AccessCategory.Id acccessCategoryId) {
    if (all.isEmpty()) {
      return Collections.emptyList();
    }
    final Collection<NewRefRight> mine = new ArrayList<NewRefRight>(all.size());
    for (final NewRefRight right : all) {
      if (right.getAccessCategoryId().equals(acccessCategoryId)) {
        mine.add(right);
      }
    }
    if (mine.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableCollection(mine);
  }
}
