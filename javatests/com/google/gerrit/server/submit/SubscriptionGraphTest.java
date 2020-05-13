// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmoduleSubscription;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class SubscriptionGraphTest {
  private static final String TEST_PATH = "test/path";
  private static final Project.NameKey SUPER_PROJECT = Project.nameKey("Superproject");
  private static final Project.NameKey SUB_PROJECT = Project.nameKey("Subproject");
  private static final BranchNameKey SUPER_BRANCH =
      BranchNameKey.create(SUPER_PROJECT, "refs/heads/one");
  private static final BranchNameKey SUB_BRANCH =
      BranchNameKey.create(SUB_PROJECT, "refs/heads/one");
  private InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();
  private MergeOpRepoManager mergeOpRepoManager;

  private Map<BranchNameKey, SubscribeSection> allowAllConfig;
  private Map<BranchNameKey, GitModules> mockModulesMap;
  private Map<Project.NameKey, TestRepository<Repository>> reposByName;

  @Mock GitModules.Factory mockGitModulesFactory = mock(GitModules.Factory.class);
  @Mock ProjectCache mockProjectCache = mock(ProjectCache.class);
  @Mock ProjectState mockProjectState = mock(ProjectState.class);

  @Before
  public void setUp() throws Exception {
    when(mockProjectCache.get(any())).thenReturn(Optional.of(mockProjectState));
    mergeOpRepoManager = new MergeOpRepoManager(repoManager, mockProjectCache, null, null);
    allowAllConfig = new HashMap<>();
    mockModulesMap = new HashMap<>();
    reposByName = new HashMap<>();

    createRepo(SUPER_PROJECT);
    createRepo(SUB_PROJECT);
    setSubscription(SUB_BRANCH, ImmutableList.of(SUPER_BRANCH));
    setSubscription(SUPER_BRANCH, ImmutableList.of());
    createBranch(
        SUPER_BRANCH, reposByName.get(SUPER_PROJECT).commit().message("Initial commit").create());
    createBranch(
        SUB_BRANCH, reposByName.get(SUB_PROJECT).commit().message("Initial commit").create());
  }

  @Test
  public void submoduleNotEnabled() throws Exception {
    SubscriptionGraph subGraph = createGraph(false);

    assertThat(subGraph.getTargets()).isEmpty();
    assertThat(subGraph.getBranchesByProject()).isEmpty();
    assertThat(subGraph.getSortedBranches()).isNull();
  }

  @Test
  public void oneSuperprojectOneSubmodule() throws Exception {
    SubscriptionGraph subGraph = createGraph(true);

    assertThat(subGraph.getTargets())
        .containsExactly(
            SUPER_BRANCH, new SubmoduleSubscription(SUPER_BRANCH, SUB_BRANCH, TEST_PATH));
    assertThat(subGraph.getBranchesByProject()).containsExactly(SUPER_PROJECT, SUPER_BRANCH);
    assertThat(subGraph.getSortedBranches()).containsExactly(SUB_BRANCH, SUPER_BRANCH).inOrder();
  }

  @Test
  public void circularSubscription() throws Exception {
    setSubscription(SUPER_BRANCH, ImmutableList.of(SUB_BRANCH));
    SubmoduleConflictException e =
        assertThrows(SubmoduleConflictException.class, () -> createGraph(true));

    String expectedErrorMessage =
        "Subproject,refs/heads/one->Superproject,refs/heads/one->Subproject,refs/heads/one";
    assertThat(e).hasMessageThat().contains(expectedErrorMessage);
  }

  @Test
  public void multipleSuperprojectsToMultipleSubmodules() throws Exception {
    // Create superprojects and subprojects.
    Project.NameKey superProject1 = Project.nameKey("superproject1");
    Project.NameKey superProject2 = Project.nameKey("superproject2");
    Project.NameKey subProject1 = Project.nameKey("subproject1");
    Project.NameKey subProject2 = Project.nameKey("subproject2");
    createRepo(superProject1);
    createRepo(superProject2);
    createRepo(subProject1);
    createRepo(subProject2);

    // Initialize super branches.
    BranchNameKey superBranch1 = BranchNameKey.create(superProject1, "refs/heads/one");
    BranchNameKey superBranch2 = BranchNameKey.create(superProject2, "refs/heads/one");
    createBranch(
        superBranch1, reposByName.get(superProject1).commit().message("Initial commit").create());
    createBranch(
        superBranch2, reposByName.get(superProject2).commit().message("Initial commit").create());

    // Initialize sub branches.
    BranchNameKey subBranch1 = BranchNameKey.create(subProject1, "refs/heads/one");
    BranchNameKey subBranch2 = BranchNameKey.create(subProject1, "refs/heads/two");
    BranchNameKey subBranch3 = BranchNameKey.create(subProject2, "refs/heads/one");
    createBranch(subBranch1, reposByName.get(subProject1).commit().message("Commit1").create());
    createBranch(subBranch2, reposByName.get(subProject1).commit().message("Commit2").create());
    createBranch(subBranch3, reposByName.get(subProject2).commit().message("Commit1").create());

    // Initialize subscriptions.
    setSubscription(subBranch1, ImmutableList.of(superBranch1, superBranch2));
    setSubscription(subBranch2, ImmutableList.of(superBranch1));
    setSubscription(subBranch3, ImmutableList.of(superBranch1, superBranch2));
    setSubscription(superBranch1, ImmutableList.of());
    setSubscription(superBranch2, ImmutableList.of());

    SubscriptionGraph subGraph = createGraph(true, ImmutableSet.of(subBranch1, subBranch2));

    assertThat(subGraph.getTargets())
        .containsExactly(
            superBranch1, new SubmoduleSubscription(superBranch1, subBranch1, TEST_PATH),
            superBranch1, new SubmoduleSubscription(superBranch1, subBranch2, TEST_PATH),
            superBranch2, new SubmoduleSubscription(superBranch2, subBranch1, TEST_PATH));
    assertThat(subGraph.getBranchesByProject())
        .containsExactly(superProject1, superBranch1, superProject2, superBranch2);
    assertThat(subGraph.getSortedBranches())
        .containsExactly(subBranch2, subBranch1, superBranch2, superBranch1)
        .inOrder();
  }

  private void createRepo(Project.NameKey project) throws Exception {
    Repository repo = repoManager.createRepository(project);
    reposByName.put(project, new TestRepository<>(repo));
  }

  private void createBranch(BranchNameKey branch, RevCommit commit) throws Exception {
    SubscribeSection s = new SubscribeSection(branch.project());
    s.addMultiMatchRefSpec("refs/heads/*:refs/heads/*");
    reposByName.get(branch.project()).update(branch.branch(), commit);
    allowAllConfig.put(branch, s);
    when(mockProjectState.getSubscribeSections(branch))
        .thenReturn(ImmutableSet.of(allowAllConfig.get(branch)));
  }

  private void setSubscription(BranchNameKey srcBranch, List<BranchNameKey> targetBranches) {
    List<SubmoduleSubscription> subscriptions =
        targetBranches.stream()
            .map((targetBranch) -> new SubmoduleSubscription(targetBranch, srcBranch, TEST_PATH))
            .collect(Collectors.toList());
    GitModules mockGitModules = mock(GitModules.class);
    when(mockGitModules.subscribedTo(srcBranch)).thenReturn(subscriptions);
    mockModulesMap.put(srcBranch, mockGitModules);
    when(mockGitModulesFactory.create(srcBranch, mergeOpRepoManager))
        .thenReturn(mockModulesMap.get(srcBranch));
  }

  private SubscriptionGraph createGraph(
      boolean enableSubscription, Set<BranchNameKey> updatedBranches) throws Exception {
    return new SubscriptionGraph(
        mockGitModulesFactory,
        updatedBranches,
        mockProjectCache,
        mergeOpRepoManager,
        enableSubscription);
  }

  private SubscriptionGraph createGraph(boolean enableSubscription) throws Exception {
    return createGraph(enableSubscription, ImmutableSet.of(SUB_BRANCH));
  }
}
