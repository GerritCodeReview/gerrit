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

package com.google.gerrit.acceptance.testsuite.project;

import static com.google.gerrit.entities.RefNames.REFS_CONFIG;
import static com.google.gerrit.server.project.ProjectConfig.PROJECT_CONFIG;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.testsuite.project.TestProjectCreation.Builder;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.TestCapability;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.TestLabelPermission;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.TestPermission;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectCreator;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

public class ProjectOperationsImpl implements ProjectOperations {
  private final AllProjectsName allProjectsName;
  private final GitRepositoryManager repoManager;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectCache projectCache;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCreator projectCreator;

  @Inject
  ProjectOperationsImpl(
      AllProjectsName allProjectsName,
      GitRepositoryManager repoManager,
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectCache projectCache,
      ProjectConfig.Factory projectConfigFactory,
      ProjectCreator projectCreator) {
    this.allProjectsName = allProjectsName;
    this.repoManager = repoManager;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectCache = projectCache;
    this.projectConfigFactory = projectConfigFactory;
    this.projectCreator = projectCreator;
  }

  @Override
  public Builder newProject() {
    return TestProjectCreation.builder(this::createNewProject);
  }

  private Project.NameKey createNewProject(TestProjectCreation projectCreation) throws Exception {
    String name = projectCreation.name().orElse(RandomStringUtils.randomAlphabetic(8));

    CreateProjectArgs args = new CreateProjectArgs();
    args.setProjectName(name);
    args.branch = Collections.singletonList(Constants.R_HEADS + Constants.MASTER);
    args.createEmptyCommit = projectCreation.createEmptyCommit().orElse(true);
    projectCreation.parent().ifPresent(p -> args.newParent = p);
    // ProjectCreator wants non-null owner IDs.
    args.ownerIds = new ArrayList<>();
    projectCreation.submitType().ifPresent(st -> args.submitType = st);
    projectCreator.createProject(args);
    return Project.nameKey(name);
  }

  @Override
  public ProjectOperations.PerProjectOperations project(Project.NameKey key) {
    return new PerProjectOperations(key);
  }

  @Override
  public TestProjectUpdate.Builder allProjectsForUpdate() {
    return project(allProjectsName).forUpdate();
  }

  private class PerProjectOperations implements ProjectOperations.PerProjectOperations {
    Project.NameKey nameKey;

    PerProjectOperations(Project.NameKey nameKey) {
      this.nameKey = nameKey;
    }

    @Override
    public RevCommit getHead(String branch) {
      return requireNonNull(headOrNull(branch));
    }

    @Override
    public boolean hasHead(String branch) {
      return headOrNull(branch) != null;
    }

    @Override
    public TestProjectUpdate.Builder forUpdate() {
      return TestProjectUpdate.builder(nameKey, allProjectsName, this::updateProject);
    }

    private void updateProject(TestProjectUpdate projectUpdate)
        throws IOException, ConfigInvalidException {
      try (MetaDataUpdate metaDataUpdate = metaDataUpdateFactory.create(nameKey)) {
        ProjectConfig projectConfig = projectConfigFactory.read(metaDataUpdate);
        removePermissions(projectConfig, projectUpdate.removedPermissions());
        addCapabilities(projectConfig, projectUpdate.addedCapabilities());
        addPermissions(projectConfig, projectUpdate.addedPermissions());
        addLabelPermissions(projectConfig, projectUpdate.addedLabelPermissions());
        setExclusiveGroupPermissions(projectConfig, projectUpdate.exclusiveGroupPermissions());
        projectConfig.commit(metaDataUpdate);
      }
      projectCache.evict(nameKey);
    }

    private void removePermissions(
        ProjectConfig projectConfig,
        ImmutableList<TestProjectUpdate.TestPermissionKey> removedPermissions) {
      for (TestProjectUpdate.TestPermissionKey p : removedPermissions) {
        projectConfig.upsertAccessSection(
            p.section(),
            as -> {
              Permission.Builder permission = as.upsertPermission(p.name());
              if (p.group().isPresent()) {
                GroupReference group =
                    GroupReference.create(p.group().get(), p.group().get().get());
                group = projectConfig.resolve(group);
                permission.removeRule(group);
              } else {
                permission.clearRules();
              }
            });
      }
    }

    private void addCapabilities(
        ProjectConfig projectConfig, ImmutableList<TestCapability> addedCapabilities) {
      for (TestCapability c : addedCapabilities) {
        PermissionRule.Builder rule = newRule(projectConfig, c.group());
        rule.setRange(c.min(), c.max());
        projectConfig.upsertAccessSection(
            AccessSection.GLOBAL_CAPABILITIES, as -> as.upsertPermission(c.name()).add(rule));
      }
    }

    private void addPermissions(
        ProjectConfig projectConfig, ImmutableList<TestPermission> addedPermissions) {
      for (TestPermission p : addedPermissions) {
        PermissionRule.Builder rule = newRule(projectConfig, p.group());
        rule.setAction(p.action());
        rule.setForce(p.force());
        projectConfig.upsertAccessSection(p.ref(), as -> as.upsertPermission(p.name()).add(rule));
      }
    }

