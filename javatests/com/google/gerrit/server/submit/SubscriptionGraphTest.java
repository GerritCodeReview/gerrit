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
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmoduleSubscription;
import com.google.gerrit.entities.SubscribeSection;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.submit.SubscriptionGraph.DefaultFactory;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import java.util.List;
import java.util.Optional;
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

  @Mock GitModules.Factory mockGitModulesFactory = mock(GitModules.Factory.class);
  @Mock ProjectCache mockProjectCache = mock(ProjectCache.class);
  @Mock ProjectState mockProjectState = mock(ProjectState.class);

  @Before
  public void setUp() throws Exception {
    when(mockProjectCache.get(any())).thenReturn(Optional.of(mockProjectState));
    mergeOpRepoManager = new MergeOpRepoManager(repoManager, mockProjectCache, null, null);

    GitModules emptyMockGitModules = mock(GitModules.class);
    when(emptyMockGitModules.subscribedTo(any())).thenReturn(ImmutableSet.of());
    when(mockGitModulesFactory.create(any(), any())).thenReturn(emptyMockGitModules);

    TestRepository<Repository> superProject = createRepo(SUPER_PROJECT);
    TestRepository<Repository> submoduleProject = createRepo(SUB_PROJECT);

    // Make sure that SUPER_BRANCH and SUB_BRANCH can be subscribed.
    allowSubscription(SUPER_BRANCH);
    allowSubscription(SUB_BRANCH);

    setSubscription(SUB_BRANCH, ImmutableList.of(SUPER_BRANCH));
    setSubscription(SUPER_BRANCH, ImmutableList.of());
    createBranch(
        superProject, SUPER_BRANCH, superProject.commit().message("Initial commit").create());
    createBranch(
        submoduleProject, SUB_BRANCH, submoduleProject.commit().message("Initial commit").create());
  }

  @Test
  public void oneSuperprojectOneSubmodule() throws Exception {
    SubscriptionGraph.Factory factory = new DefaultFactory(mockGitModulesFactory, mockProjectCache);
    SubscriptionGraph subscriptionGraph =
        factory.compute(ImmutableSet.of(SUB_BRANCH), mergeOpRepoManager);

    assertThat(subscriptionGraph.getAffectedSuperProjects()).containsExactly(SUPER_PROJECT);
    assertThat(subscriptionGraph.getAffectedSuperBranches(SUPER_PROJECT))
        .containsExactly(SUPER_BRANCH);
    assertThat(subscriptionGraph.getSubscriptions(SUPER_BRANCH))
        .containsExactly(new SubmoduleSubscription(SUPER_BRANCH, SUB_BRANCH, TEST_PATH));
    assertThat(subscriptionGraph.hasSuperproject(SUB_BRANCH)).isTrue();
    assertThat(subscriptionGraph.getSortedSuperprojectAndSubmoduleBranches())
        .containsExactly(SUB_BRANCH, SUPER_BRANCH)
        .inOrder();
  }

  @Test
  public void circularSubscription() throws Exception {
    SubscriptionGraph.Factory factory = new DefaultFactory(mockGitModulesFactory, mockProjectCache);
    setSubscription(SUPER_BRANCH, ImmutableList.of(SUB_BRANCH));
    SubmoduleConflictException e =
        assertThrows(
            SubmoduleConflictException.class,
            () -> factory.compute(ImmutableSet.of(SUB_BRANCH), mergeOpRepoManager));

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
    TestRepository<Repository> superProjectRepo1 = createRepo(superProject1);
    TestRepository<Repository> superProjectRepo2 = createRepo(superProject2);
    TestRepository<Repository> submoduleRepo1 = createRepo(subProject1);
    TestRepository<Repository> submoduleRepo2 = createRepo(subProject2);

    // Initialize super branches.
    BranchNameKey superBranch1 = BranchNameKey.create(superProject1, "refs/heads/one");
    BranchNameKey superBranch2 = BranchNameKey.create(superProject2, "refs/heads/one");
    createBranch(
        superProjectRepo1,
        superBranch1,
        superProjectRepo1.commit().message("Initial commit").create());
    createBranch(
        superProjectRepo2,
        superBranch2,
        superProjectRepo2.commit().message("Initial commit").create());

    // Initialize sub branches.
    BranchNameKey submoduleBranch1 = BranchNameKey.create(subProject1, "refs/heads/one");
    BranchNameKey submoduleBranch2 = BranchNameKey.create(subProject1, "refs/heads/two");
    BranchNameKey submoduleBranch3 = BranchNameKey.create(subProject2, "refs/heads/one");
    createBranch(
        submoduleRepo1, submoduleBranch1, submoduleRepo1.commit().message("Commit1").create());
    createBranch(
        submoduleRepo1, submoduleBranch2, submoduleRepo1.commit().message("Commit2").create());
    createBranch(
        submoduleRepo2, submoduleBranch3, submoduleRepo2.commit().message("Commit1").create());

    allowSubscription(submoduleBranch1);
    allowSubscription(submoduleBranch2);
    allowSubscription(submoduleBranch3);

    // Initialize subscriptions.
    setSubscription(submoduleBranch1, ImmutableList.of(superBranch1, superBranch2));
    setSubscription(submoduleBranch2, ImmutableList.of(superBranch1));
    setSubscription(submoduleBranch3, ImmutableList.of(superBranch1, superBranch2));

    SubscriptionGraph.Factory factory = new DefaultFactory(mockGitModulesFactory, mockProjectCache);
    SubscriptionGraph subscriptionGraph =
        factory.compute(ImmutableSet.of(submoduleBranch1, submoduleBranch2), mergeOpRepoManager);

    assertThat(subscriptionGraph.getAffectedSuperProjects())
        .containsExactly(superProject1, superProject2);
    assertThat(subscriptionGraph.getAffectedSuperBranches(superProject1))
        .containsExactly(superBranch1);
    assertThat(subscriptionGraph.getAffectedSuperBranches(superProject2))
        .containsExactly(superBranch2);

    assertThat(subscriptionGraph.getSubscriptions(superBranch1))
        .containsExactly(
            new SubmoduleSubscription(superBranch1, submoduleBranch1, TEST_PATH),
            new SubmoduleSubscription(superBranch1, submoduleBranch2, TEST_PATH));
    assertThat(subscriptionGraph.getSubscriptions(superBranch2))
        .containsExactly(new SubmoduleSubscription(superBranch2, submoduleBranch1, TEST_PATH));

    assertThat(subscriptionGraph.hasSuperproject(submoduleBranch1)).isTrue();
    assertThat(subscriptionGraph.hasSuperproject(submoduleBranch2)).isTrue();
    assertThat(subscriptionGraph.hasSuperproject(submoduleBranch3)).isFalse();

    assertThat(subscriptionGraph.getSortedSuperprojectAndSubmoduleBranches())
        .containsExactly(submoduleBranch2, submoduleBranch1, superBranch2, superBranch1)
        .inOrder();
  }

  private TestRepository<Repository> createRepo(Project.NameKey project) throws Exception {
    Repository repo = repoManager.createRepository(project);
    return new TestRepository<>(repo);
  }

  private void createBranch(TestRepository<Repository> repo, BranchNameKey branch, RevCommit commit)
      throws Exception {
    repo.update(branch.branch(), commit);
  }

  private void allowSubscription(BranchNameKey branch) {
    SubscribeSection.Builder s = SubscribeSection.builder(branch.project());
    s.addMultiMatchRefSpec("refs/heads/*:refs/heads/*");
    when(mockProjectState.getSubscribeSections(branch)).thenReturn(ImmutableSet.of(s.build()));
  }

  private void setSubscription(
      BranchNameKey submoduleBranch, List<BranchNameKey> superprojectBranches) {
    List<SubmoduleSubscription> subscriptions =
        superprojectBranches.stream()
            .map(
                (targetBranch) ->
                    new SubmoduleSubscription(targetBranch, submoduleBranch, TEST_PATH))
            .collect(Collectors.toList());
    GitModules mockGitModules = mock(GitModules.class);
    when(mockGitModules.subscribedTo(submoduleBranch)).thenReturn(subscriptions);
    when(mockGitModulesFactory.create(submoduleBranch, mergeOpRepoManager))
        .thenReturn(mockGitModules);
  }
}
