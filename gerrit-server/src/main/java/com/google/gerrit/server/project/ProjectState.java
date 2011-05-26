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
    final Set<List<AccessSection>> accessSectionLines;

    public AccessSections(final Set<ObjectId> configRevisionSet,
        final Set<List<AccessSection>> accessSectionLines) {
      this.configRevisionSet = configRevisionSet;
      this.accessSectionLines = accessSectionLines;
    }
  }

  private static class ROAccessSections extends AccessSections {
    public ROAccessSections(AccessSections as) {
      super(as.configRevisionSet == null ? null :
          Collections.unmodifiableSet(as.configRevisionSet),
          Collections.unmodifiableSet(unmodifiableLists(as.accessSectionLines)));
    }

    private static Set<List<AccessSection>> unmodifiableLists(Set<List<AccessSection>> lists) {
      Set<List<AccessSection>> roLists = new HashSet<List<AccessSection>>();
      for (final List<AccessSection> l: lists) {
        roLists.add(Collections.unmodifiableList(l));
      }
      return roLists;
    }
  }

  /**
   * Use this class to walk the ancestor tree, it will abort loops safely.
   *
   * This class provides the ability to store data of type T which
   * can then be accessed with the get()/set() methods to pass
   * to/from the child and parents, or to gather data between each
   * parent invocation in the walkParent() method.
   *
   * This class is static to ensure that developers do not mistakingly
   * access instance methods and variable in walkParent() since these
   * would likely incorrectly always refer to the instance which
   * initiated the walk and not the child ProjectState.
   */
  public abstract static class Walker<T> {
    T data;
    Set<Project.NameKey> seen = new HashSet<Project.NameKey>();

    boolean abort = false;

    final Project.NameKey wildProject;
    final ProjectCache projectCache;

    public Walker(Project.NameKey wild, ProjectCache cache) {
      wildProject = wild;
      projectCache = cache;
    }

    public T get() {
      return data;
    }

    public void set(T d) {
      data = d;
    }

    /**
     * Used to indicate that the walk should no longer call any more
     * parents at any level in the walk.  Use to quickly interrupt a
     * walk.
     */
    public void abortWalk() {
      abort = true;
    }

    /** Stack like walker (preserve/restore data before/after call) */
    public T walkAncestors(ProjectState child, T d) {
      T prev = get();
      set(d);
      T rtn = walkAncestors(child);
      set(prev);
      return rtn;
    }

    /**
     * Call this method to walk each one of the parents and in turn
     * call walkParent() with them as the parent argument.  Skips
     * parents which have already been traversed by this walker
     * at a previous level (to prevent looping).
     */
    public T walkAncestors(ProjectState child) {
      Set<Project.NameKey> parents = child.getProject().getParents();
      if (parents.isEmpty() && ! child.isWildProject()) {
        parents = new HashSet();
        parents.add(wildProject);
      }

      for (final Project.NameKey parent: parents) {
        if (abort) {
          break;
        }
        Set<Project.NameKey> previous = seen;
        seen = new HashSet<Project.NameKey>();
        seen.addAll(previous);
        if (seen.add(parent)) {
          final ProjectState s = projectCache.get(parent);
          if (s != null) {
            walkParent(child, s);
          }
        }
        seen = previous;
      }
      return get();
    }

    /**
     * In order to be able to walk all the way up the ancestory tree,
     * this method must call a method in the parent ProjectState which
     * will eventually call walkAncestors(...) somewhere in its code path.
     */
    public abstract void walkParent(ProjectState child, ProjectState parent);
  }

  /**
   * Helper to setup the Walker with the required initiator's
   *  instance variables
   */
  public abstract class MyWalker<T> extends Walker<T> {
    public MyWalker() {
      super(ProjectState.this.wildProject, ProjectState.this.projectCache);
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
   * @param walker Omit. Only used internally when recursing
   * @return revision set of the metadata revisions of this project and
   *         all its ancestors.  A null indicates an undeterminable
   *         revision set and should be considered different from any
   *         other revision set, including other null revision sets.
   */
  public Set<ObjectId> getConfigRevisionSet(Walker<Set<ObjectId>>... walker) {
    if (walker.length == 0) {
      return getConfigRevisionSet(this.new MyWalker<Set<ObjectId>>() {
          {
            set(new HashSet<ObjectId>());
          }
          @Override
          public void walkParent(ProjectState child, ProjectState parent) {
            parent.getConfigRevisionSet(this);
          }
        });
    }

    ObjectId revision = config.getRevision();
    if (revision == null) {
      walker[0].set(null);
      walker[0].abortWalk();
      return null;
    }
    walker[0].get().add(revision);

    return walker[0].walkAncestors(this);
  }

  /** @param walker Omit. Only used internally when recursing */
  protected AccessSections getAccessSections(Walker<AccessSections>... walker) {
    Set<ObjectId> configRevisionSet = getConfigRevisionSet();
    AccessSections as = accessSections;
    if (as == null || configRevisionSet == null ||
        !configRevisionSet.equals(as.configRevisionSet)) {
      as = computeAccessSections(walker);
      accessSections = as;
    }
    return as;
  }

  /** @param walker Omit. Only used internally when recursing */
  private ROAccessSections computeAccessSections(Walker<AccessSections>... walker) {
    if (walker.length == 0) {
      return computeAccessSections(this.new MyWalker<AccessSections>() {
          @Override
          public void walkParent(ProjectState child, ProjectState parent) {
            AccessSections as = get();
            AccessSections pas = parent.getAccessSections(this);
            if (as.configRevisionSet != null && pas.configRevisionSet != null) {
              as.configRevisionSet.addAll(pas.configRevisionSet);
            }

            for (final List<AccessSection> l: pas.accessSectionLines) {
              List<AccessSection> subSections = new ArrayList<AccessSection>();
              subSections.addAll(child.getConfig().getAccessSections());
              subSections.addAll(l);
              as.accessSectionLines.add(subSections);
            }
          }
        });
    }

    Set<ObjectId> revisions = null;
    ObjectId revision = config.getRevision();
    if (revision != null) {
      revisions = new HashSet<ObjectId>();
      revisions.add(revision);
    }

    Set<List<AccessSection>> accessSectionLines = new HashSet<List<AccessSection>>();

    AccessSections as = new AccessSections(revisions, accessSectionLines);

    if (isWildProject()) {
      List<AccessSection> subSections = new ArrayList<AccessSection>();
      subSections.addAll(getConfig().getAccessSections());
      accessSectionLines.add(subSections);
    } else {
      walker[0].walkAncestors(this, as);
    }
    return new ROAccessSections(as);
  }


  /** Get both local and inherited access sections. */
  public Set<List<AccessSection>> getAccessSectionLines() {
    return getAccessSections().accessSectionLines;
  }

  /**
   * @param walker Omit. Only used internally when recursing
   * @return all {@link AccountGroup}'s to which the owner privilege for
   *         'refs/*' is assigned for this project (the local owners), if there
   *         are no local owners the local owners of the nearest parent projects
   *         that have local owners are returned
   */
  public Set<AccountGroup.UUID> getOwners(Walker<Set<AccountGroup.UUID>>... walker) {
    if (!localOwners.isEmpty() || isWildProject()) {
      return localOwners;
    }
    if (walker.length == 0) {
      return getOwners(this.new MyWalker<Set<AccountGroup.UUID>>() {
          {
            set(new HashSet<AccountGroup.UUID>());
          }

          @Override
          public void walkParent(ProjectState child, ProjectState parent) {
            get().addAll(parent.getOwners(this));
          }
        });
    }

    walker[0].walkAncestors(this);
    return walker[0].get();
  }

  /**
   * @param walker Omit. Only used internally when recursing
   * @return all {@link AccountGroup}'s that are allowed to administrate the
   *         complete project. This includes all groups to which the owner
   *         privilege for 'refs/*' is assigned for this project (the local
   *         owners) and all groups to which the owner privilege for 'refs/*' is
   *         assigned for one of the parent projects (the inherited owners).
   */
  public Set<AccountGroup.UUID> getAllOwners(Walker<Set<AccountGroup.UUID>>... walker) {
    if (walker.length == 0) {
      return getAllOwners(this.new MyWalker<Set<AccountGroup.UUID>>() {
          {
            set(new HashSet<AccountGroup.UUID>());
          }

          @Override
          public void walkParent(ProjectState child, ProjectState parent) {
            parent.getAllOwners(this);
          }
        });
    }

    walker[0].get().addAll(localOwners);
    return Collections.unmodifiableSet(walker[0].walkAncestors(this));
  }

  /**
   * @param walker Omit. Only used internally when recursing
   * @return all {@link Project.NameKey}'s that are ancestors of this project.
   */
  public Set<Project.NameKey> getAncestors(Walker<Set<Project.NameKey>>... walker) {
    if (walker.length == 0) {
      return getAncestors(this.new MyWalker<Set<Project.NameKey>>() {
          {
            set(new HashSet<Project.NameKey>());
          }

          @Override
          public void walkParent(ProjectState child, ProjectState parent) {
            parent.getAncestors(this);
          }
        });
    }

    walker[0].get().addAll(getProject().getParents());
    return walker[0].walkAncestors(this);
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
