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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.inject.Inject;
import java.util.Map;
import org.junit.Test;

/**
 * Tests for the {@code aiReview} change action.
 *
 * <p>The action uses an inverted visibility convention: it is emitted only when aiReview is
 * <em>denied</em> (present, with {@code enabled=false}); when the user is permitted the action is
 * <em>absent</em>, which the frontend treats as allowed. So throughout these tests {@code
 * doesNotContainKey(AI_REVIEW)} asserts "allowed" and a present entry with {@code enabled=false}
 * asserts "denied".
 */
public class AiReviewPermissionIT extends AbstractDaemonTest {

  private static final String AI_REVIEW = "aiReview";

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void aiReviewActionAbsentByDefault() throws Exception {
    String changeId = createChange().getChangeId();

    // All-Projects seeds an explicit grant for Registered Users on
    // refs/heads/*, so a registered caller is permitted and the action is
    // absent (allowed).
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions).doesNotContainKey(AI_REVIEW);
  }

  @Test
  public void aiReviewActionDisabledWhenSeededGrantRemoved() throws Exception {
    String changeId = createChange().getChangeId();

    // Without the seeded grant the registered caller is denied, so the action
    // is present with enabled=false (see the class comment on the inverted
    // absent=allowed / present+disabled=denied convention).
    removeSeededAiReviewGrant();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isFalse();
  }

  @Test
  public void aiReviewActionDisabledWhenNotInGrantedGroup() throws Exception {
    String changeId = createChange().getChangeId();

    // Drop the seeded grant, then grant only the admin group: a non-admin
    // registered user is not covered by any ALLOW rule and is denied.
    removeSeededAiReviewGrant();
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
  public void aiReviewActionAbsentWhenUserNotInDeniedGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(deny(Permission.AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions).doesNotContainKey(AI_REVIEW);
  }

  @Test
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
  public void aiReviewActionAbsentWhenUserNotInBlockedGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions).doesNotContainKey(AI_REVIEW);
  }

  @Test
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
  public void aiReviewActionAbsentWhenAllowForUserGroupAndDenyForOtherGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .add(deny(Permission.AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions).doesNotContainKey(AI_REVIEW);
  }

  @Test
  public void aiReviewActionDisabledForAdminWhenAdminGroupBlocked() throws Exception {
    String changeId = createChange().getChangeId();

    // Admins are not exempt: a BLOCK on the admin group overrides the seeded
    // grant (a bare DENY would only cancel an ALLOW for the same group).
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(admin.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions.get(AI_REVIEW).enabled).isFalse();
  }

  @Test
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
  @GerritConfig(
      name = "experiments.enabled",
      value = ExperimentFeaturesConstants.ALLOW_AI_REVIEW_FOR_REGISTERED_USERS)
  public void aiReviewBlockIgnoredWhenExperimentEnabled() throws Exception {
    String changeId = createChange().getChangeId();

    // A BLOCK would normally deny even against an ALLOW, but the experiment is an
    // unconditional override for identified users: the block is ignored and the
    // action stays absent (allowed).
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions).doesNotContainKey(AI_REVIEW);
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      value = ExperimentFeaturesConstants.ALLOW_AI_REVIEW_FOR_REGISTERED_USERS)
  public void aiReviewActionAbsentForRegisteredUserWhenExperimentEnabledAndNoGrant()
      throws Exception {
    String changeId = createChange().getChangeId();

    // Remove the seeded grant: default-deny would deny, but the experiment lets
    // identified users pass without any rule, so the action is absent (allowed).
    removeSeededAiReviewGrant();

    requestScopeOperations.setApiUser(user.id());
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions).doesNotContainKey(AI_REVIEW);
  }

  private void removeSeededAiReviewGrant() throws Exception {
    projectOperations
        .project(allProjects)
        .forUpdate()
        .remove(permissionKey(Permission.AI_REVIEW).ref("refs/heads/*"))
        .update();
  }
}
