// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.common.data.PermissionRule.Action.ALLOW;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.api.projects.ThemeInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.CapabilityCollection;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.rules.PrologEnvironment;
import com.google.gerrit.server.rules.RulesCache;
import com.googlecode.prolog_cafe.exceptions.CompileException;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cached data stored in the {@link ProjectCacheImpl}.
 *
 * <p>Since this object is stored in a persistent cache, it must only transitively contain POJOs and
 * collection-type data. References to thick objects, such as those constructed with Guice, are not
 * appropriate to store in this class.
 *
 * <p>This class appears immutable from the outside, but as an implementation detail it may lazily
 * load data.
 *
 * <p>This class is threadsafe.
 */
class ProjectCacheEntry {
  private static final Logger log = LoggerFactory.getLogger(ProjectCacheEntry.class);

  private final boolean isAllProjects;
  private final boolean isAllUsers;
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

  // For maximum safety, all arguments to this constructor must be safe to retain references via
  // instance fields. Guice-created or otherwise thick objects belong in ProjectCacheEntryFactory,
  // and should be converted in the body ProjectCacheEntryFactory#create.
  ProjectCacheEntry(
      ProjectConfig config,
      boolean isAllProjects,
      boolean isAllUsers,
      @Nullable CapabilityCollection capabilities) {
    if (isAllProjects) {
      checkArgument(capabilities != null, "CapabilityCollection is required for All-Projects");
    } else {
      checkArgument(
          capabilities == null, "CapabilityCollection may not be passed except for All-Projects");
    }

    this.isAllProjects = isAllProjects;
    this.isAllUsers = isAllUsers;
    this.config = config;
    this.configs = new HashMap<>();
    this.capabilities = capabilities;
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

  ProjectConfig getConfig() {
    return config;
  }

  Project.NameKey getNameKey() {
    return config.getProject().getNameKey();
  }

  CapabilityCollection getCapabilityCollection() {
    return capabilities;
  }

  boolean isAllProjects() {
    return isAllProjects;
  }

  boolean isAllUsers() {
    return isAllUsers;
  }

  Set<AccountGroup.UUID> getLocalOwners() {
    return localOwners;
  }

  PrologEnvironment newPrologEnvironment(
      RulesCache rulesCache, PrologEnvironment.Factory envFactory) throws CompileException {
    PrologMachineCopy pmc = rulesMachine;
    if (pmc == null) {
      pmc = rulesCache.loadMachine(getNameKey(), config.getRulesId());
      rulesMachine = pmc;
    }
    return envFactory.create(pmc);
  }

  LabelTypes getLabelTypes(ProjectState projectState) {
    if (labelTypes == null) {
      labelTypes = loadLabelTypes(projectState);
    }
    return labelTypes;
  }

  private LabelTypes loadLabelTypes(ProjectState projectState) {
    Map<String, LabelType> types = new LinkedHashMap<>();
    for (ProjectState s : projectState.treeInOrder()) {
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

  ProjectLevelConfig getConfig(GitRepositoryManager gitMgr, String fileName) {
    if (configs.containsKey(fileName)) {
      return configs.get(fileName);
    }

    ProjectLevelConfig cfg = new ProjectLevelConfig(fileName, getNameKey());
    try (Repository git = gitMgr.openRepository(getNameKey())) {
      cfg.load(git);
    } catch (IOException | ConfigInvalidException e) {
      log.warn("Failed to load " + fileName + " for " + getNameKey(), e);
    }

    configs.put(fileName, cfg);
    return cfg;
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

  ThemeInfo getTheme(ProjectState projectState) {
    ThemeInfo theme = this.theme;
    if (theme == null) {
      synchronized (this) {
        theme = this.theme;
        if (theme == null) {
          theme = loadTheme(projectState.getSitePaths());
          this.theme = theme;
        }
      }
    }
    if (theme == ThemeInfo.INHERIT) {
      ProjectState parent = Iterables.getFirst(projectState.parents(), null);
      return parent != null ? parent.getTheme() : null;
    }
    return theme;
  }

  private ThemeInfo loadTheme(SitePaths sitePaths) {
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

  private static String readFile(Path p) throws IOException {
    return Files.exists(p) ? new String(Files.readAllBytes(p), UTF_8) : null;
  }

  void initLastCheck(long generation) {
    lastCheckGeneration = generation;
  }

  boolean needsRefresh(long generation, GitRepositoryManager gitMgr) {
    if (generation <= 0) {
      return isRevisionOutOfDate(gitMgr);
    }
    if (lastCheckGeneration != generation) {
      lastCheckGeneration = generation;
      return isRevisionOutOfDate(gitMgr);
    }
    return false;
  }

  private boolean isRevisionOutOfDate(GitRepositoryManager gitMgr) {
    try (Repository git = gitMgr.openRepository(config.getProject().getNameKey())) {
      Ref ref = git.getRefDatabase().exactRef(RefNames.REFS_CONFIG);
      if (ref == null || ref.getObjectId() == null) {
        return true;
      }
      return !ref.getObjectId().equals(config.getRevision());
    } catch (IOException gone) {
      return true;
    }
  }
}
