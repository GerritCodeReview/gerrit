// Copyright (C) 2023 The Android Open Source Project
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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_REAL_USER;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.Optional;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.junit.Test;

public class RebaseOnBehalfOfUploaderIT extends AbstractDaemonTest {
  @Inject private AccountOperations accountOperations;
  @Inject private ChangeOperations changeOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void cannotRebaseOnBehalfOfUploaderWithAllowConflicts() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    rebaseInput.allowConflicts = true;
    BadRequestException exception =
        assertThrows(
            BadRequestException.class, () -> gApi.changes().id(changeId.get()).rebase(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("allow_conflicts and on_behalf_of_uploader are mutually exclusive");
  }

  @Test
  public void cannotRebaseNonCurrentPatchSetOnBehalfOfUploader() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    changeOperations.change(changeId).newPatchset().create();
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(changeId.get()).revision(1).rebase(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("non-current patch set cannot be rebased on behalf of the uploader");
  }

  @Test
  public void cannotRebaseChainOnBehalfOfUploader() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(changeId.get()).rebaseChain(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("rebasing on behalf of the uploader is not supported when rebasing a chain");
  }

  @Test
  public void rebaseChangeOnBehalfOfUploader_withRebasePermission() throws Exception {
    testRebaseChangeOnBehalfOfUploader(
        Permission.REBASE,
        (changeId, rebaseInput) -> gApi.changes().id(changeId.get()).rebase(rebaseInput));
  }

  @Test
  public void rebaseCurrentPatchSetOnBehalfOfUploader_withRebasePermission() throws Exception {
    testRebaseChangeOnBehalfOfUploader(
        Permission.REBASE,
        (changeId, rebaseInput) -> gApi.changes().id(changeId.get()).current().rebase(rebaseInput));
  }

  @Test
  public void rebaseChangeOnBehalfOfUploader_withSubmitPermission() throws Exception {
    testRebaseChangeOnBehalfOfUploader(
        Permission.SUBMIT,
        (changeId, rebaseInput) -> gApi.changes().id(changeId.get()).rebase(rebaseInput));
  }

  @Test
  public void rebaseCurrentPatchSetOnBehalfOfUploader_withSubmitPermission() throws Exception {
    testRebaseChangeOnBehalfOfUploader(
        Permission.SUBMIT,
        (changeId, rebaseInput) -> gApi.changes().id(changeId.get()).current().rebase(rebaseInput));
  }

  private void testRebaseChangeOnBehalfOfUploader(String permissionToAllow, RebaseCall rebaseCall)
      throws Exception {
    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id changeOwner = accountOperations.newAccount().create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Grant permission to rebaser that is required to rebase on behalf of the uploader.
    AccountGroup.UUID allowedGroup =
        groupOperations.newGroup().name("can-" + permissionToAllow).addMember(rebaser).create();
    allowPermission(permissionToAllow, allowedGroup);

    // Block rebase and submit permission for uploader. For rebase on behalf of the uploader only
    // the rebaser needs to have this permission, but not the uploader on whom's behalf the rebase
    // is done.
    AccountGroup.UUID cannotRebaseGroup =
        groupOperations.newGroup().name("cannot-rebase").addMember(uploader).create();
    allowPermission(Permission.REBASE, cannotRebaseGroup);
    blockPermission(Permission.SUBMIT, cannotRebaseGroup);

    // Block push permission for rebaser, as in contrast to rebase, rebase on behalf of the uploader
    // doesn't require the rebaser to have the push permission.
    AccountGroup.UUID cannotUploadGroup =
        groupOperations.newGroup().name("cannot-upload").addMember(rebaser).create();
    blockPermission(Permission.PUSH, cannotUploadGroup);

    // Create two changes both with the same parent
    requestScopeOperations.setApiUser(changeOwner);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(changeOwner).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(changeOwner).create();

    // Create a second patch set for the change that will be rebased so that the uploader is
    // different to the change owner. This is to verify that being change owner doesn't matter for
    // the user on whom's behalf the rebase is done.
    // Set author and committer to the uploader so that rebasing on behalf of the uploader doesn't
    // require the Forge Author and Forge Committer permission.
    changeOperations
        .change(changeToBeRebased)
        .newPatchset()
        .uploader(uploader)
        .author(uploader)
        .committer(uploader)
        .create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Rebase the second change on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    rebaseCall.call(changeToBeRebased, rebaseInput);

    ChangeInfo changeInfo2 = gApi.changes().id(changeToBeRebased.get()).get();
    RevisionInfo currentRevisionInfo = changeInfo2.revisions.get(changeInfo2.currentRevision);
    // The change had 2 patch sets before the rebase, now it should be 3
    assertThat(currentRevisionInfo._number).isEqualTo(3);
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());
    assertThat(currentRevisionInfo.commit.author.email).isEqualTo(uploaderEmail);
    assertThat(currentRevisionInfo.commit.committer.email).isEqualTo(uploaderEmail);

    // Verify that the rebaser was recorded as realUser in NoteDb.
    Optional<FooterLine> realUserFooter =
        projectOperations.project(project).getHead(RefNames.changeMetaRef(changeToBeRebased))
            .getFooterLines().stream()
            .filter(footerLine -> footerLine.matches(FOOTER_REAL_USER))
            .findFirst();
    assertThat(realUserFooter.map(FooterLine::getValue))
        .hasValue(
            String.format(
                "%s <%s>",
                ChangeNoteUtil.getAccountIdAsUsername(rebaser),
                changeNoteUtil.getAccountIdAsEmailAddress(rebaser)));

    // Verify the message that has been posted on the change.
    Collection<ChangeMessageInfo> changeMessages = changeInfo2.messages;
    // Before the rebase the change had 2 messages for the upload of the 2 patch sets. Rebase is
    // expected to add another message.
    assertThat(changeMessages).hasSize(3);
    ChangeMessageInfo changeMessage = Iterables.getLast(changeMessages);
    assertThat(changeMessage.message)
        .isEqualTo(
            "Patch Set 3: Patch Set 2 was rebased on behalf of "
                + AccountTemplateUtil.getAccountTemplate(uploader));
    assertThat(changeMessage.author._accountId).isEqualTo(uploader.get());
    assertThat(changeMessage.realAuthor._accountId).isEqualTo(rebaser.get());
  }

