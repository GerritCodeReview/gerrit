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

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;
import com.google.gerrit.extensions.api.projects.ThemeInfo;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityCollection;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.BranchOrderSection;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.rules.PrologEnvironment;
import com.google.gerrit.server.rules.RulesCache;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlecode.prolog_cafe.exceptions.CompileException;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulation of all types of information about a project, including inheritance-aware getters.
 *
 * <p>{@code ProjectState} instances are not constructed by callers, but rather returned from the
 * {@link ProjectCache}. The configuration data associated with a single project is read atomically
 * from the repo using a {@link ProjectConfig}, but there is no guarantee of atomicity when walking
 * parent projects; parent projects are simply resolved sequentially using the project cache.
 */
public class ProjectState {
  public static ProjectState createForTest(
      SitePaths sitePaths,
      ProjectCache projectCache,
      AllProjectsName allProjectsName,
      PrologEnvironment.Factory envFactory,
      GitRepositoryManager gitMgr,
      RulesCache rulesCache,
      List<CommentLinkInfo> commentLinks,
      ProjectCacheEntryFactory projectCacheEntryFactory,
      ProjectConfig projectConfig) {
    // This method is intentionally difficult to call, to avoid the need for a special factory that
    // non-tests might be tempted to use. Only a very limited number of tests need to call this.
    return new ProjectState(
        sitePaths,
        projectCache,
        allProjectsName,
        envFactory,
        gitMgr,
        rulesCache,
        commentLinks,
        projectCacheEntryFactory.create(projectConfig));
  }

  interface Factory {
    ProjectState create(ProjectCacheEntry cacheEntry);
  }

  private final SitePaths sitePaths;
  private final AllProjectsName allProjectsName;
  private final ProjectCache projectCache;
  private final PrologEnvironment.Factory envFactory;
  private final GitRepositoryManager gitMgr;
  private final RulesCache rulesCache;
  private final List<CommentLinkInfo> commentLinks;

  private final ProjectCacheEntry cacheEntry;

  @Inject
  ProjectState(
      SitePaths sitePaths,
      ProjectCache projectCache,
      AllProjectsName allProjectsName,
      PrologEnvironment.Factory envFactory,
      GitRepositoryManager gitMgr,
      RulesCache rulesCache,
      List<CommentLinkInfo> commentLinks,
      @Assisted ProjectCacheEntry cacheEntry) {
    this.sitePaths = sitePaths;
    this.projectCache = projectCache;
    this.allProjectsName = allProjectsName;
    this.envFactory = envFactory;
    this.gitMgr = gitMgr;
    this.rulesCache = rulesCache;
    this.commentLinks = commentLinks;
    this.cacheEntry = cacheEntry;
  }

  /**
   * @return cached computation of all global capabilities. This should only be invoked on the state
   *     from {@link ProjectCache#getAllProjects()}. Null on any other project.
   */
  public CapabilityCollection getCapabilityCollection() {
    return cacheEntry.getCapabilityCollection();
  }

  /** @return Construct a new PrologEnvironment for the calling thread. */
  public PrologEnvironment newPrologEnvironment() throws CompileException {
    return cacheEntry.newPrologEnvironment(rulesCache, envFactory);
  }

  /**
   * Like {@link #newPrologEnvironment()} but instead of reading the rules.pl read the provided
   * input stream.
   *
   * @param name a name of the input stream. Could be any name.
   * @param in stream to read prolog rules from
   * @throws CompileException
   */
  public PrologEnvironment newPrologEnvironment(String name, Reader in) throws CompileException {
    PrologMachineCopy pmc = rulesCache.loadMachine(name, in);
    return envFactory.create(pmc);
  }

  public Project getProject() {
    return getConfig().getProject();
  }

  public Project.NameKey getNameKey() {
    return getProject().getNameKey();
  }

  public String getName() {
    return getNameKey().get();
  }

  public ProjectConfig getConfig() {
    return cacheEntry.getConfig();
  }

