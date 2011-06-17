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
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import com.googlecode.prolog_cafe.compiler.CompileException;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
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

  private final boolean isAllProjects;
  private final ProjectCache projectCache;
  private final ProjectControl.AssistedFactory projectControlFactory;
  private final PrologEnvironment.Factory envFactory;
  private final GitRepositoryManager gitMgr;
  private final RulesCache rulesCache;

  private final ProjectConfig config;
  private final Set<AccountGroup.UUID> localOwners;

  /** Prolog rule state. */
  private transient PrologMachineCopy rulesMachine;

  /** Last system time the configuration's revision was examined. */
  private transient long lastCheckTime;

  @Inject
  protected ProjectState(
      final ProjectCache projectCache,
      final AllProjectsName allProjectsName,
      final ProjectControl.AssistedFactory projectControlFactory,
      final PrologEnvironment.Factory envFactory,
      final GitRepositoryManager gitMgr,
      final RulesCache rulesCache,
      @Assisted final ProjectConfig config) {
    this.projectCache = projectCache;
    this.isAllProjects = config.getProject().getNameKey().equals(allProjectsName);
    this.projectControlFactory = projectControlFactory;
    this.envFactory = envFactory;
    this.gitMgr = gitMgr;
    this.rulesCache = rulesCache;
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
    localOwners = Collections.unmodifiableSet(groups);
  }

  boolean needsRefresh(long generation) {
    if (generation <= 0) {
      return isRevisionOutOfDate();
    }
    if (lastCheckTime != generation) {
      lastCheckTime = generation;
      return isRevisionOutOfDate();
    }
    return false;
  }

  private boolean isRevisionOutOfDate() {
    try {
      Repository git = gitMgr.openRepository(getProject().getNameKey());
      try {
        Ref ref = git.getRef(GitRepositoryManager.REF_CONFIG);
        if (ref == null || ref.getObjectId() == null) {
          return true;
        }
        return !ref.getObjectId().equals(config.getRevision());
      } finally {
        git.close();
      }
    } catch (IOException gone) {
      return true;
    }
  }

  /** @return Construct a new PrologEnvironment for the calling thread. */
  public PrologEnvironment newPrologEnvironment() throws CompileException {
    PrologMachineCopy pmc = rulesMachine;
    if (pmc == null) {
      pmc = rulesCache.loadMachine(
          getProject().getNameKey(),
          getConfig().getRulesId());
      rulesMachine = pmc;
    }
    return envFactory.create(pmc);
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
    if (isAllProjects) {
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

    // The root of the tree is the special "All-Projects" case.
    if (parent == null) {
      inherited.addAll(projectCache.getAllProjects().getLocalAccessSections());
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

  /**
   * @return all {@link AccountGroup}'s to which the owner privilege for
   *         'refs/*' is assigned for this project (the local owners), if there
   *         are no local owners the local owners of the nearest parent project
   *         that has local owners are returned
   */
  public Set<AccountGroup.UUID> getOwners() {
    Project.NameKey parentName = getProject().getParent();
    if (!localOwners.isEmpty() || parentName == null || isAllProjects) {
      return localOwners;
    }

    ProjectState parent = projectCache.get(parentName);
    if (parent != null) {
      return parent.getOwners();
    }

    return Collections.emptySet();
  }

  /**
   * @return all {@link AccountGroup}'s that are allowed to administrate the
   *         complete project. This includes all groups to which the owner
   *         privilege for 'refs/*' is assigned for this project (the local
   *         owners) and all groups to which the owner privilege for 'refs/*' is
   *         assigned for one of the parent projects (the inherited owners).
   */
  public Set<AccountGroup.UUID> getAllOwners() {
    HashSet<AccountGroup.UUID> owners = new HashSet<AccountGroup.UUID>();
    owners.addAll(localOwners);

    Set<Project.NameKey> seen = new HashSet<Project.NameKey>();
    Project.NameKey parent = getProject().getParent();

    while (parent != null && seen.add(parent)) {
      ProjectState s = projectCache.get(parent);
      if (s != null) {
        owners.addAll(s.localOwners);
        parent = s.getProject().getParent();
      } else {
        break;
      }
    }

    return Collections.unmodifiableSet(owners);
  }

  public ProjectControl controlFor(final CurrentUser user) {
    return projectControlFactory.create(user, this);
  }
}
