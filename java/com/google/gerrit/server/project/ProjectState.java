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

import static com.google.gerrit.common.data.PermissionRule.Action.ALLOW;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;
import com.google.gerrit.extensions.api.projects.ThemeInfo;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityCollection;
import com.google.gerrit.server.git.BranchOrderSection;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.rules.PrologEnvironment;
import com.google.gerrit.server.rules.RulesCache;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlecode.prolog_cafe.exceptions.CompileException;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cached information on a project. */
public class ProjectState {
  private static final Logger log = LoggerFactory.getLogger(ProjectState.class);

  public interface Factory {
    ProjectState create(ProjectConfig config);
  }

  private final boolean isAllProjects;
  private final boolean isAllUsers;
  private final SitePaths sitePaths;
  private final AllProjectsName allProjectsName;
  private final ProjectCache projectCache;
  private final PrologEnvironment.Factory envFactory;
  private final GitRepositoryManager gitMgr;
  private final RulesCache rulesCache;
  private final List<CommentLinkInfo> commentLinks;

  private final ProjectConfig config;
  private final Map<String, ProjectLevelConfig> configs;
  private final Set<AccountGroup.UUID> localOwners;

  /** Prolog rule state. */
  private volatile PrologMachineCopy rulesMachine;

  /** Last system time the configuration's revision was examined. */
  private volatile long lastCheckGeneration;

  /** Local access sections, wrapped in SectionMatchers for faster evaluation. */
  private volatile List<SectionMatcher> localAccessSections;

  /** Theme information loaded from site_path/themes. */
  private volatile ThemeInfo theme;

  /** If this is all projects, the capabilities used by the server. */
  private final CapabilityCollection capabilities;

  /** All label types applicable to changes in this project. */
  private LabelTypes labelTypes;

  @Inject
  public ProjectState(
      final SitePaths sitePaths,
      final ProjectCache projectCache,
      final AllProjectsName allProjectsName,
      final AllUsersName allUsersName,
      final PrologEnvironment.Factory envFactory,
      final GitRepositoryManager gitMgr,
      final RulesCache rulesCache,
      final List<CommentLinkInfo> commentLinks,
      final CapabilityCollection.Factory limitsFactory,
      @Assisted final ProjectConfig config) {
    this.sitePaths = sitePaths;
    this.projectCache = projectCache;
    this.isAllProjects = config.getProject().getNameKey().equals(allProjectsName);
    this.isAllUsers = config.getProject().getNameKey().equals(allUsersName);
    this.allProjectsName = allProjectsName;
    this.envFactory = envFactory;
    this.gitMgr = gitMgr;
    this.rulesCache = rulesCache;
    this.commentLinks = commentLinks;
    this.config = config;
    this.configs = new HashMap<>();
    this.capabilities =
        isAllProjects
            ? limitsFactory.create(config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES))
            : null;

