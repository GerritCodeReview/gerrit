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
    ProjectState create(Project project, Set<Project.NameKey> parentKeys,
      Collection<RefRight> localRights);
  }

  private final AnonymousUser anonymousUser;
  private final Project.NameKey wildProject;
  private final ProjectCache projectCache;
  private final ProjectControl.AssistedFactory projectControlFactory;

  private final Project project;
  private Set<Project.NameKey> parents;
  private Set<List<Project.NameKey>> ancestorLines;
  private Set<List<RefRight>> inheritedRightsLines;
  private Collection<RefRight> inheritedRights;
  private final Collection<RefRight> localRights;
  private final Set<AccountGroup.Id> owners;


  @Inject
  protected ProjectState(final AnonymousUser anonymousUser,
      final ProjectCache projectCache,
      @WildProjectName final Project.NameKey wildProject,
      final ProjectControl.AssistedFactory projectControlFactory,
      @Assisted final Project project,
      @Assisted Set<Project.NameKey> parents,
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
    this.parents = parents;
    this.localRights = rights;

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

  public Set<Project.NameKey> getParents() {
    return parents;
  }

  public void setParents(final Set<Project.NameKey> parents) {
      this.parents = parents;
  }

  /** Return all the paths from this project back to the wildcard project */
  public Set<List<Project.NameKey>> getAncestorLines() {
    if (ancestorLines == null) {
      ancestorLines = computeAncestorLines();
    }
    return ancestorLines;
  }

  private Set<List<Project.NameKey>> computeAncestorLines() {
    final Set<List<Project.NameKey>> lines =
      new HashSet<List<Project.NameKey>>();

    if (getParents() == null || getParents().size() == 0) {
      final List<Project.NameKey> line = new LinkedList();
      line.add(getProject().getNameKey());
      line.add(wildProject);
      lines.add(line);
    } else {
      for (final Project.NameKey p: getParents()) {
        final ProjectState s = projectCache.get(p);
        if (s != null) {
          final Set<List<Project.NameKey>> subLines = s.getAncestorLines();
          for (final List<Project.NameKey> l: subLines) {
            final List<Project.NameKey> line = new LinkedList(l);
            line.add(0, getProject().getNameKey());
            lines.add(line);
          }
        }
      }
    }
    return Collections.unmodifiableSet(lines);
  }

  /** Get the rights this project inherits from the parent lines. */
  public Set<List<RefRight>> getInheritedRightsLines() {
    if (inheritedRightsLines == null) {
      inheritedRightsLines = computeInheritedRightsLines();
    }
    return inheritedRightsLines;
  }

  private Set<List<RefRight>> computeInheritedRightsLines() {
    final Set<List<RefRight>> rightLines = new HashSet<List<RefRight>>();
    for (final List<Project.NameKey> line : getAncestorLines()) {
      rightLines.add(computeRightsLine(line));
    }
    return Collections.unmodifiableSet(rightLines);
  }

  private List<RefRight> computeRightsLine(List<Project.NameKey> line) {
    final List<RefRight> rights = new LinkedList<RefRight>();
    for (final Project.NameKey a : line) {
      final ProjectState s = projectCache.get(a);
      if (s != null) {
        rights.addAll(s.getLocalRights());
      }
    }
    return Collections.unmodifiableList(rights);
  }

  /** Get the rights this project inherits from all ancestors. */
  public Collection<RefRight> getInheritedRights() {
    if (inheritedRights == null) {
      inheritedRights = computeInheritedRights();
    }
    return inheritedRights;
  }

  private Collection<RefRight> computeInheritedRights() {
    Set<RefRight> inherited = new HashSet<RefRight>();
    for (Project.NameKey a : getParents()) {
      ProjectState s = projectCache.get(a);
      if (s != null) {
        Set<List<RefRight>> lines = s.getInheritedRightsLines();
        for (final List<RefRight> line : lines) {
          inherited.addAll(line);
        }
      }
    }
    return Collections.unmodifiableCollection(inherited);
  }

  /** Get the rights that pertain only to this project. */
  public Collection<RefRight> getLocalRights() {
    return localRights;
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
   * Get the rights this project has and inherits from other projects.
   *
   * @param action the category requested.
   * @param dropOverridden whether to remove inherited permissions in case if we have a
   *     local one that matches (action,group,ref)
   * @return immutable collection of rights for the requested category.
   */
  public Collection<RefRight> getAllRights(ApprovalCategory.Id action, boolean dropOverridden) {
    final Collection<RefRight> rights = new LinkedList<RefRight>();
    for (final List<RefRight> line : getInheritedRightsLines()) {
      final List<RefRight> filtered = filter(line, action);
      if (dropOverridden) {
        rights.addAll(dropOverriddenRights(filtered));
      } else {
        rights.addAll(filtered);
      }
    }
    return Collections.unmodifiableCollection(rights);
  }

  private static List<RefRight> dropOverriddenRights(List<RefRight> rights) {
    List<RefRight> granted = new ArrayList<RefRight>();
    final Set<Grant> grants = new HashSet<Grant>();
    for (final RefRight right: rights) {
      final Grant grant = new Grant(right.getAccountGroupId(), right.getRefPattern());
      if (! grants.contains(grant)) {
        grants.add(grant);
        granted.add(right);
      }
    }
    return granted;
  }

  /** Is this the special wild project? */
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
    return projectControlFactory.create(user, this);
  }

  private static List<RefRight> filter(List<RefRight> all,
      ApprovalCategory.Id actionId) {
    if (all.isEmpty()) {
      return Collections.emptyList();
    }
    final List<RefRight> mine = new ArrayList<RefRight>(all.size());
    for (final RefRight right : all) {
      if (right.getApprovalCategoryId().equals(actionId)) {
        mine.add(right);
      }
    }
    if (mine.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(mine);
  }
}
