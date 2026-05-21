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
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.inject.Inject;
import java.util.Map;
import org.junit.Test;

public class AiReviewPermissionIT extends AbstractDaemonTest {

  private static final String AI_REVIEW = "aiReview";

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void aiReviewActionAbsentByDefault() throws Exception {
    String changeId = createChange().getChangeId();

    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();

    assertThat(actions).doesNotContainKey(AI_REVIEW);
  }

  @Test
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
}
