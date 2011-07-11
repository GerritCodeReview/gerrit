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

import com.google.gerrit.common.CollectionsUtil;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityCollection;
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
  private final AllProjectsName allProjectsName;
  private final ProjectCache projectCache;
  private final ProjectControl.AssistedFactory projectControlFactory;
  private final PrologEnvironment.Factory envFactory;
  private final GitRepositoryManager gitMgr;
  private final RulesCache rulesCache;

  private final ProjectConfig config;
  private final Set<AccountGroup.UUID> localOwners;

  /** Prolog rule state. */
  private volatile PrologMachineCopy rulesMachine;

  /** Last system time the configuration's revision was examined. */
  private volatile long lastCheckTime;

  /** Local access sections, wrapped in SectionMatchers for faster evaluation. */
  private volatile List<SectionMatcher> localAccessSections;

  /** If this is all projects, the capabilities used by the server. */
  private final CapabilityCollection capabilities;

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
    this.allProjectsName = allProjectsName;
    this.projectControlFactory = projectControlFactory;
    this.envFactory = envFactory;
    this.gitMgr = gitMgr;
    this.rulesCache = rulesCache;
    this.config = config;
    this.capabilities = isAllProjects
      ? new CapabilityCollection(config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES))
      : null;

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

  /**
   * @return cached computation of all global capabilities. This should only be
   *         invoked on the state from {@link ProjectCache#getAllProjects()}.
   *         Null on any other project.
   */
  public CapabilityCollection getCapabilityCollection() {
    return capabilities;
  }

  /** @return Construct a new PrologEnvironment for the calling thread. */
  public PrologEnvironment newPrologEnvironment() throws CompileException {
    PrologMachineCopy pmc = rulesMachine;
    if (pmc == null) {
      pmc = rulesCache.loadMachine(
          getProject().getNameKey(),
          config.getRulesId());
      rulesMachine = pmc;
    }
    return envFactory.create(pmc);
  }

  public Project getProject() {
    return config.getProject();
  }

  public ProjectConfig getConfig() {
    return config;
  }

  /** Get the sections that pertain only to this project. */
  private List<SectionMatcher> getLocalAccessSections() {
    List<SectionMatcher> sm = localAccessSections;
    if (sm == null) {
      Collection<AccessSection> fromConfig = config.getAccessSections();
      sm = new ArrayList<SectionMatcher>(fromConfig.size());
      for (AccessSection section : fromConfig) {
        SectionMatcher matcher = SectionMatcher.wrap(section);
        if (matcher != null) {
          sm.add(matcher);
        }
      }
      localAccessSections = sm;
    }
    return sm;
  }

  /**
   * Obtain all local and inherited sections. This collection is looked up
   * dynamically and is not cached. Callers should try to cache this result
   * per-request as much as possible.
   */
  List<SectionMatcher> getAllSections() {
    if (isAllProjects) {
      return getLocalAccessSections();
    }

    List<SectionMatcher> all = new ArrayList<SectionMatcher>();
    Set<Project.NameKey> seen = new HashSet<Project.NameKey>();
    seen.add(getProject().getNameKey());

    ProjectState s = this;
    do {
      all.addAll(s.getLocalAccessSections());

      Project.NameKey parent = s.getProject().getParent();
      if (parent == null || !seen.add(parent)) {
        break;
      }
      s = projectCache.get(parent);
    } while (s != null);
    all.addAll(projectCache.getAllProjects().getLocalAccessSections());
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
   * @return true if any of the groups listed in {@code groups} was declared to
   *         be an owner of this project, or one of its parent projects..
   */
  boolean isOwner(Set<AccountGroup.UUID> groups) {
    Set<Project.NameKey> seen = new HashSet<Project.NameKey>();
    seen.add(getProject().getNameKey());

    ProjectState s = this;
    do {
      if (CollectionsUtil.isAnyIncludedIn(s.localOwners, groups)) {
        return true;
      }

      Project.NameKey parent = s.getProject().getParent();
      if (parent == null || !seen.add(parent)) {
        break;
      }
      s = projectCache.get(parent);
    } while (s != null);
    return false;
  }

  public ProjectControl controlFor(final CurrentUser user) {
    return projectControlFactory.create(user, this);
  }

  /**
   * @return ProjectState of project's parent. If the project does not have a
   *         parent, return state of the top level project, All-Projects. If
   *         this project is All-Projects, return null.
   */
  public ProjectState getParentState() {
    if (isAllProjects) {
      return null;
    }
    Project.NameKey parentName = getProject().getParent();
    if (parentName == null) {
      parentName = allProjectsName;
    }
    return projectCache.get(parentName);
  }
}