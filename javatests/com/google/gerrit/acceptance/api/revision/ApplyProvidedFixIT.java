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

package com.google.gerrit.acceptance.api.revision;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.extensions.common.testing.EditInfoSubject.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.common.ApplyProvidedFixInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.testing.BinaryResultSubject;
import com.google.gson.stream.JsonReader;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class ApplyProvidedFixIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private AccountOperations accountOperations;
  @Inject private ChangeOperations changeOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private static final String FILE_NAME = "file_to_fix.txt";
  private static final String FILE_NAME2 = "another_file_to_fix.txt";
  private static final String FILE_NAME3 = "file_without_newline_at_end.txt";
  private static final String FILE_CONTENT =
      "First line\nSecond line\nThird line\nFourth line\nFifth line\nSixth line"
          + "\nSeventh line\nEighth line\nNinth line\nTenth line\n";
  private static final String FILE_CONTENT2 = "1st line\n2nd line\n3rd line\n";
  private static final String FILE_CONTENT3 = "1st line\n2nd line";

  private String changeId;
  private String commitId;

  @Before
  public void setUp() throws Exception {
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Provide files which can be used for fixes",
            ImmutableMap.of(
                FILE_NAME, FILE_CONTENT, FILE_NAME2, FILE_CONTENT2, FILE_NAME3, FILE_CONTENT3));
    PushOneCommit.Result changeResult = push.to("refs/for/master");
    changeId = changeResult.getChangeId();
    commitId = changeResult.getCommit().getName();
  }

  @Test
  public void applyProvidedFixWithinALineCanBeApplied() throws Exception {
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(FILE_NAME, "Modified content", 3, 1, 3, 3);
    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nSecond line\nTModified contentrd line\nFourth line\nFifth line\n"
                + "Sixth line\nSeventh line\nEighth line\nNinth line\nTenth line\n");
  }

  @Test
  public void applyProvidedFixAfterUpdatingPreferredEmail() throws Exception {
    String emailOne = "email1@example.com";
    Account.Id testUser = accountOperations.newAccount().preferredEmail(emailOne).create();

    // Create change
    Change.Id change =
        changeOperations
            .newChange()
            .project(project)
            .file(FILE_NAME)
            .content(FILE_CONTENT)
            .owner(testUser)
            .create();

    // Change preferred email for the user
    String emailTwo = "email2@example.com";
    accountOperations.account(testUser).forUpdate().preferredEmail(emailTwo).update();
    requestScopeOperations.setApiUser(testUser);

    // Apply fix
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(FILE_NAME, "Modified content", 3, 1, 3, 3);
    gApi.changes().id(change.get()).current().applyFix(applyProvidedFixInput);

    EditInfo editInfo = gApi.changes().id(change.get()).edit().get().orElseThrow();
    assertThat(editInfo.commit.committer.email).isEqualTo(emailTwo);
  }

  @Test
  public void applyProvidedFixRestAPItestForASimpleFix() throws Exception {
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(FILE_NAME, "Modified content", 3, 1, 3, 3);
    RestResponse resp =
        adminRestSession.post(
            "/changes/" + changeId + "/revisions/" + commitId + "/fix:apply",
            applyProvidedFixInput);
    readContentFromJson(resp, 200, ReviewResult.class);
    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nSecond line\nTModified contentrd line\nFourth line\nFifth line\n"
                + "Sixth line\nSeventh line\nEighth line\nNinth line\nTenth line\n");
  }

  @Test
  public void applyProvidedFixSpanningMultipleLinesCanBeApplied() throws Exception {
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(FILE_NAME, "Modified content\n5", 3, 2, 5, 3);

    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nSecond line\nThModified content\n5th line\nSixth line\nSeventh line\n"
                + "Eighth line\nNinth line\nTenth line\n");
  }

  @Test
  public void applyProvidedFixWithTwoCloseReplacementsOnSameFileCanBeApplied() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 0);
    fixReplacementInfo1.replacement = "First modification\n";

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME;
    fixReplacementInfo2.range = createRange(3, 0, 4, 0);
    fixReplacementInfo2.replacement = "Some other modified content\n";

    List<FixReplacementInfo> fixReplacementInfoList =
        Arrays.asList(fixReplacementInfo1, fixReplacementInfo2);
    ApplyProvidedFixInput applyProvidedFixInput = new ApplyProvidedFixInput();
    applyProvidedFixInput.fixReplacementInfos = fixReplacementInfoList;

    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nFirst modification\nSome other modified content\nFourth line\nFifth line\n"
                + "Sixth line\nSeventh line\nEighth line\nNinth line\nTenth line\n");
  }

  @Test
  public void twoApplyProvidedFixesOnSameFileCanBeApplied() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 0);
    fixReplacementInfo1.replacement = "First modification\n";

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME;
    fixReplacementInfo2.range = createRange(8, 0, 9, 0);
    fixReplacementInfo2.replacement = "Some other modified content\n";

    List<FixReplacementInfo> fixReplacementInfoList =
        Arrays.asList(fixReplacementInfo1, fixReplacementInfo2);
    ApplyProvidedFixInput applyProvidedFixInput = new ApplyProvidedFixInput();
    applyProvidedFixInput.fixReplacementInfos = fixReplacementInfoList;

    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nFirst modification\nThird line\nFourth line\nFifth line\nSixth line\n"
                + "Seventh line\nSome other modified content\nNinth line\nTenth line\n");
  }

  @Test
  public void twoConflictingApplyProvidedFixesOnSameFileCannotBeApplied() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 1);
    fixReplacementInfo1.replacement = "First modification\n";

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME;
    fixReplacementInfo2.range = createRange(3, 0, 4, 0);
    fixReplacementInfo2.replacement = "Some other modified content\n";

    List<FixReplacementInfo> fixReplacementInfoList =
        Arrays.asList(fixReplacementInfo1, fixReplacementInfo2);
    ApplyProvidedFixInput applyProvidedFixInput = new ApplyProvidedFixInput();
    applyProvidedFixInput.fixReplacementInfos = fixReplacementInfoList;

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput));
    assertThat(thrown).hasMessageThat().contains("Cannot calculate fix replacement");
  }

  @Test
  public void applyProvidedFixInvolvingTwoFilesCanBeApplied() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 0);
    fixReplacementInfo1.replacement = "First modification\n";

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME2;
    fixReplacementInfo2.range = createRange(1, 0, 2, 0);
    fixReplacementInfo2.replacement = "Different file modification\n";

    List<FixReplacementInfo> fixReplacementInfoList =
        Arrays.asList(fixReplacementInfo1, fixReplacementInfo2);
    ApplyProvidedFixInput applyProvidedFixInput = new ApplyProvidedFixInput();
    applyProvidedFixInput.fixReplacementInfos = fixReplacementInfoList;

    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nFirst modification\nThird line\nFourth line\nFifth line\nSixth line\n"
                + "Seventh line\nEighth line\nNinth line\nTenth line\n");
    Optional<BinaryResult> file2 = gApi.changes().id(changeId).edit().getFile(FILE_NAME2);
    BinaryResultSubject.assertThat(file2)
        .value()
        .asString()
        .isEqualTo("Different file modification\n2nd line\n3rd line\n");
  }

  @Test
  public void applyProvidedFixReferringToNonExistentFileCannotBeApplied() throws Exception {
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput("a_non_existent_file.txt", "Modified content\n", 1, 0, 2, 0);

    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput));
  }

  @Test
  public void applyProvidedFixRestAPIcallWithoutAddPatchSetPermissionCannotBeApplied()
      throws Exception {
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(FILE_NAME, "Modified content", 3, 1, 3, 3);

    String allRefs = RefNames.REFS + "*";
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.ADD_PATCH_SET).ref(allRefs).group(ANONYMOUS_USERS))
        .add(block(Permission.ADD_PATCH_SET).ref(allRefs).group(REGISTERED_USERS))
        .update();

    RestResponse resp =
        userRestSession.post(
            "/changes/" + changeId + "/revisions/" + commitId + "/fix:apply",
            applyProvidedFixInput);
    resp.assertStatus(403);
  }

  @Test
  public void applyProvidedFixOnCurrentPatchSetWithExistingChangeEditCanBeApplied()
      throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.range = createRange(2, 0, 3, 0);
    fixReplacementInfo1.replacement = "First modification\n";

    List<FixReplacementInfo> fixReplacementInfoList = Arrays.asList(fixReplacementInfo1);
    ApplyProvidedFixInput applyProvidedFixInput = new ApplyProvidedFixInput();
    applyProvidedFixInput.fixReplacementInfos = fixReplacementInfoList;

    // Create an empty change edit.
    gApi.changes().id(changeId).edit().create();

    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);

    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo(
            "First line\nFirst modification\nThird line\nFourth line\nFifth line\nSixth line\n"
                + "Seventh line\nEighth line\nNinth line\nTenth line\n");
  }

  @Test
  public void applyProvidedFixOnPreviousPatchSetCannotBeApplied() throws Exception {
    // Remember patch set and add another one.
    String previousRevision = gApi.changes().id(changeId).get().currentRevision;
    amendChange(changeId);
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(FILE_NAME, "Modified content", 3, 1, 3, 3);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () ->
                gApi.changes()
                    .id(changeId)
                    .revision(previousRevision)
                    .applyFix(applyProvidedFixInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains("A change edit may only be created for the current patch set");
  }

  @Test
  public void applyProvidedFixOnCurrentPatchSetWithChangeEditOnPreviousPatchSetCannotBeApplied()
      throws Exception {
    // Create an empty change edit.
    gApi.changes().id(changeId).edit().create();
    // Add another patch set.
    amendChange(changeId);
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(FILE_NAME, "Modified content", 3, 1, 3, 3);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains("on which the existing change edit is based may be modified");
  }

  @Test
  public void applyProvidedFixOnCommitMessageCanBeApplied() throws Exception {
    // Set a dedicated commit message.
    String footer = "\nChange-Id: " + changeId + "\n";
    String originalCommitMessage = "Line 1 of commit message\nLine 2 of commit message\n" + footer;
    gApi.changes().id(changeId).edit().modifyCommitMessage(originalCommitMessage);
    gApi.changes().id(changeId).edit().publish();
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(Patch.COMMIT_MSG, "Modified line\n", 7, 0, 8, 0);

    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);
    String commitMessage = gApi.changes().id(changeId).edit().getCommitMessage();
    assertThat(commitMessage).isEqualTo("Modified line\nLine 2 of commit message\n" + footer);
  }

  @Test
  public void applyProvidedFixOnHeaderPartOfCommitMessageCannotBeApplied() throws Exception {
    // Set a dedicated commit message.
    String footer = "Change-Id: " + changeId;
    String originalCommitMessage =
        "Line 1 of commit message\nLine 2 of commit message\n" + "\n" + footer + "\n";
    gApi.changes().id(changeId).edit().modifyCommitMessage(originalCommitMessage);
    gApi.changes().id(changeId).edit().publish();

    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(Patch.COMMIT_MSG, "Modified line\n", 1, 0, 2, 0);
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput));
    assertThat(exception).hasMessageThat().contains("header");
  }

  @Test
  public void applyProvidedFixContainingSeveralModificationsOfCommitMessageCanBeApplied()
      throws Exception {
    // Set a dedicated commit message.
    String footer = "\nChange-Id: " + changeId + "\n";
    String originalCommitMessage =
        "Line 1 of commit message\nLine 2 of commit message\nLine 3 of commit message\n" + footer;
    gApi.changes().id(changeId).edit().modifyCommitMessage(originalCommitMessage);
    gApi.changes().id(changeId).edit().publish();
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = Patch.COMMIT_MSG;
    fixReplacementInfo1.range = createRange(7, 0, 8, 0);
    fixReplacementInfo1.replacement = "Modified line 1\n";
    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = Patch.COMMIT_MSG;
    fixReplacementInfo2.range = createRange(9, 0, 10, 0);
    fixReplacementInfo2.replacement = "Modified line 3\n";
    List<FixReplacementInfo> fixReplacementInfoList =
        Arrays.asList(fixReplacementInfo1, fixReplacementInfo2);
    ApplyProvidedFixInput applyProvidedFixInput = new ApplyProvidedFixInput();
    applyProvidedFixInput.fixReplacementInfos = fixReplacementInfoList;
    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);
    String commitMessage = gApi.changes().id(changeId).edit().getCommitMessage();
    assertThat(commitMessage)
        .isEqualTo("Modified line 1\nLine 2 of commit message\nModified line 3\n" + footer);
  }

  @Test
  public void applyProvidedFixModifyingTheCommitMessageAndAFileCanBeApplied() throws Exception {
    // Set a dedicated commit message.
    String footer = "\nChange-Id: " + changeId + "\n";
    String originalCommitMessage = "Line 1 of commit message\nLine 2 of commit message\n" + footer;
    gApi.changes().id(changeId).edit().modifyCommitMessage(originalCommitMessage);
    gApi.changes().id(changeId).edit().publish();
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = Patch.COMMIT_MSG;
    fixReplacementInfo1.range = createRange(7, 0, 8, 0);
    fixReplacementInfo1.replacement = "Modified line 1\n";
    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME2;
    fixReplacementInfo2.range = createRange(1, 0, 2, 0);
    fixReplacementInfo2.replacement = "File modification\n";
    List<FixReplacementInfo> fixReplacementInfoList =
        Arrays.asList(fixReplacementInfo1, fixReplacementInfo2);
    ApplyProvidedFixInput applyProvidedFixInput = new ApplyProvidedFixInput();
    applyProvidedFixInput.fixReplacementInfos = fixReplacementInfoList;
    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);
    String commitMessage = gApi.changes().id(changeId).edit().getCommitMessage();
    assertThat(commitMessage).isEqualTo("Modified line 1\nLine 2 of commit message\n" + footer);
    Optional<BinaryResult> file = gApi.changes().id(changeId).edit().getFile(FILE_NAME2);
    BinaryResultSubject.assertThat(file)
        .value()
        .asString()
        .isEqualTo("File modification\n2nd line\n3rd line\n");
  }

  @Test
  public void twoApplyProvidedFixesNonOverlappingOnCommitMessageCanBeAppliedSubsequently()
      throws Exception {
    // Set a dedicated commit message.
    String footer = "\nChange-Id: " + changeId + "\n";
    String originalCommitMessage =
        "Line 1 of commit message\nLine 2 of commit message\nLine 3 of commit message\n" + footer;
    gApi.changes().id(changeId).edit().modifyCommitMessage(originalCommitMessage);
    gApi.changes().id(changeId).edit().publish();
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = Patch.COMMIT_MSG;
    fixReplacementInfo1.range = createRange(7, 0, 8, 0);
    fixReplacementInfo1.replacement = "Modified line 1\n";
    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = Patch.COMMIT_MSG;
    fixReplacementInfo2.range = createRange(9, 0, 10, 0);
    fixReplacementInfo2.replacement = "Modified line 3\n";
    List<FixReplacementInfo> fixReplacementInfoList1 = Arrays.asList(fixReplacementInfo1);
    ApplyProvidedFixInput applyProvidedFixInput1 = new ApplyProvidedFixInput();
    applyProvidedFixInput1.fixReplacementInfos = fixReplacementInfoList1;
    List<FixReplacementInfo> fixReplacementInfoList2 = Arrays.asList(fixReplacementInfo2);
    ApplyProvidedFixInput applyProvidedFixInput2 = new ApplyProvidedFixInput();
    applyProvidedFixInput2.fixReplacementInfos = fixReplacementInfoList2;
    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput1);
    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput2);

    String commitMessage = gApi.changes().id(changeId).edit().getCommitMessage();
    assertThat(commitMessage)
        .isEqualTo("Modified line 1\nLine 2 of commit message\nModified line 3\n" + footer);
  }

  @Test
  public void applyingStoredFixTwiceIsIdempotent() throws Exception {
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(FILE_NAME, "Modified content", 3, 1, 3, 3);
    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);
    String expectedEditCommit =
        gApi.changes().id(changeId).edit().get().map(edit -> edit.commit.commit).orElse("");

    // Apply the fix again.
    gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);
    Optional<EditInfo> editInfo = gApi.changes().id(changeId).edit().get();
    assertThat(editInfo).value().commit().commit().isEqualTo(expectedEditCommit);
  }

  @Test
  public void applyProvidedFixReturnsEditInfoForCreatedChangeEdit() throws Exception {
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(FILE_NAME, "Modified content", 3, 1, 3, 3);
    EditInfo editInfo = gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);
    Optional<EditInfo> expectedEditInfo = gApi.changes().id(changeId).edit().get();
    String expectedEditCommit = expectedEditInfo.map(edit -> edit.commit.commit).orElse("");
    assertThat(editInfo).commit().commit().isEqualTo(expectedEditCommit);
    String expectedBaseRevision = expectedEditInfo.map(edit -> edit.baseRevision).orElse("");
    assertThat(editInfo).baseRevision().isEqualTo(expectedBaseRevision);
  }

  @Test
  public void createdApplyProvidedFixChangeEditIsBasedOnCurrentPatchSet() throws Exception {
    String currentRevision = gApi.changes().id(changeId).get().currentRevision;
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(FILE_NAME, "Modified content", 3, 1, 3, 3);
    EditInfo editInfo = gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput);
    assertThat(editInfo).baseRevision().isEqualTo(currentRevision);
  }

  @Test
  public void applyProvidedFixRestCallWithDifferentUserTheUserBecomesUploader() throws Exception {
    ApplyProvidedFixInput applyProvidedFixInput =
        createApplyProvidedFixInput(FILE_NAME, "Modified content", 3, 1, 3, 3);

    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    RevisionInfo rev = changeInfo.revisions.get(changeInfo.currentRevision);
    assertThat(rev.uploader.username).isEqualTo(admin.username());

    RestResponse resp =
        userRestSession.post(
            "/changes/" + changeId + "/revisions/" + commitId + "/fix:apply",
            applyProvidedFixInput);
    resp.assertStatus(200);

    PublishChangeEditInput publishInput = new PublishChangeEditInput();
    RestResponse resp2 =
        userRestSession.post("/changes/" + changeId + "/edit:publish", publishInput);
    resp2.assertStatus(204);

    changeInfo = gApi.changes().id(changeId).get();
    RevisionInfo rev2 = changeInfo.revisions.get(changeInfo.currentRevision);
    assertThat(rev2.uploader.username).isEqualTo(user.username());
  }

  @Test
  public void applyProvidedFixInputNullReturnsBadRequestException() throws Exception {
    ApplyProvidedFixInput applyProvidedFixInput = null;
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput));
    assertThat(thrown).hasMessageThat().contains("applyProvidedFixInput is required");
  }

  @Test
  public void applyProvidedFixInputFixReplacementInfosNullReturnsBadRequestException()
      throws Exception {
    ApplyProvidedFixInput applyProvidedFixInput = new ApplyProvidedFixInput();
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(changeId).current().applyFix(applyProvidedFixInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains("applyProvidedFixInput.fixReplacementInfos is required");
  }

  private ApplyProvidedFixInput createApplyProvidedFixInput(
      String file_name,
      String replacement,
      int startLine,
      int startCharacter,
      int endLine,
      int endCharacter) {
    FixReplacementInfo fixReplacementInfo = new FixReplacementInfo();
    fixReplacementInfo.path = file_name;
    fixReplacementInfo.replacement = replacement;
    fixReplacementInfo.range = createRange(startLine, startCharacter, endLine, endCharacter);

    List<FixReplacementInfo> fixReplacementInfoList = Arrays.asList(fixReplacementInfo);
    ApplyProvidedFixInput applyProvidedFixInput = new ApplyProvidedFixInput();
    applyProvidedFixInput.fixReplacementInfos = fixReplacementInfoList;

    return applyProvidedFixInput;
  }

  private static Comment.Range createRange(
      int startLine, int startCharacter, int endLine, int endCharacter) {
    Comment.Range range = new Comment.Range();
    range.startLine = startLine;
    range.startCharacter = startCharacter;
    range.endLine = endLine;
    range.endCharacter = endCharacter;
    return range;
  }

  private static <T> T readContentFromJson(RestResponse r, int expectedStatus, Class<T> clazz)
      throws Exception {
    r.assertStatus(expectedStatus);
    try (JsonReader jsonReader = new JsonReader(r.getReader())) {
      jsonReader.setLenient(true);
      return newGson().fromJson(jsonReader, clazz);
    }
  }
}
