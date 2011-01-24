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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/** Cached information on a project. */
public class ProjectState {
  public interface Factory {
    ProjectState create(Project project, Collection<RefRight> localRights);
  }

  private final AnonymousUser anonymousUser;
  private final Project.NameKey wildProject;
  private final ProjectCache projectCache;
  private final ProjectControl.AssistedFactory projectControlFactory;

  private final Project project;
  private final Collection<RefRight> localRights;
  private final Set<AccountGroup.Id> localOwners;

  private volatile Collection<RefRight> inheritedRights;

  @Inject
  protected ProjectState(final AnonymousUser anonymousUser,
      final ProjectCache projectCache,
      @WildProjectName final Project.NameKey wildProject,
      final ProjectControl.AssistedFactory projectControlFactory,
      @Assisted final Project project,
      @Assisted Collection<RefRight> rights) {
    this.anonymousUser = anonymousUser;
    this.projectCache = projectCache;
    this.wildProject = wildProject;
    this.projectControlFactory = projectControlFactory;

    if (wildProject.equals(project.getNameKey())) {
      rights = new ArrayList<RefRight>(rights);
      for (Iterator<RefRight> itr = rights.iterator(); itr.hasNext();) {
        if (!itr.next().getApprovalCategoryId().canBeOnWildProject()) {
          itr.remove();
        }
      }
      rights = Collections.unmodifiableCollection(rights);
    }

    this.project = project;
    this.localRights = rights;

    final HashSet<AccountGroup.Id> groups = new HashSet<AccountGroup.Id>();
    for (final RefRight right : rights) {
      if (ApprovalCategory.OWN.equals(right.getApprovalCategoryId())
          && right.getMaxValue() > 0) {
        groups.add(right.getAccountGroupId());
      }
    }
    localOwners = Collections.unmodifiableSet(groups);
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

  void setInheritedRights(Collection<RefRight> all) {
    inheritedRights = all;
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
   * Utility class that is needed to filter overridden refrights
   */
  private static class Grant {
    final AccountGroup.Id group;
    final String pattern;

    private Grant(AccountGroup.Id group, String pattern) {
      this.group = group;
      this.pattern = pattern;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null)
        return false;
      Grant grant = (Grant) o;
      return group.equals(grant.group) && pattern.equals(grant.pattern);
    }

    @Override
    public int hashCode() {
      int result = group.hashCode();
      result = 31 * result + pattern.hashCode();
      return result;
    }
  }

  /**
   * Get the rights this project has and inherits from the wild project.
   *
   * @param action the category requested.
   * @param dropOverridden whether to remove inherited permissions in case if we have a
   *     local one that matches (action,group,ref)
   * @return immutable collection of rights for the requested category.
   */
  public Collection<RefRight> getAllRights(ApprovalCategory.Id action, boolean dropOverridden) {
    Collection<RefRight> rights = new LinkedList<RefRight>(getLocalRights(action));
    rights.addAll(filter(getInheritedRights(), action));
    if (dropOverridden) {
      Set<Grant> grants = new HashSet<Grant>();
      Iterator<RefRight> iter = rights.iterator();
      while (iter.hasNext()) {
        RefRight right = iter.next();

        Grant grant = new Grant(right.getAccountGroupId(), right.getRefPattern());
        if (grants.contains(grant)) {
          iter.remove();
        } else {
          grants.add(grant);
        }
      }
    }
    return Collections.unmodifiableCollection(rights);
  }

  /** Is this the special wild project which manages inherited rights? */
  public boolean isSpecialWildProject() {
    return project.getNameKey().equals(wildProject);
  }

  /**
   * @return all {@link AccountGroup}'s to which the owner privilege is assigned
   *         for this project (the local owners), if there are no local owners
   *         the local owners of the nearest parent project that has local
   *         owners are returned
   */
  public Set<AccountGroup.Id> getOwners() {
    if (!localOwners.isEmpty()) {
      return localOwners;
    } else {
      final ProjectState parent = projectCache.get(project.getParent());
      return parent.getOwners();
    }
  }

  /**
   * @return all {@link AccountGroup}'s that are allowed to administrate the
   *         project. This includes all groups to which the owner privilege is
   *         assigned for this project (the local owners) and all groups to
   *         which the owner privilege is assigned for one of the parent
   *         projects (the inherited owners).
   */
  public Set<AccountGroup.Id> getAllOwners() {
    final HashSet<AccountGroup.Id> owners = new HashSet<AccountGroup.Id>();
    for (final RefRight right : getAllRights(ApprovalCategory.OWN, true)) {
      if (right.getMaxValue() > 0) {
        owners.add(right.getAccountGroupId());
      }
    }
    return Collections.unmodifiableSet(owners);
  }

  public ProjectControl controlForAnonymousUser() {
    return controlFor(anonymousUser);
  }

  public ProjectControl controlFor(final CurrentUser user) {
    return projectControlFactory.create(user, this);
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
}
