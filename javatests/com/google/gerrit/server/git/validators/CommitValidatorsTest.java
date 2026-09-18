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

package com.google.gerrit.server.git.validators;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.plugincontext.PluginSetEntryContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;

public class CommitValidatorsTest {
  private static final Project.NameKey PROJECT = Project.nameKey("project");
  private static final BranchNameKey BRANCH = BranchNameKey.create(PROJECT, "refs/heads/master");
  private static final String AUTHOR_EMAIL = "author@example.com";

  @Test
  public void forGerritCommitsDirectCommitUsesBranchMergePermission() throws Exception {
    PermissionBackend.ForProject forProject = mock(PermissionBackend.ForProject.class);
    PermissionBackend.ForRef branchPermissions = mock(PermissionBackend.ForRef.class);
    PermissionBackend.ForRef reviewPermissions = mock(PermissionBackend.ForRef.class);
    when(forProject.ref(BRANCH.branch())).thenReturn(branchPermissions);
    when(forProject.ref("refs/for/" + BRANCH.branch())).thenReturn(reviewPermissions);
    when(branchPermissions.test(RefPermission.MERGE)).thenReturn(true);
    when(reviewPermissions.test(RefPermission.MERGE)).thenReturn(false);

    ProjectState projectState = mock(ProjectState.class);
    when(projectState.statePermitsWrite()).thenReturn(true);

    CommitValidators validators =
        newFactory(projectState)
            .forGerritCommits(forProject, BRANCH, user(), mock(RevWalk.class), /* change= */ null);

    assertThat(validators.validate(mergeCommitEvent())).isNotNull();
  }

  @Test
  public void forGerritCommitsPatchSetUsesReviewMergePermission() throws Exception {
    PermissionBackend.ForProject forProject = mock(PermissionBackend.ForProject.class);
    PermissionBackend.ForRef branchPermissions = mock(PermissionBackend.ForRef.class);
    PermissionBackend.ForRef reviewPermissions = mock(PermissionBackend.ForRef.class);
    when(forProject.ref(BRANCH.branch())).thenReturn(branchPermissions);
    when(forProject.ref("refs/for/" + BRANCH.branch())).thenReturn(reviewPermissions);
    when(branchPermissions.test(RefPermission.MERGE)).thenReturn(false);
    when(reviewPermissions.test(RefPermission.MERGE)).thenReturn(true);

    ProjectState projectState = mock(ProjectState.class);
    when(projectState.statePermitsWrite()).thenReturn(true);

    CommitValidators validators =
        newFactory(projectState)
            .forGerritCommits(
                forProject, BRANCH, user(), mock(RevWalk.class), /* change= */ mock(Change.class));

    assertThat(validators.validate(mergeCommitEvent())).isNotNull();
  }

  private static IdentifiedUser user() {
    IdentifiedUser user = mock(IdentifiedUser.class);
    when(user.hasEmailAddress(AUTHOR_EMAIL)).thenReturn(true);
    return user;
  }

  private static CommitReceivedEvent mergeCommitEvent() {
    CommitReceivedEvent event = mock(CommitReceivedEvent.class);
    RevCommit commit = mock(RevCommit.class);
    when(commit.getParentCount()).thenReturn(2);
    when(commit.getAuthorIdent()).thenReturn(new PersonIdent("author", AUTHOR_EMAIL));
    when(commit.getCommitterIdent()).thenReturn(new PersonIdent("author", AUTHOR_EMAIL));
    when(commit.name())
        .thenReturn(ObjectId.fromString("0000000000000000000000000000000000000001").name());
    when(event.getProjectNameKey()).thenReturn(PROJECT);
    when(event.getBranchNameKey()).thenReturn(BRANCH);
    when(event.getRefName()).thenReturn(BRANCH.branch());
    event.commit = commit;
    event.command =
        new ReceiveCommand(
            ObjectId.zeroId(),
            ObjectId.fromString("0000000000000000000000000000000000000001"),
            BRANCH.branch());
    event.refName = BRANCH.branch();
    event.project = Project.builder(PROJECT).build();
    return event;
  }

  private static CommitValidators.Factory newFactory(ProjectState projectState) {
    ProjectCache projectCache = mock(ProjectCache.class);
    when(projectCache.get(PROJECT)).thenReturn(Optional.of(projectState));
    @SuppressWarnings("unchecked")
    DynamicItem<UrlFormatter> urlFormatter = mock(DynamicItem.class);
    @SuppressWarnings("unchecked")
    PluginSetContext<CommitValidationListener> pluginValidators = mock(PluginSetContext.class);
    when(pluginValidators.iterator())
        .thenReturn(ImmutableList.<PluginSetEntryContext<CommitValidationListener>>of().iterator());
    @SuppressWarnings("unchecked")
    PluginSetContext<CommitValidationInfoListener> commitValidationInfoListeners =
        mock(PluginSetContext.class);
    return new CommitValidators.Factory(
        new PersonIdent("gerrit", "gerrit@example.com"),
        urlFormatter,
        new Config(),
        pluginValidators,
        new AllUsersName("All-Users"),
        new AllProjectsName("All-Projects"),
        projectCache,
        mock(ProjectConfig.Factory.class),
        mock(ChangeUtil.class),
        mock(MetricMaker.class),
        mock(ApprovalQueryBuilder.class),
        commitValidationInfoListeners);
  }
}
