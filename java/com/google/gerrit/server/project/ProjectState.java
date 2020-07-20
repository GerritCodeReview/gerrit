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

import static com.google.gerrit.entities.PermissionRule.Action.ALLOW;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.BranchOrderSection;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.StoredCommentLinkInfo;
import com.google.gerrit.entities.SubscribeSection;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.server.account.CapabilityCollection;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * Cached information on a project. Must not contain any data derived from parents other than it's
 * immediate parent's {@link com.google.gerrit.entities.Project.NameKey}.
 */
public class ProjectState {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    ProjectState create(CachedProjectConfig config);
  }

  private final boolean isAllProjects;
  private final boolean isAllUsers;
  private final AllProjectsName allProjectsName;
  private final ProjectCache projectCache;
  private final GitRepositoryManager gitMgr;
  private final List<CommentLinkInfo> commentLinks;

  private final CachedProjectConfig cachedConfig;
  private final Set<AccountGroup.UUID> localOwners;
  private final long globalMaxObjectSizeLimit;
  private final boolean inheritProjectMaxObjectSizeLimit;

  /** Local access sections, wrapped in SectionMatchers for faster evaluation. */
  private volatile List<SectionMatcher> localAccessSections;

  /** If this is all projects, the capabilities used by the server. */
  private final CapabilityCollection capabilities;

  @Inject
  public ProjectState(
      ProjectCache projectCache,
      AllProjectsName allProjectsName,
      AllUsersName allUsersName,
      GitRepositoryManager gitMgr,
      List<CommentLinkInfo> commentLinks,
      CapabilityCollection.Factory limitsFactory,
      TransferConfig transferConfig,
      @Assisted CachedProjectConfig cachedProjectConfig) {
    this.projectCache = projectCache;
    this.isAllProjects = cachedProjectConfig.getProject().getNameKey().equals(allProjectsName);
    this.isAllUsers = cachedProjectConfig.getProject().getNameKey().equals(allUsersName);
    this.allProjectsName = allProjectsName;
    this.gitMgr = gitMgr;
    this.commentLinks = commentLinks;
    this.cachedConfig = cachedProjectConfig;
    this.capabilities =
        isAllProjects
            ? limitsFactory.create(
                cachedProjectConfig
                    .getAccessSection(AccessSection.GLOBAL_CAPABILITIES)
                    .orElse(null))
            : null;
    this.globalMaxObjectSizeLimit = transferConfig.getMaxObjectSizeLimit();
    this.inheritProjectMaxObjectSizeLimit = transferConfig.inheritProjectMaxObjectSizeLimit();

    if (isAllProjects && !Permission.canBeOnAllProjects(AccessSection.ALL, Permission.OWNER)) {
      localOwners = Collections.emptySet();
    } else {
      HashSet<AccountGroup.UUID> groups = new HashSet<>();
      Optional<AccessSection> all = cachedProjectConfig.getAccessSection(AccessSection.ALL);
      if (all.isPresent()) {
        Permission owner = all.get().getPermission(Permission.OWNER);
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
    if (getConfig().getRulesId().isPresent()) {
      return true;
    }

    // If not, we check the parents.
    return parents().stream()
        .map(ProjectState::getConfig)
        .map(CachedProjectConfig::getRulesId)
        .anyMatch(Optional::isPresent);
  }

  public Project getProject() {
    return cachedConfig.getProject();
  }

  public Project.NameKey getNameKey() {
    return getProject().getNameKey();
  }

  public String getName() {
    return getNameKey().get();
  }

  public CachedProjectConfig getConfig() {
    return cachedConfig;
  }

  public ProjectLevelConfig getConfig(String fileName) {
    Optional<Config> rawConfig = cachedConfig.getProjectLevelConfig(fileName);
    return new ProjectLevelConfig(fileName, this, rawConfig.orElse(new Config()));
  }

  public long getMaxObjectSizeLimit() {
    return cachedConfig.getMaxObjectSizeLimit();
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

  public static class EffectiveMaxObjectSizeLimit {
    public long value;
    public String summary;
  }

  private static final String MAY_NOT_SET = "This project may not set a higher limit.";

  @VisibleForTesting
  public static final String INHERITED_FROM_PARENT = "Inherited from parent project '%s'.";

  @VisibleForTesting
  public static final String OVERRIDDEN_BY_PARENT =
      "Overridden by parent project '%s'. " + MAY_NOT_SET;

  @VisibleForTesting
  public static final String INHERITED_FROM_GLOBAL = "Inherited from the global config.";

  @VisibleForTesting
  public static final String OVERRIDDEN_BY_GLOBAL =
      "Overridden by the global config. " + MAY_NOT_SET;

  public EffectiveMaxObjectSizeLimit getEffectiveMaxObjectSizeLimit() {
    EffectiveMaxObjectSizeLimit result = new EffectiveMaxObjectSizeLimit();

    result.value = cachedConfig.getMaxObjectSizeLimit();

    if (inheritProjectMaxObjectSizeLimit) {
      for (ProjectState parent : parents()) {
        long parentValue = parent.cachedConfig.getMaxObjectSizeLimit();
        if (parentValue > 0 && result.value > 0) {
          if (parentValue < result.value) {
            result.value = parentValue;
            result.summary =
                String.format(OVERRIDDEN_BY_PARENT, parent.cachedConfig.getProject().getNameKey());
          }
        } else if (parentValue > 0) {
          result.value = parentValue;
          result.summary =
              String.format(INHERITED_FROM_PARENT, parent.cachedConfig.getProject().getNameKey());
        }
      }
    }

    if (globalMaxObjectSizeLimit > 0 && result.value > 0) {
      if (globalMaxObjectSizeLimit < result.value) {
        result.value = globalMaxObjectSizeLimit;
        result.summary = OVERRIDDEN_BY_GLOBAL;
      }
    } else if (globalMaxObjectSizeLimit > result.value) {
      // zero means "no limit", in this case the max is more limiting
      result.value = globalMaxObjectSizeLimit;
      result.summary = INHERITED_FROM_GLOBAL;
    }
    return result;
  }

  /** Get the sections that pertain only to this project. */
  List<SectionMatcher> getLocalAccessSections() {
    List<SectionMatcher> sm = localAccessSections;
    if (sm == null) {
      Collection<AccessSection> fromConfig = cachedConfig.getAccessSections().values();
      sm = new ArrayList<>(fromConfig.size());
      for (AccessSection section : fromConfig) {
        if (isAllProjects) {
          List<Permission.Builder> copy = new ArrayList<>();
          for (Permission p : section.getPermissions()) {
            if (Permission.canBeOnAllProjects(section.getName(), p.getName())) {
              copy.add(p.toBuilder());
            }
          }
          section =
              AccessSection.builder(section.getName())
                  .modifyPermissions(permissions -> permissions.addAll(copy))
                  .build();
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
    return () -> new ProjectHierarchyIterator(projectCache, allProjectsName, ProjectState.this);
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
    Map<String, LabelType> types = new LinkedHashMap<>();
    for (ProjectState s : treeInOrder()) {
      for (LabelType type : s.getConfig().getLabelSections().values()) {
        String lower = type.getName().toLowerCase();
        LabelType old = types.get(lower);
        if (old == null || old.isCanOverride()) {
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

  /** All available label types for this change. */
  public LabelTypes getLabelTypes(ChangeNotes notes) {
    return getLabelTypes(notes.getChange().getDest());
  }

  /** All available label types for this branch. */
  public LabelTypes getLabelTypes(BranchNameKey destination) {
    List<LabelType> all = getLabelTypes().getLabelTypes();

    List<LabelType> r = Lists.newArrayListWithCapacity(all.size());
    for (LabelType l : all) {
      List<String> refs = l.getRefPatterns();
      if (refs == null) {
        r.add(l);
      } else {
        for (String refPattern : refs) {
          if (refPattern.contains("${")) {
            logger.atWarning().log(
                "Ref pattern for label %s in project %s contains illegal expanded parameters: %s."
                    + " Ref pattern will be ignored.",
                l, getName(), refPattern);
            continue;
          }

          if (AccessSection.isValidRefSectionName(refPattern) && match(destination, refPattern)) {
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
      for (StoredCommentLinkInfo cl : s.getConfig().getCommentLinkSections().values()) {
        String name = cl.getName().toLowerCase();
        if (cl.getOverrideOnly()) {
          CommentLinkInfo parent = cls.get(name);
          if (parent == null) {
            continue; // Ignore invalid overrides.
          }
          cls.put(name, StoredCommentLinkInfo.fromInfo(parent, cl.getEnabled()).toInfo());
        } else {
          cls.put(name, cl.toInfo());
        }
      }
    }
    return ImmutableList.copyOf(cls.values());
  }

  /**
   * Returns the {@link PluginConfig} that got parsed from the {@code plugins} section of {@code
   * project.config}. The returned instance is a defensive copy of the cached value. Returns an
   * empty config in case we find no config for the given plugin name. This is useful when calling
   * {@code PluginConfig#withInheritance(ProjectState.Factory)}
   */
  public PluginConfig getPluginConfig(String pluginName) {
    if (getConfig().getPluginConfigs().containsKey(pluginName)) {
      Config config = new Config();
      try {
        config.fromText(getConfig().getPluginConfigs().get(pluginName));
      } catch (ConfigInvalidException e) {
        // This is OK to propagate as IllegalStateException because it's a programmer error.
        // The config was converted to a String using Config#toText. So #fromText must not
        // throw a ConfigInvalidException
        throw new IllegalStateException("invalid plugin config for " + pluginName, e);
      }
      return PluginConfig.create(pluginName, config, getConfig());
    }
    return PluginConfig.create(pluginName, new Config(), getConfig());
  }

  public Optional<BranchOrderSection> getBranchOrderSection() {
    for (ProjectState s : tree()) {
      Optional<BranchOrderSection> section = s.getConfig().getBranchOrderSection();
      if (section.isPresent()) {
        return section;
      }
    }
    return Optional.empty();
  }

  public Collection<SubscribeSection> getSubscribeSections(BranchNameKey branch) {
    Collection<SubscribeSection> ret = new ArrayList<>();
    for (ProjectState s : tree()) {
      ret.addAll(s.getConfig().getSubscribeSections(branch));
    }
    return ret;
  }

  public Set<GroupReference> getAllGroups() {
    return getGroups(getAllSections());
  }

  public Set<GroupReference> getLocalGroups() {
    return getGroups(getLocalAccessSections());
  }

  public SubmitType getSubmitType() {
    for (ProjectState s : tree()) {
      SubmitType t = s.getProject().getSubmitType();
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
    ProjectData project = null;
    for (ProjectState state : treeInOrder()) {
      project = new ProjectData(state.getProject(), Optional.ofNullable(project));
    }
    return project;
  }

  private boolean match(BranchNameKey destination, String refPattern) {
    return RefPatternMatcher.getMatcher(refPattern).match(destination.branch(), null);
  }
}
