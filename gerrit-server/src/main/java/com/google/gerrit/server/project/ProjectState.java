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
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
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

  /** Used to record a snapshot of project's AccessSections along
   *  with the revisionSet to which these sections apply
   */
  private static class AccessSections {
    final Set<ObjectId> configRevisionSet;
    final Collection<AccessSection> allAccessSections;

    public AccessSections(final Set<ObjectId> configRevisionSet,
        final Collection<AccessSection> allAccessSections) {
      this.configRevisionSet = configRevisionSet;
      this.allAccessSections = allAccessSections;
    }
  }

  private final AnonymousUser anonymousUser;
  private final Project.NameKey wildProject;
  private final ProjectCache projectCache;
  private final ProjectControl.AssistedFactory projectControlFactory;
  private final GitRepositoryManager gitMgr;

  private final ProjectConfig config;
  private final Set<AccountGroup.UUID> localOwners;

  /** Last system time the configuration's revision was examined. */
  private transient long lastCheckTime;
  private volatile AccessSections accessSections;

  @Inject
  protected ProjectState(final AnonymousUser anonymousUser,
      final ProjectCache projectCache,
      @WildProjectName final Project.NameKey wildProject,
      final ProjectControl.AssistedFactory projectControlFactory,
      final GitRepositoryManager gitMgr,
      @Assisted final ProjectConfig config) {
    this.anonymousUser = anonymousUser;
    this.projectCache = projectCache;
    this.wildProject = wildProject;
    this.projectControlFactory = projectControlFactory;
    this.gitMgr = gitMgr;
    this.config = config;
    this.lastCheckTime = System.currentTimeMillis();

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

  public Project getProject() {
    return getConfig().getProject();
  }

  public ProjectConfig getConfig() {
    return config;
  }

  /**
   * @param seen is the parents seen so far, used to detect loops
   * @return revision set of the metadata revisions of this project and
   *         all its ancestors.  A null indicates an undeterminable
   *         revision set and should be considered different from any
   *         other revision set, including other null revision sets.
   */
  protected Set<ObjectId> getConfigRevisionSet(Set<Project.NameKey> seen) {
    ObjectId revision = config.getRevision();
    if (revision == null) {
      return null;
    }
    Set<ObjectId> revisions = new HashSet<ObjectId>();
    revisions.add(revision);

    ProjectState parent = projectCache.get(getProject().getParent());
    if (parent == null) {
      parent = projectCache.get(wildProject);
    }
    if (parent != null && seen.add(getProject().getNameKey())) {
      Set<ObjectId> parentRevisions = parent.getConfigRevisionSet(seen);
      if (parentRevisions == null) {
        return null;
      }
      revisions.addAll(parentRevisions);
    }
    return Collections.unmodifiableSet(revisions);
  }

  /** @param seen is the parents seen so far, used to detect loops */
  protected AccessSections getAccessSections(Set<Project.NameKey> seen) {
    Set<ObjectId> configRevisionSet = getConfigRevisionSet(
        new HashSet<Project.NameKey>());
    if (accessSections == null || configRevisionSet == null ||
        !configRevisionSet.equals(accessSections.configRevisionSet)) {
      accessSections = calculateAccessSections(seen);
    }
    return accessSections;
  }

  /** @param seen is the parents seen so far, used to detect loops */
  private AccessSections calculateAccessSections(Set<Project.NameKey> seen) {
    ObjectId revision = config.getRevision();
    Set<ObjectId> revisions = null;
    if (revision != null) {
      revisions = new HashSet<ObjectId>();
      revisions.add(revision);
    }

    List<AccessSection> all = new ArrayList<AccessSection>(
        getConfig().getAccessSections());

    ProjectState parent = projectCache.get(getProject().getParent());
    if (parent == null) {
      parent = projectCache.get(wildProject);
    }
    if (parent != null && seen.add(getProject().getNameKey())) {
      AccessSections pvas = parent.getAccessSections(seen);
      if (revisions != null && pvas.configRevisionSet != null) {
        revisions.addAll(pvas.configRevisionSet);
      } else {
        revisions = null;
      }
      all.addAll(pvas.allAccessSections);
    }

    return new AccessSections(revisions, all);
  }

  /** Get both local and inherited access sections. */
  public Collection<AccessSection> getAllAccessSections() {
    return getAccessSections(new HashSet<Project.NameKey>()).allAccessSections;
  }

  /**
   * @return all {@link AccountGroup}'s to which the owner privilege for
   *         'refs/*' is assigned for this project (the local owners), if there
   *         are no local owners the local owners of the nearest parent project
   *         that has local owners are returned
   */
  public Set<AccountGroup.UUID> getOwners() {
    Project.NameKey parentName = getProject().getParent();
    if (!localOwners.isEmpty() || parentName == null || isWildProject()) {
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

  public ProjectControl controlForAnonymousUser() {
    return controlFor(anonymousUser);
  }

  public ProjectControl controlFor(final CurrentUser user) {
    return projectControlFactory.create(user, this);
  }

  private boolean isWildProject() {
    return wildProject.equals(getProject().getNameKey());
  }
}
