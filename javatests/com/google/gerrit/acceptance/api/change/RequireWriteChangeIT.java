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
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.changes.ChangeIdentifier;
import com.google.gerrit.extensions.api.changes.MoveInput;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.inject.Inject;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

/**
 * Tests for the {@code change.requireWriteChange} project option. When set, every change write
 * requires the {@code Write Change} permission on the change's destination ref. Internal
 * operations, server administrators, and the change owner (on their own changes) are exempt.
 * Enforcement lives in a single guard in {@code BatchUpdate}, with a destination-branch check in
 * {@code Move} because a move relocates the change to a new ref.
 */
public class RequireWriteChangeIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private AccountOperations accountOperations;
  @Inject private ChangeOperations changeOperations;

  @Test
  public void userWithoutWriteChangeCannotWriteWhenEnabled() throws Exception {
    PushOneCommit.Result r = createChange();
    enableRequireWriteChange(project.get());

    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.changes()
                    .id(r.getChangeId())
                    .current()
                    .review(new ReviewInput().message("spam")));
    assertThat(thrown).hasMessageThat().contains("Write Change permission");
  }

  @Test
  public void userWithWriteChangeCanWriteWhenEnabled() throws Exception {
    PushOneCommit.Result r = createChange();
    enableRequireWriteChange(project.get());
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.WRITE_CHANGE).ref("refs/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().message("granted ok"));
    assertMessagePosted(r.getChangeId(), "granted ok");
  }

  @Test
  public void ownerCanWriteOwnChangeWhenEnabled() throws Exception {
    // A change owned by a non-admin user who has no Write Change permission.
    TestRepository<?> userRepo = cloneProject(project, user);
    PushOneCommit.Result r = pushFactory.create(user.newIdent(), userRepo).to("refs/for/master");
    r.assertOkStatus();
    enableRequireWriteChange(project.get());

    requestScopeOperations.setApiUser(user.id());
    // The owner may always write to their own change, even without Write Change.
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().message("owner ok"));
    assertMessagePosted(r.getChangeId(), "owner ok");
  }

  @Test
  public void writesAreUnaffectedWhenDisabled() throws Exception {
    PushOneCommit.Result r = createChange();

    // Default: option off -> an ordinary user can still comment, and the permission is irrelevant.
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().message("disabled ok"));
    assertMessagePosted(r.getChangeId(), "disabled ok");
  }

  @Test
  public void userCanStillReadWhenEnabled() throws Exception {
    PushOneCommit.Result r = createChange();
    enableRequireWriteChange(project.get());

    // Read is never restricted by the write guard.
    requestScopeOperations.setApiUser(user.id());
    assertThat(gApi.changes().id(r.getChangeId()).get().subject).isNotNull();
  }

  @Test
  public void adminIsNeverRestricted() throws Exception {
    // Change owned by a non-admin, so this exercises the admin exemption independently of the
    // owner exemption.
    TestRepository<?> userRepo = cloneProject(project, user);
    PushOneCommit.Result r = pushFactory.create(user.newIdent(), userRepo).to("refs/for/master");
    r.assertOkStatus();
    enableRequireWriteChange(project.get());

    // Default API user is admin: not the owner, no Write Change, yet must still be able to write.
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().message("admin ok"));
    assertMessagePosted(r.getChangeId(), "admin ok");
  }

  @Test
  public void projectOwnerCanWriteWhenEnabled() throws Exception {
    // Make the non-admin user a project owner, with no Write Change permission.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.OWNER).ref("refs/*").group(REGISTERED_USERS))
        .update();
    PushOneCommit.Result r = createChange(); // owned by admin
    r.assertOkStatus();
    enableRequireWriteChange(project.get());

    // A project owner is exempt even without Write Change and even on another user's change.
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().message("project owner ok"));
    assertMessagePosted(r.getChangeId(), "project owner ok");
  }

  @Test
  public void changeEditRequiresWriteChange() throws Exception {
    PushOneCommit.Result r = createChange(); // owned by admin
    r.assertOkStatus();
    // Give the non-owner the ability to edit, so only Write Change is in question.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.ADD_PATCH_SET).ref("refs/*").group(REGISTERED_USERS))
        .update();
    // Control: with the option off, the non-owner can create a change edit.
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(r.getChangeId())
        .edit()
        .modifyCommitMessage("Edited before\n\nChange-Id: " + r.getChangeId() + "\n");

    // Enable the option as admin (writing refs/meta/config), then retry as the non-owner.
    requestScopeOperations.setApiUser(admin.id());
    enableRequireWriteChange(project.get());
    requestScopeOperations.setApiUser(user.id());

    // With the option on and no Write Change, editing another user's change is refused. The edit
    // ref write would otherwise bypass the BatchUpdate guard entirely.
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.changes()
                    .id(r.getChangeId())
                    .edit()
                    .modifyCommitMessage("Edited after\n\nChange-Id: " + r.getChangeId() + "\n"));
    assertThat(thrown).hasMessageThat().contains("edit not permitted");

    // Granting Write Change lets the same non-owner edit again, confirming the earlier denial was
    // the Write Change gate rather than the still-held Add Patch Set permission.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.WRITE_CHANGE).ref("refs/*").group(REGISTERED_USERS))
        .update();
    gApi.changes()
        .id(r.getChangeId())
        .edit()
        .modifyCommitMessage("Edited granted\n\nChange-Id: " + r.getChangeId() + "\n");
  }

  @Test
  public void branchOwnerCanWriteWhenEnabled() throws Exception {
    // Make the non-admin user a branch owner of the change's branch only (not a project owner),
    // with no Write Change permission. Isolates the branch-owner exemption.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.OWNER).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();
    PushOneCommit.Result r = createChange(); // owned by admin
    r.assertOkStatus();
    enableRequireWriteChange(project.get());

    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().message("branch owner ok"));
    assertMessagePosted(r.getChangeId(), "branch owner ok");
  }

  @Test
  public void directPushAutoCloseIsNotBlockedWhenEnabled() throws Exception {
    // A change created through review, owned by the admin.
    PushOneCommit.Result r = createChange();
    r.assertOkStatus();
    String changeId = r.getChangeId();

    // A non-admin who may push directly to the branch, but has no Write Change.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
    enableRequireWriteChange(project.get());

    // As the non-admin (non-owner) user, push the change's own commit straight to the branch, which
    // triggers auto-close of the admin-owned change.
    TestRepository<?> userRepo = cloneProject(project, user);
    userRepo.git().fetch().setRefSpecs(new RefSpec("refs/changes/*:refs/changes/*")).call();
    userRepo.reset(r.getCommit());
    pushHead(userRepo, "refs/heads/master", false);

    // Auto-close bookkeeping must complete: the change is MERGED, not stranded as NEW.
    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void rebaseOnBehalfOfUploaderRequiresWriteChangeForRealUser() throws Exception {
    // Everyone may rebase, so only Write Change is in question.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.REBASE).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    Account.Id uploader = accountOperations.newAccount().create();
    Account.Id rebaser = accountOperations.newAccount().create();

    requestScopeOperations.setApiUser(uploader);
    ChangeIdentifier base = changeOperations.newChange().project(project).owner(uploader).create();
    ChangeIdentifier toRebase =
        changeOperations.newChange().project(project).owner(uploader).create();

    // Submit the base as admin so toRebase actually needs a rebase.
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(base).current().review(ReviewInput.approve());
    gApi.changes().id(base).current().submit();
    enableRequireWriteChange(project.get());

    // The rebaser is not the owner and has no Write Change. Rebasing on behalf of the uploader must
    // not let them ride the uploader's owner exemption.
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput in = new RebaseInput();
    in.onBehalfOfUploader = true;
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.changes().id(toRebase).rebase(in));
    assertThat(thrown).hasMessageThat().contains("Write Change");
  }

  @Test
  public void moveToProtectedBranchRequiresWriteChangeOnDestination() throws Exception {
    BranchNameKey restricted = BranchNameKey.create(project, "restricted");
    createBranch(restricted);
    // Change on master, owned by admin (the attacker is not the owner).
    PushOneCommit.Result r = createChange();
    r.assertOkStatus();

    // Attacker may write changes on master and may abandon + create on the destination, but has no
    // Write Change on the destination branch.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.WRITE_CHANGE).ref("refs/heads/master").group(REGISTERED_USERS))
        .add(allow(Permission.ABANDON).ref("refs/heads/master").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(restricted.branch()).group(REGISTERED_USERS))
        .update();
    enableRequireWriteChange(project.get());

    requestScopeOperations.setApiUser(user.id());
    MoveInput in = new MoveInput();
    in.destinationBranch = restricted.shortName();
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.changes().id(r.getChangeId()).move(in));
    assertThat(thrown).hasMessageThat().contains("Write Change");
  }

  @Test
  public void impersonatedMoveChecksRealUserOnDestination() throws Exception {
    BranchNameKey restricted = BranchNameKey.create(project, "restricted");
    createBranch(restricted);
    PushOneCommit.Result r = createChange();
    r.assertOkStatus();

    // The real requester may run as others and write changes on master and create on the
    // destination, but has no Write Change on the destination branch.
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.RUN_AS).group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.WRITE_CHANGE).ref("refs/heads/master").group(REGISTERED_USERS))
        .add(allow(Permission.ABANDON).ref("refs/heads/master").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(restricted.branch()).group(REGISTERED_USERS))
        .update();
    enableRequireWriteChange(project.get());

    // Act as the non-admin user, impersonating the admin (who would be exempt). The real user must
    // still be checked on the destination branch.
    MoveInput in = new MoveInput();
    in.destinationBranch = restricted.shortName();
    RestResponse res =
        userRestSession.postWithHeaders(
            "/changes/" + r.getChangeId() + "/move", in, runAsHeader(admin.id()));
    res.assertForbidden();
    // Pin the 403 to the Write Change guard, not an unrelated permission failure.
    assertThat(res.getEntityContent()).contains("Write Change");
  }

  @Test
  public void submitRequiresWriteChangeWhenEnabled() throws Exception {
    PushOneCommit.Result r = createChange(); // owned by admin
    r.assertOkStatus();
    // Make the change submittable, then let a non-owner submit but without Write Change.
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
    enableRequireWriteChange(project.get());

    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class, () -> gApi.changes().id(r.getChangeId()).current().submit());
    assertThat(thrown).hasMessageThat().contains("Write Change");
  }

  @Test
  public void moveSucceedsWithWriteChangeOnDestination() throws Exception {
    BranchNameKey restricted = BranchNameKey.create(project, "restricted");
    createBranch(restricted);
    PushOneCommit.Result r = createChange(); // owned by admin
    r.assertOkStatus();
    // The mover holds Write Change on both branches, plus abandon on source and create on dest.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.WRITE_CHANGE).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(Permission.ABANDON).ref("refs/heads/master").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(restricted.branch()).group(REGISTERED_USERS))
        .update();
    enableRequireWriteChange(project.get());

    requestScopeOperations.setApiUser(user.id());
    MoveInput in = new MoveInput();
    in.destinationBranch = restricted.shortName();
    gApi.changes().id(r.getChangeId()).move(in);
    assertThat(gApi.changes().id(r.getChangeId()).get().branch).isEqualTo("restricted");
  }

  private void assertMessagePosted(String changeId, String text) throws Exception {
    boolean found = false;
    for (ChangeMessageInfo m :
        gApi.changes().id(changeId).get(ListChangesOption.MESSAGES).messages) {
      if (m.message.contains(text)) {
        found = true;
      }
    }
    assertThat(found).isTrue();
  }

  private void enableRequireWriteChange(String projectName) throws Exception {
    ConfigInput input = new ConfigInput();
    input.requireWriteChange = InheritableBoolean.TRUE;
    gApi.projects().name(projectName).config(input);
  }

  private static Header runAsHeader(Object user) {
    return new BasicHeader("X-Gerrit-RunAs", user.toString());
  }
}