    private void addLabelPermissions(
        ProjectConfig projectConfig, ImmutableList<TestLabelPermission> addedLabelPermissions) {
      for (TestLabelPermission p : addedLabelPermissions) {
        PermissionRule.Builder rule = newRule(projectConfig, p.group());
        rule.setAction(p.action());
        rule.setRange(p.min(), p.max());
        String permissionName =
            p.impersonation() ? Permission.forLabelAs(p.name()) : Permission.forLabel(p.name());
        projectConfig.upsertAccessSection(
            p.ref(), as -> as.upsertPermission(permissionName).add(rule));
      }
    }

    private void setExclusiveGroupPermissions(
        ProjectConfig projectConfig,
        ImmutableMap<TestProjectUpdate.TestPermissionKey, Boolean> exclusiveGroupPermissions) {
      exclusiveGroupPermissions.forEach(
          (key, exclusive) ->
              projectConfig.upsertAccessSection(
                  key.section(),
                  as -> as.upsertPermission(key.name()).setExclusiveGroup(exclusive)));
    }

    private RevCommit headOrNull(String branch) {
      if (!branch.startsWith(Constants.R_REFS)) {
        branch = RefNames.REFS_HEADS + branch;
      }

      try (Repository repo = repoManager.openRepository(nameKey);
          RevWalk rw = new RevWalk(repo)) {
        Ref r = repo.exactRef(branch);
        return r == null ? null : rw.parseCommit(r.getObjectId());
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public ProjectConfig getProjectConfig() {
      try (Repository repo = repoManager.openRepository(nameKey)) {
        ProjectConfig projectConfig = projectConfigFactory.create(nameKey);
        projectConfig.load(nameKey, repo);
        return projectConfig;
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public Config getConfig() {
      try (Repository repo = repoManager.openRepository(nameKey);
          RevWalk rw = new RevWalk(repo)) {
        Ref ref = repo.exactRef(REFS_CONFIG);
        if (ref == null) {
          return new Config();
        }
        RevTree tree = rw.parseTree(ref.getObjectId());
        TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), PROJECT_CONFIG, tree);
        if (tw == null) {
          return new Config();
        }
        ObjectLoader loader = rw.getObjectReader().open(tw.getObjectId(0));
        String text = new String(loader.getCachedBytes(), UTF_8);
        Config config = new Config();
        config.fromText(text);
        return config;
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    private void setConfig(Config projectConfig) {
      try (TestRepository<Repository> repo =
          new TestRepository<>(repoManager.openRepository(nameKey))) {
        repo.update(
            RefNames.REFS_CONFIG,
            repo.commit()
                .message("Update project.config from test")
                .parent(getHead(RefNames.REFS_CONFIG))
                .add(ProjectConfig.PROJECT_CONFIG, projectConfig.toText()));
      } catch (Exception e) {
        throw new IllegalStateException(
            "updating project.config of project " + nameKey + " failed", e);
      }
    }

    @Override
    public TestProjectInvalidation.Builder forInvalidation() {
      return TestProjectInvalidation.builder(this::invalidateProject);
    }

    private void invalidateProject(TestProjectInvalidation testProjectInvalidation)
        throws Exception {
      if (testProjectInvalidation.makeProjectConfigInvalid()) {
        Config projectConfig = new Config();
        projectConfig.fromText(getConfig().toText());

        // Make the project config invalid by adding a permission entry with an invalid permission
        // name.
        projectConfig.setString(
            "access", "refs/*", "Invalid Permission Name", "group Administrators");

        setConfig(projectConfig);
        try {
          projectCache.evict(nameKey);
        } catch (Exception e) {
          // Evicting the project from the cache, also triggers a reindex of the project.
          // The reindex step fails if the project config is invalid. That's fine, since it was our
          // intention to make the project config invalid. Hence we ignore exceptions that are cause
          // by an invalid project config here.
          if (!Throwables.getCausalChain(e).stream()
              .anyMatch(ConfigInvalidException.class::isInstance)) {
            throw e;
          }
        }
      }
      if (!testProjectInvalidation.projectConfigUpdater().isEmpty()) {
        Config projectConfig = new Config();
        projectConfig.fromText(getConfig().toText());
        testProjectInvalidation.projectConfigUpdater().forEach(c -> c.accept(projectConfig));
        setConfig(projectConfig);
        try {
          projectCache.evict(nameKey);
        } catch (Exception e) {
          // Evicting the project from the cache, also triggers a reindex of the project.
          // The reindex step fails if the project config is invalid. That's fine, since it was our
          // intention to make the project config invalid. Hence we ignore exceptions that are cause
          // by an invalid project config here.
          if (!Throwables.getCausalChain(e).stream()
              .anyMatch(ConfigInvalidException.class::isInstance)) {
            throw e;
          }
        }
      }
    }
  }

  private static PermissionRule.Builder newRule(
      ProjectConfig project, AccountGroup.UUID groupUUID) {
    GroupReference group = GroupReference.create(groupUUID, groupUUID.get());
    group = project.resolve(group);
    return PermissionRule.builder(group);
  }
}