  @Test
  public void rebaseChangeOnBehalfOfUploaderMultipleTimesInARow() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Create two changes both with the same parent.
    requestScopeOperations.setApiUser(uploader);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(uploader).create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Rebase the second change on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput);

    ChangeInfo changeInfo2 = gApi.changes().id(changeToBeRebased.get()).get();
    RevisionInfo currentRevisionInfo = changeInfo2.revisions.get(changeInfo2.currentRevision);
    // The change had 1 patch set before the rebase, now it should be 2
    assertThat(currentRevisionInfo._number).isEqualTo(2);
    assertThat(currentRevisionInfo.commit.committer.email).isEqualTo(uploaderEmail);
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());

    // Create and submit another change so that we can rebase the change once again.
    requestScopeOperations.setApiUser(approver);
    Change.Id changeToBeTheNewBase2 =
        changeOperations.newChange().project(project).owner(uploader).create();
    gApi.changes().id(changeToBeTheNewBase2.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase2.get()).current().submit();

    // Rebase the change once again on behalf of the uploader.
    requestScopeOperations.setApiUser(rebaser);
    gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput);

    changeInfo2 = gApi.changes().id(changeToBeRebased.get()).get();
    currentRevisionInfo = changeInfo2.revisions.get(changeInfo2.currentRevision);
    // The change had 2 patch sets before the rebase, now it should be 3
    assertThat(currentRevisionInfo._number).isEqualTo(3);
    assertThat(currentRevisionInfo.commit.committer.email).isEqualTo(uploaderEmail);
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());

    // Create and submit another change so that we can rebase the change once again.
    requestScopeOperations.setApiUser(approver);
    Change.Id changeToBeTheNewBase3 =
        changeOperations.newChange().project(project).owner(uploader).create();
    gApi.changes().id(changeToBeTheNewBase3.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase3.get()).current().submit();

    // Rebase the change once again on behalf of the uploader, this time by another rebaser.
    Account.Id rebaser2 = accountOperations.newAccount().create();
    requestScopeOperations.setApiUser(rebaser2);
    gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput);

    changeInfo2 = gApi.changes().id(changeToBeRebased.get()).get();
    currentRevisionInfo = changeInfo2.revisions.get(changeInfo2.currentRevision);
    // The change had 3 patch sets before the rebase, now it should be 4
    assertThat(currentRevisionInfo._number).isEqualTo(4);
    assertThat(currentRevisionInfo.commit.committer.email).isEqualTo(uploaderEmail);
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser2.get());
  }

  @Test
  public void nonChangeOwnerWithoutSubmitAndRebasePermissionCannotRebaseOnBehalfOfUploader()
      throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();

    blockPermissionForAllUsers(Permission.REBASE);
    blockPermissionForAllUsers(Permission.SUBMIT);

    Account.Id rebaserId = accountOperations.newAccount().create();
    requestScopeOperations.setApiUser(rebaserId);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    AuthException exception =
        assertThrows(
            AuthException.class, () -> gApi.changes().id(changeId.get()).rebase(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "rebase on behalf of uploader not permitted (change owners and users with the 'Submit'"
                + " or 'Rebase' permission can rebase on behalf of the uploader)");
  }

  @Test
  public void cannotRebaseChangeOnBehalfOfUploaderIfTheUploaderHasNoReadPermission()
      throws Exception {
    String uploaderEmail = "uploader@example.com";
    testCannotRebaseChangeOnBehalfOfUploaderIfTheUploaderHasNoPermission(
        uploaderEmail,
        Permission.READ,
        String.format("uploader %s cannot read change", uploaderEmail));
  }

  @Test
  public void cannotRebaseChangeOnBehalfOfUploaderIfTheUploaderHasNoPushPermission()
      throws Exception {
    String uploaderEmail = "uploader@example.com";
    testCannotRebaseChangeOnBehalfOfUploaderIfTheUploaderHasNoPermission(
        uploaderEmail,
        Permission.PUSH,
        String.format("uploader %s cannot add patch set", uploaderEmail));
  }

  private void testCannotRebaseChangeOnBehalfOfUploaderIfTheUploaderHasNoPermission(
      String uploaderEmail, String permissionToBlock, String expectedErrorMessage)
      throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Create two changes both with the same parent
    requestScopeOperations.setApiUser(uploader);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(uploader).create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Block the required permission for uploader. Without this permission it should not be possible
    // to rebase the change on behalf of the uploader.
    AccountGroup.UUID blockedGroup =
        groupOperations.newGroup().name("cannot-" + permissionToBlock).addMember(uploader).create();
    blockPermission(permissionToBlock, blockedGroup);

    // Try to rebase the second change on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput));
    assertThat(exception).hasMessageThat().isEqualTo(expectedErrorMessage);
  }

  @Test
  public void rebaseChangeOnBehalfOfUploaderWhenUploaderIsNotTheChangeOwner() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    Account.Id changeOwner = accountOperations.newAccount().create();
    Account.Id uploader =
        accountOperations.newAccount().preferredEmail("uploader@example.com").create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Create two changes both with the same parent. Forge the author of the change that will be
    // rebased.
    requestScopeOperations.setApiUser(changeOwner);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(changeOwner).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(changeOwner).create();

    // Create a second patch set for the change that will be rebased so that the uploader is
    // different to the change owner.
    // Set author and committer to the uploader so that rebasing on behalf of the uploader doesn't
    // require the Forge Author and Forge Committer permission.
    changeOperations
        .change(changeToBeRebased)
        .newPatchset()
        .uploader(uploader)
        .author(uploader)
        .committer(uploader)
        .create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Grant add patch set permission for uploader. Without the add patch set permission it is not
    // possible to rebase the change on behalf of the uploader since the uploader cannot add a
    // patch set to a change that is owned by another user.
    AccountGroup.UUID canAddPatchSet =
        groupOperations.newGroup().name("can-add-patch-set").addMember(uploader).create();
    allowPermission(Permission.ADD_PATCH_SET, canAddPatchSet);

    // Rebase the second change on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput);

    ChangeInfo changeInfo2 = gApi.changes().id(changeToBeRebased.get()).get();
    RevisionInfo currentRevisionInfo = changeInfo2.revisions.get(changeInfo2.currentRevision);
    // The change had 2 patch set before the rebase, now it should be 3
    assertThat(currentRevisionInfo._number).isEqualTo(3);
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());
  }

  @Test
  public void
      cannotRebaseChangeOnBehalfOfUploaderWhenUploaderIsNotTheChangeOwnerAndDoesntHaveAddPatchSetPermission()
          throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id changeOwner = accountOperations.newAccount().create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Create two changes both with the same parent. Forge the author of the change that will be
    // rebased.
    requestScopeOperations.setApiUser(changeOwner);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(changeOwner).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(changeOwner).create();

    // Create a second patch set for the change that will be rebased so that the uploader is
    // different to the change owner.
    // Set author and committer to the uploader so that rebasing on behalf of the uploader doesn't
    // require the Forge Author and Forge Committer permission.
    changeOperations
        .change(changeToBeRebased)
        .newPatchset()
        .uploader(uploader)
        .author(uploader)
        .committer(uploader)
        .create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Block add patch set permission for uploader. Without the add patch set permission it should
    // not possible to rebase the change on behalf of the uploader since the uploader cannot add a
    // patch set to a change that is owned by another user.
    AccountGroup.UUID cannotAddPatchSet =
        groupOperations.newGroup().name("cannot-add-patch-set").addMember(uploader).create();
    blockPermission(Permission.ADD_PATCH_SET, cannotAddPatchSet);

    // Try to rebase the second change on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("uploader %s cannot add patch set", uploaderEmail));
  }

  @Test
  public void rebaseChangeWithForgedAuthorOnBehalfOfUploader() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String authorEmail = "author@example.com";
    Account.Id author = accountOperations.newAccount().preferredEmail(authorEmail).create();
    Account.Id uploader =
        accountOperations.newAccount().preferredEmail("uploader@example.com").create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Create two changes both with the same parent. Forge the author of the change that will be
    // rebased.
    requestScopeOperations.setApiUser(uploader);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(uploader).author(author).create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Grant forge author permission for uploader. Without the forge author permission it is not
    // possible to rebase the change on behalf of the uploader.
    AccountGroup.UUID canForgeAuthor =
        groupOperations.newGroup().name("can-forge-author").addMember(uploader).create();
    allowPermission(Permission.FORGE_AUTHOR, canForgeAuthor);

    // Rebase the second change on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput);

    ChangeInfo changeInfo2 = gApi.changes().id(changeToBeRebased.get()).get();
    RevisionInfo currentRevisionInfo = changeInfo2.revisions.get(changeInfo2.currentRevision);
    // The change had 1 patch set before the rebase, now it should be 2
    assertThat(currentRevisionInfo._number).isEqualTo(2);
    assertThat(currentRevisionInfo.commit.author.email).isEqualTo(authorEmail);
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());
  }

  @Test
  public void
      cannotRebaseChangeWithForgedAuthorOnBehalfOfUploaderIfTheUploaderHasNoForgeAuthorPermission()
          throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id author = accountOperations.newAccount().create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Create two changes both with the same parent. Forge the author of the change that will be
    // rebased.
    requestScopeOperations.setApiUser(uploader);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(uploader).author(author).create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Block forge author permission for uploader. Without the forge author permission it should not
    // be possible to rebase the change on behalf of the uploader.
    AccountGroup.UUID cannotForgeAuthor =
        groupOperations.newGroup().name("cannot-forge-author").addMember(uploader).create();
    blockPermission(Permission.FORGE_AUTHOR, cannotForgeAuthor);

    // Try to rebase the second change on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "author of patch set 1 is forged and the uploader %s cannot forge author",
                uploaderEmail));
  }

  @Test
  public void
      rebaseChangeWithForgedCommitterOnBehalfOfUploaderDoesntRequireForgeCommitterPermission()
          throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id committer =
        accountOperations.newAccount().preferredEmail("committer@example.com").create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Create two changes both with the same parent. Forge the committer of the change that will be
    // rebased.
    requestScopeOperations.setApiUser(uploader);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(uploader).committer(committer).create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Rebase the second change on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput);

    ChangeInfo changeInfo2 = gApi.changes().id(changeToBeRebased.get()).get();
    RevisionInfo currentRevisionInfo = changeInfo2.revisions.get(changeInfo2.currentRevision);
    // The change had 1 patch set before the rebase, now it should be 2
    assertThat(currentRevisionInfo._number).isEqualTo(2);
    assertThat(currentRevisionInfo.commit.committer.email).isEqualTo(uploaderEmail);
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());
  }

  @Test
  public void rebaseChangeWithServerIdentOnBehalfOfUploader() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Create two changes both with the same parent. Use the server identity as the author of the
    // change that will be rebased.
    requestScopeOperations.setApiUser(uploader);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .authorIdent(serverIdent.get())
            .create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Grant forge author and forge server permission for uploader. Without these permissions it is
    // not possible to rebase the change on behalf of the uploader.
    AccountGroup.UUID canForgeAuthorAndForgeServer =
        groupOperations
            .newGroup()
            .name("can-forge-author-and-forge-server")
            .addMember(uploader)
            .create();
    allowPermission(Permission.FORGE_AUTHOR, canForgeAuthorAndForgeServer);
    allowPermission(Permission.FORGE_SERVER, canForgeAuthorAndForgeServer);

    // Rebase the second change on behalf of the uploader.
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput);

    ChangeInfo changeInfo2 = gApi.changes().id(changeToBeRebased.get()).get();
    RevisionInfo currentRevisionInfo = changeInfo2.revisions.get(changeInfo2.currentRevision);
    // The change had 1 patch set before the rebase, now it should be 2
    assertThat(currentRevisionInfo._number).isEqualTo(2);
    assertThat(currentRevisionInfo.commit.author.email)
        .isEqualTo(serverIdent.get().getEmailAddress());
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());
  }

  @Test
  public void
      cannotRebaseChangeWithServerIdentOnBehalfOfUploaderIfTheUploaderHasNoForgeServerPermission()
          throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Create two changes both with the same parent. Use the server identity as the author of the
    // change that will be rebased.
    requestScopeOperations.setApiUser(uploader);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .authorIdent(serverIdent.get())
            .create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Grant forge author permission for uploader, but not the forge server permission. Without the
    // forge server permission it is not possible to rebase the change on behalf of the uploader.
    AccountGroup.UUID canForgeAuthor =
        groupOperations.newGroup().name("can-forge-author").addMember(uploader).create();
    allowPermission(Permission.FORGE_AUTHOR, canForgeAuthor);

    // Try to rebase the second change on behalf of the uploader.
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "author of patch set 1 is the server identity and the uploader %s cannot forge"
                    + " the server identity",
                uploaderEmail));
  }

  @Test
  public void rebaseActionEnabled_withRebasePermission() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);
    testRebaseActionEnabled();
  }

  @Test
  public void rebaseActionEnabled_withSubmitPermission() throws Exception {
    allowPermissionToAllUsers(Permission.SUBMIT);
    testRebaseActionEnabled();
  }

  private void testRebaseActionEnabled() throws Exception {
    Account.Id uploader = accountOperations.newAccount().create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Block push permission for rebaser, as in contrast to rebase, rebase on behalf of the uploader
    // doesn't require the rebaser to have the push permission.
    AccountGroup.UUID cannotUploadGroup =
        groupOperations.newGroup().name("cannot-upload").addMember(rebaser).create();
    blockPermission(Permission.PUSH, cannotUploadGroup);

    // Create two changes both with the same parent
    requestScopeOperations.setApiUser(uploader);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(uploader).create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    requestScopeOperations.setApiUser(rebaser);
    ChangeInfo changeInfo = gApi.changes().id(changeToBeRebased.get()).get();
    RevisionInfo revisionInfo = changeInfo.revisions.get(changeInfo.currentRevision);
    assertThat(revisionInfo.actions).containsKey("rebase");
    ActionInfo rebaseActionInfo = revisionInfo.actions.get("rebase");
    assertThat(rebaseActionInfo.enabled).isTrue();

    // rebase is disabled because rebaser doesn't have the 'Push' permission and hence cannot create
    // new patch sets
    assertThat(rebaseActionInfo.options)
        .containsExactly("rebase", false, "rebase_on_behalf_of_uploader", true);
  }

  @Test
  public void rebaseActionEnabled_forChangeOwner() throws Exception {
    Account.Id changeOwner = accountOperations.newAccount().create();
    Account.Id approver = admin.id();

    // Create two changes both with the same parent
    requestScopeOperations.setApiUser(changeOwner);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(changeOwner).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(changeOwner).create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    requestScopeOperations.setApiUser(changeOwner);
    ChangeInfo changeInfo = gApi.changes().id(changeToBeRebased.get()).get();
    RevisionInfo revisionInfo = changeInfo.revisions.get(changeInfo.currentRevision);
    assertThat(revisionInfo.actions).containsKey("rebase");
    ActionInfo rebaseActionInfo = revisionInfo.actions.get("rebase");
    assertThat(rebaseActionInfo.enabled).isTrue();

    // rebase is disabled because change owner has the 'Push' permission and hence can create new
    // patch sets
    assertThat(rebaseActionInfo.options)
        .containsExactly("rebase", true, "rebase_on_behalf_of_uploader", true);
  }

  @UseLocalDisk
  @Test
  public void rebaseChangeOnBehalfOfUploaderRecordsUploaderInRefLog() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Create two changes both with the same parent
    requestScopeOperations.setApiUser(uploader);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(uploader).create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    try (Repository repo = repoManager.openRepository(project)) {
      String changeMetaRef = RefNames.changeMetaRef(changeToBeRebased);
      String patchSetRef = RefNames.patchSetRef(PatchSet.id(changeToBeRebased, 2));
      createRefLogFileIfMissing(repo, changeMetaRef);
      createRefLogFileIfMissing(repo, patchSetRef);

      // Rebase the second change on behalf of the uploader
      requestScopeOperations.setApiUser(rebaser);
      RebaseInput rebaseInput = new RebaseInput();
      rebaseInput.onBehalfOfUploader = true;
      gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput);

      // The ref log for the patch set ref records the impersonated user aka the uploader.
      ReflogEntry patchSetRefLogEntry = repo.getReflogReader(patchSetRef).getLastEntry();
      assertThat(patchSetRefLogEntry.getWho().getEmailAddress()).isEqualTo(uploaderEmail);

      // The ref log for the change meta ref records the impersonated user aka the uploader.
      ReflogEntry changeMetaRefLogEntry = repo.getReflogReader(changeMetaRef).getLastEntry();
      assertThat(changeMetaRefLogEntry.getWho().getEmailAddress()).isEqualTo(uploaderEmail);
    }
  }

  @Test
  public void rebaserCanApproveChangeAfterRebasingOnBehalfOfUploader() throws Exception {
    // Require a Code-Review approval from a non-uploader for submit.
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .upsertSubmitRequirement(
              SubmitRequirement.builder()
                  .setName(TestLabels.codeReview().getName())
                  .setSubmittabilityExpression(
                      SubmitRequirementExpression.create(
                          String.format(
                              "label:%s=MAX,user=non_uploader", TestLabels.codeReview().getName())))
                  .setAllowOverrideInChildProjects(false)
                  .build());
      u.save();
    }

    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Create two changes both with the same parent
    requestScopeOperations.setApiUser(uploader);
    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased =
        changeOperations.newChange().project(project).owner(uploader).create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Rebase it on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput);

    // Approve the change as the rebaser.
    allowVotingOnCodeReviewToAllUsers();
    gApi.changes().id(changeToBeRebased.get()).current().review(ReviewInput.approve());

    // The change is submittable because the approval is from a user (the rebaser) that is not the
    // uploader.
    assertThat(gApi.changes().id(changeToBeRebased.get()).get().submittable).isTrue();

    // Create and submit another change so that we can rebase the change once again.
    requestScopeOperations.setApiUser(approver);
    Change.Id changeToBeTheNewBase2 =
        changeOperations.newChange().project(project).owner(uploader).create();
    gApi.changes().id(changeToBeTheNewBase2.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase2.get()).current().submit();

    // Doing a normal rebase (not on behalf of the uploader) makes the rebaser the uploader. This
    // makse the change non-submittable since the approval of the rebaser is ignored now (due to
    // using 'user=non_uploader' in the submit requirement expression).
    requestScopeOperations.setApiUser(rebaser);
    rebaseInput.onBehalfOfUploader = false;
    gApi.changes().id(changeToBeRebased.get()).rebase(rebaseInput);
    gApi.changes().id(changeToBeRebased.get()).current().review(ReviewInput.approve());
    assertThat(gApi.changes().id(changeToBeRebased.get()).get().submittable).isFalse();
  }

  private void allowPermissionToAllUsers(String permission) {
    allowPermission(permission, REGISTERED_USERS);
  }

  private void allowPermission(String permission, AccountGroup.UUID groupUuid) {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(permission).ref("refs/*").group(groupUuid))
        .update();
  }

  private void allowVotingOnCodeReviewToAllUsers() {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();
  }

  private void blockPermissionForAllUsers(String permission) {
    blockPermission(permission, REGISTERED_USERS);
  }

  private void blockPermission(String permission, AccountGroup.UUID groupUuid) {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(permission).ref("refs/*").group(groupUuid))
        .update();
  }

  @FunctionalInterface
  private interface RebaseCall {
    void call(Change.Id changeId, RebaseInput rebaseInput) throws RestApiException;
  }
}
