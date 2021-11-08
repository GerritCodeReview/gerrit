// Copyright (C) 2021 The Android Open Source Project
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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

public class SubmitWithStickyApprovalDiffIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private ChangeOperations changeOperations;

  @Before
  public void setup() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      // Overwrite "Code-Review" label that is inherited from All-Projects.
      // This way changes to the "Code Review" label don't affect other tests.
      // Also make the vote sticky.
      LabelType.Builder codeReview =
          labelBuilder(
              LabelId.CODE_REVIEW,
              value(2, "Looks good to me, approved"),
              value(1, "Looks good to me, but someone else must approve"),
              value(0, "No score"),
              value(-1, "I would prefer that you didn't submit this"),
              value(-2, "Do not submit"));
      codeReview.setCopyAnyScore(true);
      u.getConfig().upsertLabelType(codeReview.build());
      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();
  }

  @Test
  public void diffChangeMessageOnSubmitWithStickyVote_modifiedFileWithReplaces() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file("file")
            .content("content\naa\nsF\naa\naaa\nsomething\nfoo\nbla\ndeletedEnd")
            .create();

    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    changeOperations
        .change(changeId)
        .newPatchset()
        .file("file")
        .content("content\naa\nsS\naa\naaa\ndifferent\nfoo\nbla")
        .create();

    // add a reviewer to ensure an email is sent.
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageAndEmailWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "file",
        /* insertions= */ 3,
        /* deletions= */ 4,
        /* expectedFileDiff= */ "@@ -1,9 +1,8 @@\n"
            + " content\n"
            + " aa\n"
            + "-sF\n"
            + "+sS\n"
            + " aa\n"
            + " aaa\n"
            + "-something\n"
            + "+different\n"
            + " foo\n"
            + "-bla\n"
            + "-deletedEnd\n"
            + "+bla",
        /* oldFileName= */ null);
  }

  @Test
  public void diffChangeMessageOnSubmitWithStickyVote_ignoreDiffFromRebaseAdditions()
      throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file("file")
            .content("line1\nline2\nline3\nline4")
            .create();

    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());
    changeOperations
        .change(changeId)
        .newPatchset()
        .file("file")
        .content("line012\nline1\nline2\nline3\nline4")
        .create();

    // add a reviewer to ensure an email is sent.
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    testRepo.reset(initial);

    // create 2 unrelated changes and rebase on top of them. Those rebases should be ignored.
    // The changes add files.
    Change.Id unrelated =
        changeOperations.newChange().project(project).file("a").content("a").create();
    gApi.changes().id(unrelated.get()).current().review(ReviewInput.approve());
    gApi.changes().id(unrelated.get()).current().submit();
    unrelated = changeOperations.newChange().project(project).file("z").content("z").create();
    gApi.changes().id(unrelated.get()).current().review(ReviewInput.approve());
    gApi.changes().id(unrelated.get()).current().submit();

    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.base = gApi.changes().id(unrelated.get()).current().commit(true).commit;
    gApi.changes().id(changeId.get()).current().rebase(rebaseInput);

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageAndEmailWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "file",
        /* insertions= */ 1,
        /* deletions= */ 0,
        /* expectedFileDiff= */ "@@ -1,3 +1,4 @@\n"
            + "+line012\n"
            + " line1\n"
            + " line2\n"
            + " line3",
        /* oldFileName= */ null);
  }

  @Test
  public void diffChangeMessageOnSubmitWithStickyVote_ignoreDiffFromRebaseRenames()
      throws Exception {
    Change.Id setup = changeOperations.newChange().project(project).file("a").content("a").create();
    gApi.changes().id(setup.get()).current().review(ReviewInput.approve());
    gApi.changes().id(setup.get()).current().submit();

    setup = changeOperations.newChange().project(project).file("z").content("z").create();
    gApi.changes().id(setup.get()).current().review(ReviewInput.approve());
    gApi.changes().id(setup.get()).current().submit();

    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file("file")
            .content("line1\nline2\nline3\nline4")
            .create();

    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());
    changeOperations
        .change(changeId)
        .newPatchset()
        .file("file")
        .content("line012\nline1\nline2\nline3\nline4")
        .create();

    // add a reviewer to ensure an email is sent.
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    testRepo.reset(initial);

    // create 2 unrelated changes and rebase on top of them. Those rebases should be ignored.
    // The changes rename files.
    Change.Id unrelated =
        changeOperations.newChange().project(project).file("a").renameTo("aa").create();
    gApi.changes().id(unrelated.get()).current().review(ReviewInput.approve());
    gApi.changes().id(unrelated.get()).current().submit();
    unrelated = changeOperations.newChange().project(project).file("z").renameTo("zz").create();
    gApi.changes().id(unrelated.get()).current().review(ReviewInput.approve());
    gApi.changes().id(unrelated.get()).current().submit();

    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.base = gApi.changes().id(unrelated.get()).current().commit(true).commit;
    gApi.changes().id(changeId.get()).current().rebase(rebaseInput);

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageAndEmailWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "file",
        /* insertions= */ 1,
        /* deletions= */ 0,
        /* expectedFileDiff= */ "@@ -1,3 +1,4 @@\n"
            + "+line012\n"
            + " line1\n"
            + " line2\n"
            + " line3",
        /* oldFileName= */ null);
  }

  @Test
  public void diffChangeMessageOnSubmitWithStickyVote_ignoreDiffFromRebaseDeletions()
      throws Exception {
    Change.Id setup = changeOperations.newChange().project(project).file("a").content("a").create();
    gApi.changes().id(setup.get()).current().review(ReviewInput.approve());
    gApi.changes().id(setup.get()).current().submit();

    setup = changeOperations.newChange().project(project).file("z").content("z").create();
    gApi.changes().id(setup.get()).current().review(ReviewInput.approve());
    gApi.changes().id(setup.get()).current().submit();

    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file("file")
            .content("line1\nline2\nline3\nline4")
            .create();

    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());
    changeOperations
        .change(changeId)
        .newPatchset()
        .file("file")
        .content("line012\nline1\nline2\nline3\nline4")
        .create();

    // add a reviewer to ensure an email is sent.
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    testRepo.reset(initial);
    // create 2 unrelated changes and rebase on top of them. Those rebases should be ignored.
    // The changes delete files.
    Change.Id unrelated = changeOperations.newChange().project(project).file("a").delete().create();
    gApi.changes().id(unrelated.get()).current().review(ReviewInput.approve());
    gApi.changes().id(unrelated.get()).current().submit();
    unrelated = changeOperations.newChange().project(project).file("z").delete().create();
    gApi.changes().id(unrelated.get()).current().review(ReviewInput.approve());
    gApi.changes().id(unrelated.get()).current().submit();

    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.base = gApi.changes().id(unrelated.get()).current().commit(true).commit;
    gApi.changes().id(changeId.get()).current().rebase(rebaseInput);

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageAndEmailWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "file",
        /* insertions= */ 1,
        /* deletions= */ 0,
        /* expectedFileDiff= */ "@@ -1,3 +1,4 @@\n"
            + "+line012\n"
            + " line1\n"
            + " line2\n"
            + " line3",
        /* oldFileName= */ null);
  }

  @Test
  public void diffChangeMessageOnSubmitWithStickyVote_modifiedFileWithInsertionAndDeletion()
      throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file("file")
            .content("content\naa\nbb\ncc\ndd\nee\nff\nTODELETE1\nTODELETE2\ngg\nend")
            .create();
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    changeOperations
        .change(changeId)
        .newPatchset()
        .file("file")
        .content("content\naa\nbb\ncc\nINSERTION\nINSERTED\nVERY\nLONG\ndd\nee\nff\ngg\nend")
        .create();

    // add a reviewer to ensure an email is sent.
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageAndEmailWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "file",
        /* insertions= */ 4,
        /* deletions= */ 2,
        /* expectedFileDiff= */ "@@ -2,10 +2,12 @@\n"
            + " aa\n"
            + " bb\n"
            + " cc\n"
            + "+INSERTION\n"
            + "+INSERTED\n"
            + "+VERY\n"
            + "+LONG\n"
            + " dd\n"
            + " ee\n"
            + " ff\n"
            + "-TODELETE1\n"
            + "-TODELETE2\n"
            + " gg\n"
            + " end",
        /* oldFileName= */ null);
  }

  @Test
  @GerritConfig(name = "change.cumulativeCommentSizeLimit", value = "10k")
  public void autoGeneratedPostSubmitDiffIsPartOfTheCommentSizeLimit() throws Exception {
    Change.Id changeId =
        changeOperations.newChange().project(project).file("file").content("content").create();
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());
    String content = new String(new char[800]).replace("\0", "a");
    changeOperations.change(changeId).newPatchset().file("file").content(content).create();

    // Post a submit diff that is almost the cumulativeCommentSizeLimit
    gApi.changes().id(changeId.get()).current().submit();
    assertThat(Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message)
        .doesNotContain("The diff is too large to show. Please review the diff");

    // unrelated comment and change message posting doesn't work, since the post submit diff is
    // counted towards the cumulativeCommentSizeLimit for unrelated follow-up comments.
    // 800 + 9500 > 10k.
    String message = new String(new char[9500]).replace("\0", "a");
    ReviewInput reviewInput = new ReviewInput().message(message);
    CommentInput commentInput = new CommentInput();
    commentInput.line = 1;
    commentInput.path = "file";
    reviewInput.comments = ImmutableMap.of("file", ImmutableList.of(commentInput));

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(changeId.get()).current().review(reviewInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Exceeding maximum cumulative size of comments and change messages");
  }

  @Test
  @GerritConfig(name = "change.cumulativeCommentSizeLimit", value = "1k")
  public void postSubmitDiffCannotBeTooBig() throws Exception {
    Change.Id changeId =
        changeOperations.newChange().project(project).file("file").content("content").create();
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    // max size is 10k / 10 = 1k. 1100 > 1k.
    String content = new String(new char[1100]).replace("\0", "a");

    changeOperations.change(changeId).newPatchset().file("file").content(content).create();

    // Post submit diff is over the cumulativeCommentSizeLimit, (divided by 10) so we shorten the
    // message.
    gApi.changes().id(changeId.get()).current().submit();
    assertThat(Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message)
        .isEqualTo(
            "Change has been successfully merged\n\n1 is the latest approved patch-set.\nThe "
                + "change was submitted with unreviewed changes in the following "
                + "files:\n\n```\nThe name of the file: file\nInsertions: 1, Deletions: 1.\n\nThe"
                + " diff is too large to show. Please review the diff.\n```\n");
  }

  @Test
  public void diffChangeMessageOnSubmitWithStickyVote_addedFile() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    changeOperations
        .change(changeId)
        .newPatchset()
        .file("file")
        .content("content\nmore content\nlast content")
        .create();

    // add a reviewer to ensure an email is sent.
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageAndEmailWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "file",
        /* insertions= */ 3,
        /* deletions= */ 0,
        /* expectedFileDiff= */ "@@ -0,0 +1,3 @@\n+content\n+more content\n+last content",
        /* oldFileName= */ null);
  }

  @Test
  public void diffChangeMessageOnSubmitWithStickyVote_addedMultipleFiles() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    changeOperations
        .change(changeId)
        .newPatchset()
        .file("file")
        .content("content1\nmore content\nlast content")
        .create();

    changeOperations
        .change(changeId)
        .newPatchset()
        .file("otherFile")
        .content("content2\nmore content\nlast content")
        .create();

    // add a reviewer to ensure an email is sent.
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageAndEmailWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file1= */ "otherFile",
        /* insertions1= */ 3,
        /* deletions1= */ 0,
        /* expectedFileDiff1= */ "@@ -0,0 +1,3 @@\n+content2\n+more content\n+last content",
        /* oldFileName1= */ null,
        /* file2= */ "file",
        /* insertions2= */ 3,
        /* deletions2= */ 0,
        /* expectedFileDiff2= */ "@@ -0,0 +1,3 @@\n+content1\n+more content\n+last content",
        /* oldFileName2= */ null);
  }

  @Test
  public void diffChangeMessageOnSubmitWithStickyVote_removedFile() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file("file")
            .content("content\nmore content\nlast content")
            .create();
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    changeOperations.change(changeId).newPatchset().file("file").delete().create();

    // add a reviewer to ensure an email is sent.
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageAndEmailWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "file",
        /* insertions= */ 0,
        /* deletions= */ 3,
        /* expectedFileDiff= */ "@@ -1,3 +0,0 @@\n-content\n-more content\n-last content",
        /* oldFileName= */ null);
  }

  @Test
  public void diffChangeMessageOnSubmitWithStickyVote_renamedFile() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file("file")
            .content("content\nmoreContent")
            .create();
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    changeOperations.change(changeId).newPatchset().file("file").renameTo("new_file").create();

    // add a reviewer to ensure an email is sent.
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageAndEmailWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "new_file",
        /* insertions= */ 0,
        /* deletions= */ 0,
        /* expectedFileDiff= */ "",
        /* oldFileName= */ "file");
  }

  @Test
  public void noDiffChangeMessageOnSubmitWhenVotedOnLastPatchset() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file("file")
            .content("content\nmoreContent")
            .create();
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    changeOperations.change(changeId).newPatchset().file("file").renameTo("new_file").create();

    // Approve last patch-set again, although there is already a +2 on the change (since it's
    // sticky).
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());
    gApi.changes().id(changeId.get()).current().submit();

    assertThat(Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message.trim())
        .isEqualTo("Change has been successfully merged");
  }

  @Test
  public void diffChangeMessageOnSubmitWithStickyVote_approvedPatchset() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    changeOperations.change(changeId).newPatchset().create();

    // approve patch-set 2
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    // create patch-set 3
    changeOperations.change(changeId).newPatchset().create();

    gApi.changes().id(changeId.get()).current().submit();

    // patch-set 2 was the latest approved one.
    assertThat(Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message)
        .contains("2 is the latest approved patch-set.");
  }

  @Test
  public void diffChangeMessageOnSubmitWithStickyVote_noChanges() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    // no file changed
    changeOperations.change(changeId).newPatchset().create();

    gApi.changes().id(changeId.get()).current().submit();

    // No other content in the message since the diff is the same.
    assertThat(Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message)
        .isEqualTo(
            "Change has been successfully merged\n\n1 is the latest approved patch-set.\n"
                + "No files were changed between the latest approved patch-set and the submitted"
                + " one.\n");
  }

  private void assertDiffChangeMessageAndEmailWithStickyApproval(
      String message,
      String file,
      int insertions,
      int deletions,
      String expectedFileDiff,
      String oldFileName) {
    assertDiffChangeMessageAndEmailWithStickyApproval(
        message,
        file,
        insertions,
        deletions,
        expectedFileDiff,
        oldFileName,
        /* file2= */ null,
        /* insertions2= */ 0,
        /* deletions2 =
         */ 0,
        /* expectedFileDiff2= */ null,
        /* oldFileName2= */ null);
  }

  private void assertDiffChangeMessageAndEmailWithStickyApproval(
      String message,
      String file1,
      int insertions1,
      int deletions1,
      String expectedFileDiff1,
      String oldFileName1,
      String file2,
      int insertions2,
      int deletions2,
      String expectedFileDiff2,
      String oldFileName2) {
    String beginningOfMessage =
        "1 is the latest approved patch-set.\n"
            + "The change was submitted with unreviewed changes in the following files:\n"
            + "\n";
    String fileDiff1 = fileDiff(expectedFileDiff1, oldFileName1, file1, insertions1, deletions1);
    String expectedMessage1 = beginningOfMessage + fileDiff1;
    String expectedMessage2 = "";
    Set<String> expectedChangeMessages = new HashSet<>();
    if (file2 != null) {
      String fileDiff2 = fileDiff(expectedFileDiff2, oldFileName2, file2, insertions2, deletions2);
      expectedMessage2 = beginningOfMessage + fileDiff2 + fileDiff1;
      String expectedChangeMessage2 = "Change has been successfully merged\n\n" + expectedMessage2;
      expectedMessage1 += fileDiff2;
      expectedChangeMessages.add(expectedChangeMessage2.trim());
    }
    String expectedChangeMessage1 = "Change has been successfully merged\n\n" + expectedMessage1;
    expectedChangeMessage1 = expectedChangeMessage1.trim();
    expectedChangeMessages.add(expectedChangeMessage1.trim());

    // The order of appearance in the diff for multiple files is not defined, so check both
    // possible orders.
    assertThat(expectedChangeMessages).contains(message.trim());
    String email = Iterables.getLast(sender.getMessages()).body();
    if (email.contains(expectedMessage1) || expectedMessage2.isEmpty()) {
      assertThat(email).contains(expectedMessage1.trim());
    } else {
      assertThat(email).contains(expectedMessage2.trim());
    }
  }

  private String fileDiff(
      String expectedFileDiff, String oldFileName, String file, int insertions, int deletions) {
    String expectedMessage =
        "```\n"
            + String.format("The name of the file: %s\n", file)
            + String.format("Insertions: %d, Deletions: %d.\n\n", insertions, deletions);

    if (oldFileName != null) {
      expectedMessage += String.format("The file %s was renamed to %s\n", oldFileName, file);
    }
    expectedMessage += expectedFileDiff;
    expectedMessage += "\n```\n";
    return expectedMessage;
  }
}
