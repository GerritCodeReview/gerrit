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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.server.patch.filediff.Edit;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import java.util.Iterator;
import java.util.List;
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

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "file",
        /* insertions= */ 3,
        /* deletions= */ 4,
        /* edits= */ ImmutableList.of(
            Edit.create(2, 3, 2, 3), Edit.create(5, 6, 5, 6), Edit.create(7, 9, 7, 8)),
        /* previousLines= */ ImmutableList.of(
            "-  sF\n", "-  something\n", "-  bla\n-  " + "deletedEnd\n"),
        /* newLines= */ ImmutableList.of("+  sS\n", "+  different\n", "+  bla\n"),
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
            .content("content\naa\nbb\ncc" + "\ndd\nee\nff\nTODELETE1\nTODELETE2\ngg\nend")
            .create();
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    changeOperations
        .change(changeId)
        .newPatchset()
        .file("file")
        .content("content\naa\nbb\ncc\nINSERTION\nINSERTED\nVERY\nLONG\ndd\nee\nff\ngg\nend")
        .create();

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "file",
        /* insertions= */ 4,
        /* deletions= */ 2,
        /* edits= */ ImmutableList.of(Edit.create(4, 4, 4, 8), Edit.create(7, 9, 7, 7)),
        /* previousLines= */ ImmutableList.of("-  TODELETE1\n-  TODELETE2\n"),
        /* newLines= */ ImmutableList.of("+  INSERTION\n+  INSERTED\n+  VERY\n+  LONG\n"),
        /* oldFileName= */ null);
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

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "file",
        /* insertions= */ 3,
        /* deletions= */ 0,
        /* edits= */ ImmutableList.of(Edit.create(0, 0, 0, 3)),
        /* previousLines= */ ImmutableList.of(),
        /* newLines= */ ImmutableList.of("+  content\n+  more content\n+  last content"),
        /* oldFileName= */ null);
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

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "file",
        /* insertions= */ 0,
        /* deletions= */ 3,
        /* edits= */ ImmutableList.of(Edit.create(0, 3, 0, 0)),
        /* previousLines= */ ImmutableList.of("-  content\n-  more content\n-  last content"),
        /* newLines= */ ImmutableList.of(),
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

    gApi.changes().id(changeId.get()).current().submit();

    assertDiffChangeMessageWithStickyApproval(
        Iterables.getLast(gApi.changes().id(changeId.get()).messages()).message,
        /* file= */ "new_file",
        /* insertions= */ 0,
        /* deletions= */ 0,
        /* edits= */ ImmutableList.of(),
        /* previousLines= */ ImmutableList.of(),
        /* newLines= */ ImmutableList.of(),
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

  private void assertDiffChangeMessageWithStickyApproval(
      String message,
      String file,
      int insertions,
      int deletions,
      List<Edit> edits,
      List<String> previousLines,
      List<String> newLines,
      String oldFileName) {
    String expectedMessage =
        "Change has been successfully merged\n\n"
            + "The change was submitted with unreviewed changes in the following files:\n"
            + "\n"
            + String.format("The name of the file: %s\n", file)
            + String.format("Insertions: %d, Deletions: %d.\n\n", insertions, deletions);

    if (oldFileName != null) {
      expectedMessage += String.format("The file '%s' was renamed to '%s'\n", oldFileName, file);
    }

    Iterator previousLinesIterator = previousLines.iterator();
    Iterator newLinesIterator = newLines.iterator();
    for (Edit edit : edits) {
      if (edit.beginA() == edit.endA()) {
        // Insertion
        expectedMessage += String.format("@@ +%d:%d @@\n", edit.beginB(), edit.endB());
        expectedMessage += newLinesIterator.next();
        expectedMessage += "\n";
        continue;
      }
      if (edit.beginB() == edit.endB()) {
        // Deletion
        expectedMessage += String.format("@@ -%d:%d @@\n", edit.beginA(), edit.endA());
        expectedMessage += previousLinesIterator.next();
        expectedMessage += "\n";
        continue;
      }
      // Replace
      expectedMessage +=
          String.format(
              "@@ -%d:%d, +%d:%d @@\n", edit.beginA(), edit.endA(), edit.beginB(), edit.endB());
      expectedMessage += previousLinesIterator.next();
      expectedMessage += newLinesIterator.next();
      expectedMessage += "\n";
    }
    assertThat(expectedMessage.trim()).isEqualTo(message.trim());
  }
}
