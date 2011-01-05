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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.git.ProjectConfig;
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
    ProjectState create(ProjectConfig config);
  }

  private final AnonymousUser anonymousUser;
  private final Project.NameKey wildProject;
  private final ProjectCache projectCache;
  private final ProjectControl.AssistedFactory projectControlFactory;

  private final ProjectConfig config;
  private final Set<AccountGroup.UUID> owners;

  @Inject
  protected ProjectState(final AnonymousUser anonymousUser,
      final ProjectCache projectCache,
      @WildProjectName final Project.NameKey wildProject,
      final ProjectControl.AssistedFactory projectControlFactory,
      @Assisted final ProjectConfig config) {
    this.anonymousUser = anonymousUser;
    this.projectCache = projectCache;
    this.wildProject = wildProject;
    this.projectControlFactory = projectControlFactory;
    this.config = config;

    HashSet<AccountGroup.UUID> groups = new HashSet<AccountGroup.UUID>();
    AccessSection all = config.getAccessSection(AccessSection.ALL);
    if (all != null) {
      Permission owner = all.getPermission(Permission.OWNER);
      if (owner != null) {
        for (PermissionRule rule : owner.getRules()) {
          GroupReference ref = rule.getGroup();
          if (ref.getUUID() != null) {
            groups.add(ref.getUUID());
          }
        }
      }
    }
    owners = Collections.unmodifiableSet(groups);
  }

  public Project getProject() {
    return getConfig().getProject();
  }

  public ProjectConfig getConfig() {
    return config;
  }

  /** Get the rights that pertain only to this project. */
  public Collection<AccessSection> getLocalAccessSections() {
    return getConfig().getAccessSections();
  }

  /** Get the rights this project inherits. */
  public Collection<AccessSection> getInheritedAccessSections() {
    if (getProject().getNameKey().equals(wildProject)) {
      return Collections.emptyList();
    }

    List<AccessSection> inherited = new ArrayList<AccessSection>();
    Set<Project.NameKey> seen = new HashSet<Project.NameKey>();
    Project.NameKey parent = getProject().getParent();

    while (parent != null && seen.add(parent)) {
      ProjectState s = projectCache.get(parent);
      if (s != null) {
        inherited.addAll(s.getLocalAccessSections());
        parent = s.getProject().getParent();
      } else {
        break;
      }
    }

    // Wild project is the parent, or the root of the tree
    if (parent == null) {
      ProjectState s = projectCache.get(wildProject);
      if (s != null) {
        inherited.addAll(s.getLocalAccessSections());
      }
    }

    return inherited;
  }

  /** Get both local and inherited access sections. */
  public Collection<AccessSection> getAllAccessSections() {
    List<AccessSection> all = new ArrayList<AccessSection>();
    all.addAll(getLocalAccessSections());
    all.addAll(getInheritedAccessSections());
    return all;
  }

  public Set<AccountGroup.UUID> getOwners() {
    return owners;
  }

  public ProjectControl controlForAnonymousUser() {
    return controlFor(anonymousUser);
  }

  public ProjectControl controlFor(final CurrentUser user) {
    return projectControlFactory.create(user, this);
  }
}