  public ProjectLevelConfig getConfig(String fileName) {
    return cacheEntry.getConfig(gitMgr, fileName);
  }

  public long getMaxObjectSizeLimit() {
    return cacheEntry.getConfig().getMaxObjectSizeLimit();
  }

  public boolean statePermitsRead() {
    return getProject().getState().permitsRead();
  }

  public void checkStatePermitsRead() throws ResourceConflictException {
    if (!statePermitsRead()) {
      throw new ResourceConflictException(
          "project state " + getProject().getState().name() + " does not permit read");
    }
  }

  public boolean statePermitsWrite() {
    return getProject().getState().permitsWrite();
  }

  public void checkStatePermitsWrite() throws ResourceConflictException {
    if (!statePermitsWrite()) {
      throw new ResourceConflictException(
          "project state " + getProject().getState().name() + " does not permit write");
    }
  }

  /** Get the sections that pertain only to this project. */
  List<SectionMatcher> getLocalAccessSections() {
    return cacheEntry.getLocalAccessSections();
  }

  /**
   * Obtain all local and inherited sections. This collection is looked up dynamically and is not
   * cached. Callers should try to cache this result per-request as much as possible.
   */
  public List<SectionMatcher> getAllSections() {
    if (cacheEntry.isAllProjects()) {
      return getLocalAccessSections();
    }

    List<SectionMatcher> all = new ArrayList<>();
    for (ProjectState s : tree()) {
      all.addAll(s.getLocalAccessSections());
    }
    return all;
  }

  /**
   * @return all {@link AccountGroup}'s to which the owner privilege for 'refs/*' is assigned for
   *     this project (the local owners), if there are no local owners the local owners of the
   *     nearest parent project that has local owners are returned
   */
  public Set<AccountGroup.UUID> getOwners() {
    for (ProjectState p : tree()) {
      Set<AccountGroup.UUID> localOwners = p.cacheEntry.getLocalOwners();
      if (!localOwners.isEmpty()) {
        return localOwners;
      }
    }
    return Collections.emptySet();
  }

  /**
   * @return all {@link AccountGroup}'s that are allowed to administrate the complete project. This
   *     includes all groups to which the owner privilege for 'refs/*' is assigned for this project
   *     (the local owners) and all groups to which the owner privilege for 'refs/*' is assigned for
   *     one of the parent projects (the inherited owners).
   */
  public Set<AccountGroup.UUID> getAllOwners() {
    Set<AccountGroup.UUID> result = new HashSet<>();

    for (ProjectState p : tree()) {
      result.addAll(p.cacheEntry.getLocalOwners());
    }

    return result;
  }

  /**
   * @return an iterable that walks through this project and then the parents of this project.
   *     Starts from this project and progresses up the hierarchy to All-Projects.
   */
  public Iterable<ProjectState> tree() {
    return new Iterable<ProjectState>() {
      @Override
      public Iterator<ProjectState> iterator() {
        return new ProjectHierarchyIterator(projectCache, allProjectsName, ProjectState.this);
      }
    };
  }

  /**
   * @return an iterable that walks in-order from All-Projects through the project hierarchy to this
   *     project.
   */
  public Iterable<ProjectState> treeInOrder() {
    List<ProjectState> projects = Lists.newArrayList(tree());
    Collections.reverse(projects);
    return projects;
  }

  /**
   * @return an iterable that walks through the parents of this project. Starts from the immediate
   *     parent of this project and progresses up the hierarchy to All-Projects.
   */
  public FluentIterable<ProjectState> parents() {
    return FluentIterable.from(tree()).skip(1);
  }

  public boolean isAllProjects() {
    return cacheEntry.isAllProjects();
  }

  public boolean isAllUsers() {
    return cacheEntry.isAllUsers();
  }

