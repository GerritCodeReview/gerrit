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

import com.google.common.base.Preconditions;
import com.google.gerrit.acceptance.testsuite.project.TestProjectCreation.Builder;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class ProjectOperationsImpl implements ProjectOperations {
  private final GitRepositoryManager repoManager;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectCache projectCache;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCreator projectCreator;

  @Inject
  ProjectOperationsImpl(
      GitRepositoryManager repoManager,
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectCache projectCache,
      ProjectConfig.Factory projectConfigFactory,
      ProjectCreator projectCreator) {
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

  private class PerProjectOperations implements ProjectOperations.PerProjectOperations {

    Project.NameKey nameKey;

    PerProjectOperations(Project.NameKey nameKey) {
      this.nameKey = nameKey;
    }

    @Override
    public RevCommit getHead(String branch) {
      return Preconditions.checkNotNull(headOrNull(branch));
    }

    @Override
    public boolean hasHead(String branch) {
      return headOrNull(branch) != null;
    }

    @Override
    public TestProjectUpdate.Builder forUpdate() {
      return TestProjectUpdate.builder(this::updateProject);
    }

    private void updateProject(TestProjectUpdate projectUpdate)
        throws IOException, ConfigInvalidException {
      try (MetaDataUpdate metaDataUpdate = metaDataUpdateFactory.create(nameKey)) {
        ProjectConfig projectConfig = projectConfigFactory.read(metaDataUpdate);
        projectUpdate.configModification().accept(projectConfig);
        projectConfig.commit(metaDataUpdate);
      }
      projectCache.evict(nameKey);
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
  }
}
