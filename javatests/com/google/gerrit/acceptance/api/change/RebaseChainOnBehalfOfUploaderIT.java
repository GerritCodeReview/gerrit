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
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
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
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.junit.Test;

/**
 * Tests for the {@link com.google.gerrit.server.restapi.change.RebaseChain} REST endpoint with the
 * {@link RebaseInput#onBehalfOfUploader} option being set.
 *
 * <p>Rebasing a single change on behalf of the uploader is covered by {@link
 * RebaseOnBehalfOfUploaderIT}.
 */
public class RebaseChainOnBehalfOfUploaderIT extends AbstractDaemonTest {
  @Inject private AccountOperations accountOperations;
  @Inject private ChangeOperations changeOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private TestMetricMaker testMetricMaker;

  @Test
  public void cannotRebaseOnBehalfOfUploaderWithAllowConflicts() throws Exception {
    Account.Id uploader = accountOperations.newAccount().create();
    Change.Id changeId = changeOperations.newChange().owner(uploader).create();
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    rebaseInput.allowConflicts = true;
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(changeId.get()).rebaseChain(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("allow_conflicts and on_behalf_of_uploader are mutually exclusive");
  }

  @Test
  public void rebaseChangeOnBehalfOfUploader_withRebasePermission() throws Exception {
    testRebaseChainOnBehalfOfUploader(Permission.REBASE);
  }

  @Test
  public void rebaseChangeOnBehalfOfUploader_withSubmitPermission() throws Exception {
    testRebaseChainOnBehalfOfUploader(Permission.SUBMIT);
  }

  private void testRebaseChainOnBehalfOfUploader(String permissionToAllow) throws Exception {
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Grant permission to rebaser that is required to rebase on behalf of the uploader.
    AccountGroup.UUID allowedGroup =
        groupOperations.newGroup().name("can-" + permissionToAllow).addMember(rebaser).create();
    allowPermission(permissionToAllow, allowedGroup);

    // Block push permission for rebaser, as in contrast to rebase, rebase on behalf of the uploader
    // doesn't require the rebaser to have the push permission.
    AccountGroup.UUID cannotUploadGroup =
        groupOperations.newGroup().name("cannot-upload").addMember(rebaser).create();
    blockPermission(Permission.PUSH, cannotUploadGroup);

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    // Create a chain of changes for being rebased, each change with a different uploader.
    Account.Id uploader1 =
        accountOperations.newAccount().preferredEmail("uploader1@example.com").create();
    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader1).create();

    Account.Id uploader2 =
        accountOperations.newAccount().preferredEmail("uploader2@example.com").create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .childOf()
            .change(changeToBeRebased1)
            .owner(uploader2)
            .create();

    Account.Id uploader3 =
        accountOperations.newAccount().preferredEmail("uploader3@example.com").create();
    Change.Id changeToBeRebased3 =
        changeOperations
            .newChange()
            .project(project)
            .childOf()
            .change(changeToBeRebased2)
            .owner(uploader3)
            .create();

    Account.Id uploader4 =
        accountOperations.newAccount().preferredEmail("uploader4@example.com").create();
    Change.Id changeToBeRebased4 =
        changeOperations
            .newChange()
            .project(project)
            .childOf()
            .change(changeToBeRebased3)
            .owner(uploader4)
            .create();

    // Block rebase and submit permission for the uploaders. For rebase on behalf of the uploader
    // only
    // the rebaser needs to have these permission, but not the uploaders on whom's behalf the rebase
    // is done.
    AccountGroup.UUID cannotRebaseAndSubmitGroup =
        groupOperations
            .newGroup()
            .name("cannot-rebase")
            .addMember(uploader1)
            .addMember(uploader2)
            .addMember(uploader3)
            .addMember(uploader4)
            .create();
    blockPermission(Permission.REBASE, cannotRebaseAndSubmitGroup);
    blockPermission(Permission.SUBMIT, cannotRebaseAndSubmitGroup);

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Rebase the chain on behalf of the uploaders through changeToBeRebased4
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;

    TestRevisionCreatedListener testRevisionCreatedListener = new TestRevisionCreatedListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(testRevisionCreatedListener)) {
      gApi.changes().id(changeToBeRebased4.get()).rebaseChain(rebaseInput);

      testRevisionCreatedListener.assertUploaders(changeToBeRebased1, uploader1, rebaser);
      testRevisionCreatedListener.assertUploaders(changeToBeRebased2, uploader2, rebaser);
      testRevisionCreatedListener.assertUploaders(changeToBeRebased3, uploader3, rebaser);
      testRevisionCreatedListener.assertUploaders(changeToBeRebased4, uploader4, rebaser);
    }

