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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

/**
 * Integration test to verify that change-no-longer-submittable emails are sent when a change
 * becomes not submittable, and that they are sent only in this case (and not when the change
 * becomes submittable or stays submittable/unsubmittable).
 */
public class ChangeNoLongerSubmittableIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void postReview_changeBecomesUnsubmittable_approvalRemoved() throws Exception {
    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Revoke the approval
    requestScopeOperations.setApiUser(approver.id());
    sender.clear();
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.noScore());

    // Verify the email notifications that have been sent for removing the approval.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has posted comments on this change by %s.",
                admin.fullName(), user.fullName(), approver.fullName(), admin.fullName()));
    assertThat(message.body())
        .contains("The change is no longer submittable: Code-Review is unsatisfied now.\n");
    assertThat(message.htmlBody())
        .contains("<p>The change is no longer submittable: Code-Review is unsatisfied now.</p>");
  }

  @Test
  public void postReview_changeBecomesUnsubmittable_approvalDowngraded() throws Exception {
    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Downgrade the approval
    requestScopeOperations.setApiUser(approver.id());
    sender.clear();
    recommend(r.getChangeId());

    // Verify the email notification that has been sent for downgrading the approval.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has posted comments on this change by %s.",
                admin.fullName(), user.fullName(), approver.fullName(), admin.fullName()));
    assertThat(message.body())
        .contains("The change is no longer submittable: Code-Review is unsatisfied now.\n");
    assertThat(message.htmlBody())
        .contains("<p>The change is no longer submittable: Code-Review is unsatisfied now.</p>");
  }

  @Test
  public void postReview_changeBecomesUnsubmittable_vetoApplied() throws Exception {
    // Allow all users to approve and veto.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Apply veto by another user.
    TestAccount approver2 = accountCreator.user2();
    sender.clear();
    requestScopeOperations.setApiUser(approver2.id());
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.reject());

    // Verify the email notification that has been sent for adding the veto.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has posted comments on this change by %s.",
                admin.fullName(), user.fullName(), approver.fullName(), admin.fullName()));
    assertThat(message.body())
        .contains("The change is no longer submittable: Code-Review is unsatisfied now.\n");
    assertThat(message.htmlBody())
        .contains("<p>The change is no longer submittable: Code-Review is unsatisfied now.</p>");
  }

  @Test
  public void postReview_changeBecomesUnsubmittable_multipleSubmitRequirementsNoLongerSatisfied()
      throws Exception {
    // Create a Verify, a Foo-Bar and a Bar-Baz label and allow voting on it.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(verified.build());

      LabelType.Builder fooBar =
          labelBuilder("Foo-Bar", value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(fooBar.build());

      LabelType.Builder barBaz =
          labelBuilder("Bar-Baz", value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(barBaz.build());

      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(LabelId.VERIFIED)
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .add(
            allowLabel("Foo-Bar")
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .add(
            allowLabel("Bar-Baz")
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve all labels.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.VERIFIED, 1));
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().label("Foo-Bar", 1));
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().label("Bar-Baz", 1));
    requestScopeOperations.setApiUser(admin.id());

    // Revoke several approval
    requestScopeOperations.setApiUser(approver.id());
    sender.clear();
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label("Code-Review", 0).label("Foo-Bar", 0).label("Verified", 0));

    // Verify the email notification that have been sent for removing the approval.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has posted comments on this change by %s.",
                admin.fullName(), user.fullName(), approver.fullName(), admin.fullName()));
    assertThat(message.body())
        .contains(
            "The change is no longer submittable:"
                + " Code-Review, Foo-Bar and Verified are unsatisfied now.\n");
    assertThat(message.htmlBody())
        .contains(
            "<p>The change is no longer submittable:"
                + " Code-Review, Foo-Bar and Verified are unsatisfied now.</p>");
  }

  @Test
  public void postReview_changeStaysSubmittable_approvalRemoved() throws Exception {
    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve the change by 2 users.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    TestAccount approver2 =
        accountCreator.create("user3", "user3@email.com", "User3", /* displayName= */ null);
    requestScopeOperations.setApiUser(approver2.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Revoke one approval.
    requestScopeOperations.setApiUser(approver.id());
    sender.clear();
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.noScore());

    // Verify the email notification.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has posted comments on this change by %s.",
                admin.fullName(), user.fullName(), approver.fullName(), admin.fullName()));
    assertThat(message.body()).doesNotContain("The change is no longer submittable");
    assertThat(message.htmlBody()).doesNotContain("The change is no longer submittable");
  }

  @Test
  public void postReview_changeStaysSubmittable_approvalDowngraded() throws Exception {
    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve the change by 2 users.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    TestAccount approver2 =
        accountCreator.create("user3", "user3@email.com", "User3", /* displayName= */ null);
    requestScopeOperations.setApiUser(approver2.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Downgrade one approval
    requestScopeOperations.setApiUser(approver.id());
    sender.clear();
    recommend(r.getChangeId());

    // Verify the email notification.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has posted comments on this change by %s.",
                admin.fullName(), user.fullName(), approver.fullName(), admin.fullName()));
    assertThat(message.body()).doesNotContain("The change is no longer submittable");
    assertThat(message.htmlBody()).doesNotContain("The change is no longer submittable");
  }

  @Test
  public void postReview_changeStaysUnsubmittable_approvalAdded() throws Exception {
    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Add a vote that doesn't make the change submittable.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    sender.clear();
    recommend(r.getChangeId());

    // Verify the email notification.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has posted comments on this change by %s.",
                admin.fullName(), user.fullName(), approver.fullName(), admin.fullName()));
    assertThat(message.body()).doesNotContain("The change is no longer submittable");
    assertThat(message.htmlBody()).doesNotContain("The change is no longer submittable");
  }

  @Test
  public void postReview_changeBecomesSubmittable_approvalAdded() throws Exception {
    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Add a vote that makes the change submittable.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    sender.clear();
    approve(r.getChangeId());

    // Verify the email notification.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has posted comments on this change by %s.",
                admin.fullName(), user.fullName(), approver.fullName(), admin.fullName()));
    assertThat(message.body()).doesNotContain("The change is no longer submittable");
    assertThat(message.htmlBody()).doesNotContain("The change is no longer submittable");
  }

  @Test
  public void pushNewPatchSet_changeBecomesUnsubmittable_approvalNotCopied() throws Exception {
    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Upload a new patch set that removes the approval.
    TestAccount uploaderPs3 =
        accountCreator.create("user3", "user3@email.com", "User3", /* displayName= */ null);
    sender.clear();
    r = amendChangeWithUploader(r, project, uploaderPs3);
    r.assertOkStatus();

    r.assertMessage(
        "The following approvals got outdated and were removed:\n* Code-Review+2 by user2\n");

    // Verify the email notification that has been sent for uploading the new patch set.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s, %s.\n"
                    + "\n"
                    + "%s has uploaded a new patch set (#3) to the change originally created by %s.",
                admin.fullName(),
                user.fullName(),
                approver.fullName(),
                uploaderPs3.fullName(),
                admin.fullName()));
    assertThat(message.body())
        .contains("The change is no longer submittable: Code-Review is unsatisfied now.\n");
    assertThat(message.htmlBody())
        .contains("<p>The change is no longer submittable: Code-Review is unsatisfied now.</p>");
  }

  @Test
  public void pushNewPatchSet_changeBecomesUnsubmittable_approvalCopiedAndRevoked()
      throws Exception {
    // Make Code-Review approvals sticky.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder codeReview =
          labelBuilder(
              LabelId.CODE_REVIEW,
              value(2, "Looks good to me, approved"),
              value(1, "Looks good to me, but someone else must approve"),
              value(0, "No score"),
              value(-1, "I would prefer this is not submitted as is"),
              value(-2, "This shall not be submitted"));
      codeReview.setCopyCondition("is:MAX");
      u.getConfig().upsertLabelType(codeReview.build());
      u.save();
    }

    // Allow all users to veto and approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Upload a new patch set that copies the approval, but revoke it on push.
    sender.clear();
    TestRepository<InMemoryRepository> repo = cloneProject(project, approver);
    GitUtil.fetch(repo, "refs/*:refs/*");
    repo.reset(r.getCommit());
    r =
        amendChange(
            r.getChangeId(),
            "refs/for/master%l=-Code-Review",
            approver,
            repo,
            "new subject",
            "new file",
            "new content");
    r.assertOkStatus();

    // Verify the email notification that has been sent for uploading the new patch set.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has uploaded a new patch set (#3) to the change originally created by %s.",
                admin.fullName(), user.fullName(), approver.fullName(), admin.fullName()));
    assertThat(message.body())
        .contains("The change is no longer submittable: Code-Review is unsatisfied now.\n");
    assertThat(message.htmlBody())
        .contains("<p>The change is no longer submittable: Code-Review is unsatisfied now.</p>");
  }

  @Test
  public void pushNewPatchSet_changeBecomesUnsubmittable_approvalCopiedAndDowngraded()
      throws Exception {
    // Make Code-Review approvals sticky.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder codeReview =
          labelBuilder(
              LabelId.CODE_REVIEW,
              value(2, "Looks good to me, approved"),
              value(1, "Looks good to me, but someone else must approve"),
              value(0, "No score"),
              value(-1, "I would prefer this is not submitted as is"),
              value(-2, "This shall not be submitted"));
      codeReview.setCopyCondition("is:MAX");
      u.getConfig().upsertLabelType(codeReview.build());
      u.save();
    }

    // Allow all users to veto and approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Upload a new patch set that copies the approval, but downgrade it on push.
    sender.clear();
    TestRepository<InMemoryRepository> repo = cloneProject(project, approver);
    GitUtil.fetch(repo, "refs/*:refs/*");
    repo.reset(r.getCommit());
    r =
        amendChange(
            r.getChangeId(),
            "refs/for/master%l=Code-Review+1",
            approver,
            repo,
            "new subject",
            "new file",
            "new content");
    r.assertOkStatus();

    // Verify the email notification that has been sent for uploading the new patch set.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has uploaded a new patch set (#3) to the change originally created by %s.",
                admin.fullName(), user.fullName(), approver.fullName(), admin.fullName()));
    assertThat(message.body())
        .contains("The change is no longer submittable: Code-Review is unsatisfied now.\n");
    assertThat(message.htmlBody())
        .contains("<p>The change is no longer submittable: Code-Review is unsatisfied now.</p>");
  }

  @Test
  public void pushNewPatchSet_changeBecomesUnsubmittable_approvalCopiedVetoApplied()
      throws Exception {
    // Make Code-Review approvals sticky.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder codeReview =
          labelBuilder(
              LabelId.CODE_REVIEW,
              value(2, "Looks good to me, approved"),
              value(1, "Looks good to me, but someone else must approve"),
              value(0, "No score"),
              value(-1, "I would prefer this is not submitted as is"),
              value(-2, "This shall not be submitted"));
      codeReview.setCopyCondition("is:MAX");
      u.getConfig().upsertLabelType(codeReview.build());
      u.save();
    }

    // Allow all users to veto and approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Upload a new patch set that copies the approval, but apply a new veto on push.
    TestAccount uploaderPs3 =
        accountCreator.create("user3", "user3@email.com", "User3", /* displayName= */ null);
    sender.clear();
    TestRepository<InMemoryRepository> repo = cloneProject(project, uploaderPs3);
    GitUtil.fetch(repo, "refs/*:refs/*");
    repo.reset(r.getCommit());
    r =
        amendChange(
            r.getChangeId(),
            "refs/for/master%l=Code-Review-2",
            uploaderPs3,
            repo,
            "new subject",
            "new file",
            "new content");
    r.assertOkStatus();

    // Verify the email notification that has been sent for uploading the new patch set.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s, %s.\n"
                    + "\n"
                    + "%s has uploaded a new patch set (#3) to the change originally created by %s.",
                admin.fullName(),
                user.fullName(),
                uploaderPs3.fullName(),
                uploaderPs3.fullName(),
                admin.fullName()));
    assertThat(message.body())
        .contains("The change is no longer submittable: Code-Review is unsatisfied now.\n");
    assertThat(message.htmlBody())
        .contains("<p>The change is no longer submittable: Code-Review is unsatisfied now.</p>");
  }

  @Test
  public void pushNewPatchSet_changeStaysSubmittable_approvalCopied() throws Exception {
    // Make Code-Review approvals sticky.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder codeReview =
          labelBuilder(
              LabelId.CODE_REVIEW,
              value(2, "Looks good to me, approved"),
              value(1, "Looks good to me, but someone else must approve"),
              value(0, "No score"),
              value(-1, "I would prefer this is not submitted as is"),
              value(-2, "This shall not be submitted"));
      codeReview.setCopyCondition("is:MAX");
      u.getConfig().upsertLabelType(codeReview.build());
      u.save();
    }

    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Upload a new patch set, the approval is copied.
    TestAccount uploaderPs3 =
        accountCreator.create("user3", "user3@email.com", "User3", /* displayName= */ null);
    sender.clear();
    r = amendChangeWithUploader(r, project, uploaderPs3);
    r.assertOkStatus();

    // Verify the email notification that has been sent for uploading the new patch set.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has uploaded a new patch set (#3) to the change originally created by %s.",
                admin.fullName(), user.fullName(), uploaderPs3.fullName(), admin.fullName()));
    assertThat(message.body()).doesNotContain("The change is no longer submittable");
    assertThat(message.htmlBody()).doesNotContain("The change is no longer submittable");
  }

  @Test
  public void pushNewPatchSet_changeStaysSubmittable_approvalReapplied() throws Exception {
    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Upload a new patch set that removes the approval, but re-apply a new approval on push.
    TestAccount uploaderPs3 =
        accountCreator.create("user3", "user3@email.com", "User3", /* displayName= */ null);
    sender.clear();
    TestRepository<InMemoryRepository> repo = cloneProject(project, uploaderPs3);
    GitUtil.fetch(repo, "refs/*:refs/*");
    repo.reset(r.getCommit());
    r =
        amendChange(
            r.getChangeId(),
            "refs/for/master%l=Code-Review+2",
            uploaderPs3,
            repo,
            "new subject",
            "new file",
            "new content");
    r.assertOkStatus();

    // Verify the email notification that has been sent for uploading the new patch set.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    // uploaderPs3 gets added to the attention set because this user is a new reviewer on the change
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s, %s, %s.\n"
                    + "\n"
                    + "%s has uploaded a new patch set (#3) to the change originally created by %s.",
                admin.fullName(),
                user.fullName(),
                approver.fullName(),
                uploaderPs3.fullName(),
                uploaderPs3.fullName(),
                admin.fullName()));
    assertThat(message.body()).doesNotContain("The change is no longer submittable");
    assertThat(message.htmlBody()).doesNotContain("The change is no longer submittable");
  }

  @Test
  public void pushNewPatchSet_changeStaysUnsubmittable() throws Exception {
    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Upload a new patch set.
    TestAccount uploaderPs3 =
        accountCreator.create("user3", "user3@email.com", "User3", /* displayName= */ null);
    sender.clear();
    r = amendChangeWithUploader(r, project, uploaderPs3);
    r.assertOkStatus();

    // Verify the email notification that has been sent for uploading the new patch set.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "%s has uploaded a new patch set (#3) to the change originally created by %s.",
                uploaderPs3.fullName(), admin.fullName()));
    assertThat(message.body()).doesNotContain("The change is no longer submittable");
    assertThat(message.htmlBody()).doesNotContain("The change is no longer submittable");
  }

  @Test
  public void pushNewPatchSet_changeBecomesSubmittable() throws Exception {
    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Upload a new patch set and approve it.
    TestAccount uploaderPs3 =
        accountCreator.create("user3", "user3@email.com", "User3", /* displayName= */ null);
    sender.clear();
    TestRepository<InMemoryRepository> repo = cloneProject(project, uploaderPs3);
    GitUtil.fetch(repo, "refs/*:refs/*");
    repo.reset(r.getCommit());
    r =
        amendChange(
            r.getChangeId(),
            "refs/for/master%l=Code-Review+2",
            uploaderPs3,
            repo,
            "new subject",
            "new file",
            "new content");
    r.assertOkStatus();

    // Verify the email notification that has been sent for uploading the new patch set.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    // uploaderPs3 gets added to the attention set because this user is a new reviewer on the change
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s.\n"
                    + "\n"
                    + "%s has uploaded a new patch set (#3) to the change originally created by %s.",
                uploaderPs3.fullName(), uploaderPs3.fullName(), admin.fullName()));
    assertThat(message.body()).doesNotContain("The change is no longer submittable");
    assertThat(message.htmlBody()).doesNotContain("The change is no longer submittable");
  }
}
