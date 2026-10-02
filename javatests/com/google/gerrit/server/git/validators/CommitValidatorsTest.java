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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.plugincontext.PluginSetEntryContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectNotifyFilterValidator;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.ReceiveCommitControl;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

public class CommitValidatorsTest {
  private static final Project.NameKey PROJECT = Project.nameKey("project");
  private static final BranchNameKey BRANCH = BranchNameKey.create(PROJECT, "refs/heads/master");
  private static final String AUTHOR_EMAIL = "author@example.com";

  @Test
  public void registersFileCountMetricOnlyOnce() {
    ProjectState projectState = mock(ProjectState.class);
    when(projectState.statePermitsWrite()).thenReturn(true);
    PermissionBackend.ForProject forProject = mock(PermissionBackend.ForProject.class);
    MetricMaker metricMaker = mock(MetricMaker.class);
    CommitValidators.Factory factory = newFactory(projectState, metricMaker);

    // Create two CommitValidator instances
    assertThat(
            factory.forGerritCommits(
                forProject, BRANCH, user(), mock(RevWalk.class), /* change= */ null))
        .isNotNull();
    assertThat(
            factory.forGerritCommits(
                forProject, BRANCH, user(), mock(RevWalk.class), /* change= */ null))
        .isNotNull();

    // Confirm only 1 metric was created
    verify(metricMaker, times(1))
        .newCounter(
            eq("validation/file_count"),
            any(Description.class),
            ArgumentMatchers.<Field<Integer>>any(),
            ArgumentMatchers.<Field<String>>any());
  }

  private static IdentifiedUser user() {
    IdentifiedUser user = mock(IdentifiedUser.class);
    when(user.hasEmailAddress(AUTHOR_EMAIL)).thenReturn(true);
    return user;
  }

  private static CommitValidators.Factory newFactory(
      ProjectState projectState, MetricMaker metricMaker) {
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
        mock(ReceiveCommitControl.class),
        urlFormatter,
        new Config(),
        pluginValidators,
        new AllUsersName("All-Users"),
        new AllProjectsName("All-Projects"),
        projectCache,
        mock(ProjectConfig.Factory.class),
        mock(ProjectNotifyFilterValidator.class),
        mock(ProjectConfigRegexValidator.class),
        mock(ChangeUtil.class),
        new CommitValidators.Metrics(metricMaker),
        mock(ApprovalQueryBuilder.class),
        commitValidationInfoListeners);
  }
}
