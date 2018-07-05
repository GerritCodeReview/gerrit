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

package com.google.gerrit.acceptance.api.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.fetch;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.project.ProjectField;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.project.StalenessChecker;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.junit.Test;

public class ProjectIndexerIT extends AbstractDaemonTest {
  @Inject private ProjectIndexer projectIndexer;
  @Inject private ProjectIndexCollection indexes;
  @Inject private IndexConfig indexConfig;
  @Inject private StalenessChecker stalenessChecker;

  private static final ImmutableSet<String> FIELDS =
      ImmutableSet.of(ProjectField.NAME.getName(), ProjectField.REF_STATE.getName());

  @Test
  public void indexProject_indexesRefStateOfProjectAndParents() throws Exception {
    projectIndexer.index(project);
    ProjectIndex i = indexes.getSearchIndex();
    assertThat(i.getSchema().hasField(ProjectField.REF_STATE)).isTrue();

    Optional<FieldBundle> result =
        i.getRaw(project, QueryOptions.create(indexConfig, 0, 1, FIELDS));

    assertThat(result.isPresent()).isTrue();
    Iterable<byte[]> refState = result.get().getValue(ProjectField.REF_STATE);
    assertThat(refState).isNotEmpty();

    Map<Project.NameKey, Collection<RefState>> states = RefState.parseStates(refState).asMap();

    fetch(testRepo, "refs/meta/config:refs/meta/config");
    Ref projectConfigRef = testRepo.getRepository().exactRef("refs/meta/config");
    TestRepository<InMemoryRepository> allProjectsRepo = cloneProject(allProjects, admin);
    fetch(allProjectsRepo, "refs/meta/config:refs/meta/config");
    Ref allProjectConfigRef = allProjectsRepo.getRepository().exactRef("refs/meta/config");
    assertThat(states)
        .containsExactly(
            project,
            ImmutableSet.of(RefState.of(projectConfigRef)),
            allProjects,
            ImmutableSet.of(RefState.of(allProjectConfigRef)));
  }

  @Test
  public void stalenessChecker_currentProject_notStale() throws Exception {
    assertThat(stalenessChecker.isStale(project)).isFalse();
  }

  @Test
  public void stalenessChecker_currentProjectUpdates_isStale() throws Exception {
    updateProjectConfigWithoutIndexUpdate(project);
    assertThat(stalenessChecker.isStale(project)).isTrue();
  }

  @Test
  public void stalenessChecker_parentProjectUpdates_isStale() throws Exception {
    updateProjectConfigWithoutIndexUpdate(allProjects);
    assertThat(stalenessChecker.isStale(project)).isTrue();
  }

  @Test
  public void stalenessChecker_hierarchyChange_isStale() throws Exception {
    Project.NameKey p1 = createProject("p1", allProjects);
    Project.NameKey p2 = createProject("p2", allProjects);
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().getProject().setParentName(p1);
      u.save();
    }
    assertThat(stalenessChecker.isStale(project)).isFalse();

    updateProjectConfigWithoutIndexUpdate(p1, c -> c.getProject().setParentName(p2));
    assertThat(stalenessChecker.isStale(project)).isTrue();
  }

  private void updateProjectConfigWithoutIndexUpdate(Project.NameKey project) throws Exception {
    updateProjectConfigWithoutIndexUpdate(
        project, c -> c.getProject().setDescription("making it stale"));
  }

  private void updateProjectConfigWithoutIndexUpdate(
      Project.NameKey project, Consumer<ProjectConfig> update) throws Exception {
    try (AutoCloseable ignored = disableProjectIndex()) {
      try (ProjectConfigUpdate u = updateProject(project)) {
        update.accept(u.getConfig());
        u.save();
      }
    } catch (UnsupportedOperationException e) {
      // Drop, as we just wanted to drop the index update
      return;
    }
    fail("should have a UnsupportedOperationException");
  }
}
