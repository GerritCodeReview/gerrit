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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.inject.Inject;
import java.util.Map;
import org.junit.Test;

// TODO(AI review experiment): When UiFeature__enable_ai_chat is removed, revise tests for
// standard default-deny model. DENY-only and BLOCK-only tests assume default-allow behavior and
// will need to be rewritten to include explicit ALLOW rules.
public class AiReviewPermissionIT extends AbstractDaemonTest {

  private static final String AI_REVIEW = "aiReview";

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionEnabledWhenNoRulesConfigured() throws Exception {
    String changeId = createChange().getChangeId();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions).containsKey(AI_REVIEW);
    assertThat(actions.get(AI_REVIEW).enabled).isTrue();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionEnabledWhenUserInGrantedGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isTrue();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionDisabledWhenUserNotInGrantedGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isFalse();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionDisabledWhenUserInDeniedGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(deny(Permission.AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isFalse();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionEnabledWhenUserNotInDeniedGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(deny(Permission.AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isTrue();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionDisabledWhenUserInBlockedGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isFalse();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionEnabledWhenUserNotInBlockedGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isTrue();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionDisabledWhenDenySuppressesAllow() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .add(deny(Permission.AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isFalse();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionDisabledWhenAllowForOtherGroupAndDenyForUserGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .add(deny(Permission.AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isFalse();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionEnabledWhenAllowForUserGroupAndDenyForOtherGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .add(deny(Permission.AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isTrue();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionDisabledForAdminWhenAdminGroupDenied() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(deny(Permission.AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(admin.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isFalse();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"UiFeature__enable_ai_chat"})
  public void aiReviewActionDisabledWhenDenyInheritedFromAllProjects() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(deny(Permission.AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isFalse();
  }

  @Test
  public void aiReviewActionAbsentWhenExperimentDisabled() throws Exception {
    String changeId = createChange().getChangeId();

    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions).doesNotContainKey(AI_REVIEW);
  }
}
