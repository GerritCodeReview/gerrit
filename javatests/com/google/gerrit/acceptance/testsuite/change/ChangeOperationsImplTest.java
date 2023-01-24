// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.hasCommit;
import static com.google.gerrit.extensions.common.testing.DiffInfoSubject.assertThat;
import static com.google.gerrit.extensions.restapi.testing.BinaryResultSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.MapSubject.assertThatMap;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.inject.Inject;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

public class ChangeOperationsImplTest extends AbstractDaemonTest {

  @Inject private ChangeOperations changeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private AccountOperations accountOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void changeCanBeCreatedWithoutSpecifyingAnyParameters() throws Exception {
    Change.Id numericChangeId = changeOperations.newChange().create();

    ChangeInfo change = getChangeFromServer(numericChangeId);
    assertThat(change._number).isEqualTo(numericChangeId.get());
    assertThat(change.changeId).isNotEmpty();
  }

  @Test
  public void changeCanBeCreatedEvenWithRequestScopeOfArbitraryUser() throws Exception {
    Account.Id user = accountOperations.newAccount().create();

    requestScopeOperations.setApiUser(user);
    Change.Id numericChangeId = changeOperations.newChange().create();

    ChangeInfo change = getChangeFromServer(numericChangeId);
    assertThat(change._number).isEqualTo(numericChangeId.get());
  }

  @Test
  public void twoChangesWithoutAnyParametersDoNotClash() {
    Change.Id changeId1 = changeOperations.newChange().create();
    Change.Id changeId2 = changeOperations.newChange().create();

    TestChange change1 = changeOperations.change(changeId1).get();
    TestChange change2 = changeOperations.change(changeId2).get();
    assertThat(change1.numericChangeId()).isNotEqualTo(change2.numericChangeId());
    assertThat(change1.changeId()).isNotEqualTo(change2.changeId());
  }

  @Test
  public void twoSubsequentlyCreatedChangesDoNotDependOnEachOther() throws Exception {
    Change.Id changeId1 = changeOperations.newChange().create();
    Change.Id changeId2 = changeOperations.newChange().create();

    ChangeInfo change1 = getChangeFromServer(changeId1);
    ChangeInfo change2 = getChangeFromServer(changeId2);
    CommitInfo currentPatchsetCommit1 = change1.revisions.get(change1.currentRevision).commit;
    CommitInfo currentPatchsetCommit2 = change2.revisions.get(change2.currentRevision).commit;
    assertThat(currentPatchsetCommit1)
        .parents()
        .comparingElementsUsing(hasCommit())
        .doesNotContain(currentPatchsetCommit2.commit);
    assertThat(currentPatchsetCommit2)
        .parents()
        .comparingElementsUsing(hasCommit())
        .doesNotContain(currentPatchsetCommit1.commit);
  }