    assertRebase(changeToBeRebased1, 2, uploader1, rebaser);
    assertRebase(changeToBeRebased2, 2, uploader2, rebaser);
    assertRebase(changeToBeRebased3, 2, uploader3, rebaser);
    assertRebase(changeToBeRebased4, 2, uploader4, rebaser);
  }

  @Test
  public void rebaseChainOnBehalfOfUploaderAfterUpdatingPreferredEmailForUploader()
      throws Exception {
    // Create a chain of changes for being rebased
    String uploaderEmailOne = "uploader1@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmailOne).create();
    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).create();

    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .childOf()
            .change(changeToBeRebased1)
            .owner(uploader)
            .create();

    Change.Id changeToBeRebased3 =
        changeOperations
            .newChange()
            .project(project)
            .childOf()
            .change(changeToBeRebased2)
            .owner(uploader)
            .create();

    // Change preferred email for the uploader
    String uploaderEmailTwo = "uploader2@example.com";
    accountOperations.account(uploader).forUpdate().preferredEmail(uploaderEmailTwo).update();

    // Create, approve and submit the change that will be the new base for the chain that will be
    // rebased
    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Rebase the chain on behalf of the uploader through changeToBeRebased3
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased3.get()).rebaseChain(rebaseInput);
    assertThat(
            gApi.changes()
                .id(changeToBeRebased1.get())
                .get()
                .getCurrentRevision()
                .commit
                .committer
                .email)
        .isEqualTo(uploaderEmailOne);
    assertThat(
            gApi.changes()
                .id(changeToBeRebased2.get())
                .get()
                .getCurrentRevision()
                .commit
                .committer
                .email)
        .isEqualTo(uploaderEmailOne);
    assertThat(
            gApi.changes()
                .id(changeToBeRebased3.get())
                .get()
                .getCurrentRevision()
                .commit
                .committer
                .email)
        .isEqualTo(uploaderEmailOne);
  }

  @Test
  public void rebaseChainOnBehalfOfUploaderMultipleTimesInARow() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    // Create a chain of changes for being rebased, each change with a different uploader.
    Account.Id uploader1 =
        accountOperations.newAccount().preferredEmail("uploader1@example.com").create();
    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader1).create();

    Account.Id uploader2 =
        accountOperations.newAccount().preferredEmail("uploader2@example.com").create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .childOf()
            .change(changeToBeRebased1)
            .owner(uploader2)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Rebase the chain on behalf of the uploaders.
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);

    assertRebase(changeToBeRebased1, 2, uploader1, rebaser);
    assertRebase(changeToBeRebased2, 2, uploader2, rebaser);

    // Create and submit another change so that we can rebase the chain once again.
    requestScopeOperations.setApiUser(approver);
    Change.Id changeToBeTheNewBase2 = changeOperations.newChange().project(project).create();
    gApi.changes().id(changeToBeTheNewBase2.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase2.get()).current().submit();

    // Rebase the chain once again on behalf of the uploaders.
    requestScopeOperations.setApiUser(rebaser);
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);

    assertRebase(changeToBeRebased1, 3, uploader1, rebaser);
    assertRebase(changeToBeRebased2, 3, uploader2, rebaser);
  }

  @Test
  public void nonChangeOwnerWithoutSubmitAndRebasePermissionCannotRebaseChainOnBehalfOfUploader()
      throws Exception {
    Change.Id changeToBeRebased1 = changeOperations.newChange().project(project).create();
    Change.Id changeToBeRebased2 =
        changeOperations.newChange().project(project).childOf().change(changeToBeRebased1).create();

    blockPermissionForAllUsers(Permission.REBASE);
    blockPermissionForAllUsers(Permission.SUBMIT);

    Account.Id rebaserId = accountOperations.newAccount().create();
    requestScopeOperations.setApiUser(rebaserId);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    AuthException exception =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "rebase on behalf of uploader not permitted (change owners and users with the 'Submit'"
                + " or 'Rebase' permission can rebase on behalf of the uploader)");
  }

  @Test
  public void cannotRebaseChainOnBehalfOfUploaderIfTheUploaderHasNoReadPermission()
      throws Exception {
    String uploaderEmail = "uploader@example.com";
    testCannotRebaseChainOnBehalfOfUploaderIfTheUploaderHasNoPermission(
        uploaderEmail,
        Permission.READ,
        String.format("uploader %s cannot read change", uploaderEmail));
  }

  @Test
  public void cannotRebaseChainOnBehalfOfUploaderIfTheUploaderHasNoPushPermission()
      throws Exception {
    String uploaderEmail = "uploader@example.com";
    testCannotRebaseChainOnBehalfOfUploaderIfTheUploaderHasNoPermission(
        uploaderEmail,
        Permission.PUSH,
        String.format("uploader %s cannot add patch set", uploaderEmail));
  }

  private void testCannotRebaseChainOnBehalfOfUploaderIfTheUploaderHasNoPermission(
      String uploaderEmail, String permissionToBlock, String expectedErrorMessage)
      throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Block the required permission for uploader. Without this permission it should not be possible
    // to rebase the change on behalf of the uploader.
    AccountGroup.UUID blockedGroup =
        groupOperations.newGroup().name("cannot-" + permissionToBlock).addMember(uploader).create();
    blockPermission(permissionToBlock, blockedGroup);

    // Try to rebase the chain on behalf of the uploader.
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("change %s: %s", changeToBeRebased1, expectedErrorMessage));
  }

  @Test
  public void rebaseChainOnBehalfOfYourself() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    Account.Id uploader =
        accountOperations.newAccount().preferredEmail("uploader@example.com").create();
    Account.Id approver = admin.id();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Rebase the chain as uploader on behalf of the uploader
    requestScopeOperations.setApiUser(uploader);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);

    assertRebase(changeToBeRebased1, 2, uploader, /* expectedRealUploader= */ null);
    assertRebase(changeToBeRebased2, 2, uploader, /* expectedRealUploader= */ null);
  }

  @Test
  public void cannotRebaseChangeOnBehalfOfYourselfWithoutPushPermission() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    Account.Id uploader =
        accountOperations.newAccount().preferredEmail("uploader@example.com").create();
    Account.Id approver = admin.id();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Block push for the uploader aka the rebaser. This permission is required for creating the new
    // patch set and if it is blocked we expect the rebase to fail.
    AccountGroup.UUID cannotPushGroup =
        groupOperations.newGroup().name("cannot-push").addMember(uploader).create();
    blockPermission(Permission.PUSH, cannotPushGroup);

    // Rebase the chain as uploader on behalf of the uploader
    requestScopeOperations.setApiUser(uploader);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    AuthException exception =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "rebase not permitted (change owners and users with the 'Submit' or 'Rebase'"
                + " permission can rebase if they have the 'Push' permission)");
  }

  @Test
  public void rebaseChainOnBehalfOfUploaderWhenUploaderIsNotTheChangeOwner() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    Account.Id changeOwner =
        accountOperations.newAccount().preferredEmail("change-owner@example.com").create();
    Account.Id uploader =
        accountOperations.newAccount().preferredEmail("uploader@example.com").create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(changeOwner).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(changeOwner)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Create a second patch set for the second change in the chain that will be rebased so that the
    // uploader is different to the change owner.
    // Set author and committer to the uploader so that rebasing on behalf of the uploader doesn't
    // require the Forge Author and Forge Committer permission.
    changeOperations
        .change(changeToBeRebased2)
        .newPatchset()
        .uploader(uploader)
        .author(uploader)
        .committer(uploader)
        .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Grant add patch set permission for uploader. Without the add patch set permission it is not
    // possible to rebase the change on behalf of the uploader since the uploader cannot add a
    // patch set to a change that is owned by another user.
    AccountGroup.UUID canAddPatchSet =
        groupOperations.newGroup().name("can-add-patch-set").addMember(uploader).create();
    allowPermission(Permission.ADD_PATCH_SET, canAddPatchSet);

    // Rebase the chain on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);

    assertRebase(changeToBeRebased1, 2, changeOwner, rebaser);
    assertRebase(changeToBeRebased2, 3, uploader, rebaser);
  }

  @Test
  public void
      cannotRebaseChainOnBehalfOfUploaderWhenUploaderIsNotTheChangeOwnerAndDoesntHaveAddPatchSetPermission()
          throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id changeOwner = accountOperations.newAccount().create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(changeOwner).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(changeOwner)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Create a second patch set for the second change in the chain that will be rebased so that the
    // uploader is different to the change owner.
    // Set author and committer to the uploader so that rebasing on behalf of the uploader doesn't
    // require the Forge Author and Forge Committer permission.
    changeOperations
        .change(changeToBeRebased2)
        .newPatchset()
        .uploader(uploader)
        .author(uploader)
        .committer(uploader)
        .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Block add patch set permission for uploader. Without the add patch set permission it should
    // not possible to rebase the change on behalf of the uploader since the uploader cannot add a
    // patch set to a change that is owned by another user.
    AccountGroup.UUID cannotAddPatchSet =
        groupOperations.newGroup().name("cannot-add-patch-set").addMember(uploader).create();
    blockPermission(Permission.ADD_PATCH_SET, cannotAddPatchSet);

    // Try to rebase the chain on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "change %s: uploader %s cannot add patch set", changeToBeRebased2, uploaderEmail));
  }

  @Test
  public void rebaseChainWithForgedAuthorOnBehalfOfUploader() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String authorEmail = "author@example.com";
    Account.Id author = accountOperations.newAccount().preferredEmail(authorEmail).create();
    Account.Id uploader =
        accountOperations.newAccount().preferredEmail("uploader@example.com").create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).author(author).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .author(author)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
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
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);

    RevisionInfo currentRevisionInfo =
        gApi.changes().id(changeToBeRebased1.get()).get().getCurrentRevision();
    // The change had 1 patch set before the rebase, now it should be 2
    assertThat(currentRevisionInfo._number).isEqualTo(2);
    assertThat(currentRevisionInfo.commit.author.email).isEqualTo(authorEmail);
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());

    currentRevisionInfo = gApi.changes().id(changeToBeRebased2.get()).get().getCurrentRevision();
    // The change had 1 patch set before the rebase, now it should be 2
    assertThat(currentRevisionInfo._number).isEqualTo(2);
    assertThat(currentRevisionInfo.commit.author.email).isEqualTo(authorEmail);
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());
  }

  @Test
  public void
      cannotRebaseChainWithForgedAuthorOnBehalfOfUploaderIfTheUploaderHasNoForgeAuthorPermission()
          throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id author = accountOperations.newAccount().create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).author(author).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .author(author)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Block forge author permission for uploader. Without the forge author permission it should not
    // be possible to rebase the chain on behalf of the uploader.
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
            () -> gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "change %s: author of patch set 1 is forged and the uploader %s cannot forge author",
                changeToBeRebased1, uploaderEmail));
  }

  @Test
  public void
      rebaseChainWithForgedCommitterOnBehalfOfUploaderDoesntRequireForgeCommitterPermission()
          throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id committer =
        accountOperations.newAccount().preferredEmail("committer@example.com").create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).committer(committer).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .committer(committer)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Rebase the second change on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);

    RevisionInfo currentRevisionInfo =
        gApi.changes().id(changeToBeRebased1.get()).get().getCurrentRevision();
    // The change had 1 patch set before the rebase, now it should be 2
    assertThat(currentRevisionInfo._number).isEqualTo(2);
    assertThat(currentRevisionInfo.commit.committer.email).isEqualTo(uploaderEmail);
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());

    currentRevisionInfo = gApi.changes().id(changeToBeRebased2.get()).get().getCurrentRevision();
    // The change had 1 patch set before the rebase, now it should be 2
    assertThat(currentRevisionInfo._number).isEqualTo(2);
    assertThat(currentRevisionInfo.commit.committer.email).isEqualTo(uploaderEmail);
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());
  }

  @Test
  public void rebaseChainWithServerIdentOnBehalfOfUploader() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .authorIdent(serverIdent.get())
            .create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .authorIdent(serverIdent.get())
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
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

    // Rebase the chain on behalf of the uploader.
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);

    RevisionInfo currentRevisionInfo =
        gApi.changes().id(changeToBeRebased1.get()).get().getCurrentRevision();
    // The change had 1 patch set before the rebase, now it should be 2
    assertThat(currentRevisionInfo._number).isEqualTo(2);
    assertThat(currentRevisionInfo.commit.author.email)
        .isEqualTo(serverIdent.get().getEmailAddress());
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());

    currentRevisionInfo = gApi.changes().id(changeToBeRebased2.get()).get().getCurrentRevision();
    // The change had 1 patch set before the rebase, now it should be 2
    assertThat(currentRevisionInfo._number).isEqualTo(2);
    assertThat(currentRevisionInfo.commit.author.email)
        .isEqualTo(serverIdent.get().getEmailAddress());
    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(uploader.get());
    assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(rebaser.get());
  }

  @Test
  public void
      cannotRebaseChainWithServerIdentOnBehalfOfUploaderIfTheUploaderHasNoForgeServerPermission()
          throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .authorIdent(serverIdent.get())
            .create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .authorIdent(serverIdent.get())
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Grant forge author permission for uploader, but not the forge server permission. Without the
    // forge server permission it is not possible to rebase the change on behalf of the uploader.
    AccountGroup.UUID canForgeAuthor =
        groupOperations.newGroup().name("can-forge-author").addMember(uploader).create();
    allowPermission(Permission.FORGE_AUTHOR, canForgeAuthor);

    // Try to rebase the chain on behalf of the uploader.
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "change %s: author of patch set 1 is the server identity and the uploader %s cannot forge"
                    + " the server identity",
                changeToBeRebased1, uploaderEmail));
  }

  @Test
  public void rebaseChainActionEnabled_withRebasePermission() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);
    testRebaseChainActionEnabled();
  }

  @Test
  public void rebaseChainActionEnabled_withSubmitPermission() throws Exception {
    allowPermissionToAllUsers(Permission.SUBMIT);
    testRebaseChainActionEnabled();
  }

  private void testRebaseChainActionEnabled() throws Exception {
    Account.Id uploader = accountOperations.newAccount().create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    // Block push permission for rebaser, as in contrast to rebase, rebase on behalf of the uploader
    // doesn't require the rebaser to have the push permission.
    AccountGroup.UUID cannotUploadGroup =
        groupOperations.newGroup().name("cannot-upload").addMember(rebaser).create();
    blockPermission(Permission.PUSH, cannotUploadGroup);

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain so that the chain is
    // rebasable.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    requestScopeOperations.setApiUser(rebaser);
    ChangeInfo changeInfo = gApi.changes().id(changeToBeRebased2.get()).get();
    assertThat(changeInfo.actions).containsKey("rebase:chain");
    ActionInfo rebaseActionInfo = changeInfo.actions.get("rebase:chain");
    assertThat(rebaseActionInfo.enabled).isTrue();

    // rebase is disabled because rebaser doesn't have the 'Push' permission and hence cannot create
    // new patch sets
    assertThat(rebaseActionInfo.enabledOptions).containsExactly("rebase_on_behalf_of_uploader");
  }

  @Test
  public void rebaseChainActionEnabled_forChangeOwner() throws Exception {
    Account.Id changeOwner = accountOperations.newAccount().create();
    Account.Id approver = admin.id();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(changeOwner).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(changeOwner)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the change that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    requestScopeOperations.setApiUser(changeOwner);
    ChangeInfo changeInfo = gApi.changes().id(changeToBeRebased2.get()).get();
    assertThat(changeInfo.actions).containsKey("rebase:chain");
    ActionInfo rebaseActionInfo = changeInfo.actions.get("rebase:chain");
    assertThat(rebaseActionInfo.enabled).isTrue();

    // rebase is enabled because change owner has the 'Push' permission and hence can create new
    // patch sets
    assertThat(rebaseActionInfo.enabledOptions)
        .containsExactly("rebase", "rebase_on_behalf_of_uploader");
  }

  @UseLocalDisk
  @Test
  public void rebaseChainWithIdenticalUploadersOnBehalfOfUploaderRecordsUploaderInRefLog()
      throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    String uploaderEmail = "uploader@example.com";
    Account.Id uploader = accountOperations.newAccount().preferredEmail(uploaderEmail).create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).create();

    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    try (Repository repo = repoManager.openRepository(project)) {
      String changeMetaRef1 = RefNames.changeMetaRef(changeToBeRebased1);
      String patchSetRef1 = RefNames.patchSetRef(PatchSet.id(changeToBeRebased1, 2));
      String changeMetaRef2 = RefNames.changeMetaRef(changeToBeRebased2);
      String patchSetRef2 = RefNames.patchSetRef(PatchSet.id(changeToBeRebased2, 2));
      createRefLogFileIfMissing(repo, changeMetaRef1);
      createRefLogFileIfMissing(repo, patchSetRef1);
      createRefLogFileIfMissing(repo, changeMetaRef2);
      createRefLogFileIfMissing(repo, patchSetRef2);

      // Rebase the chain on behalf of the uploader
      requestScopeOperations.setApiUser(rebaser);
      RebaseInput rebaseInput = new RebaseInput();
      rebaseInput.onBehalfOfUploader = true;
      gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);

      // The ref log for the patch set ref records the impersonated user aka the uploader.
      ReflogEntry patchSetRefLogEntry1 = repo.getReflogReader(patchSetRef1).getLastEntry();
      assertThat(patchSetRefLogEntry1.getWho().getEmailAddress()).isEqualTo(uploaderEmail);
      ReflogEntry patchSetRefLogEntry2 = repo.getReflogReader(patchSetRef2).getLastEntry();
      assertThat(patchSetRefLogEntry2.getWho().getEmailAddress()).isEqualTo(uploaderEmail);

      // The ref log for the change meta ref records the impersonated user aka the uploader.
      ReflogEntry changeMetaRefLogEntry1 = repo.getReflogReader(changeMetaRef1).getLastEntry();
      assertThat(changeMetaRefLogEntry1.getWho().getEmailAddress()).isEqualTo(uploaderEmail);
      ReflogEntry changeMetaRefLogEntry2 = repo.getReflogReader(changeMetaRef2).getLastEntry();
      assertThat(changeMetaRefLogEntry2.getWho().getEmailAddress()).isEqualTo(uploaderEmail);
    }
  }

  @UseLocalDisk
  @Test
  public void rebaseChainWithDifferentUploadersOnBehalfOfUploaderRecordsCombinedIdentityInRefLog()
      throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Account.Id uploader1 = accountOperations.newAccount().create();
    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader1).create();

    Account.Id uploader2 = accountOperations.newAccount().create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader2)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    try (Repository repo = repoManager.openRepository(project)) {
      String changeMetaRef1 = RefNames.changeMetaRef(changeToBeRebased1);
      String patchSetRef1 = RefNames.patchSetRef(PatchSet.id(changeToBeRebased1, 2));
      String changeMetaRef2 = RefNames.changeMetaRef(changeToBeRebased2);
      String patchSetRef2 = RefNames.patchSetRef(PatchSet.id(changeToBeRebased2, 2));
      createRefLogFileIfMissing(repo, changeMetaRef1);
      createRefLogFileIfMissing(repo, patchSetRef1);
      createRefLogFileIfMissing(repo, changeMetaRef2);
      createRefLogFileIfMissing(repo, patchSetRef2);

      // Rebase the chain on behalf of the uploader
      requestScopeOperations.setApiUser(rebaser);
      RebaseInput rebaseInput = new RebaseInput();
      rebaseInput.onBehalfOfUploader = true;
      gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);

      String combinedEmail = String.format("account-%s|account-%s@unknown", uploader1, uploader2);

      // The ref log for the patch set ref records the impersonated user aka the uploader.
      ReflogEntry patchSetRefLogEntry1 = repo.getReflogReader(patchSetRef1).getLastEntry();
      assertThat(patchSetRefLogEntry1.getWho().getEmailAddress()).isEqualTo(combinedEmail);
      ReflogEntry patchSetRefLogEntry2 = repo.getReflogReader(patchSetRef2).getLastEntry();
      assertThat(patchSetRefLogEntry2.getWho().getEmailAddress()).isEqualTo(combinedEmail);

      // The ref log for the change meta ref records the impersonated user aka the uploader.
      ReflogEntry changeMetaRefLogEntry1 = repo.getReflogReader(changeMetaRef1).getLastEntry();
      assertThat(changeMetaRefLogEntry1.getWho().getEmailAddress()).isEqualTo(combinedEmail);
      ReflogEntry changeMetaRefLogEntry2 = repo.getReflogReader(changeMetaRef2).getLastEntry();
      assertThat(changeMetaRefLogEntry2.getWho().getEmailAddress()).isEqualTo(combinedEmail);
    }
  }

  @Test
  public void rebaserCanApproveChainAfterRebasingOnBehalfOfUploader() throws Exception {
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

    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().owner(uploader).project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Rebase it on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);

    // Approve the chain as the rebaser.
    allowVotingOnCodeReviewToAllUsers();
    gApi.changes().id(changeToBeRebased1.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeRebased2.get()).current().review(ReviewInput.approve());

    // The chain is submittable because the approval is from a user (the rebaser) that is not the
    // uploader.
    assertThat(gApi.changes().id(changeToBeRebased1.get()).get().submittable).isTrue();
    assertThat(gApi.changes().id(changeToBeRebased2.get()).get().submittable).isTrue();

    // Create and submit another change so that we can rebase the chain once again.
    requestScopeOperations.setApiUser(approver);
    Change.Id changeToBeTheNewBase2 =
        changeOperations.newChange().project(project).owner(uploader).create();
    gApi.changes().id(changeToBeTheNewBase2.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase2.get()).current().submit();

    // Doing a normal rebase (not on behalf of the uploader) makes the rebaser the uploader. This
    // makse the chain non-submittable since the approval of the rebaser is ignored now (due to
    // using 'user=non_uploader' in the submit requirement expression).
    requestScopeOperations.setApiUser(rebaser);
    rebaseInput.onBehalfOfUploader = false;
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);
    gApi.changes().id(changeToBeRebased1.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeRebased2.get()).current().review(ReviewInput.approve());
    assertThat(gApi.changes().id(changeToBeRebased1.get()).get().submittable).isFalse();
    assertThat(gApi.changes().id(changeToBeRebased2.get()).get().submittable).isFalse();
  }

  @Test
  public void testSubmittedWithRebaserApprovalMetric() throws Exception {
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

    Change.Id changeToBeTheNewBase =
        changeOperations.newChange().owner(uploader).project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    testMetricMaker.reset();
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();
    assertThat(testMetricMaker.getCount("change/submitted_with_rebaser_approval")).isEqualTo(0);

    // Rebase it on behalf of the uploader
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);

    // Approve the chain as the rebaser.
    allowVotingOnCodeReviewToAllUsers();
    gApi.changes().id(changeToBeRebased1.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeRebased2.get()).current().review(ReviewInput.approve());

    // The chain is submittable because the approval is from a user (the rebaser) that is not the
    // uploader.
    allowPermissionToAllUsers(Permission.SUBMIT);
    testMetricMaker.reset();
    gApi.changes().id(changeToBeRebased1.get()).current().submit();
    gApi.changes().id(changeToBeRebased2.get()).current().submit();
    assertThat(testMetricMaker.getCount("change/submitted_with_rebaser_approval")).isEqualTo(2);
  }

  @Test
  public void testCountRebasesMetric() throws Exception {
    allowPermissionToAllUsers(Permission.REBASE);

    Account.Id uploader = accountOperations.newAccount().create();
    Account.Id approver = admin.id();
    Account.Id rebaser = accountOperations.newAccount().create();

    Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).create();

    Change.Id changeToBeRebased1 =
        changeOperations.newChange().project(project).owner(uploader).create();
    Change.Id changeToBeRebased2 =
        changeOperations
            .newChange()
            .project(project)
            .owner(uploader)
            .childOf()
            .change(changeToBeRebased1)
            .create();

    // Approve and submit the change that will be the new base for the chain that will be rebased.
    requestScopeOperations.setApiUser(approver);
    gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

    // Rebase it on behalf of the uploader
    testMetricMaker.reset();
    requestScopeOperations.setApiUser(rebaser);
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.onBehalfOfUploader = true;
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);
    // field1 is on_behalf_of_uploader, field2 is rebase_chain, field3 is allow_conflicts
    assertThat(testMetricMaker.getCount("change/count_rebases", true, true, false)).isEqualTo(1);
    assertThat(testMetricMaker.getCount("change/count_rebases", true, false, false)).isEqualTo(0);
    assertThat(testMetricMaker.getCount("change/count_rebases", false, false, false)).isEqualTo(0);
    assertThat(testMetricMaker.getCount("change/count_rebases", false, true, false)).isEqualTo(0);

    // Create and submit another change so that we can rebase the change once again.
    requestScopeOperations.setApiUser(approver);
    Change.Id changeToBeTheNewBase2 =
        changeOperations.newChange().project(project).owner(uploader).create();
    gApi.changes().id(changeToBeTheNewBase2.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeToBeTheNewBase2.get()).current().submit();

    // Rebase the change once again, this time as the uploader.
    // If the uploader sets on_behalf_of_uploader = true, the flag is ignored and a normal rebase is
    // done, hence the metric should count this as a a rebase with on_behalf_of_uploader = false.
    requestScopeOperations.setApiUser(uploader);
    testMetricMaker.reset();
    gApi.changes().id(changeToBeRebased2.get()).rebaseChain(rebaseInput);
    // field1 is on_behalf_of_uploader, field2 is rebase_chain, field3 is allow_conflicts
    assertThat(testMetricMaker.getCount("change/count_rebases", false, true, false)).isEqualTo(1);
    assertThat(testMetricMaker.getCount("change/count_rebases", true, true, false)).isEqualTo(0);
    assertThat(testMetricMaker.getCount("change/count_rebases", false, false, false)).isEqualTo(0);
    assertThat(testMetricMaker.getCount("change/count_rebases", true, false, false)).isEqualTo(0);
  }

  private void assertRebase(
      Change.Id changeId,
      int expectedPatchSetNum,
      Account.Id expectedUploader,
      @Nullable Account.Id expectedRealUploader)
      throws RestApiException {
    assertRebaseRevision(changeId, expectedPatchSetNum, expectedUploader, expectedRealUploader);
    assetRebaseChangeMessage(changeId, expectedPatchSetNum, expectedUploader, expectedRealUploader);
    assertRealUserForChangeUpdate(changeId, expectedRealUploader);
  }

  private void assertRebaseRevision(
      Change.Id changeId,
      int expectedPatchSetNum,
      Account.Id expectedUploader,
      @Nullable Account.Id expectedRealUploader)
      throws RestApiException {
    RevisionInfo currentRevisionInfo = gApi.changes().id(changeId.get()).get().getCurrentRevision();

    assertThat(currentRevisionInfo._number).isEqualTo(expectedPatchSetNum);

    assertThat(currentRevisionInfo.uploader._accountId).isEqualTo(expectedUploader.get());

    if (expectedRealUploader != null) {
      assertThat(currentRevisionInfo.realUploader._accountId).isEqualTo(expectedRealUploader.get());
    } else {
      assertThat(currentRevisionInfo.realUploader).isNull();
    }

    String uploaderEmail = accountOperations.account(expectedUploader).get().preferredEmail().get();
    assertThat(currentRevisionInfo.commit.author.email).isEqualTo(uploaderEmail);
    assertThat(currentRevisionInfo.commit.committer.email).isEqualTo(uploaderEmail);
  }

  private void assetRebaseChangeMessage(
      Change.Id changeId,
      int expectedPatchSetNum,
      Account.Id expectedUploader,
      @Nullable Account.Id expectedRealUploader)
      throws RestApiException {
    Collection<ChangeMessageInfo> changeMessages = gApi.changes().id(changeId.get()).get().messages;

    // Expect 1 change message per patch set.
    assertThat(changeMessages).hasSize(expectedPatchSetNum);

    ChangeMessageInfo changeMessage = Iterables.getLast(changeMessages);
    assertThat(changeMessage.author._accountId).isEqualTo(expectedUploader.get());

    if (expectedRealUploader != null) {
      assertThat(changeMessage.message)
          .isEqualTo(
              String.format(
                  "Patch Set %d: Patch Set %d was rebased on behalf of %s",
                  expectedPatchSetNum,
                  expectedPatchSetNum - 1,
                  AccountTemplateUtil.getAccountTemplate(expectedUploader)));
      assertThat(changeMessage.realAuthor._accountId).isEqualTo(expectedRealUploader.get());
    } else {
      assertThat(changeMessage.message)
          .isEqualTo(
              String.format(
                  "Patch Set %d: Patch Set %d was rebased",
                  expectedPatchSetNum, expectedPatchSetNum - 1));
      assertThat(changeMessage.realAuthor).isNull();
    }
  }

  private void assertRealUserForChangeUpdate(
      Change.Id changeId, @Nullable Account.Id expectedRealUser) {
    Optional<FooterLine> realUserFooter =
        projectOperations.project(project).getHead(RefNames.changeMetaRef(changeId))
            .getFooterLines().stream()
            .filter(footerLine -> footerLine.matches(FOOTER_REAL_USER))
            .findFirst();

    if (expectedRealUser != null) {
      assertThat(realUserFooter.map(FooterLine::getValue))
          .hasValue(
              String.format(
                  "%s <%s>",
                  ChangeNoteUtil.getAccountIdAsUsername(expectedRealUser),
                  changeNoteUtil.getAccountIdAsEmailAddress(expectedRealUser)));
    } else {
      assertThat(realUserFooter).isEmpty();
    }
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

  private static class TestRevisionCreatedListener implements RevisionCreatedListener {
    private Map<Change.Id, RevisionInfo> revisionInfos = new HashMap<>();

    void assertUploaders(
        Change.Id changeId, Account.Id expectedUploader, Account.Id expectedRealUploader) {
      RevisionInfo revisionInfo = revisionInfos.get(changeId);
      assertThat(revisionInfo.uploader._accountId).isEqualTo(expectedUploader.get());
      assertThat(revisionInfo.realUploader._accountId).isEqualTo(expectedRealUploader.get());
    }

    @Override
    public void onRevisionCreated(RevisionCreatedListener.Event event) {
      revisionInfos.put(Change.id(event.getChange()._number), event.getRevision());
    }
  }
}
