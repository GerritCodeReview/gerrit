// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.DelegateRefDatabase;
import com.google.gerrit.server.git.DelegateRepository;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryableIndexQueryAction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link CreateRefControl}. */
public class CreateRefControlTest {
  private static final String REFS_BRANCH_HEADS = "refs/branch-heads/";
  private static final Project.NameKey PROJECT_ENABLED = Project.nameKey("project-enabled");
  private static final Project.NameKey PROJECT_DISABLED = Project.nameKey("project-disabled");

  private InMemoryRepository baseRepo;
  private TestRepository<InMemoryRepository> testRepo;
  private Repository repo;
  private List<ImmutableList<String>> recordedPrefixCalls;
  private CurrentUser currentUser;
  private PermissionBackend permissionBackend;
  private PermissionBackend.ForRef forRef;
  private ProjectCache projectCache;
  private RetryHelper retryHelper;
  private RetryableIndexQueryAction<InternalChangeQuery, List<ChangeData>> indexQueryAction;
  private CreateRefControl createRefControl;
  private RevCommit branchHeadCommit;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() throws Exception {
    baseRepo = new InMemoryRepository(new DfsRepositoryDescription("repo"));
    testRepo = new TestRepository<>(baseRepo);
    recordedPrefixCalls = new ArrayList<>();
    RefDatabase recordingRefDatabase =
        new DelegateRefDatabase(baseRepo) {
          @Override
          public List<Ref> getRefsByPrefix(String... prefixes) throws IOException {
            recordedPrefixCalls.add(ImmutableList.copyOf(prefixes));
            return super.getRefsByPrefix(prefixes);
          }
        };
    repo =
        new DelegateRepository(baseRepo) {
          @Override
          public RefDatabase getRefDatabase() {
            return recordingRefDatabase;
          }
        };

    branchHeadCommit = testRepo.branch("refs/branch-heads/m1").commit().create();

    currentUser = mock(CurrentUser.class);
    permissionBackend = mock(PermissionBackend.class);
    PermissionBackend.WithUser withUser = mock(PermissionBackend.WithUser.class);
    forRef = mock(PermissionBackend.ForRef.class);
    PermissionBackend.ForProject forProject = mock(PermissionBackend.ForProject.class);
    when(permissionBackend.user(currentUser)).thenReturn(withUser);
    when(withUser.ref(any())).thenReturn(forRef);
    when(withUser.project(any())).thenReturn(forProject);
    when(forProject.filter(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

    projectCache = mock(ProjectCache.class);
    ProjectState enabledState = mock(ProjectState.class);
    when(enabledState.getNameKey()).thenReturn(PROJECT_ENABLED);
    when(projectCache.get(PROJECT_ENABLED)).thenReturn(Optional.of(enabledState));

    ProjectState disabledState = mock(ProjectState.class);
    when(disabledState.getNameKey()).thenReturn(PROJECT_DISABLED);
    when(projectCache.get(PROJECT_DISABLED)).thenReturn(Optional.of(disabledState));

    retryHelper = mock(RetryHelper.class);
    indexQueryAction = mock(RetryableIndexQueryAction.class);
    when(indexQueryAction.call()).thenReturn(ImmutableList.of());
    when(retryHelper.<List<ChangeData>>changeIndexQuery(any(), any())).thenReturn(indexQueryAction);

    createRefControl = newCreateRefControl(new Config());
  }

  private CreateRefControl newCreateRefControl(Config cfg) {
    return new CreateRefControl(
        permissionBackend, projectCache, new Reachable(permissionBackend), retryHelper, cfg);
  }

  @After
  public void tearDown() {
    baseRepo.close();
  }

  @Test
  public void checkCreateRef_defaultWhenRestrictionDisabled_checksHeadsAndTagsAndFallsBackToIndex()
      throws Exception {
    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () ->
                createRefControl.checkCreateRef(
                    () -> currentUser,
                    repo,
                    BranchNameKey.create(PROJECT_ENABLED, "refs/heads/new-branch"),
                    branchHeadCommit,
                    /* forPush= */ false));
    assertThat(thrown).hasMessageThat().contains("Unable to resolve object");
    assertThat(recordedPrefixCalls)
        .containsExactly(ImmutableList.of(Constants.R_HEADS, Constants.R_TAGS));
    verify(retryHelper).changeIndexQuery(eq("queryChangesByProjectCommitWithLimit1"), any());

    recordedPrefixCalls.clear();
    when(indexQueryAction.call()).thenReturn(ImmutableList.of(mock(ChangeData.class)));
    createRefControl.checkCreateRef(
        () -> currentUser,
        repo,
        BranchNameKey.create(PROJECT_ENABLED, "refs/heads/new-branch"),
        branchHeadCommit,
        /* forPush= */ false);
    assertThat(recordedPrefixCalls)
        .containsExactly(ImmutableList.of(Constants.R_HEADS, Constants.R_TAGS));
  }

  @Test
  public void checkCreateRef_whenRestrictedWithDefaultAllowedRefs_checksDefaultRefsAndSkipsIndex()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean("receive", null, "restrictCreateRefToVisibleBranches", true);
    CreateRefControl control = newCreateRefControl(cfg);

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () ->
                control.checkCreateRef(
                    () -> currentUser,
                    repo,
                    BranchNameKey.create(PROJECT_DISABLED, "refs/heads/new-branch"),
                    branchHeadCommit,
                    /* forPush= */ false));
    assertThat(thrown)
        .hasMessageThat()
        .contains("and that it's reachable from a branch/tag that is visible to you");
    assertThat(recordedPrefixCalls)
        .containsExactly(
            ImmutableList.of(Constants.R_HEADS, Constants.R_TAGS, RefNames.REFS_CONFIG));
    verifyNoInteractions(retryHelper);
  }

  @Test
  public void checkCreateRef_whenRestrictedWithCustomAllowedRefs_usesConfiguredPrefixes()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean("receive", null, "restrictCreateRefToVisibleBranches", true);
    cfg.setStringList(
        "receive",
        null,
        "createBranchAllowedRefs",
        ImmutableList.of(
            Constants.R_HEADS, Constants.R_TAGS, RefNames.REFS_CONFIG, REFS_BRANCH_HEADS));
    CreateRefControl control = newCreateRefControl(cfg);

    control.checkCreateRef(
        () -> currentUser,
        repo,
        BranchNameKey.create(PROJECT_ENABLED, "refs/heads/branch-1"),
        branchHeadCommit,
        /* forPush= */ false);
    control.checkCreateRef(
        () -> currentUser,
        repo,
        BranchNameKey.create(PROJECT_ENABLED, "refs/heads/branch-2"),
        branchHeadCommit,
        /* forPush= */ false);

    ImmutableList<String> expectedPrefixes =
        ImmutableList.of(
            Constants.R_HEADS, Constants.R_TAGS, RefNames.REFS_CONFIG, REFS_BRANCH_HEADS);
    assertThat(recordedPrefixCalls).containsExactly(expectedPrefixes, expectedPrefixes);
    verifyNoInteractions(retryHelper);
  }

  @Test
  public void checkCreateRef_whenForPushWithUpdatePermission_skipsRefLookup() throws Exception {
    when(forRef.test(RefPermission.UPDATE)).thenReturn(true);

    createRefControl.checkCreateRef(
        () -> currentUser,
        repo,
        BranchNameKey.create(PROJECT_ENABLED, "refs/heads/new-branch"),
        branchHeadCommit,
        /* forPush= */ true);

    assertThat(recordedPrefixCalls).isEmpty();
    verifyNoInteractions(retryHelper);
  }
}