  public boolean is(BooleanProjectConfig config) {
    for (ProjectState s : tree()) {
      switch (s.getProject().getBooleanConfig(config)) {
        case TRUE:
          return true;
        case FALSE:
          return false;
        case INHERIT:
        default:
          continue;
      }
    }
    return false;
  }

  /** All available label types. */
  public LabelTypes getLabelTypes() {
    return cacheEntry.getLabelTypes(this);
  }

  /** All available label types for this change and user. */
  public LabelTypes getLabelTypes(ChangeNotes notes, CurrentUser user) {
    return getLabelTypes(notes.getChange().getDest(), user);
  }

  /** All available label types for this branch and user. */
  public LabelTypes getLabelTypes(Branch.NameKey destination, CurrentUser user) {
    List<LabelType> all = getLabelTypes().getLabelTypes();

    List<LabelType> r = Lists.newArrayListWithCapacity(all.size());
    for (LabelType l : all) {
      List<String> refs = l.getRefPatterns();
      if (refs == null) {
        r.add(l);
      } else {
        for (String refPattern : refs) {
          if (RefConfigSection.isValid(refPattern) && match(destination, refPattern, user)) {
            r.add(l);
            break;
          }
        }
      }
    }

    return new LabelTypes(r);
  }

  public List<CommentLinkInfo> getCommentLinks() {
    Map<String, CommentLinkInfo> cls = new LinkedHashMap<>();
    for (CommentLinkInfo cl : commentLinks) {
      cls.put(cl.name.toLowerCase(), cl);
    }
    for (ProjectState s : treeInOrder()) {
      for (CommentLinkInfoImpl cl : s.getConfig().getCommentLinkSections()) {
        String name = cl.name.toLowerCase();
        if (cl.isOverrideOnly()) {
          CommentLinkInfo parent = cls.get(name);
          if (parent == null) {
            continue; // Ignore invalid overrides.
          }
          cls.put(name, cl.inherit(parent));
        } else {
          cls.put(name, cl);
        }
      }
    }
    return ImmutableList.copyOf(cls.values());
  }

  public BranchOrderSection getBranchOrderSection() {
    for (ProjectState s : tree()) {
      BranchOrderSection section = s.getConfig().getBranchOrderSection();
      if (section != null) {
        return section;
      }
    }
    return null;
  }

  public Collection<SubscribeSection> getSubscribeSections(Branch.NameKey branch) {
    Collection<SubscribeSection> ret = new ArrayList<>();
    for (ProjectState s : tree()) {
      ret.addAll(s.getConfig().getSubscribeSections(branch));
    }
    return ret;
  }

  public ThemeInfo getTheme() {
    return cacheEntry.getTheme(this);
  }

  SitePaths getSitePaths() {
    return sitePaths;
  }

  public Set<GroupReference> getAllGroups() {
    return getGroups(getAllSections());
  }

  public Set<GroupReference> getLocalGroups() {
    return getGroups(getLocalAccessSections());
  }

  public SubmitType getSubmitType() {
    for (ProjectState s : tree()) {
      SubmitType t = s.getProject().getConfiguredSubmitType();
      if (t != SubmitType.INHERIT) {
        return t;
      }
    }
    return Project.DEFAULT_ALL_PROJECTS_SUBMIT_TYPE;
  }

  private static Set<GroupReference> getGroups(List<SectionMatcher> sectionMatcherList) {
    final Set<GroupReference> all = new HashSet<>();
    for (SectionMatcher matcher : sectionMatcherList) {
      final AccessSection section = matcher.getSection();
      for (Permission permission : section.getPermissions()) {
        for (PermissionRule rule : permission.getRules()) {
          all.add(rule.getGroup());
        }
      }
    }
    return all;
  }

  public ProjectData toProjectData() {
    return new ProjectData(getProject(), parents().transform(s -> s.getProject().getNameKey()));
  }

  private boolean match(Branch.NameKey destination, String refPattern, CurrentUser user) {
    return RefPatternMatcher.getMatcher(refPattern).match(destination.get(), user);
  }
}
