// Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class ProjectConfigIT extends AbstractDaemonTest {
  private static final String INVALID_PRROJECT_CONFIG =
      "[label \"Foo\"]\n"
          // copyAllScoresOnTrivialRebase is deprecated and no longer allowed to be set
          + "  copyAllScoresOnTrivialRebase = true";

  @Inject private ProjectOperations projectOperations;

  @Test
  public void noLabelValidationForNonRefsMetaConfigChange() throws Exception {
    PushOneCommit.Result r =
        createChange(
            testRepo,
            "refs/heads/master",
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            INVALID_PRROJECT_CONFIG,
            /* topic= */ null);
    r.assertOkStatus();
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void noLabelValidationForNoneProjectConfigChange() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit.Result r =
        createChange(
            testRepo,
            RefNames.REFS_CONFIG,
            "Test Change",
            "foo.config",
            INVALID_PRROJECT_CONFIG,
            /* topic= */ null);
    r.assertOkStatus();
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void validateNoIssues_push() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            "[label \"Foo\"]\n  description = Foo Label");
    PushOneCommit.Result r = push.to("refs/for/" + RefNames.REFS_CONFIG);
    r.assertOkStatus();

    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void validateNoIssues_createChangeApi() throws Exception {
    ChangeInput changeInput = new ChangeInput();
    changeInput.project = project.get();
    changeInput.branch = RefNames.REFS_CONFIG;
    changeInput.subject = "A change";
    changeInput.status = ChangeStatus.NEW;
    ChangeInfo changeInfo = gApi.changes().create(changeInput).get();

    gApi.changes().id(changeInfo.id).edit().create();
    gApi.changes()
        .id(changeInfo.id)
        .edit()
        .modifyFile(
            ProjectConfig.PROJECT_CONFIG,
            RawInputUtil.create("[label \"Foo\"]\n  description = Foo Label"));

    PublishChangeEditInput publishInput = new PublishChangeEditInput();
    gApi.changes().id(changeInfo.id).edit().publish(publishInput);

    approve(changeInfo.id);
    gApi.changes().id(changeInfo.id).current().submit();
    assertThat(gApi.changes().id(changeInfo.id).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void rejectSettingCopyAnyScore() throws Exception {
    testRejectSettingLabelFlag(ProjectConfig.KEY_COPY_ANY_SCORE, /* value= */ true, "is:ANY");
    testRejectSettingLabelFlag(ProjectConfig.KEY_COPY_ANY_SCORE, /* value= */ false, "is:ANY");
  }

  @Test
  public void rejectSettingCopyMinScore() throws Exception {
    testRejectSettingLabelFlag(ProjectConfig.KEY_COPY_MIN_SCORE, /* value= */ true, "is:MIN");
    testRejectSettingLabelFlag(ProjectConfig.KEY_COPY_MIN_SCORE, /* value= */ false, "is:MIN");
  }

  @Test
  public void rejectSettingCopyMaxScore() throws Exception {
    testRejectSettingLabelFlag(ProjectConfig.KEY_COPY_MAX_SCORE, /* value= */ true, "is:MAX");
    testRejectSettingLabelFlag(ProjectConfig.KEY_COPY_MAX_SCORE, /* value= */ false, "is:MAX");
  }

  @Test
  public void rejectSettingCopyAllScoresIfNoChange() throws Exception {
    testRejectSettingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* value= */ true, "changekind:NO_CHANGE");
    testRejectSettingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* value= */ false, "changekind:NO_CHANGE");
  }

  @Test
  public void rejectSettingCopyAllScoresIfNoCodeChange() throws Exception {
    testRejectSettingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
        /* value= */ true,
        "changekind:NO_CODE_CHANGE");
    testRejectSettingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
        /* value= */ false,
        "changekind:NO_CODE_CHANGE");
  }

  @Test
  public void rejectSettingCopyAllScoresOnMergeFirstParentUpdate() throws Exception {
    testRejectSettingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        /* value= */ true,
        "changekind:MERGE_FIRST_PARENT_UPDATE");
    testRejectSettingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        /* value= */ false,
        "changekind:MERGE_FIRST_PARENT_UPDATE");
  }

  @Test
  public void rejectSettingCopyAllScoresOnTrivialRebase() throws Exception {
    testRejectSettingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
        /* value= */ true,
        "changekind:TRIVIAL_REBASE");
    testRejectSettingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
        /* value= */ false,
        "changekind:TRIVIAL_REBASE");
  }

  @Test
  public void rejectSettingCopyAllScoresIfListOfFilesDidNotChange() throws Exception {
    testRejectSettingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        /* value= */ true,
        "has:unchanged-files");
    testRejectSettingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        /* value= */ false,
        "has:unchanged-files");
  }

  private void testRejectSettingLabelFlag(
      String key, boolean value, String expectedPredicateSuggestion) throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            String.format("[label \"Foo\"]\n  %s = %s", key, value));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format(
            "invalid %s file in revision %s", ProjectConfig.PROJECT_CONFIG, r.getCommit().name()));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: Parameter 'label.Foo.%s' is deprecated and cannot be set,"
                + " use '%s' in 'label.Foo.copyCondition' instead.",
            abbreviateName(r.getCommit()), key, expectedPredicateSuggestion));
  }

  @Test
  public void rejectSettingCopyValues() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            String.format(
                "[label \"Foo\"]\n  %s = 1\n  %s = 2",
                ProjectConfig.KEY_COPY_VALUE, ProjectConfig.KEY_COPY_VALUE));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format(
            "invalid %s file in revision %s", ProjectConfig.PROJECT_CONFIG, r.getCommit().name()));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: Parameter 'label.Foo.%s' is deprecated and cannot be set,"
                + " use 'is:<copy-value>' in 'label.Foo.copyCondition' instead.",
            abbreviateName(r.getCommit()), ProjectConfig.KEY_COPY_VALUE));
  }

  @Test
  public void rejectChangingCopyAnyScore() throws Exception {
    testRejectChangingLabelFlag(ProjectConfig.KEY_COPY_ANY_SCORE, /* value= */ true, "is:ANY");
    testRejectChangingLabelFlag(ProjectConfig.KEY_COPY_ANY_SCORE, /* value= */ false, "is:ANY");
  }

  @Test
  public void rejectChangingCopyMinScore() throws Exception {
    testRejectChangingLabelFlag(ProjectConfig.KEY_COPY_MIN_SCORE, /* value= */ true, "is:MIN");
    testRejectChangingLabelFlag(ProjectConfig.KEY_COPY_MIN_SCORE, /* value= */ false, "is:MIN");
  }

  @Test
  public void rejectChangingCopyMaxScore() throws Exception {
    testRejectChangingLabelFlag(ProjectConfig.KEY_COPY_MAX_SCORE, /* value= */ true, "is:MAX");
    testRejectChangingLabelFlag(ProjectConfig.KEY_COPY_MAX_SCORE, /* value= */ false, "is:MAX");
  }

  @Test
  public void rejectChangingCopyAllScoresIfNoChange() throws Exception {
    testRejectChangingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* value= */ true, "changekind:NO_CHANGE");
    testRejectChangingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* value= */ false, "changekind:NO_CHANGE");
  }

  @Test
  public void rejectChangingCopyAllScoresIfNoCodeChange() throws Exception {
    testRejectChangingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
        /* value= */ true,
        "changekind:NO_CODE_CHANGE");
    testRejectChangingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
        /* value= */ false,
        "changekind:NO_CODE_CHANGE");
  }

  @Test
  public void rejectChangingCopyAllScoresOnMergeFirstParentUpdate() throws Exception {
    testRejectChangingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        /* value= */ true,
        "changekind:MERGE_FIRST_PARENT_UPDATE");
    testRejectChangingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        /* value= */ false,
        "changekind:MERGE_FIRST_PARENT_UPDATE");
  }

  @Test
  public void rejectChangingCopyAllScoresOnTrivialRebase() throws Exception {
    testRejectChangingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
        /* value= */ true,
        "changekind:TRIVIAL_REBASE");
    testRejectChangingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
        /* value= */ false,
        "changekind:TRIVIAL_REBASE");
  }

  @Test
  public void rejectChangingCopyAllScoresIfListOfFilesDidNotChange() throws Exception {
    testRejectChangingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        /* value= */ true,
        "has:unchanged-files");
    testRejectChangingLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        /* value= */ false,
        "has:unchanged-files");
  }

  private void testRejectChangingLabelFlag(
      String key, boolean value, String expectedPredicateSuggestion) throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .add(
              ProjectConfig.PROJECT_CONFIG,
              String.format("[label \"Foo\"]\n  %s = %s", key, !value))
          .parent(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
          .create();
    }

    testRejectSettingLabelFlag(key, value, expectedPredicateSuggestion);
  }

  @Test
  public void rejectChangingCopyValues() throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .add(
              ProjectConfig.PROJECT_CONFIG,
              String.format(
                  "[label \"Foo\"]\n  %s = 1\n  %s = 2",
                  ProjectConfig.KEY_COPY_VALUE, ProjectConfig.KEY_COPY_VALUE))
          .parent(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
          .create();
    }

    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            String.format(
                "[label \"Foo\"]\n  %s = -1\n  %s = -2",
                ProjectConfig.KEY_COPY_VALUE, ProjectConfig.KEY_COPY_VALUE));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format(
            "invalid %s file in revision %s", ProjectConfig.PROJECT_CONFIG, r.getCommit().name()));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: Parameter 'label.Foo.%s' is deprecated and cannot be set,"
                + " use 'is:<copy-value>' in 'label.Foo.copyCondition' instead.",
            abbreviateName(r.getCommit()), ProjectConfig.KEY_COPY_VALUE));
  }

  @Test
  public void testRejectChangingLabelFunction_toMaxWithBlock() throws Exception {
    testChangingLabelFunction(
        /* initialLabelFunction= */ LabelFunction.NO_BLOCK,
        /* newLabelFunction= */ LabelFunction.MAX_WITH_BLOCK,
        /* errorMessage= */ String.format(
            "Value '%s' of 'label.foo.function' is not allowed and cannot be set."
                + " Label functions can only be set to {no_block, no_op}."
                + " Use submit requirements instead of label functions.",
            LabelFunction.MAX_WITH_BLOCK.getFunctionName()));
  }

  @Test
  public void testRejectChangingLabelFunction_toMaxNoBlock() throws Exception {
    testChangingLabelFunction(
        /* initialLabelFunction= */ LabelFunction.NO_BLOCK,
        /* newLabelFunction= */ LabelFunction.MAX_NO_BLOCK,
        /* errorMessage= */ String.format(
            "Value '%s' of 'label.foo.function' is not allowed and cannot be set."
                + " Label functions can only be set to {no_block, no_op}."
                + " Use submit requirements instead of label functions.",
            LabelFunction.MAX_NO_BLOCK.getFunctionName()));
  }

  @Test
  public void testRejectChangingLabelFunction_toAnyWithBlock() throws Exception {
    testChangingLabelFunction(
        /* initialLabelFunction= */ LabelFunction.NO_BLOCK,
        /* newLabelFunction= */ LabelFunction.ANY_WITH_BLOCK,
        /* errorMessage= */ String.format(
            "Value '%s' of 'label.foo.function' is not allowed and cannot be set."
                + " Label functions can only be set to {no_block, no_op}."
                + " Use submit requirements instead of label functions.",
            LabelFunction.ANY_WITH_BLOCK.getFunctionName()));
  }

  @Test
  public void testChangingLabelFunction_toNoBlock() throws Exception {
    testChangingLabelFunction(
        /* initialLabelFunction= */ LabelFunction.MAX_WITH_BLOCK,
        /* newLabelFunction= */ LabelFunction.NO_BLOCK,
        /* errorMessage= */ null);
  }

  @Test
  public void testChangingLabelFunction_toNoOp() throws Exception {
    testChangingLabelFunction(
        /* initialLabelFunction= */ LabelFunction.MAX_WITH_BLOCK,
        /* newLabelFunction= */ LabelFunction.NO_OP,
        /* errorMessage= */ null);
  }

  @Test
  public void testRejectRemovingLabelFunction() throws Exception {
    testChangingLabelFunction(
        /* initialLabelFunction= */ LabelFunction.MAX_WITH_BLOCK,
        /* newLabelFunction= */ null,
        /* errorMessage= */ String.format(
            "Cannot delete '%s.%s.%s'."
                + " Label functions can only be set to {%s, %s}."
                + " Use submit requirements instead of label functions.",
            ProjectConfig.LABEL,
            "Foo",
            ProjectConfig.KEY_FUNCTION,
            LabelFunction.NO_BLOCK,
            LabelFunction.NO_OP));
  }

  private void testChangingLabelFunction(
      LabelFunction initialLabelFunction,
      @Nullable LabelFunction newLabelFunction,
      @Nullable String errorMessage)
      throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .add(
              ProjectConfig.PROJECT_CONFIG,
              String.format(
                  "[label \"Foo\"]\n  %s = %s\n",
                  ProjectConfig.KEY_FUNCTION, initialLabelFunction.getFunctionName()))
          .parent(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
          .create();
    }

    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            newLabelFunction == null
                ? "[label \"Foo\"]\n"
                : String.format(
                    "[label \"Foo\"]\n  %s = %s\n",
                    ProjectConfig.KEY_FUNCTION, newLabelFunction.getFunctionName()));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    if (errorMessage == null) {
      r.assertOkStatus();
      return;
    }
    r.assertErrorStatus(
        String.format(
            "invalid %s file in revision %s", ProjectConfig.PROJECT_CONFIG, r.getCommit().name()));
    r.assertMessage(errorMessage);
  }

  @Test
  public void unsetCopyAnyScore() throws Exception {
    testUnsetLabelFlag(ProjectConfig.KEY_COPY_ANY_SCORE, /* previousValue= */ true);
    testUnsetLabelFlag(ProjectConfig.KEY_COPY_ANY_SCORE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyMinScore() throws Exception {
    testUnsetLabelFlag(ProjectConfig.KEY_COPY_MIN_SCORE, /* previousValue= */ true);
    testUnsetLabelFlag(ProjectConfig.KEY_COPY_MIN_SCORE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyMaxScore() throws Exception {
    testUnsetLabelFlag(ProjectConfig.KEY_COPY_MAX_SCORE, /* previousValue= */ true);
    testUnsetLabelFlag(ProjectConfig.KEY_COPY_MAX_SCORE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyAllScoresIfNoChange() throws Exception {
    testUnsetLabelFlag(ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* previousValue= */ true);
    testUnsetLabelFlag(ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyAllScoresIfNoCodeChange() throws Exception {
    testUnsetLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE, /* previousValue= */ true);
    testUnsetLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyAllScoresOnMergeFirstParentUpdate() throws Exception {
    testUnsetLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE, /* previousValue= */ true);
    testUnsetLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyAllScoresOnTrivialRebase() throws Exception {
    testUnsetLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE, /* previousValue= */ true);
    testUnsetLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyAllScoresIfListOfFilesDidNotChange() throws Exception {
    testUnsetLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        /* previousValue= */ true);
    testUnsetLabelFlag(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        /* previousValue= */ false);
  }

  private void testUnsetLabelFlag(String key, boolean previousValue) throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .add(
              ProjectConfig.PROJECT_CONFIG,
              String.format("[label \"Foo\"]\n  %s = %s", key, previousValue))
          .parent(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
          .create();
    }

    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            String.format("[label \"Foo\"]\n  otherKey = value"));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertOkStatus();
  }

  @Test
  public void unsetCopyValues() throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .add(
              ProjectConfig.PROJECT_CONFIG,
              String.format(
                  "[label \"Foo\"]\n  %s = 1\n  %s = 2",
                  ProjectConfig.KEY_COPY_VALUE, ProjectConfig.KEY_COPY_VALUE))
          .parent(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
          .create();
    }

    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            String.format("[label \"Foo\"]\n  otherKey = value"));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertOkStatus();
  }

  @Test
  public void keepCopyAnyScoreUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(ProjectConfig.KEY_COPY_ANY_SCORE, /* value= */ true);
    testKeepLabelFlagUnchanged(ProjectConfig.KEY_COPY_ANY_SCORE, /* value= */ false);
  }

  @Test
  public void keepCopyMinScoreUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(ProjectConfig.KEY_COPY_MIN_SCORE, /* value= */ true);
    testKeepLabelFlagUnchanged(ProjectConfig.KEY_COPY_MIN_SCORE, /* value= */ false);
  }

  @Test
  public void keepCopyMaxScoreUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(ProjectConfig.KEY_COPY_MAX_SCORE, /* value= */ true);
    testKeepLabelFlagUnchanged(ProjectConfig.KEY_COPY_MAX_SCORE, /* value= */ false);
  }

  @Test
  public void keepCopyAllScoresIfNoChangeUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* value= */ true);
    testKeepLabelFlagUnchanged(ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* value= */ false);
  }

  @Test
  public void keepCopyAllScoresIfNoCodeChangeUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE, /* value= */ true);
    testKeepLabelFlagUnchanged(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE, /* value= */ false);
  }

  @Test
  public void keepCopyAllScoresOnMergeFirstParentUpdateUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE, /* value= */ true);
    testKeepLabelFlagUnchanged(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE, /* value= */ false);
  }

  @Test
  public void keepCopyAllScoresOnTrivialRebaseUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE, /* value= */ true);
    testKeepLabelFlagUnchanged(
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE, /* value= */ false);
  }

  @Test
  public void keepCopyAllScoresIfListOfFilesDidNotChangeUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE, /* value= */ true);
    testKeepLabelFlagUnchanged(
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE, /* value= */ false);
  }

  private void testKeepLabelFlagUnchanged(String key, boolean value) throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .add(
              ProjectConfig.PROJECT_CONFIG, String.format("[label \"Foo\"]\n  %s = %s", key, value))
          .parent(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
          .create();
    }

    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            String.format("[label \"Foo\"]\n  %s = %s\n  otherKey = value", key, value));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertOkStatus();
  }

  @Test
  public void keepCopyValuesUnchanged() throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .add(
              ProjectConfig.PROJECT_CONFIG,
              String.format(
                  "[label \"Foo\"]\n  %s = 1\n  %s = 2",
                  ProjectConfig.KEY_COPY_VALUE, ProjectConfig.KEY_COPY_VALUE))
          .parent(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
          .create();
    }

    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            String.format(
                "[label \"Foo\"]\n  %s = 1\n  %s = 2\n  otherKey = value",
                ProjectConfig.KEY_COPY_VALUE, ProjectConfig.KEY_COPY_VALUE));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertOkStatus();
  }

  @Test
  public void keepCopyValuesUnchanged_differentOrder() throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .add(
              ProjectConfig.PROJECT_CONFIG,
              String.format(
                  "[label \"Foo\"]\n  %s = 1\n  %s = 2",
                  ProjectConfig.KEY_COPY_VALUE, ProjectConfig.KEY_COPY_VALUE))
          .parent(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
          .create();
    }

    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            String.format(
                "[label \"Foo\"]\n  %s = 2\n  %s = 1",
                ProjectConfig.KEY_COPY_VALUE, ProjectConfig.KEY_COPY_VALUE));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertOkStatus();
  }

  @Test
  public void rejectMultipleLabelFlags() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            String.format(
                "[label \"Foo\"]\n  %s = true\n  %s = true",
                ProjectConfig.KEY_COPY_MIN_SCORE, ProjectConfig.KEY_COPY_MAX_SCORE));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format(
            "invalid %s file in revision %s", ProjectConfig.PROJECT_CONFIG, r.getCommit().name()));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: Parameter 'label.Foo.%s' is deprecated and cannot be set,"
                + " use 'is:MIN' in 'label.Foo.copyCondition' instead.",
            abbreviateName(r.getCommit()), ProjectConfig.KEY_COPY_MIN_SCORE));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: Parameter 'label.Foo.%s' is deprecated and cannot be set,"
                + " use 'is:MAX' in 'label.Foo.copyCondition' instead.",
            abbreviateName(r.getCommit()), ProjectConfig.KEY_COPY_MAX_SCORE));
  }

  @Test
  public void setCopyCondition() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            String.format("[label \"Foo\"]\n  %s = is:ANY", ProjectConfig.KEY_COPY_CONDITION));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertOkStatus();
  }

  @Test
  public void validateLabelConfigInInitialCommit() throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      testRepo.delete(RefNames.REFS_CONFIG);
    }

    PushOneCommit push =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "Test Change",
                ProjectConfig.PROJECT_CONFIG,
                INVALID_PRROJECT_CONFIG)
            .setParents(ImmutableList.of());
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format(
            "invalid %s file in revision %s", ProjectConfig.PROJECT_CONFIG, r.getCommit().name()));
  }

  private void fetchRefsMetaConfig() throws Exception {
    fetch(testRepo, RefNames.REFS_CONFIG + ":" + RefNames.REFS_CONFIG);
    testRepo.reset(RefNames.REFS_CONFIG);
  }

  private String abbreviateName(AnyObjectId id) throws Exception {
    return ObjectIds.abbreviateName(id, testRepo.getRevWalk().getObjectReader());
  }
}