  @Test
  public void createdChangeHasAtLeastOnePatchset() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThatMap(change.revisions).size().isAtLeast(1);
  }

  @Test
  public void createdChangeIsInSpecifiedProject() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    Change.Id changeId = changeOperations.newChange().project(project).create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.project).isEqualTo(project.get());
  }

  @Test
  public void changeCanBeCreatedInEmptyRepository() throws Exception {
    Project.NameKey project = projectOperations.newProject().noEmptyCommit().create();
    Change.Id changeId = changeOperations.newChange().project(project).create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.project).isEqualTo(project.get());
  }

  @Test
  public void createdChangeHasSpecifiedTargetBranch() throws Exception {
    Project.NameKey project = projectOperations.newProject().branches("test-branch").create();
    Change.Id changeId =
        changeOperations.newChange().project(project).branch("test-branch").create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.branch).isEqualTo("test-branch");
  }

  @Test
  public void createdChangeUsesTipOfTargetBranchAsParentByDefault() throws Exception {
    Project.NameKey project = projectOperations.newProject().branches("test-branch").create();
    ObjectId parentCommitId = projectOperations.project(project).getHead("test-branch").getId();
    Change.Id changeId =
        changeOperations.newChange().project(project).branch("test-branch").create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    assertThat(currentPatchsetCommit)
        .parents()
        .onlyElement()
        .commit()
        .isEqualTo(parentCommitId.name());
  }

  @Test
  public void createdChangeUsesSpecifiedBranchTipAsParent() throws Exception {
    Project.NameKey project = projectOperations.newProject().branches("test-branch").create();

    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .childOf()
            .tipOfBranch("refs/heads/test-branch")
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    ObjectId parentCommitId = projectOperations.project(project).getHead("test-branch").getId();
    assertThat(currentPatchsetCommit)
        .parents()
        .onlyElement()
        .commit()
        .isEqualTo(parentCommitId.name());
  }

  @Test
  public void specifiedParentBranchMayHaveShortName() throws Exception {
    Project.NameKey project = projectOperations.newProject().branches("test-branch").create();

    Change.Id changeId =
        changeOperations.newChange().project(project).childOf().tipOfBranch("test-branch").create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    ObjectId parentCommitId = projectOperations.project(project).getHead("test-branch").getId();
    assertThat(currentPatchsetCommit)
        .parents()
        .onlyElement()
        .commit()
        .isEqualTo(parentCommitId.name());
  }

  @Test
  public void specifiedParentBranchMustExist() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                changeOperations.newChange().childOf().tipOfBranch("not-existing-branch").create());
    assertThat(exception).hasMessageThat().ignoringCase().contains("parent");
  }

  @Test
  public void createdChangeUsesSpecifiedChangeAsParent() throws Exception {
    Change.Id parentChangeId = changeOperations.newChange().create();

    Change.Id changeId = changeOperations.newChange().childOf().change(parentChangeId).create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    ObjectId parentCommitId =
        changeOperations.change(parentChangeId).currentPatchset().get().commitId();
    assertThat(currentPatchsetCommit)
        .parents()
        .onlyElement()
        .commit()
        .isEqualTo(parentCommitId.name());
  }

  @Test
  public void specifiedParentChangeMustExist() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> changeOperations.newChange().childOf().change(Change.id(987654321)).create());
    assertThat(exception).hasMessageThat().ignoringCase().contains("parent");
  }

  @Test
  public void createdChangeUsesSpecifiedPatchsetAsParent() throws Exception {
    Change.Id parentChangeId = changeOperations.newChange().create();
    TestPatchset parentPatchset = changeOperations.change(parentChangeId).currentPatchset().get();

    Change.Id changeId =
        changeOperations.newChange().childOf().patchset(parentPatchset.patchsetId()).create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    assertThat(currentPatchsetCommit)
        .parents()
        .onlyElement()
        .commit()
        .isEqualTo(parentPatchset.commitId().name());
  }

  @Test
  public void changeOfSpecifiedParentPatchsetMustExist() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                changeOperations
                    .newChange()
                    .childOf()
                    .patchset(PatchSet.id(Change.id(987654321), 1))
                    .create());
    assertThat(exception).hasMessageThat().ignoringCase().contains("parent");
  }

  @Test
  public void specifiedParentPatchsetMustExist() {
    Change.Id parentChangeId = changeOperations.newChange().create();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                changeOperations
                    .newChange()
                    .childOf()
                    .patchset(PatchSet.id(parentChangeId, 1000))
                    .create());
    assertThat(exception).hasMessageThat().ignoringCase().contains("parent");
  }

  @Test
  public void createdChangeUsesSpecifiedCommitAsParent() throws Exception {
    // Currently, the easiest way to create a commit is by creating another change.
    Change.Id anotherChangeId = changeOperations.newChange().create();
    ObjectId parentCommitId =
        changeOperations.change(anotherChangeId).currentPatchset().get().commitId();

    Change.Id changeId = changeOperations.newChange().childOf().commit(parentCommitId).create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    assertThat(currentPatchsetCommit)
        .parents()
        .onlyElement()
        .commit()
        .isEqualTo(parentCommitId.name());
  }

  @Test
  public void specifiedParentCommitMustExist() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                changeOperations
                    .newChange()
                    .childOf()
                    .commit(ObjectId.fromString("0123456789012345678901234567890123456789"))
                    .create());
    assertThat(exception).hasMessageThat().ignoringCase().contains("parent");
  }

  @Test
  public void createdChangeUsesSpecifiedChangesInGivenOrderAsParents() throws Exception {
    Change.Id parent1ChangeId = changeOperations.newChange().create();
    Change.Id parent2ChangeId = changeOperations.newChange().create();

    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    ObjectId parent1CommitId =
        changeOperations.change(parent1ChangeId).currentPatchset().get().commitId();
    ObjectId parent2CommitId =
        changeOperations.change(parent2ChangeId).currentPatchset().get().commitId();
    assertThat(currentPatchsetCommit)
        .parents()
        .comparingElementsUsing(hasSha1())
        .containsExactly(parent1CommitId.name(), parent2CommitId.name())
        .inOrder();
  }

  @Test
  public void createdChangeUsesMergedParentsAsBaseCommit() throws Exception {
    Change.Id parent1ChangeId =
        changeOperations.newChange().file("file1").content("Line 1").create();
    Change.Id parent2ChangeId =
        changeOperations.newChange().file("file2").content("Some other content").create();

    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

    PatchSet.Id patchsetId = changeOperations.change(changeId).currentPatchset().get().patchsetId();
    BinaryResult file1Content = getFileContent(changeId, patchsetId, "file1");
    assertThat(file1Content).asString().isEqualTo("Line 1");
    BinaryResult file2Content = getFileContent(changeId, patchsetId, "file2");
    assertThat(file2Content).asString().isEqualTo("Some other content");
  }

  @Test
  public void mergeConflictsOfParentsAreReported() {
    Change.Id parent1ChangeId =
        changeOperations.newChange().file("file1").content("Content 1").create();
    Change.Id parent2ChangeId =
        changeOperations.newChange().file("file1").content("Content 2").create();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                changeOperations
                    .newChange()
                    .mergeOf()
                    .change(parent1ChangeId)
                    .and()
                    .change(parent2ChangeId)
                    .create());

    assertThat(exception).hasMessageThat().ignoringCase().contains("conflict");
  }

  @Test
  public void mergeConflictsCanBeAvoidedByUsingTheFirstParentAsBase() throws Exception {
    Change.Id parent1ChangeId =
        changeOperations.newChange().file("file1").content("Content 1").create();
    Change.Id parent2ChangeId =
        changeOperations.newChange().file("file1").content("Content 2").create();

    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOfButBaseOnFirst()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

    PatchSet.Id patchsetId = changeOperations.change(changeId).currentPatchset().get().patchsetId();
    BinaryResult file1Content = getFileContent(changeId, patchsetId, "file1");
    assertThat(file1Content).asString().isEqualTo("Content 1");
  }

  @Test
  public void createdChangeHasAllParentsEvenWhenBasedOnFirst() throws Exception {
    Change.Id parent1ChangeId = changeOperations.newChange().create();
    Change.Id parent2ChangeId = changeOperations.newChange().create();

    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOfButBaseOnFirst()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    ObjectId parent1CommitId =
        changeOperations.change(parent1ChangeId).currentPatchset().get().commitId();
    ObjectId parent2CommitId =
        changeOperations.change(parent2ChangeId).currentPatchset().get().commitId();
    assertThat(currentPatchsetCommit)
        .parents()
        .comparingElementsUsing(hasSha1())
        .containsExactly(parent1CommitId.name(), parent2CommitId.name())
        .inOrder();
  }

  @Test
  public void automaticMergeOfMoreThanTwoParentsIsNotPossible() {
    Change.Id parent1ChangeId =
        changeOperations.newChange().file("file1").content("Content 1").create();
    Change.Id parent2ChangeId =
        changeOperations.newChange().file("file2").content("Content 2").create();
    Change.Id parent3ChangeId =
        changeOperations.newChange().file("file3").content("Content 3").create();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                changeOperations
                    .newChange()
                    .mergeOf()
                    .change(parent1ChangeId)
                    .followedBy()
                    .change(parent2ChangeId)
                    .and()
                    .change(parent3ChangeId)
                    .create());

    assertThat(exception).hasMessageThat().ignoringCase().contains("conflict");
  }

  @Test
  public void createdChangeCanHaveMoreThanTwoParentsWhenBasedOnFirst() throws Exception {
    Change.Id parent1ChangeId =
        changeOperations.newChange().file("file1").content("Content 1").create();
    Change.Id parent2ChangeId =
        changeOperations.newChange().file("file2").content("Content 2").create();
    Change.Id parent3ChangeId =
        changeOperations.newChange().file("file3").content("Content 3").create();

    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOfButBaseOnFirst()
            .change(parent1ChangeId)
            .followedBy()
            .change(parent2ChangeId)
            .and()
            .change(parent3ChangeId)
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    ObjectId parent1CommitId =
        changeOperations.change(parent1ChangeId).currentPatchset().get().commitId();
    ObjectId parent2CommitId =
        changeOperations.change(parent2ChangeId).currentPatchset().get().commitId();
    ObjectId parent3CommitId =
        changeOperations.change(parent3ChangeId).currentPatchset().get().commitId();
    assertThat(currentPatchsetCommit)
        .parents()
        .comparingElementsUsing(hasSha1())
        .containsExactly(parent1CommitId.name(), parent2CommitId.name(), parent3CommitId.name())
        .inOrder();
  }

  @Test
  public void changeBasedOnParentMayHaveAdditionalFileModifications() throws Exception {
    Change.Id parentChangeId =
        changeOperations
            .newChange()
            .file("file1")
            .content("Content 1")
            .file("file2")
            .content("Content 2")
            .create();

    Change.Id changeId =
        changeOperations
            .newChange()
            .childOf()
            .change(parentChangeId)
            .file("file1")
            .content("Different content")
            .create();

    PatchSet.Id patchsetId = changeOperations.change(changeId).currentPatchset().get().patchsetId();
    BinaryResult file1Content = getFileContent(changeId, patchsetId, "file1");
    assertThat(file1Content).asString().isEqualTo("Different content");
    BinaryResult file2Content = getFileContent(changeId, patchsetId, "file2");
    assertThat(file2Content).asString().isEqualTo("Content 2");
  }

  @Test
  public void changeFromMergedParentsMayHaveAdditionalFileModifications() throws Exception {
    Change.Id parent1ChangeId =
        changeOperations.newChange().file("file1").content("Content 1").create();
    Change.Id parent2ChangeId =
        changeOperations.newChange().file("file2").content("Content 2").create();

    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .file("file1")
            .content("Different content")
            .create();

    PatchSet.Id patchsetId = changeOperations.change(changeId).currentPatchset().get().patchsetId();
    BinaryResult file1Content = getFileContent(changeId, patchsetId, "file1");
    assertThat(file1Content).asString().isEqualTo("Different content");
    BinaryResult file2Content = getFileContent(changeId, patchsetId, "file2");
    assertThat(file2Content).asString().isEqualTo("Content 2");
  }

  @Test
  public void changeBasedOnFirstOfMultipleParentsMayHaveAdditionalFileModifications()
      throws Exception {
    Change.Id parent1ChangeId =
        changeOperations.newChange().file("file1").content("Content 1").create();
    Change.Id parent2ChangeId = changeOperations.newChange().create();

    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOfButBaseOnFirst()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .file("file1")
            .content("Different content")
            .create();

    PatchSet.Id patchsetId = changeOperations.change(changeId).currentPatchset().get().patchsetId();
    BinaryResult file1Content = getFileContent(changeId, patchsetId, "file1");
    assertThat(file1Content).asString().isEqualTo("Different content");
  }

  @Test
  public void createdChangeHasSpecifiedOwner() throws Exception {
    Account.Id changeOwner = accountOperations.newAccount().create();
    Change.Id changeId = changeOperations.newChange().owner(changeOwner).create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.owner._accountId).isEqualTo(changeOwner.get());
  }

  @Test
  public void changeOwnerDoesNotNeedAnyPermissionsForChangeCreation() throws Exception {
    Account.Id changeOwner = accountOperations.newAccount().create();
    Project.NameKey project = projectOperations.newProject().branches("test-branch").create();
    // Remove any read and push permissions which might potentially exist. Without read, users
    // shouldn't be able to do anything. The newly created project should only inherit from
    // All-Projects.
    projectOperations
        .project(project)
        .forUpdate()
        .remove(permissionKey(Permission.READ).ref("refs/heads/test-branch"))
        .remove(permissionKey(Permission.PUSH).ref("refs/heads/test-branch"))
        .update();
    projectOperations
        .allProjectsForUpdate()
        .remove(permissionKey(Permission.READ).ref("refs/heads/test-branch"))
        .remove(permissionKey(Permission.PUSH).ref("refs/heads/test-branch"))
        .update();

    Change.Id changeId =
        changeOperations
            .newChange()
            .owner(changeOwner)
            .branch("test-branch")
            .project(project)
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.owner._accountId).isEqualTo(changeOwner.get());
  }

  @Test
  public void createdChangeHasOwnerAsAuthor() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    ChangeInfo change = getChangeFromServer(changeId);
    TestAccount changeOwner = accountOperations.account(Account.id(change.owner._accountId)).get();
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.author.name).isEqualTo(changeOwner.fullname().get());
    assertThat(revision.commit.author.email).isEqualTo(changeOwner.preferredEmail().get());
  }

  @Test
  public void createdChangeHasSpecifiedOwnerAsAuthor() throws Exception {
    String changeOwnerName = "Change Owner";
    String changeOwnerEmail = "change-owner@example.com";
    Account.Id changeOwner =
        accountOperations
            .newAccount()
            .fullname(changeOwnerName)
            .preferredEmail(changeOwnerEmail)
            .create();
    Change.Id changeId = changeOperations.newChange().owner(changeOwner).create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.owner._accountId).isEqualTo(changeOwner.get());
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.author.name).isEqualTo(changeOwnerName);
    assertThat(revision.commit.author.email).isEqualTo(changeOwnerEmail);
  }

  @Test
  public void createdChangeHasSpecifiedAuthor() throws Exception {
    String authorName = "Author";
    String authorEmail = "author@example.com";
    Account.Id author =
        accountOperations.newAccount().fullname(authorName).preferredEmail(authorEmail).create();
    Change.Id changeId = changeOperations.newChange().author(author).create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.owner._accountId).isNotEqualTo(author.get());
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.author.name).isEqualTo(authorName);
    assertThat(revision.commit.author.email).isEqualTo(authorEmail);
  }

  @Test
  public void createdChangeHasSpecifiedAuthorIdent() throws Exception {
    PersonIdent authorIdent = new PersonIdent("Author", "author@example.com");
    Change.Id changeId = changeOperations.newChange().authorIdent(authorIdent).create();

    ChangeInfo change = getChangeFromServer(changeId);
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.author.name).isEqualTo(authorIdent.getName());
    assertThat(revision.commit.author.email).isEqualTo(authorIdent.getEmailAddress());
  }

  @Test
  public void changeCannotBeCreatedWithAuthorAndAuthorIdent() throws Exception {
    Account.Id author = accountOperations.newAccount().create();
    PersonIdent authorIdent = new PersonIdent("Author", "author@example.com");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> changeOperations.newChange().author(author).authorIdent(authorIdent).create());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("author and authorIdent cannot be set together");
  }

  @Test
  public void createdChangeHasOwnerAsCommitter() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    ChangeInfo change = getChangeFromServer(changeId);
    TestAccount changeOwner = accountOperations.account(Account.id(change.owner._accountId)).get();
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.committer.name).isEqualTo(changeOwner.fullname().get());
    assertThat(revision.commit.committer.email).isEqualTo(changeOwner.preferredEmail().get());
  }

  @Test
  public void createdChangeHasSpecifiedOwnerAsCommitter() throws Exception {
    String changeOwnerName = "Change Owner";
    String changeOwnerEmail = "change-owner@example.com";
    Account.Id changeOwner =
        accountOperations
            .newAccount()
            .fullname(changeOwnerName)
            .preferredEmail(changeOwnerEmail)
            .create();
    Change.Id changeId = changeOperations.newChange().owner(changeOwner).create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.owner._accountId).isEqualTo(changeOwner.get());
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.committer.name).isEqualTo(changeOwnerName);
    assertThat(revision.commit.committer.email).isEqualTo(changeOwnerEmail);
  }

  @Test
  public void createdChangeHasSpecifiedCommitter() throws Exception {
    String committerName = "Committer";
    String committerEmail = "committer@example.com";
    Account.Id committer =
        accountOperations
            .newAccount()
            .fullname(committerName)
            .preferredEmail(committerEmail)
            .create();
    Change.Id changeId = changeOperations.newChange().committer(committer).create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.owner._accountId).isNotEqualTo(committer.get());
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.committer.name).isEqualTo(committerName);
    assertThat(revision.commit.committer.email).isEqualTo(committerEmail);
  }

  @Test
  public void createdChangeHasSpecifiedCommitterIdent() throws Exception {
    PersonIdent committerIdent = new PersonIdent("Committer", "committer@example.com");
    Change.Id changeId = changeOperations.newChange().committerIdent(committerIdent).create();

    ChangeInfo change = getChangeFromServer(changeId);
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.committer.name).isEqualTo(committerIdent.getName());
    assertThat(revision.commit.committer.email).isEqualTo(committerIdent.getEmailAddress());
  }

  @Test
  public void changeCannotBeCreatedWithCommitterAndCommitterIdent() throws Exception {
    Account.Id committer = accountOperations.newAccount().create();
    PersonIdent committerIdent = new PersonIdent("Committer", "committer@example.com");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                changeOperations
                    .newChange()
                    .committer(committer)
                    .committerIdent(committerIdent)
                    .create());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("committer and committerIdent cannot be set together");
  }

  @Test
  public void createdChangeHasSpecifiedTopic() throws Exception {
    Change.Id changeId = changeOperations.newChange().topic("test-topic").create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.topic).isEqualTo("test-topic");
  }

  @Test
  public void createdChangeHasSpecifiedApprovals() throws Exception {
    Change.Id changeId =
        changeOperations.newChange().approvals(ImmutableMap.of("Code-Review", (short) 1)).create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.labels).hasSize(1);
    assertThat(change.labels.get("Code-Review").recommended._accountId)
        .isEqualTo(change.owner._accountId);
  }

  @Test
  public void createdChangeHasSpecifiedCommitMessage() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .commitMessage("Summary line\n\nDetailed description.")
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    assertThat(currentPatchsetCommit).message().startsWith("Summary line\n\nDetailed description.");
  }

  @Test
  public void changeCannotBeCreatedWithoutCommitMessage() {
    assertThrows(
        IllegalStateException.class, () -> changeOperations.newChange().commitMessage("").create());
  }

  @Test
  public void commitMessageOfCreatedChangeAutomaticallyGetsChangeId() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .commitMessage("Summary line\n\nDetailed description.")
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    assertThat(currentPatchsetCommit).message().contains("Change-Id:");
  }

  @Test
  public void changeIdSpecifiedInCommitMessageIsKeptForCreatedChange() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .commitMessage("Summary line\n\nChange-Id: I0123456789012345678901234567890123456789")
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    assertThat(currentPatchsetCommit)
        .message()
        .contains("Change-Id: I0123456789012345678901234567890123456789");
    assertThat(change.changeId).isEqualTo("I0123456789012345678901234567890123456789");
  }

  @Test
  public void createdChangeHasSpecifiedFiles() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .file("file1")
            .content("Line 1")
            .file("path/to/file2.txt")
            .content("Line one")
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    Map<String, FileInfo> files = change.revisions.get(change.currentRevision).files;
    assertThatMap(files).keys().containsExactly("file1", "path/to/file2.txt");
    BinaryResult fileContent1 = gApi.changes().id(changeId.get()).current().file("file1").content();
    assertThat(fileContent1).asString().isEqualTo("Line 1");
    BinaryResult fileContent2 =
        gApi.changes().id(changeId.get()).current().file("path/to/file2.txt").content();
    assertThat(fileContent2).asString().isEqualTo("Line one");
  }

  @Test
  public void existingChangeCanBeCheckedForExistence() {
    Change.Id changeId = changeOperations.newChange().create();

    boolean exists = changeOperations.change(changeId).exists();

    assertThat(exists).isTrue();
  }

  @Test
  public void notExistingChangeCanBeCheckedForExistence() {
    Change.Id changeId = Change.id(123456789);

    boolean exists = changeOperations.change(changeId).exists();

    assertThat(exists).isFalse();
  }

  @Test
  public void retrievingNotExistingChangeFails() {
    Change.Id changeId = Change.id(123456789);
    assertThrows(IllegalStateException.class, () -> changeOperations.change(changeId).get());
  }

  @Test
  public void numericChangeIdOfExistingChangeCanBeRetrieved() {
    Change.Id changeId = changeOperations.newChange().create();

    TestChange change = changeOperations.change(changeId).get();
    assertThat(change.numericChangeId()).isEqualTo(changeId);
  }

  @Test
  public void changeIdOfExistingChangeCanBeRetrieved() {
    Change.Id changeId =
        changeOperations
            .newChange()
            .commitMessage("Summary line\n\nChange-Id: I0123456789012345678901234567890123456789")
            .create();

    TestChange change = changeOperations.change(changeId).get();
    assertThat(change.changeId()).isEqualTo("I0123456789012345678901234567890123456789");
  }

  @Test
  public void currentPatchsetOfExistingChangeCanBeRetrieved() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    TestPatchset patchset = changeOperations.change(changeId).currentPatchset().get();

    ChangeInfo expectedChange = getChangeFromServer(changeId);
    String expectedCommitId = expectedChange.currentRevision;
    int expectedPatchsetNumber = expectedChange.revisions.get(expectedCommitId)._number;
    assertThat(patchset.commitId()).isEqualTo(ObjectId.fromString(expectedCommitId));
    assertThat(patchset.patchsetId()).isEqualTo(PatchSet.id(changeId, expectedPatchsetNumber));
  }

  @Test
  public void earlierPatchsetOfExistingChangeCanBeRetrieved() {
    Change.Id changeId = changeOperations.newChange().create();
    PatchSet.Id earlierPatchsetId =
        changeOperations.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id currentPatchsetId = changeOperations.change(changeId).newPatchset().create();

    TestPatchset earlierPatchset =
        changeOperations.change(changeId).patchset(earlierPatchsetId).get();

    assertThat(earlierPatchset.patchsetId()).isEqualTo(earlierPatchsetId);
    assertThat(earlierPatchset.patchsetId()).isNotEqualTo(currentPatchsetId);
  }

  @Test
  public void newPatchsetCanBeCreatedWithoutSpecifyingAnyParameters() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    ChangeInfo unmodifiedChange = getChangeFromServer(changeId);
    int originalPatchsetCount = unmodifiedChange.revisions.size();

    PatchSet.Id patchsetId = changeOperations.change(changeId).newPatchset().create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThatMap(change.revisions).hasSize(originalPatchsetCount + 1);
    RevisionInfo currentRevision = change.revisions.get(change.currentRevision);
    assertThat(currentRevision._number).isEqualTo(patchsetId.get());
  }

  @Test
  public void newPatchsetIsCopyOfPreviousPatchsetByDefault() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    PatchSet.Id patchsetId = changeOperations.change(changeId).newPatchset().create();

    ChangeInfo change = getChangeFromServer(changeId);
    RevisionInfo patchsetRevision = getRevision(change, patchsetId);
    assertThat(patchsetRevision.kind).isEqualTo(ChangeKind.NO_CHANGE);
  }

  @Test
  public void newPatchsetCanHaveDifferentUploader() throws Exception {
    Account.Id changeOwner = accountOperations.newAccount().create();
    Change.Id changeId = changeOperations.newChange().owner(changeOwner).create();

    ChangeInfo change = getChangeFromServer(changeId);
    RevisionInfo currentPatchsetRevision = change.revisions.get(change.currentRevision);
    assertThat(currentPatchsetRevision.uploader._accountId).isEqualTo(changeOwner.get());

    Account.Id newUploader = accountOperations.newAccount().create();
    changeOperations.change(changeId).newPatchset().uploader(newUploader).create();

    change = getChangeFromServer(changeId);
    currentPatchsetRevision = change.revisions.get(change.currentRevision);
    assertThat(currentPatchsetRevision.uploader._accountId).isEqualTo(newUploader.get());
  }

  @Test
  public void createdPatchsetPreviousAuthorAsAuthor() throws Exception {
    String authorName = "Author";
    String authorEmail = "author@example.com";
    Account.Id author =
        accountOperations.newAccount().fullname(authorName).preferredEmail(authorEmail).create();
    Change.Id changeId = changeOperations.newChange().author(author).create();
    ChangeInfo change = getChangeFromServer(changeId);
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.author.name).isEqualTo(authorName);
    assertThat(revision.commit.author.email).isEqualTo(authorEmail);

    changeOperations.change(changeId).newPatchset().create();
    change = getChangeFromServer(changeId);
    revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.author.name).isEqualTo(authorName);
    assertThat(revision.commit.author.email).isEqualTo(authorEmail);
  }

  @Test
  public void createdPatchsetHasSpecifiedAuthor() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String authorName = "Author";
    String authorEmail = "author@example.com";
    Account.Id author =
        accountOperations.newAccount().fullname(authorName).preferredEmail(authorEmail).create();
    changeOperations.change(changeId).newPatchset().author(author).create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.owner._accountId).isNotEqualTo(author.get());
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.author.name).isEqualTo(authorName);
    assertThat(revision.commit.author.email).isEqualTo(authorEmail);
  }

  @Test
  public void createdPatchsetHasSpecifiedAuthorIdent() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    PersonIdent authorIdent = new PersonIdent("Author", "author@example.com");
    changeOperations.change(changeId).newPatchset().authorIdent(authorIdent).create();

    ChangeInfo change = getChangeFromServer(changeId);
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.author.name).isEqualTo(authorIdent.getName());
    assertThat(revision.commit.author.email).isEqualTo(authorIdent.getEmailAddress());
  }

  @Test
  public void patchsetCannotBeCreatedWithAuthorAndAuthorIdent() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    Account.Id author = accountOperations.newAccount().create();
    PersonIdent authorIdent = new PersonIdent("Author", "author@example.com");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                changeOperations
                    .change(changeId)
                    .newPatchset()
                    .author(author)
                    .authorIdent(authorIdent)
                    .create());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("author and authorIdent cannot be set together");
  }

  @Test
  public void createdPatchsetPreviousCommitterAsCommitter() throws Exception {
    String committerName = "Committer";
    String committerEmail = "committer@example.com";
    Account.Id committer =
        accountOperations
            .newAccount()
            .fullname(committerName)
            .preferredEmail(committerEmail)
            .create();
    Change.Id changeId = changeOperations.newChange().committer(committer).create();
    ChangeInfo change = getChangeFromServer(changeId);
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.committer.name).isEqualTo(committerName);
    assertThat(revision.commit.committer.email).isEqualTo(committerEmail);

    changeOperations.change(changeId).newPatchset().create();
    change = getChangeFromServer(changeId);
    revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.committer.name).isEqualTo(committerName);
    assertThat(revision.commit.committer.email).isEqualTo(committerEmail);
  }

  @Test
  public void createdPatchsetHasSpecifiedCommitter() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String committerName = "Committer";
    String committerEmail = "committer@example.com";
    Account.Id committer =
        accountOperations
            .newAccount()
            .fullname(committerName)
            .preferredEmail(committerEmail)
            .create();
    changeOperations.change(changeId).newPatchset().committer(committer).create();

    ChangeInfo change = getChangeFromServer(changeId);
    assertThat(change.owner._accountId).isNotEqualTo(committer.get());
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.committer.name).isEqualTo(committerName);
    assertThat(revision.commit.committer.email).isEqualTo(committerEmail);
  }

  @Test
  public void createdPatchsetHasSpecifiedCommitterIdent() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    PersonIdent committerIdent = new PersonIdent("Committer", "committer@example.com");
    changeOperations.change(changeId).newPatchset().committerIdent(committerIdent).create();

    ChangeInfo change = getChangeFromServer(changeId);
    RevisionInfo revision = change.revisions.get(change.currentRevision);
    assertThat(revision.commit.committer.name).isEqualTo(committerIdent.getName());
    assertThat(revision.commit.committer.email).isEqualTo(committerIdent.getEmailAddress());
  }

  @Test
  public void patchsetCannotBeCreatedWithCommitterAndCommitterIdent() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    Account.Id committer = accountOperations.newAccount().create();
    PersonIdent committerIdent = new PersonIdent("Committer", "committer@example.com");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                changeOperations
                    .change(changeId)
                    .newPatchset()
                    .committer(committer)
                    .committerIdent(committerIdent)
                    .create());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("committer and committerIdent cannot be set together");
  }

  @Test
  public void newPatchsetCanHaveUpdatedCommitMessage() throws Exception {
    Change.Id changeId = changeOperations.newChange().commitMessage("Old message").create();

    changeOperations.change(changeId).newPatchset().commitMessage("New message").create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    assertThat(currentPatchsetCommit).message().startsWith("New message");
  }

  @Test
  public void updatedCommitMessageOfNewPatchsetAutomaticallyKeepsChangeId() throws Exception {
    Change.Id numericChangeId = changeOperations.newChange().commitMessage("Old message").create();
    String changeId = changeOperations.change(numericChangeId).get().changeId();

    changeOperations.change(numericChangeId).newPatchset().commitMessage("New message").create();

    ChangeInfo change = getChangeFromServer(numericChangeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    assertThat(currentPatchsetCommit).message().contains("Change-Id: " + changeId);
  }

  @Test
  public void newPatchsetCanHaveDifferentChangeIdFooter() throws Exception {
    Change.Id numericChangeId =
        changeOperations
            .newChange()
            .commitMessage("Old message\n\nChange-Id: I1111111111111111111111111111111111111111")
            .create();

    // Specifying another change-id is not an officially supported behavior of Gerrit but we might
    // need this for some test scenarios and hence we support it in the test API.
    changeOperations
        .change(numericChangeId)
        .newPatchset()
        .commitMessage("New message\n\nChange-Id: I0123456789012345678901234567890123456789")
        .create();

    ChangeInfo change = getChangeFromServer(numericChangeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    assertThat(currentPatchsetCommit)
        .message()
        .contains("Change-Id: I0123456789012345678901234567890123456789");
    assertThat(currentPatchsetCommit)
        .message()
        .doesNotContain("Change-Id: I1111111111111111111111111111111111111111");
    // Actual change-id should not have been updated.
    String changeId = changeOperations.change(numericChangeId).get().changeId();
    assertThat(changeId).isEqualTo("I1111111111111111111111111111111111111111");
  }

  @Test
  public void newPatchsetCanHaveReplacedFileContent() throws Exception {
    Change.Id changeId = changeOperations.newChange().file("file1").content("Line 1").create();

    PatchSet.Id patchsetId =
        changeOperations
            .change(changeId)
            .newPatchset()
            .file("file1")
            .content("Different content")
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    Map<String, FileInfo> files = change.revisions.get(change.currentRevision).files;
    assertThatMap(files).keys().containsExactly("file1");
    BinaryResult fileContent = getFileContent(changeId, patchsetId, "file1");
    assertThat(fileContent).asString().isEqualTo("Different content");
  }

  @Test
  public void newPatchsetCanHaveAdditionalFile() throws Exception {
    Change.Id changeId = changeOperations.newChange().file("file1").content("Line 1").create();

    PatchSet.Id patchsetId =
        changeOperations
            .change(changeId)
            .newPatchset()
            .file("file2")
            .content("My file content")
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    Map<String, FileInfo> files = change.revisions.get(change.currentRevision).files;
    assertThatMap(files).keys().containsExactly("file1", "file2");
    BinaryResult fileContent = getFileContent(changeId, patchsetId, "file2");
    assertThat(fileContent).asString().isEqualTo("My file content");
  }

  @Test
  public void newPatchsetCanHaveLessFiles() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .file("file1")
            .content("Line 1")
            .file("file2")
            .content("Line one")
            .create();

    changeOperations.change(changeId).newPatchset().file("file2").delete().create();

    ChangeInfo change = getChangeFromServer(changeId);
    Map<String, FileInfo> files = change.revisions.get(change.currentRevision).files;
    assertThatMap(files).keys().containsExactly("file1");
  }

  @Test
  public void newPatchsetCanHaveRenamedFile() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .file("file1")
            .content("Line 1")
            .file("file2")
            .content("Line one")
            .create();

    PatchSet.Id patchsetId =
        changeOperations
            .change(changeId)
            .newPatchset()
            .file("file2")
            .renameTo("renamed file")
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    Map<String, FileInfo> files = change.revisions.get(change.currentRevision).files;
    assertThatMap(files).keys().containsExactly("file1", "renamed file");
    BinaryResult fileContent = getFileContent(changeId, patchsetId, "renamed file");
    assertThat(fileContent).asString().isEqualTo("Line one");
  }

  @Test
  public void newPatchsetCanHaveRenamedFileWithModifiedContent() throws Exception {
    // We need sufficient content so that the slightly modified content is considered similar enough
    // (> 60% line similarity) for a rename.
    Change.Id changeId =
        changeOperations
            .newChange()
            .file("file1")
            .content("Some content")
            .file("file2")
            .content("Line 1\nLine 2\nLine 3\n")
            .create();
    PatchSet.Id patchset1Id =
        changeOperations.change(changeId).currentPatchset().get().patchsetId();

    PatchSet.Id patchset2Id =
        changeOperations
            .change(changeId)
            .newPatchset()
            .file("file2")
            .delete()
            .file("renamed file")
            .content("Line 1\nLine two\nLine 3\n")
            .create();

    ChangeInfo change = getChangeFromServer(changeId);
    Map<String, FileInfo> files = change.revisions.get(change.currentRevision).files;
    assertThatMap(files).keys().containsExactly("file1", "renamed file");
    BinaryResult fileContent = getFileContent(changeId, patchset2Id, "renamed file");
    assertThat(fileContent).asString().isEqualTo("Line 1\nLine two\nLine 3\n");
    DiffInfo diff =
        gApi.changes()
            .id(changeId.get())
            .revision(patchset2Id.get())
            .file("renamed file")
            .diffRequest()
            .withBase(patchset1Id.getId())
            .get();
    assertThat(diff).changeType().isEqualTo(ChangeType.RENAMED);
  }

  @Test
  public void newPatchsetCanHaveCopiedFile() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .file("file1")
            .content("Some content")
            .file("file2")
            .content("Line 1")
            .create();
    PatchSet.Id patchset1Id =
        changeOperations.change(changeId).currentPatchset().get().patchsetId();

    // Copies currently can only happen if a rename happens at the same time.
    PatchSet.Id patchset2Id =
        changeOperations
            .change(changeId)
            .newPatchset()
            .file("file2")
            .renameTo("renamed/copied file 1")
            .file("renamed/copied file 2")
            .content("Line 1")
            .create();

    // We can't control which of the files Gerrit/Git considers as rename and which as copy.
    // -> Check both for the copy.
    DiffInfo diff1 =
        gApi.changes()
            .id(changeId.get())
            .revision(patchset2Id.get())
            .file("renamed/copied file 1")
            .diffRequest()
            .withBase(patchset1Id.getId())
            .get();
    DiffInfo diff2 =
        gApi.changes()
            .id(changeId.get())
            .revision(patchset2Id.get())
            .file("renamed/copied file 2")
            .diffRequest()
            .withBase(patchset1Id.getId())
            .get();
    assertThat(ImmutableSet.of(diff1.changeType, diff2.changeType)).contains(ChangeType.COPIED);
  }

  @Test
  public void newPatchsetCanHaveCopiedFileWithModifiedContent() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .file("file1")
            .content("Some content")
            .file("file2")
            .content("Line 1\nLine 2\nLine 3\nLine 4\n")
            .create();
    PatchSet.Id patchset1Id =
        changeOperations.change(changeId).currentPatchset().get().patchsetId();

    // A copy with modified content currently can only happen if the renamed file also has slightly
    // modified content. Modify the copy slightly more as Gerrit/Git will then select it as the
    // copied and not renamed file.
    PatchSet.Id patchset2Id =
        changeOperations
            .change(changeId)
            .newPatchset()
            .file("file2")
            .delete()
            .file("renamed file")
            .content("Line 1\nLine 1.1\nLine 2\nLine 3\nLine 4\n")
            .file("copied file")
            .content("Line 1\nLine 1.1\nLine 1.2\nLine 2\nLine 3\nLine 4\n")
            .create();

    DiffInfo diff =
        gApi.changes()
            .id(changeId.get())
            .revision(patchset2Id.get())
            .file("copied file")
            .diffRequest()
            .withBase(patchset1Id.getId())
            .get();
    assertThat(diff).changeType().isEqualTo(ChangeType.COPIED);
    BinaryResult fileContent = getFileContent(changeId, patchset2Id, "copied file");
    assertThat(fileContent)
        .asString()
        .isEqualTo("Line 1\nLine 1.1\nLine 1.2\nLine 2\nLine 3\nLine 4\n");
  }

  @Test
  public void newPatchsetCanHaveADifferentParent() throws Exception {
    Change.Id originalParentChange = changeOperations.newChange().create();
    Change.Id changeId =
        changeOperations.newChange().childOf().change(originalParentChange).create();
    Change.Id newParentChange = changeOperations.newChange().create();

    changeOperations.change(changeId).newPatchset().parent().change(newParentChange).create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    ObjectId newParentCommitId =
        changeOperations.change(newParentChange).currentPatchset().get().commitId();
    assertThat(currentPatchsetCommit)
        .parents()
        .onlyElement()
        .commit()
        .isEqualTo(newParentCommitId.name());
  }

  @Test
  public void newPatchsetCanHaveDifferentParents() throws Exception {
    Change.Id originalParent1Change = changeOperations.newChange().create();
    Change.Id originalParent2Change = changeOperations.newChange().create();
    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOf()
            .change(originalParent1Change)
            .and()
            .change(originalParent2Change)
            .create();
    Change.Id newParent1Change = changeOperations.newChange().create();
    Change.Id newParent2Change = changeOperations.newChange().create();

    changeOperations
        .change(changeId)
        .newPatchset()
        .parents()
        .change(newParent1Change)
        .and()
        .change(newParent2Change)
        .create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    ObjectId newParent1CommitId =
        changeOperations.change(newParent1Change).currentPatchset().get().commitId();
    ObjectId newParent2CommitId =
        changeOperations.change(newParent2Change).currentPatchset().get().commitId();
    assertThat(currentPatchsetCommit)
        .parents()
        .comparingElementsUsing(hasSha1())
        .containsExactly(newParent1CommitId.name(), newParent2CommitId.name());
  }

  @Test
  public void newPatchsetCanHaveADifferentNumberOfParents() throws Exception {
    Change.Id originalParentChange = changeOperations.newChange().create();
    Change.Id changeId =
        changeOperations.newChange().childOf().change(originalParentChange).create();
    Change.Id newParent1Change = changeOperations.newChange().create();
    Change.Id newParent2Change = changeOperations.newChange().create();

    changeOperations
        .change(changeId)
        .newPatchset()
        .parents()
        .change(newParent1Change)
        .and()
        .change(newParent2Change)
        .create();

    ChangeInfo change = getChangeFromServer(changeId);
    CommitInfo currentPatchsetCommit = change.revisions.get(change.currentRevision).commit;
    ObjectId newParent1CommitId =
        changeOperations.change(newParent1Change).currentPatchset().get().commitId();
    ObjectId newParent2CommitId =
        changeOperations.change(newParent2Change).currentPatchset().get().commitId();
    assertThat(currentPatchsetCommit)
        .parents()
        .comparingElementsUsing(hasSha1())
        .containsExactly(newParent1CommitId.name(), newParent2CommitId.name());
  }

  @Test
  public void newPatchsetKeepsFileContentsWithDifferentParent() throws Exception {
    Change.Id changeId =
        changeOperations.newChange().file("file1").content("Actual change content").create();
    Change.Id newParentChange =
        changeOperations.newChange().file("file1").content("Parent content").create();

    changeOperations.change(changeId).newPatchset().parent().change(newParentChange).create();

    PatchSet.Id patchsetId = changeOperations.change(changeId).currentPatchset().get().patchsetId();
    BinaryResult file1Content = getFileContent(changeId, patchsetId, "file1");
    assertThat(file1Content).asString().isEqualTo("Actual change content");
  }

  @Test
  public void publishedCommentCanBeRetrieved() {
    Change.Id changeId = changeOperations.newChange().create();
    String commentUuid = changeOperations.change(changeId).currentPatchset().newComment().create();

    TestHumanComment comment = changeOperations.change(changeId).comment(commentUuid).get();

    assertThat(comment.uuid()).isEqualTo(commentUuid);
  }

  @Test
  public void retrievingDraftCommentAsPublishedCommentFails() {
    Change.Id changeId = changeOperations.newChange().create();
    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newDraftComment().create();

    assertThrows(
        Exception.class, () -> changeOperations.change(changeId).comment(commentUuid).get());
  }

  @Test
  public void parentUuidOfPublishedCommentCanBeRetrieved() {
    Change.Id changeId = changeOperations.newChange().create();
    String parentCommentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().create();
    String childCommentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .parentUuid(parentCommentUuid)
            .create();

    TestHumanComment comment = changeOperations.change(changeId).comment(childCommentUuid).get();

    assertThat(comment.parentUuid()).value().isEqualTo(parentCommentUuid);
  }

  @Test
  public void tagOfPublishedCommentCanBeRetrieved() {
    Change.Id changeId = changeOperations.newChange().create();
    String childCommentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().tag("tag").create();

    TestHumanComment comment = changeOperations.change(changeId).comment(childCommentUuid).get();

    assertThat(comment.tag()).value().isEqualTo("tag");
  }

  @Test
  public void unresolvedOfUnresolvedPublishedCommentCanBeRetrieved() {
    Change.Id changeId = changeOperations.newChange().create();
    String childCommentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().unresolved().create();

    TestHumanComment comment = changeOperations.change(changeId).comment(childCommentUuid).get();

    assertThat(comment.unresolved()).isTrue();
  }

  @Test
  public void unresolvedOfResolvedPublishedCommentCanBeRetrieved() {
    Change.Id changeId = changeOperations.newChange().create();
    String childCommentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().resolved().create();

    TestHumanComment comment = changeOperations.change(changeId).comment(childCommentUuid).get();

    assertThat(comment.unresolved()).isFalse();
  }

  @Test
  public void draftCommentCanBeRetrieved() {
    Change.Id changeId = changeOperations.newChange().create();
    String commentUuid = changeOperations.change(changeId).currentPatchset().newComment().create();

    TestHumanComment comment = changeOperations.change(changeId).comment(commentUuid).get();

    assertThat(comment.uuid()).isEqualTo(commentUuid);
  }

  @Test
  public void retrievingPublishedCommentAsDraftCommentFails() {
    Change.Id changeId = changeOperations.newChange().create();
    String commentUuid = changeOperations.change(changeId).currentPatchset().newComment().create();

    assertThrows(
        Exception.class, () -> changeOperations.change(changeId).draftComment(commentUuid).get());
  }

  @Test
  public void parentUuidOfDraftCommentCanBeRetrieved() {
    Change.Id changeId = changeOperations.newChange().create();
    String parentCommentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().create();
    String childCommentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .parentUuid(parentCommentUuid)
            .create();

    TestHumanComment comment =
        changeOperations.change(changeId).draftComment(childCommentUuid).get();

    assertThat(comment.parentUuid()).value().isEqualTo(parentCommentUuid);
  }

  @Test
  public void robotCommentCanBeRetrieved() {
    Change.Id changeId = changeOperations.newChange().create();
    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newRobotComment().create();

    TestRobotComment comment = changeOperations.change(changeId).robotComment(commentUuid).get();

    assertThat(comment.uuid()).isEqualTo(commentUuid);
  }

  @Test
  public void parentUuidOfRobotCommentCanBeRetrieved() {
    Change.Id changeId = changeOperations.newChange().create();
    String parentCommentUuid =
        changeOperations.change(changeId).currentPatchset().newRobotComment().create();
    String childCommentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .parentUuid(parentCommentUuid)
            .create();

    TestRobotComment comment =
        changeOperations.change(changeId).robotComment(childCommentUuid).get();

    assertThat(comment.parentUuid()).value().isEqualTo(parentCommentUuid);
  }

  private ChangeInfo getChangeFromServer(Change.Id changeId) throws RestApiException {
    return gApi.changes().id(changeId.get()).get();
  }

  private RevisionInfo getRevision(ChangeInfo change, PatchSet.Id patchsetId) {
    return change.revisions.values().stream()
        .filter(revision -> revision._number == patchsetId.get())
        .findAny()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Change %d doesn't have specified patchset %d.",
                        change._number, patchsetId.get())));
  }

  private BinaryResult getFileContent(Change.Id changeId, PatchSet.Id patchsetId, String filePath)
      throws RestApiException {
    return gApi.changes().id(changeId.get()).revision(patchsetId.get()).file(filePath).content();
  }

  private Correspondence<CommitInfo, String> hasSha1() {
    return NullAwareCorrespondence.transforming(commitInfo -> commitInfo.commit, "hasSha1");
  }
}
