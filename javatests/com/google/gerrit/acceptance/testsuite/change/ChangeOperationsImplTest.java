/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.acceptance.testsuite.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.hasCommit;
import static com.google.gerrit.extensions.restapi.testing.BinaryResultSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.MapSubject.assertThatMap;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
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
        .isEqualTo(parentCommitId.getName());
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
    // shouldn't be able to do anything.
    projectOperations
        .project(project)
        .forUpdate()
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

  private ChangeInfo getChangeFromServer(Change.Id changeId) throws RestApiException {
    return gApi.changes().id(changeId.get()).get();
  }
}
