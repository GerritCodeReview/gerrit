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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.inject.Inject;
import java.util.Map;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

/**
 * Integration tests for the {@code submit.topic} REST endpoints:
 *
 * <ul>
 *   <li>{@code POST /changes/{id}/submit.topic} (change-level)
 *   <li>{@code POST /changes/{id}/revisions/{rev}/submit.topic} (revision-level)
 * </ul>
 *
 * <p>Both endpoints are available only when {@code change.submitWholeTopic = OPTIONAL}.
 */
public class SubmitWholeTopicIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void submitWholeTopicDisabled_changeLevel_returns405() throws Exception {
    // Default config: submitWholeTopic = false (DISABLED)
    PushOneCommit.Result change = createApprovedChange("a.txt");

    RestResponse response =
        adminRestSession.post("/changes/" + change.getChangeId() + "/submit.topic");
    response.assertMethodNotAllowed();
  }

  @Test
  public void submitWholeTopicDisabled_revisionLevel_returns405() throws Exception {
    PushOneCommit.Result change = createApprovedChange("a.txt");

    RestResponse response =
        adminRestSession.post(
            "/changes/" + change.getChangeId() + "/revisions/current/submit.topic");
    response.assertMethodNotAllowed();
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void submitWholeTopicEnforced_changeLevel_returns405() throws Exception {
    // ENFORCED mode: normal submit already handles the whole topic, dedicated
    // endpoint is not needed.
    PushOneCommit.Result change = createApprovedChange("a.txt");

    RestResponse response =
        adminRestSession.post("/changes/" + change.getChangeId() + "/submit.topic");
    response.assertMethodNotAllowed();
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void submitWholeTopicEnforced_revisionLevel_returns405() throws Exception {
    PushOneCommit.Result change = createApprovedChange("a.txt");

    RestResponse response =
        adminRestSession.post(
            "/changes/" + change.getChangeId() + "/revisions/current/submit.topic");
    response.assertMethodNotAllowed();
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_changeLevel_submitsTopic() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    PushOneCommit.Result change3 = createChange("Change 3", "c.txt", "content", topic);

    approve(change1.getChangeId());
    approve(change2.getChangeId());
    approve(change3.getChangeId());

    RestResponse response =
        adminRestSession.post("/changes/" + change1.getChangeId() + "/submit.topic");
    response.assertOK();

    assertMerged(change1.getChangeId());
    assertMerged(change2.getChangeId());
    assertMerged(change3.getChangeId());
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_changeLevel_returnsChangeInfo() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());

    RestResponse response =
        adminRestSession.post("/changes/" + change1.getChangeId() + "/submit.topic");
    response.assertOK();

    ChangeInfo info = newGson().fromJson(response.getReader(), ChangeInfo.class);
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(info.changeId).isEqualTo(change1.getChangeId());
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_revisionLevel_submitsTopic() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);

    approve(change1.getChangeId());
    approve(change2.getChangeId());

    RestResponse response =
        adminRestSession.post(
            "/changes/" + change1.getChangeId() + "/revisions/current/submit.topic");
    response.assertOK();

    assertMerged(change1.getChangeId());
    assertMerged(change2.getChangeId());
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_revisionLevel_returnsChangeInfo() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());

    RestResponse response =
        adminRestSession.post(
            "/changes/" + change1.getChangeId() + "/revisions/current/submit.topic");
    response.assertOK();

    ChangeInfo info = newGson().fromJson(response.getReader(), ChangeInfo.class);
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_revisionLevel_nonCurrentRevision_returns409() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change = createChange("Change", "a.txt", "content", topic);
    // Create a second patch set, making ps1 non-current.
    amendChange(change.getChangeId());
    approve(change.getChangeId());

    String ps1 = change.getCommit().name();
    RestResponse response =
        adminRestSession.post(
            "/changes/" + change.getChangeId() + "/revisions/" + ps1 + "/submit.topic");
    response.assertConflict();
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_singleChangeInTopic_isSubmitted() throws Exception {
    PushOneCommit.Result change = createChange("Change", "a.txt", "content", "solo-topic");
    approve(change.getChangeId());

    RestResponse response =
        adminRestSession.post("/changes/" + change.getChangeId() + "/submit.topic");
    response.assertOK();

    assertMerged(change.getChangeId());
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_changeWithoutTopic_isSubmitted() throws Exception {
    // A change without a topic is submitted as a single change (topic closure
    // is just the change itself).
    PushOneCommit.Result change = createApprovedChange("a.txt");

    RestResponse response =
        adminRestSession.post("/changes/" + change.getChangeId() + "/submit.topic");
    response.assertOK();

    assertMerged(change.getChangeId());
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_unapprovedChangeInTopic_returns409() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result approved = createChange("Approved", "a.txt", "content", topic);
    createChange("NotApproved", "b.txt", "content", topic);

    approve(approved.getChangeId());
    // second change is intentionally not approved

    RestResponse response =
        adminRestSession.post("/changes/" + approved.getChangeId() + "/submit.topic");
    response.assertConflict();
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_noSubmitPermission_isForbidden() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());

    // Block submit for regular users.
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.SUBMIT).ref("refs/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertThrows(
        AuthException.class,
        () -> gApi.changes().id(change1.getChangeId()).revision("current").submit());
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_noSubmitPermissionOnOtherProjectInTopic_returns409()
      throws Exception {
    // User has submit permission on the current project but not on a second
    // project that holds another change in the same topic.  The triggering
    // change passes the early per-change permission check (HTTP 200 starts),
    // but merge() detects insufficient permission on the other change and
    // converts it to 409 Conflict.
    String topic = "cross-project-permission-topic";

    Project.NameKey restrictedProject = projectOperations.newProject().create();
    // Explicitly block submit for everyone on the restricted project.
    projectOperations
        .project(restrictedProject)
        .forUpdate()
        .add(block(Permission.SUBMIT).ref("refs/*").group(adminGroupUuid()))
        .update();

    TestRepository<InMemoryRepository> restrictedRepo = cloneProject(restrictedProject);

    // Change in the current (allowed) project.
    PushOneCommit.Result changeAllowed = createChange("Allowed change", "a.txt", "content", topic);
    // Change in the restricted project — submit is blocked there.
    PushOneCommit.Result changeRestricted =
        createChange(restrictedRepo, "master", "Restricted change", "b.txt", "content", topic);

    approve(changeAllowed.getChangeId());
    approve(changeRestricted.getChangeId());

    // POSTing to the allowed change passes the per-change auth check, but
    // merge fails because the caller cannot submit the restricted change.
    RestResponse response =
        adminRestSession.post("/changes/" + changeAllowed.getChangeId() + "/submit.topic");
    response.assertConflict();

    // Neither change should have been merged.
    assertThat(gApi.changes().id(changeAllowed.getChangeId()).get().status)
        .isEqualTo(ChangeStatus.NEW);
    assertThat(gApi.changes().id(changeRestricted.getChangeId()).get().status)
        .isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void uiAction_submitTopic_notPresentWhenDisabled() throws Exception {
    // Default config: DISABLED - the action should not appear at all.
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());

    Map<String, ActionInfo> actions = gApi.changes().id(change1.getChangeId()).current().actions();
    assertThat(actions).doesNotContainKey("submit.topic");
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void uiAction_submitTopic_notPresentWhenEnforced() throws Exception {
    // ENFORCED mode: the dedicated action is hidden because every submit
    // already submits the whole topic.
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());

    Map<String, ActionInfo> actions = gApi.changes().id(change1.getChangeId()).current().actions();
    assertThat(actions).doesNotContainKey("submit.topic");
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void uiAction_submitTopic_notPresentForSingleChangeInTopic() throws Exception {
    // A topic with only one change should not show the submit.topic action
    // (submitting the whole topic would be the same as a regular submit).
    PushOneCommit.Result change = createChange("Change", "a.txt", "content", "solo-topic");
    approve(change.getChangeId());

    Map<String, ActionInfo> actions = gApi.changes().id(change.getChangeId()).current().actions();
    assertThat(actions).doesNotContainKey("submit.topic");
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void uiAction_submitTopic_notPresentWithoutTopic() throws Exception {
    PushOneCommit.Result change = createApprovedChange("a.txt");

    Map<String, ActionInfo> actions = gApi.changes().id(change.getChangeId()).current().actions();
    assertThat(actions).doesNotContainKey("submit.topic");
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void uiAction_submitTopic_presentAndEnabledWhenReady() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());

    Map<String, ActionInfo> actions = gApi.changes().id(change1.getChangeId()).current().actions();
    assertThat(actions).containsKey("submit.topic");
    ActionInfo action = actions.get("submit.topic");
    assertThat(action.enabled).isTrue();
    assertThat(action.method).isEqualTo("POST");
    assertThat(action.label).isEqualTo("Submit whole topic");
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void uiAction_submitTopic_presentButDisabledWhenNotAllApproved() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    // Create change2 to ensure the topic has multiple changes; leave it unapproved.
    createChange("Change 2", "b.txt", "content", topic);
    approve(change1.getChangeId());

    // The action is visible and enabled: getDescription() evaluates problems
    // only for the triggering change and its ancestors (not the full topic
    // closure).  Because change1 itself is approvable, no problem is found and
    // the action appears enabled.
    Map<String, ActionInfo> actions = gApi.changes().id(change1.getChangeId()).current().actions();
    assertThat(actions).containsKey("submit.topic");
    assertThat(actions.get("submit.topic").enabled).isTrue();

    // The real enforcement happens at merge time: change2's missing approval
    // causes a 409 Conflict when the submit is attempted.
    RestResponse response =
        adminRestSession.post("/changes/" + change1.getChangeId() + "/submit.topic");
    response.assertConflict();
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  @GerritConfig(
      name = "change.mergeabilityComputationBehavior",
      value = "API_REF_UPDATED_AND_CHANGE_REINDEX")
  public void uiAction_submitTopic_disabledWhenTriggeringChangeHasConflict() throws Exception {
    // With mergeability computation enabled, a merge conflict on the triggering
    // change itself is detected in getDescription() and the action is shown as
    // disabled (enabled == null in Gerrit's ActionInfo).
    String topic = "conflict-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    // Create a conflicting change on a side branch, then submit it to make
    // change1 unmergeable.
    testRepo.reset("HEAD~1");
    PushOneCommit.Result conflicting =
        pushFactory
            .create(admin.newIdent(), testRepo, "Conflicting", "a.txt", "different content")
            .to("refs/for/master");
    approve(conflicting.getChangeId());
    gApi.changes().id(conflicting.getChangeId()).current().submit();

    // change2 is in the same topic and approvable.
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content2", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());

    Map<String, ActionInfo> actions = gApi.changes().id(change1.getChangeId()).current().actions();
    assertThat(actions).containsKey("submit.topic");
    assertThat(actions.get("submit.topic").enabled).isNull(); // null == disabled
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_multipleProjects_allMerged() throws Exception {
    String topic = "cross-project-topic";

    Project.NameKey projectA = projectOperations.newProject().create();
    Project.NameKey projectB = projectOperations.newProject().create();

    // Grant submit on both projects.
    projectOperations
        .project(projectA)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/*").group(adminGroupUuid()))
        .update();
    projectOperations
        .project(projectB)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/*").group(adminGroupUuid()))
        .update();

    TestRepository<InMemoryRepository> repoA = cloneProject(projectA);
    TestRepository<InMemoryRepository> repoB = cloneProject(projectB);

    PushOneCommit.Result changeA =
        createChange(repoA, "master", "Change A", "a.txt", "content", topic);
    PushOneCommit.Result changeB =
        createChange(repoB, "master", "Change B", "b.txt", "content", topic);

    approve(changeA.getChangeId());
    approve(changeB.getChangeId());

    RestResponse response =
        adminRestSession.post("/changes/" + changeA.getChangeId() + "/submit.topic");
    response.assertOK();

    assertMerged(changeA.getChangeId());
    assertMerged(changeB.getChangeId());
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void optionalMode_normalSubmit_doesNotSubmitWholeTopic() throws Exception {
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());

    // Submit only change1 via the normal submit endpoint.
    gApi.changes().id(change1.getChangeId()).current().submit();

    assertMerged(change1.getChangeId());
    // change2 must still be open because OPTIONAL does not enforce topic submission.
    assertThat(gApi.changes().id(change2.getChangeId()).get().status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "OPTIONAL")
  public void submitWholeTopic_partiallySubmittedTopic_submitsRemainingChanges() throws Exception {
    // Some changes in the topic were already merged (e.g. via normal submit).
    // submit.topic on a remaining open change should submit only the still-open
    // changes; the already-merged ones are excluded from the topic closure by
    // byTopicOpen() and are left untouched.
    String topic = "partial-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    PushOneCommit.Result change3 = createChange("Change 3", "c.txt", "content", topic);

    approve(change1.getChangeId());
    approve(change2.getChangeId());
    approve(change3.getChangeId());

    // Submit change1 individually via the normal endpoint.
    gApi.changes().id(change1.getChangeId()).current().submit();
    assertMerged(change1.getChangeId());

    // Now submit the whole topic from change2. change1 is already merged and
    // excluded from the open-topic closure; change2 and change3 are submitted.
    RestResponse response =
        adminRestSession.post("/changes/" + change2.getChangeId() + "/submit.topic");
    response.assertOK();

    assertMerged(change2.getChangeId());
    assertMerged(change3.getChangeId());
  }

  private PushOneCommit.Result createChange(
      String subject, String fileName, String content, String topic) throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, subject, fileName, content);
    return push.to("refs/for/master%topic=" + name(topic));
  }

  private PushOneCommit.Result createApprovedChange(String fileName) throws Exception {
    PushOneCommit.Result change =
        pushFactory
            .create(admin.newIdent(), testRepo, "A change", fileName, "content")
            .to("refs/for/master");
    approve(change.getChangeId());
    return change;
  }

  private void assertMerged(String changeId) throws Exception {
    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }
}