    if (isAllProjects && !Permission.canBeOnAllProjects(AccessSection.ALL, Permission.OWNER)) {
      localOwners = Collections.emptySet();
    } else {
      HashSet<AccountGroup.UUID> groups = new HashSet<>();
      AccessSection all = config.getAccessSection(AccessSection.ALL);
      if (all != null) {
        Permission owner = all.getPermission(Permission.OWNER);
        if (owner != null) {
          for (PermissionRule rule : owner.getRules()) {
            GroupReference ref = rule.getGroup();
            if (rule.getAction() == ALLOW && ref.getUUID() != null) {
              groups.add(ref.getUUID());
            }
          }
        }
      }
      localOwners = Collections.unmodifiableSet(groups);
    }
  }

  void initLastCheck(long generation) {
    lastCheckGeneration = generation;
  }

  boolean needsRefresh(long generation) {
    if (generation <= 0) {
      return isRevisionOutOfDate();
    }
    if (lastCheckGeneration != generation) {
      lastCheckGeneration = generation;
      return isRevisionOutOfDate();
    }
    return false;
  }

  private boolean isRevisionOutOfDate() {
    try (Repository git = gitMgr.openRepository(getNameKey())) {
      Ref ref = git.getRefDatabase().exactRef(RefNames.REFS_CONFIG);
      if (ref == null || ref.getObjectId() == null) {
        return true;
      }
      return !ref.getObjectId().equals(config.getRevision());
    } catch (IOException gone) {
      return true;
    }
  }

  /**
   * @return cached computation of all global capabilities. This should only be invoked on the state
   *     from {@link ProjectCache#getAllProjects()}. Null on any other project.
   */
  public CapabilityCollection getCapabilityCollection() {
    return capabilities;
  }

  /**
   * Returns true if the Prolog engine is expected to run for this project, that is if this project
   * or a parent possesses a rules.pl file.
   */
  public boolean hasPrologRules() {
    // We check if this project has a rules.pl file
    if (getConfig().getRulesId() != null) {
      return true;
    }

    // If not, we check the parents.
    return parents()
        .stream()
        .map(ProjectState::getConfig)
        .map(ProjectConfig::getRulesId)
        .anyMatch(Objects::nonNull);
  }

  /** @return Construct a new PrologEnvironment for the calling thread. */
  public PrologEnvironment newPrologEnvironment() throws CompileException {
    PrologMachineCopy pmc = rulesMachine;
    if (pmc == null) {
      pmc = rulesCache.loadMachine(getNameKey(), config.getRulesId());
      rulesMachine = pmc;
    }
    return envFactory.create(pmc);
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
    return config.getProject();
  }

  public Project.NameKey getNameKey() {
    return getProject().getNameKey();
  }

  public String getName() {
    return getNameKey().get();
  }

  public ProjectConfig getConfig() {
    return config;
  }

  public ProjectLevelConfig getConfig(String fileName) {
    if (configs.containsKey(fileName)) {
      return configs.get(fileName);
    }

    ProjectLevelConfig cfg = new ProjectLevelConfig(fileName, this);
    try (Repository git = gitMgr.openRepository(getNameKey())) {
      cfg.load(git);
    } catch (IOException | ConfigInvalidException e) {
      log.warn("Failed to load " + fileName + " for " + getName(), e);
    }

    configs.put(fileName, cfg);
    return cfg;
  }

  public long getMaxObjectSizeLimit() {
    return config.getMaxObjectSizeLimit();
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
    List<SectionMatcher> sm = localAccessSections;
    if (sm == null) {
      Collection<AccessSection> fromConfig = config.getAccessSections();
      sm = new ArrayList<>(fromConfig.size());
      for (AccessSection section : fromConfig) {
        if (isAllProjects) {
          List<Permission> copy = Lists.newArrayListWithCapacity(section.getPermissions().size());
          for (Permission p : section.getPermissions()) {
            if (Permission.canBeOnAllProjects(section.getName(), p.getName())) {
              copy.add(p);
            }
          }
          section = new AccessSection(section.getName());
          section.setPermissions(copy);
        }

        SectionMatcher matcher = SectionMatcher.wrap(getNameKey(), section);
        if (matcher != null) {
          sm.add(matcher);
        }
      }
      localAccessSections = sm;
    }
    return sm;
  }

  /**
   * Obtain all local and inherited sections. This collection is looked up dynamically and is not
   * cached. Callers should try to cache this result per-request as much as possible.
   */
  public List<SectionMatcher> getAllSections() {
    if (isAllProjects) {
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
      if (!p.localOwners.isEmpty()) {
        return p.localOwners;
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
      result.addAll(p.localOwners);
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
    return isAllProjects;
  }

  public boolean isAllUsers() {
    return isAllUsers;
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
    if (labelTypes == null) {
      labelTypes = loadLabelTypes();
    }
    return labelTypes;
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
    ThemeInfo theme = this.theme;
    if (theme == null) {
      synchronized (this) {
        theme = this.theme;
        if (theme == null) {
          theme = loadTheme();
          this.theme = theme;
        }
      }
    }
    if (theme == ThemeInfo.INHERIT) {
      ProjectState parent = Iterables.getFirst(parents(), null);
      return parent != null ? parent.getTheme() : null;
    }
    return theme;
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

  private ThemeInfo loadTheme() {
    String name = getConfig().getProject().getName();
    Path dir = sitePaths.themes_dir.resolve(name);
    if (!Files.exists(dir)) {
      return ThemeInfo.INHERIT;
    } else if (!Files.isDirectory(dir)) {
      log.warn("Bad theme for {}: not a directory", name);
      return ThemeInfo.INHERIT;
    }
    try {
      return new ThemeInfo(
          readFile(dir.resolve(SitePaths.CSS_FILENAME)),
          readFile(dir.resolve(SitePaths.HEADER_FILENAME)),
          readFile(dir.resolve(SitePaths.FOOTER_FILENAME)));
    } catch (IOException e) {
      log.error("Error reading theme for " + name, e);
      return ThemeInfo.INHERIT;
    }
  }

  public ProjectData toProjectData() {
    return new ProjectData(getProject(), parents().transform(s -> s.getProject().getNameKey()));
  }

  private String readFile(Path p) throws IOException {
    return Files.exists(p) ? new String(Files.readAllBytes(p), UTF_8) : null;
  }

  private LabelTypes loadLabelTypes() {
    Map<String, LabelType> types = new LinkedHashMap<>();
    for (ProjectState s : treeInOrder()) {
      for (LabelType type : s.getConfig().getLabelSections().values()) {
        String lower = type.getName().toLowerCase();
        LabelType old = types.get(lower);
        if (old == null || old.canOverride()) {
          types.put(lower, type);
        }
      }
    }
    List<LabelType> all = Lists.newArrayListWithCapacity(types.size());
    for (LabelType type : types.values()) {
      if (!type.getValues().isEmpty()) {
        all.add(type);
      }
    }
    return new LabelTypes(Collections.unmodifiableList(all));
  }

  private boolean match(Branch.NameKey destination, String refPattern, CurrentUser user) {
    return RefPatternMatcher.getMatcher(refPattern).match(destination.get(), user);
  }
}
