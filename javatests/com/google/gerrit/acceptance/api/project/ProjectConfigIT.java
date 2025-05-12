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
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ConfigValue;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.GroupList;
import com.google.gerrit.server.project.LabelConfigValidator;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class ProjectConfigIT extends AbstractDaemonTest {
  private static final String INVALID_PRROJECT_CONFIG =
      "[label \"Foo\"]\n"
          // copyAllScoresOnTrivialRebase is deprecated and no longer allowed to be set
          + "  copyAllScoresOnTrivialRebase = true";

  @Inject private ProjectOperations projectOperations;
  @Inject private ExtensionRegistry extensionRegistry;

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
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ANY_SCORE, /* value= */ true, "is:ANY");
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ANY_SCORE, /* value= */ false, "is:ANY");
  }

  @Test
  public void rejectCreatingLabelWithInvalidFunction() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            "[label \"Foo\"]\n  function = INVALID");
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format("commit %s: invalid project configuration", abbreviateName(r.getCommit())));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: invalid project configuration:\n"
                + "ERROR: commit %s:   project.config: Invalid function for label \"foo\"."
                + " Valid names are: NoBlock, NoOp, PatchSetLock",
            abbreviateName(r.getCommit()), abbreviateName(r.getCommit())));
  }

  @Test
  public void rejectCreatingLabelPermissionWithInvalidRange_minGreaterThanMax() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ImmutableMap.of(
                ProjectConfig.PROJECT_CONFIG,
                "[access \"refs/heads/*\"]\n  label-Code-Review = 1..-1 group Registered-Users",
                GroupList.FILE_NAME,
                String.format("%s\tRegistered-Users", SystemGroupBackend.REGISTERED_USERS.get())));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format("commit %s: invalid project configuration", abbreviateName(r.getCommit())));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: invalid project configuration:\n"
                + "ERROR: commit %s:   project.config: invalid rule in"
                + " access.refs/heads/*.label-Code-Review:"
                + " invalid range in rule: 1..-1 group Registered-Users",
            abbreviateName(r.getCommit()), abbreviateName(r.getCommit())));
  }

  @Test
  public void rejectCreatingLabelPermissionWithInvalidRange_minSetMaxMissing() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ImmutableMap.of(
                ProjectConfig.PROJECT_CONFIG,
                "[access \"refs/heads/*\"]\n  label-Code-Review = -1.. group Registered-Users",
                GroupList.FILE_NAME,
                String.format("%s\tRegistered-Users", SystemGroupBackend.REGISTERED_USERS.get())));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format("commit %s: invalid project configuration", abbreviateName(r.getCommit())));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: invalid project configuration:\n"
                + "ERROR: commit %s:   project.config: invalid rule in"
                + " access.refs/heads/*.label-Code-Review:"
                + " invalid range in rule: -1.. group Registered-Users",
            abbreviateName(r.getCommit()), abbreviateName(r.getCommit())));
  }

  @Test
  public void rejectCreatingLabelPermissionWithInvalidRange_maxSetMinMissing() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ImmutableMap.of(
                ProjectConfig.PROJECT_CONFIG,
                "[access \"refs/heads/*\"]\n  label-Code-Review = ..1 group Registered-Users",
                GroupList.FILE_NAME,
                String.format("%s\tRegistered-Users", SystemGroupBackend.REGISTERED_USERS.get())));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format("commit %s: invalid project configuration", abbreviateName(r.getCommit())));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: invalid project configuration:\n"
                + "ERROR: commit %s:   project.config: invalid rule in"
                + " access.refs/heads/*.label-Code-Review:"
                + " invalid range in rule: ..1 group Registered-Users",
            abbreviateName(r.getCommit()), abbreviateName(r.getCommit())));
  }

  @Test
  public void rejectSettingCopyMinScore() throws Exception {
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_MIN_SCORE, /* value= */ true, "is:MIN");
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_MIN_SCORE, /* value= */ false, "is:MIN");
  }

  @Test
  public void rejectSettingCopyMaxScore() throws Exception {
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_MAX_SCORE, /* value= */ true, "is:MAX");
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_MAX_SCORE, /* value= */ false, "is:MAX");
  }

  @Test
  public void rejectSettingCopyAllScoresIfNoChange() throws Exception {
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
        /* value= */ true,
        "changekind:NO_CHANGE");
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
        /* value= */ false,
        "changekind:NO_CHANGE");
  }

  @Test
  public void rejectSettingCopyAllScoresIfNoCodeChange() throws Exception {
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
        /* value= */ true,
        "changekind:NO_CODE_CHANGE");
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
        /* value= */ false,
        "changekind:NO_CODE_CHANGE");
  }

  @Test
  public void rejectSettingCopyAllScoresOnMergeFirstParentUpdate() throws Exception {
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        /* value= */ true,
        "changekind:MERGE_FIRST_PARENT_UPDATE");
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        /* value= */ false,
        "changekind:MERGE_FIRST_PARENT_UPDATE");
  }

  @Test
  public void rejectSettingCopyAllScoresOnTrivialRebase() throws Exception {
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
        /* value= */ true,
        "changekind:TRIVIAL_REBASE");
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
        /* value= */ false,
        "changekind:TRIVIAL_REBASE");
  }

  @Test
  public void rejectSettingCopyAllScoresIfListOfFilesDidNotChange() throws Exception {
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        /* value= */ true,
        "has:unchanged-files");
    testRejectSettingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
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
                LabelConfigValidator.KEY_COPY_VALUE, LabelConfigValidator.KEY_COPY_VALUE));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format(
            "invalid %s file in revision %s", ProjectConfig.PROJECT_CONFIG, r.getCommit().name()));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: Parameter 'label.Foo.%s' is deprecated and cannot be set,"
                + " use 'is:<copy-value>' in 'label.Foo.copyCondition' instead.",
            abbreviateName(r.getCommit()), LabelConfigValidator.KEY_COPY_VALUE));
  }

  @Test
  public void rejectChangingCopyAnyScore() throws Exception {
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ANY_SCORE, /* value= */ true, "is:ANY");
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ANY_SCORE, /* value= */ false, "is:ANY");
  }

  @Test
  public void rejectChangingCopyMinScore() throws Exception {
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_MIN_SCORE, /* value= */ true, "is:MIN");
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_MIN_SCORE, /* value= */ false, "is:MIN");
  }

  @Test
  public void rejectChangingCopyMaxScore() throws Exception {
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_MAX_SCORE, /* value= */ true, "is:MAX");
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_MAX_SCORE, /* value= */ false, "is:MAX");
  }

  @Test
  public void rejectChangingCopyAllScoresIfNoChange() throws Exception {
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
        /* value= */ true,
        "changekind:NO_CHANGE");
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
        /* value= */ false,
        "changekind:NO_CHANGE");
  }

  @Test
  public void rejectChangingCopyAllScoresIfNoCodeChange() throws Exception {
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
        /* value= */ true,
        "changekind:NO_CODE_CHANGE");
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
        /* value= */ false,
        "changekind:NO_CODE_CHANGE");
  }

  @Test
  public void rejectChangingCopyAllScoresOnMergeFirstParentUpdate() throws Exception {
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        /* value= */ true,
        "changekind:MERGE_FIRST_PARENT_UPDATE");
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        /* value= */ false,
        "changekind:MERGE_FIRST_PARENT_UPDATE");
  }

  @Test
  public void rejectChangingCopyAllScoresOnTrivialRebase() throws Exception {
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
        /* value= */ true,
        "changekind:TRIVIAL_REBASE");
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
        /* value= */ false,
        "changekind:TRIVIAL_REBASE");
  }

  @Test
  public void rejectChangingCopyAllScoresIfListOfFilesDidNotChange() throws Exception {
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        /* value= */ true,
        "has:unchanged-files");
    testRejectChangingLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
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
                  LabelConfigValidator.KEY_COPY_VALUE, LabelConfigValidator.KEY_COPY_VALUE))
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
                LabelConfigValidator.KEY_COPY_VALUE, LabelConfigValidator.KEY_COPY_VALUE));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format(
            "invalid %s file in revision %s", ProjectConfig.PROJECT_CONFIG, r.getCommit().name()));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: Parameter 'label.Foo.%s' is deprecated and cannot be set,"
                + " use 'is:<copy-value>' in 'label.Foo.copyCondition' instead.",
            abbreviateName(r.getCommit()), LabelConfigValidator.KEY_COPY_VALUE));
  }

  @Test
  public void rejectSubmitRequirement_duplicateDescriptionKeys() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            "[submit-requirement \"Foo\"]\n"
                + "    description = description 1\n "
                + "    submittableIf = label:Code-Review=MAX\n"
                + "[submit-requirement \"Foo\"]\n"
                + "    description = description 2\n");
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format("commit %s: invalid project configuration", abbreviateName(r.getCommit())));
    r.assertMessage(
        String.format(
            "ERROR: commit %s:   project.config: multiple definitions of description"
                + " for submit requirement 'foo'",
            abbreviateName(r.getCommit())));
  }

  @Test
  public void pluginConfigs_neverWriteDefaultValueToConfigFile() throws Exception {
    String projectConfig = projectOperations.project(project).getConfig().toText();
    assertThat(projectConfig).doesNotContain("myPlugin");

    ProjectConfigEntry entry = new ProjectConfigEntry("enabled", "true");
    try (Registration ignored =
        extensionRegistry.newRegistration().add(entry, "test-config-entry")) {
      // Default value is populated in API response
      ConfigInfo configInfo = gApi.projects().name(project.get()).config();
      assertThat(configInfo.pluginConfig.get("myPlugin").get("test-config-entry").value)
          .isEqualTo("true");

      // Set an unrelated parameter
      ConfigInput input = new ConfigInput();
      input.description = "New description";

      gApi.projects().name(project.get()).config(input);

      // The project config does not contain a section for the plugin
      projectConfig = projectOperations.project(project).getConfig().toText();
      assertThat(projectConfig).doesNotContain("myPlugin");
      assertThat(projectConfig).contains("New description");

      // Set the plugin config to the default value. Set an unrelated setting on the side.
      Map<String, ConfigValue> val = new HashMap<>();
      input.pluginConfigValues = new HashMap<>();
      input.pluginConfigValues.put("myPlugin", val);
      val.put("test-config-entry", new ConfigValue("true"));
      input.description = "New description2";
      gApi.projects().name(project.get()).config(input);

      // The project config does not contain a section for the plugin
      projectConfig = projectOperations.project(project).getConfig().toText();
      assertThat(projectConfig).doesNotContain("myPlugin");
      assertThat(projectConfig).contains("New description2");
    }
  }

  @Test
  public void pluginConfigs_persistNonDefaultConfig() throws Exception {
    String projectConfig = projectOperations.project(project).getConfig().toText();
    assertThat(projectConfig).doesNotContain("myPlugin");

    ProjectConfigEntry entry = new ProjectConfigEntry("enabled", "true");
    try (Registration ignored =
        extensionRegistry.newRegistration().add(entry, "test-config-entry")) {
      // Default value is populated in API response
      ConfigInfo configInfo = gApi.projects().name(project.get()).config();
      assertThat(configInfo.pluginConfig.get("myPlugin").get("test-config-entry").value)
          .isEqualTo("true");

      // Change value to non-default
      ConfigInput input = new ConfigInput();
      input.pluginConfigValues = new HashMap<>();
      Map<String, ConfigValue> val = new HashMap<>();
      input.pluginConfigValues.put("myPlugin", val);
      val.put("test-config-entry", new ConfigValue("false"));
      gApi.projects().name(project.get()).config(input);

      // API response serves new setting
      configInfo = gApi.projects().name(project.get()).config();
      assertThat(configInfo.pluginConfig.get("myPlugin").get("test-config-entry").value)
          .isEqualTo("false");

      // The project config contains a section for the plugin
      projectConfig = projectOperations.project(project).getConfig().toText();
      assertThat(projectConfig).contains("myPlugin");
    }
  }

  @Test
  public void pluginConfigs_canUnsetPluginSetting() throws Exception {
    String projectConfig = projectOperations.project(project).getConfig().toText();
    assertThat(projectConfig).doesNotContain("myPlugin");

    ProjectConfigEntry entry = new ProjectConfigEntry("enabled", "true");
    try (Registration ignored =
        extensionRegistry.newRegistration().add(entry, "test-config-entry")) {
      // Default value is populated in API response
      ConfigInfo configInfo = gApi.projects().name(project.get()).config();
      assertThat(configInfo.pluginConfig.get("myPlugin").get("test-config-entry").value)
          .isEqualTo("true");

      // Change value to non-default
      ConfigInput input = new ConfigInput();
      input.pluginConfigValues = new HashMap<>();
      Map<String, ConfigValue> val = new HashMap<>();
      input.pluginConfigValues.put("myPlugin", val);
      val.put("test-config-entry", new ConfigValue("false"));
      gApi.projects().name(project.get()).config(input);

      // API response serves new setting
      configInfo = gApi.projects().name(project.get()).config();
      assertThat(configInfo.pluginConfig.get("myPlugin").get("test-config-entry").value)
          .isEqualTo("false");

      // The project config contains a section for the plugin
      projectConfig = projectOperations.project(project).getConfig().toText();
      assertThat(projectConfig).contains("myPlugin");

      // Reset value to default
      val.put("test-config-entry", new ConfigValue("true"));
      gApi.projects().name(project.get()).config(input);

      projectConfig = projectOperations.project(project).getConfig().toText();
      assertThat(projectConfig).doesNotContain("myPlugin");
      configInfo = gApi.projects().name(project.get()).config();
      assertThat(configInfo.pluginConfig.get("myPlugin").get("test-config-entry").value)
          .isEqualTo("true");
    }
  }

  @Test
  public void rejectSubmitRequirement_duplicateApplicableIfKeys() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            "[submit-requirement \"Foo\"]\n "
                + "   applicableIf = is:true\n  "
                + "   submittableIf = label:Code-Review=MAX\n"
                + "[submit-requirement \"Foo\"]\n"
                + "   applicableIf = is:false\n");
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format("commit %s: invalid project configuration", abbreviateName(r.getCommit())));
    r.assertMessage(
        String.format(
            "ERROR: commit %s:   project.config: multiple definitions of applicableif"
                + " for submit requirement 'foo'",
            abbreviateName(r.getCommit())));
  }

  @Test
  public void rejectSubmitRequirement_duplicateSubmittableIfKeys() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            "[submit-requirement \"Foo\"]\n"
                + "    submittableIf = label:Code-Review=MAX\n"
                + "[submit-requirement \"Foo\"]\n"
                + "    submittableIf = label:Code-Review=MIN\n");
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format("commit %s: invalid project configuration", abbreviateName(r.getCommit())));
    r.assertMessage(
        String.format(
            "ERROR: commit %s:   project.config: multiple definitions of submittableif"
                + " for submit requirement 'foo'",
            abbreviateName(r.getCommit())));
  }

  @Test
  public void rejectSubmitRequirement_duplicateOverrideIfKeys() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            "[submit-requirement \"Foo\"]\n"
                + "  overrideIf = is:true\n "
                + "  submittableIf = label:Code-Review=MAX\n"
                + "[submit-requirement \"Foo\"]\n"
                + "  overrideIf = is:false\n");
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format("commit %s: invalid project configuration", abbreviateName(r.getCommit())));
    r.assertMessage(
        String.format(
            "ERROR: commit %s:   project.config: multiple definitions of overrideif"
                + " for submit requirement 'foo'",
            abbreviateName(r.getCommit())));
  }

  @Test
  public void rejectSubmitRequirement_duplicateCanOverrideInChildProjectsKey() throws Exception {
    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            "[submit-requirement \"Foo\"]\n"
                + "    canOverrideInChildProjects = true\n"
                + "    submittableIf = label:Code-Review=MAX\n"
                + "[submit-requirement \"Foo\"]\n "
                + "    canOverrideInChildProjects = false\n");
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format("commit %s: invalid project configuration", abbreviateName(r.getCommit())));
    r.assertMessage(
        String.format(
            "ERROR: commit %s:   project.config: multiple definitions of canoverrideinchildprojects"
                + " for submit requirement 'foo'",
            abbreviateName(r.getCommit())));
  }

  @Test
  public void submitRequirementsAreParsed_forExistingDuplicateDefinitions() throws Exception {
    // Duplicate submit requirement definitions are rejected on config change uploads. For setups
    // already containing duplicate SR definitions, the server is able to parse the "submit
    // requirements correctly"

    RevCommit revision;
    // Commit a change to the project config, bypassing server validation.
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      revision =
          testRepo
              .branch(RefNames.REFS_CONFIG)
              .commit()
              .add(
                  ProjectConfig.PROJECT_CONFIG,
                  "[submit-requirement \"Foo\"]\n"
                      + "    canOverrideInChildProjects = true\n"
                      + "    submittableIf = label:Code-Review=MAX\n"
                      + "[submit-requirement \"Foo\"]\n "
                      + "    canOverrideInChildProjects = false\n")
              .parent(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
              .create();
    }

    try (Repository git = repoManager.openRepository(project)) {
      // Server is able to parse the config.
      ProjectConfig cfg = projectConfigFactory.create(project);
      cfg.load(git, revision);

      // One of the two definitions takes precedence and overrides the other.
      assertThat(cfg.getSubmitRequirementSections())
          .containsExactly(
              "Foo",
              SubmitRequirement.builder()
                  .setName("Foo")
                  .setAllowOverrideInChildProjects(false)
                  .setSubmittabilityExpression(
                      SubmitRequirementExpression.create("label:Code-Review=MAX"))
                  .build());
    }
  }

  @Test
  public void testRejectChangingLabelFunction_toMaxWithBlock() throws Exception {
    testChangingLabelFunction(
        /* initialLabelFunction= */ LabelFunction.NO_BLOCK,
        /* newLabelFunction= */ LabelFunction.MAX_WITH_BLOCK,
        /* errorMessage= */ String.format(
            "Value '%s' of 'label.foo.function' is not allowed and cannot be set."
                + " Label functions can only be set to {no_block, no_op, patch_set_lock}."
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
                + " Label functions can only be set to {no_block, no_op, patch_set_lock}."
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
                + " Label functions can only be set to {no_block, no_op, patch_set_lock}."
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
  public void testChangingLabelFunction_toPatchSetLock() throws Exception {
    testChangingLabelFunction(
        /* initialLabelFunction= */ LabelFunction.MAX_WITH_BLOCK,
        /* newLabelFunction= */ LabelFunction.PATCH_SET_LOCK,
        /* errorMessage= */ null);
  }

  @Test
  public void testRejectRemovingLabelFunction() throws Exception {
    testChangingLabelFunction(
        /* initialLabelFunction= */ LabelFunction.MAX_WITH_BLOCK,
        /* newLabelFunction= */ null,
        /* errorMessage= */ String.format(
            "Cannot delete '%s.%s.%s'."
                + " Label functions can only be set to {%s, %s, %s}."
                + " Use submit requirements instead of label functions.",
            ProjectConfig.LABEL,
            "Foo",
            ProjectConfig.KEY_FUNCTION,
            LabelFunction.NO_BLOCK,
            LabelFunction.NO_OP,
            LabelFunction.PATCH_SET_LOCK));
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
    testUnsetLabelFlag(LabelConfigValidator.KEY_COPY_ANY_SCORE, /* previousValue= */ true);
    testUnsetLabelFlag(LabelConfigValidator.KEY_COPY_ANY_SCORE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyMinScore() throws Exception {
    testUnsetLabelFlag(LabelConfigValidator.KEY_COPY_MIN_SCORE, /* previousValue= */ true);
    testUnsetLabelFlag(LabelConfigValidator.KEY_COPY_MIN_SCORE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyMaxScore() throws Exception {
    testUnsetLabelFlag(LabelConfigValidator.KEY_COPY_MAX_SCORE, /* previousValue= */ true);
    testUnsetLabelFlag(LabelConfigValidator.KEY_COPY_MAX_SCORE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyAllScoresIfNoChange() throws Exception {
    testUnsetLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* previousValue= */ true);
    testUnsetLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyAllScoresIfNoCodeChange() throws Exception {
    testUnsetLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE, /* previousValue= */ true);
    testUnsetLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyAllScoresOnMergeFirstParentUpdate() throws Exception {
    testUnsetLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        /* previousValue= */ true);
    testUnsetLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        /* previousValue= */ false);
  }

  @Test
  public void unsetCopyAllScoresOnTrivialRebase() throws Exception {
    testUnsetLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE, /* previousValue= */ true);
    testUnsetLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE, /* previousValue= */ false);
  }

  @Test
  public void unsetCopyAllScoresIfListOfFilesDidNotChange() throws Exception {
    testUnsetLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        /* previousValue= */ true);
    testUnsetLabelFlag(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
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
                  LabelConfigValidator.KEY_COPY_VALUE, LabelConfigValidator.KEY_COPY_VALUE))
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
    testKeepLabelFlagUnchanged(LabelConfigValidator.KEY_COPY_ANY_SCORE, /* value= */ true);
    testKeepLabelFlagUnchanged(LabelConfigValidator.KEY_COPY_ANY_SCORE, /* value= */ false);
  }

  @Test
  public void keepCopyMinScoreUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(LabelConfigValidator.KEY_COPY_MIN_SCORE, /* value= */ true);
    testKeepLabelFlagUnchanged(LabelConfigValidator.KEY_COPY_MIN_SCORE, /* value= */ false);
  }

  @Test
  public void keepCopyMaxScoreUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(LabelConfigValidator.KEY_COPY_MAX_SCORE, /* value= */ true);
    testKeepLabelFlagUnchanged(LabelConfigValidator.KEY_COPY_MAX_SCORE, /* value= */ false);
  }

  @Test
  public void keepCopyAllScoresIfNoChangeUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* value= */ true);
    testKeepLabelFlagUnchanged(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CHANGE, /* value= */ false);
  }

  @Test
  public void keepCopyAllScoresIfNoCodeChangeUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE, /* value= */ true);
    testKeepLabelFlagUnchanged(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE, /* value= */ false);
  }

  @Test
  public void keepCopyAllScoresOnMergeFirstParentUpdateUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE, /* value= */ true);
    testKeepLabelFlagUnchanged(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE, /* value= */ false);
  }

  @Test
  public void keepCopyAllScoresOnTrivialRebaseUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE, /* value= */ true);
    testKeepLabelFlagUnchanged(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE, /* value= */ false);
  }

  @Test
  public void keepCopyAllScoresIfListOfFilesDidNotChangeUnchanged() throws Exception {
    testKeepLabelFlagUnchanged(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        /* value= */ true);
    testKeepLabelFlagUnchanged(
        LabelConfigValidator.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        /* value= */ false);
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
                  LabelConfigValidator.KEY_COPY_VALUE, LabelConfigValidator.KEY_COPY_VALUE))
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
                LabelConfigValidator.KEY_COPY_VALUE, LabelConfigValidator.KEY_COPY_VALUE));
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
                  LabelConfigValidator.KEY_COPY_VALUE, LabelConfigValidator.KEY_COPY_VALUE))
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
                LabelConfigValidator.KEY_COPY_VALUE, LabelConfigValidator.KEY_COPY_VALUE));
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
                LabelConfigValidator.KEY_COPY_MIN_SCORE, LabelConfigValidator.KEY_COPY_MAX_SCORE));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    r.assertErrorStatus(
        String.format(
            "invalid %s file in revision %s", ProjectConfig.PROJECT_CONFIG, r.getCommit().name()));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: Parameter 'label.Foo.%s' is deprecated and cannot be set,"
                + " use 'is:MIN' in 'label.Foo.copyCondition' instead.",
            abbreviateName(r.getCommit()), LabelConfigValidator.KEY_COPY_MIN_SCORE));
    r.assertMessage(
        String.format(
            "ERROR: commit %s: Parameter 'label.Foo.%s' is deprecated and cannot be set,"
                + " use 'is:MAX' in 'label.Foo.copyCondition' instead.",
            abbreviateName(r.getCommit()), LabelConfigValidator.KEY_COPY_MAX_SCORE));
  }

  @Test
  public void testSettingCopyCondition() throws Exception {
    testChangingCopyCondition(/* initialCopyCondition= */ null, /* newCopyCondition= */ "is:ANY");
  }

  @Test
  public void testRejectNonParseableCopyCondition_badSyntax() throws Exception {
    testChangingCopyConditionExpectError(
        /* initialCopyCondition= */ "is:ANY",
        /* newCopyCondition= */ ":",
        /* errorMessage= */ "Cannot parse copy condition ':' of label Foo (parameter"
            + " 'label.Foo.copyCondition'): line 1:0 no viable alternative at input ':'");
  }

  @Test
  public void testRejectNonParseableCopyCondition_unsupportedOperator() throws Exception {
    testChangingCopyConditionExpectError(
        /* initialCopyCondition= */ "is:ANY",
        /* newCopyCondition= */ "foo:bar",
        /* errorMessage= */ "Cannot parse copy condition 'foo:bar' of label Foo (parameter"
            + " 'label.Foo.copyCondition'): unsupported operator foo:bar");
  }

  @Test
  public void testFixNonParseableCopyCondition() throws Exception {
    testChangingCopyCondition(/* initialCopyCondition= */ ":", /* newCopyCondition= */ "is:ANY");
  }

  @Test
  public void testChangingCopyCondition() throws Exception {
    testChangingCopyCondition(
        /* initialCopyCondition= */ "is:ANY", /* newCopyCondition= */ "is:MAX");
  }

  @Test
  public void testDeletingCopyCondition() throws Exception {
    testChangingCopyCondition(/* initialCopyCondition= */ "is:ANY", /* newCopyCondition= */ null);
  }

  @Test
  public void testDeletingNonParseableCopyCondition() throws Exception {
    testChangingCopyCondition(/* initialCopyCondition= */ ":", /* newCopyCondition= */ null);
  }

  @Test
  public void testChangingNonParseableCopyCondition() throws Exception {
    testChangingCopyConditionExpectWarning(
        /* initialCopyCondition= */ ":",
        /* newCopyCondition= */ ":foo",
        /* warningMessage= */ "Cannot parse copy condition ':foo' of label Foo (parameter"
            + " 'label.Foo.copyCondition'): line 1:0 no viable alternative at input ':'");
  }

  @Test
  public void falseIsTheDefaultForBooleanProjectConfigIfNoDefaultIsConfigured() throws Exception {
    assertThat(
            projectCache
                .get(project)
                .orElseThrow(illegalState(project))
                .is(BooleanProjectConfig.REJECT_IMPLICIT_MERGES))
        .isFalse();
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=true")
  public void configureTrueAsDefaultForBooleanProjectConfig() throws Exception {
    assertThat(
            projectCache
                .get(project)
                .orElseThrow(illegalState(project))
                .is(BooleanProjectConfig.REJECT_IMPLICIT_MERGES))
        .isTrue();

    // true can be overridden in the project
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateProject(
              b ->
                  b.setBooleanConfig(
                      BooleanProjectConfig.REJECT_IMPLICIT_MERGES, InheritableBoolean.FALSE));
      u.save();
    }
    assertThat(
            projectCache
                .get(project)
                .orElseThrow(illegalState(project))
                .is(BooleanProjectConfig.REJECT_IMPLICIT_MERGES))
        .isFalse();
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=forced")
  public void configureForcedAsDefaultForBooleanProjectConfig() throws Exception {
    assertThat(
            projectCache
                .get(project)
                .orElseThrow(illegalState(project))
                .is(BooleanProjectConfig.REJECT_IMPLICIT_MERGES))
        .isTrue();

    // forced cannot be overridden in the project
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateProject(
              b ->
                  b.setBooleanConfig(
                      BooleanProjectConfig.REJECT_IMPLICIT_MERGES, InheritableBoolean.FALSE));
      u.save();
    }
    assertThat(
            projectCache
                .get(project)
                .orElseThrow(illegalState(project))
                .is(BooleanProjectConfig.REJECT_IMPLICIT_MERGES))
        .isTrue();
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=false")
  public void configureFalseAsDefaultForBooleanProjectConfig() throws Exception {
    assertThat(
            projectCache
                .get(project)
                .orElseThrow(illegalState(project))
                .is(BooleanProjectConfig.REJECT_IMPLICIT_MERGES))
        .isFalse();

    // false can be overridden in the project
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateProject(
              b ->
                  b.setBooleanConfig(
                      BooleanProjectConfig.REJECT_IMPLICIT_MERGES, InheritableBoolean.TRUE));
      u.save();
    }
    assertThat(
            projectCache
                .get(project)
                .orElseThrow(illegalState(project))
                .is(BooleanProjectConfig.REJECT_IMPLICIT_MERGES))
        .isTrue();
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=invalid")
  public void invalidDefaultForBooleanProjectConfigIsIgnored() throws Exception {
    assertThat(
            projectCache
                .get(project)
                .orElseThrow(illegalState(project))
                .is(BooleanProjectConfig.REJECT_IMPLICIT_MERGES))
        .isFalse();

    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateProject(
              b ->
                  b.setBooleanConfig(
                      BooleanProjectConfig.REJECT_IMPLICIT_MERGES, InheritableBoolean.TRUE));
      u.save();
    }
    assertThat(
            projectCache
                .get(project)
                .orElseThrow(illegalState(project))
                .is(BooleanProjectConfig.REJECT_IMPLICIT_MERGES))
        .isTrue();
  }

  @Test
  @GerritConfig(
      name = "repository.*.defaultConfig",
      values = {"receive.rejectImplicitMerges=true", "receive.rejectImplicitMerges=false"})
  public void firstConfigureDefaultForBooleanProjectConfigApplies() throws Exception {
    assertThat(
            projectCache
                .get(project)
                .orElseThrow(illegalState(project))
                .is(BooleanProjectConfig.REJECT_IMPLICIT_MERGES))
        .isTrue();
  }

  private void testChangingCopyCondition(
      String initialCopyCondition, @Nullable String newCopyCondition) throws Exception {
    testChangingCopyCondition(
        initialCopyCondition, newCopyCondition, /* type= */ null, /* message= */ null);
  }

  private void testChangingCopyConditionExpectError(
      String initialCopyCondition, @Nullable String newCopyCondition, String errorMessage)
      throws Exception {
    testChangingCopyCondition(
        initialCopyCondition, newCopyCondition, ValidationMessage.Type.ERROR, errorMessage);
  }

  private void testChangingCopyConditionExpectWarning(
      String initialCopyCondition, @Nullable String newCopyCondition, String warningMessage)
      throws Exception {
    testChangingCopyCondition(
        initialCopyCondition, newCopyCondition, ValidationMessage.Type.WARNING, warningMessage);
  }

  private void testChangingCopyCondition(
      @Nullable String initialCopyCondition,
      @Nullable String newCopyCondition,
      @Nullable ValidationMessage.Type type,
      @Nullable String message)
      throws Exception {
    if (initialCopyCondition != null) {
      try (TestRepository<Repository> testRepo =
          new TestRepository<>(repoManager.openRepository(project))) {
        testRepo
            .branch(RefNames.REFS_CONFIG)
            .commit()
            .add(
                ProjectConfig.PROJECT_CONFIG,
                String.format(
                    "[label \"Foo\"]\n  %s = %s\n",
                    ProjectConfig.KEY_COPY_CONDITION, initialCopyCondition))
            .parent(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
            .create();
      }
    }

    fetchRefsMetaConfig();
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test Change",
            ProjectConfig.PROJECT_CONFIG,
            newCopyCondition == null
                ? "[label \"Foo\"]\n"
                : String.format(
                    "[label \"Foo\"]\n  %s = %s\n",
                    ProjectConfig.KEY_COPY_CONDITION, newCopyCondition));
    PushOneCommit.Result r = push.to(RefNames.REFS_CONFIG);
    if (!ValidationMessage.Type.ERROR.equals(type)) {
      r.assertOkStatus();
      return;
    }
    r.assertErrorStatus(
        String.format(
            "invalid %s file in revision %s", ProjectConfig.PROJECT_CONFIG, r.getCommit().name()));
    if (message != null) {
      r.assertMessage(message);
    }
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
