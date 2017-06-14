// Copyright (C) 2017 The Android Open Source Project
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
import static com.google.gerrit.extensions.common.DiffInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.FileInfoSubject.assertThat;
import static com.google.gerrit.reviewdb.client.Patch.COMMIT_MSG;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class RevisionDiffIT extends AbstractDaemonTest {
  private static final String FILE_NAME = "some_file.txt";
  private static final String FILE_NAME2 = "another_file.txt";
  private static final String FILE_CONTENT =
      IntStream.rangeClosed(1, 100)
          .mapToObj(number -> String.format("Line %d\n", number))
          .collect(Collectors.joining());
  private static final String FILE_CONTENT2 = "1st line\n2nd line\n3rd line\n";

  private ObjectId commit1;
  private String changeId;
  private String initialPatchSetId;

  @Before
  public void setUp() throws Exception {
    ObjectId headCommit = testRepo.getRepository().resolve("HEAD");
    commit1 =
        addCommit(headCommit, ImmutableMap.of(FILE_NAME, FILE_CONTENT, FILE_NAME2, FILE_CONTENT2));

    Result result = createEmptyChange();
    changeId = result.getChangeId();
    initialPatchSetId = result.getPatchSetId().getId();
  }

  @Test
  public void diff() throws Exception {
    String fileName = "a_new_file.txt";
    String fileContent = "First line\nSecond line\n";
    PushOneCommit.Result result = createChange("Add a file", fileName, fileContent);
    assertDiffForNewFile(result, fileName, fileContent);
    assertDiffForNewFile(result, COMMIT_MSG, result.getCommit().getFullMessage());
  }

  @Test
  public void diffDeletedFile() throws Exception {
    gApi.changes().id(changeId).edit().deleteFile(FILE_NAME);
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diff = gApi.changes().id(changeId).current().file(FILE_NAME).diff();
    assertThat(diff.metaA.lines).isEqualTo(100);
    assertThat(diff.metaB).isNull();
  }

  @Test
  public void diffOnMergeCommitChange() throws Exception {
    PushOneCommit.Result r = createMergeCommitChange("refs/for/master");

    DiffInfo diff;

    // automerge
    diff = gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).file("foo").diff();
    assertThat(diff.metaA.lines).isEqualTo(5);
    assertThat(diff.metaB.lines).isEqualTo(1);

    diff = gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).file("bar").diff();
    assertThat(diff.metaA.lines).isEqualTo(5);
    assertThat(diff.metaB.lines).isEqualTo(1);

    // parent 1
    diff = gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).file("bar").diff(1);
    assertThat(diff.metaA.lines).isEqualTo(1);
    assertThat(diff.metaB.lines).isEqualTo(1);

    // parent 2
    diff = gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).file("foo").diff(2);
    assertThat(diff.metaA.lines).isEqualTo(1);
    assertThat(diff.metaB.lines).isEqualTo(1);
  }

  @Test
  public void addedUnrelatedFileIsIgnored_ForPatchSetDiffWithRebase() throws Exception {
    ObjectId commit2 = addCommit(commit1, "file_added_in_another_commit.txt", "Some file content");

    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(changeId, FILE_NAME, "Another line\n"::concat);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, FILE_NAME);
  }

  @Test
  public void removedUnrelatedFileIsIgnored_ForPatchSetDiffWithRebase() throws Exception {
    ObjectId commit2 = addCommitRemovingFiles(commit1, FILE_NAME2);

    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(changeId, FILE_NAME, "Another line\n"::concat);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, FILE_NAME);
  }

  @Test
  public void renamedUnrelatedFileIsIgnored_ForPatchSetDiffWithRebase() throws Exception {
    ObjectId commit2 = addCommitRenamingFile(commit1, FILE_NAME2, "a_new_file_name.txt");

    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(changeId, FILE_NAME, "Another line\n"::concat);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, FILE_NAME);
  }

  @Test
  public void filesNotTouchedByPatchSetsAndContainingOnlyRebaseHunksAreIgnored() throws Exception {
    String newFileContent = FILE_CONTENT.replace("Line 10\n", "Line ten\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);
    ObjectId commit3 = addCommitRenamingFile(commit2, FILE_NAME2, "a_new_file_name.txt");

    rebaseChangeOn(changeId, commit3);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG);
  }

  @Test
  public void filesTouchedByPatchSetsAndContainingOnlyRebaseHunksAreIgnored() throws Exception {
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 50\n", "Line fifty\n"));
    addModifiedPatchSet(
        changeId, FILE_NAME2, fileContent -> fileContent.replace("1st line\n", "First line\n"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    // Revert the modification to allow rebasing.
    addModifiedPatchSet(
        changeId, FILE_NAME2, fileContent -> fileContent.replace("First line\n", "1st line\n"));

    String newFileContent = FILE_CONTENT.replace("Line 10\n", "Line ten\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);
    String newFilePath = "a_new_file_name.txt";
    ObjectId commit3 = addCommitRenamingFile(commit2, FILE_NAME2, newFilePath);

    rebaseChangeOn(changeId, commit3);
    // Apply the modification again to bring the file into the same state as for the previous
    // patch set.
    addModifiedPatchSet(
        changeId, newFilePath, fileContent -> fileContent.replace("1st line\n", "First line\n"));

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG);
  }

  @Test
  public void rebaseHunksAtStartOfFileAreIdentified() throws Exception {
    String newFileContent =
        FILE_CONTENT.replace("Line 1\n", "Line one\n").replace("Line 5\n", "Line five\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line 50\n", "Line fifty\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line one");
    assertThat(diffInfo).content().element(0).isDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().hasSize(3);
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 5");
    assertThat(diffInfo).content().element(2).linesOfB().containsExactly("Line five");
    assertThat(diffInfo).content().element(2).isDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().hasSize(44);
    assertThat(diffInfo).content().element(4).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(4).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(4).isNotDueToRebase();
    assertThat(diffInfo).content().element(5).commonLines().hasSize(50);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void rebaseHunksAtEndOfFileAreIdentified() throws Exception {
    String newFileContent =
        FILE_CONTENT
            .replace("Line 60\n", "Line sixty\n")
            .replace("Line 100\n", "Line one hundred\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line 50\n", "Line fifty\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(49);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(9);
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 60");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line sixty");
    assertThat(diffInfo).content().element(3).isDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().hasSize(39);
    assertThat(diffInfo).content().element(5).linesOfA().containsExactly("Line 100");
    assertThat(diffInfo).content().element(5).linesOfB().containsExactly("Line one hundred");
    assertThat(diffInfo).content().element(5).isDueToRebase();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void rebaseHunksInBetweenRegularHunksAreIdentified() throws Exception {
    String newFileContent =
        FILE_CONTENT.replace("Line 40\n", "Line forty\n").replace("Line 45\n", "Line forty five\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification =
        fileContent ->
            fileContent
                .replace("Line 1\n", "Line one\n")
                .replace("Line 100\n", "Line one hundred\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line one");
    assertThat(diffInfo).content().element(0).isNotDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().hasSize(38);
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo).content().element(2).linesOfB().containsExactly("Line forty");
    assertThat(diffInfo).content().element(2).isDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().hasSize(4);
    assertThat(diffInfo).content().element(4).linesOfA().containsExactly("Line 45");
    assertThat(diffInfo).content().element(4).linesOfB().containsExactly("Line forty five");
    assertThat(diffInfo).content().element(4).isDueToRebase();
    assertThat(diffInfo).content().element(5).commonLines().hasSize(54);
    assertThat(diffInfo).content().element(6).linesOfA().containsExactly("Line 100");
    assertThat(diffInfo).content().element(6).linesOfB().containsExactly("Line one hundred");
    assertThat(diffInfo).content().element(6).isNotDueToRebase();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(2);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(2);
  }

  @Test
  public void rebaseHunkIsIdentifiedWhenMovedDownInPreviousPatchSet() throws Exception {
    // Move the code down by introducing additional lines (pure insert + enlarging replacement) in
    // the previous patch set.
    Function<String, String> contentModification1 =
        fileContent ->
            "Line zero\n" + fileContent.replace("Line 10\n", "Line ten\nLine ten and a half\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification1);
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    String newFileContent = FILE_CONTENT.replace("Line 40\n", "Line forty\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification2 =
        fileContent -> fileContent.replace("Line 100\n", "Line one hundred\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification2);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(previousPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(41);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line forty");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(59);
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 100");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line one hundred");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void rebaseHunkIsIdentifiedWhenMovedDownInLatestPatchSet() throws Exception {
    String newFileContent = FILE_CONTENT.replace("Line 40\n", "Line forty\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    // Move the code down by introducing additional lines (pure insert + enlarging replacement) in
    // the latest patch set.
    Function<String, String> contentModification =
        fileContent ->
            "Line zero\n" + fileContent.replace("Line 10\n", "Line ten\nLine ten and a half\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).linesOfA().isNull();
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line zero");
    assertThat(diffInfo).content().element(0).isNotDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().hasSize(9);
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 10");
    assertThat(diffInfo)
        .content()
        .element(2)
        .linesOfB()
        .containsExactly("Line ten", "Line ten and a half");
    assertThat(diffInfo).content().element(2).isNotDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().hasSize(29);
    assertThat(diffInfo).content().element(4).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo).content().element(4).linesOfB().containsExactly("Line forty");
    assertThat(diffInfo).content().element(4).isDueToRebase();
    assertThat(diffInfo).content().element(5).commonLines().hasSize(60);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(3);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void rebaseHunkIsIdentifiedWhenMovedUpInPreviousPatchSet() throws Exception {
    // Move the code up by removing lines (pure deletion + shrinking replacement) in the previous
    // patch set.
    Function<String, String> contentModification1 =
        fileContent ->
            fileContent.replace("Line 1\n", "").replace("Line 10\nLine 11\n", "Line ten\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification1);
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    String newFileContent = FILE_CONTENT.replace("Line 40\n", "Line forty\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification2 =
        fileContent -> fileContent.replace("Line 100\n", "Line one hundred\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification2);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(previousPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(37);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line forty");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(59);
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 100");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line one hundred");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void rebaseHunkIsIdentifiedWhenMovedUpInLatestPatchSet() throws Exception {
    String newFileContent = FILE_CONTENT.replace("Line 40\n", "Line forty\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    // Move the code up by removing lines (pure deletion + shrinking replacement) in the latest
    // patch set.
    Function<String, String> contentModification =
        fileContent ->
            fileContent.replace("Line 1\n", "").replace("Line 10\nLine 11\n", "Line ten\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().isNull();
    assertThat(diffInfo).content().element(0).isNotDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().hasSize(8);
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 10", "Line 11");
    assertThat(diffInfo).content().element(2).linesOfB().containsExactly("Line ten");
    assertThat(diffInfo).content().element(2).isNotDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().hasSize(28);
    assertThat(diffInfo).content().element(4).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo).content().element(4).linesOfB().containsExactly("Line forty");
    assertThat(diffInfo).content().element(4).isDueToRebase();
    assertThat(diffInfo).content().element(5).commonLines().hasSize(60);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(3);
  }

  @Test
  public void modifiedRebaseHunkWithSameRegionConsideredAsRegularHunk() throws Exception {
    String newFileContent = FILE_CONTENT.replace("Line 40\n", "Line forty\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line forty\n", "Line modified after rebase\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(39);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line modified after rebase");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(60);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void rebaseHunkOverlappingAtBeginningConsideredAsRegularHunk() throws Exception {
    String newFileContent =
        FILE_CONTENT.replace("Line 40\nLine 41\n", "Line forty\nLine forty one\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification =
        fileContent ->
            fileContent
                .replace("Line 39\n", "Line thirty nine\n")
                .replace("Line forty one\n", "Line 41\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(38);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 39", "Line 40");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line thirty nine", "Line forty");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(60);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(2);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(2);
  }

  @Test
  public void rebaseHunkOverlappingAtEndConsideredAsRegularHunk() throws Exception {
    String newFileContent =
        FILE_CONTENT.replace("Line 40\nLine 41\n", "Line forty\nLine forty one\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification =
        fileContent ->
            fileContent
                .replace("Line forty\n", "Line 40\n")
                .replace("Line 42\n", "Line forty two\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(40);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 41", "Line 42");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line forty one", "Line forty two");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(58);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(2);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(2);
  }

  @Test
  public void rebaseHunkModifiedInsideConsideredAsRegularHunk() throws Exception {
    String newFileContent =
        FILE_CONTENT.replace(
            "Line 39\nLine 40\nLine 41\n", "Line thirty nine\nLine forty\nLine forty one\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line forty\n", "A different line forty\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(38);
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfA()
        .containsExactly("Line 39", "Line 40", "Line 41");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line thirty nine", "A different line forty", "Line forty one");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(59);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(3);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(3);
  }

  @Test
  public void rebaseHunkAfterLineNumberChangingOverlappingHunksIsIdentified() throws Exception {
    String newFileContent =
        FILE_CONTENT
            .replace("Line 40\nLine 41\n", "Line forty\nLine forty one\n")
            .replace("Line 60\n", "Line sixty\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification =
        fileContent ->
            fileContent
                .replace("Line forty\n", "Line 40\n")
                .replace("Line 42\n", "Line forty two\nLine forty two and a half\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(40);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 41", "Line 42");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line forty one", "Line forty two", "Line forty two and a half");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(17);
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 60");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line sixty");
    assertThat(diffInfo).content().element(3).isDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().hasSize(40);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(3);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(2);
  }

  @Test
  public void rebaseHunksOneLineApartFromRegularHunkAreIdentified() throws Exception {
    String newFileContent =
        FILE_CONTENT.replace("Line 1\n", "Line one\n").replace("Line 5\n", "Line five\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line 3\n", "Line three\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line one");
    assertThat(diffInfo).content().element(0).isDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().hasSize(1);
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 3");
    assertThat(diffInfo).content().element(2).linesOfB().containsExactly("Line three");
    assertThat(diffInfo).content().element(2).isNotDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().hasSize(1);
    assertThat(diffInfo).content().element(4).linesOfA().containsExactly("Line 5");
    assertThat(diffInfo).content().element(4).linesOfB().containsExactly("Line five");
    assertThat(diffInfo).content().element(4).isDueToRebase();
    assertThat(diffInfo).content().element(5).commonLines().hasSize(95);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void multipleRebaseEditsMixedWithRegularEditsCanBeIdentified() throws Exception {
    addModifiedPatchSet(
        changeId,
        FILE_NAME,
        fileContent -> fileContent.replace("Line 7\n", "Line seven\n").replace("Line 24\n", ""));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    ObjectId commit2 =
        addCommit(
            commit1,
            FILE_NAME,
            FILE_CONTENT
                .replace("Line 2\n", "Line two\n")
                .replace("Line 18\nLine 19\n", "Line eighteen\nLine nineteen\n")
                .replace("Line 50\n", "Line fifty\n"));

    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(
        changeId,
        FILE_NAME,
        fileContent ->
            fileContent
                .replace("Line seven\n", "Line 7\n")
                .replace("Line 9\n", "Line nine\n")
                .replace("Line 60\n", "Line sixty\n"));

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(previousPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(1);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 2");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line two");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(4);
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line seven");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line 7");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().hasSize(1);
    assertThat(diffInfo).content().element(5).linesOfA().containsExactly("Line 9");
    assertThat(diffInfo).content().element(5).linesOfB().containsExactly("Line nine");
    assertThat(diffInfo).content().element(5).isNotDueToRebase();
    assertThat(diffInfo).content().element(6).commonLines().hasSize(8);
    assertThat(diffInfo).content().element(7).linesOfA().containsExactly("Line 18", "Line 19");
    assertThat(diffInfo)
        .content()
        .element(7)
        .linesOfB()
        .containsExactly("Line eighteen", "Line nineteen");
    assertThat(diffInfo).content().element(7).isDueToRebase();
    assertThat(diffInfo).content().element(8).commonLines().hasSize(29);
    assertThat(diffInfo).content().element(9).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(9).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(9).isDueToRebase();
    assertThat(diffInfo).content().element(10).commonLines().hasSize(9);
    assertThat(diffInfo).content().element(11).linesOfA().containsExactly("Line 60");
    assertThat(diffInfo).content().element(11).linesOfB().containsExactly("Line sixty");
    assertThat(diffInfo).content().element(11).isNotDueToRebase();
    assertThat(diffInfo).content().element(12).commonLines().hasSize(40);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(3);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(3);
  }

  @Test
  public void multipleRebaseEditsMixedWithRegularEditsCanBeIdentified_WithIntraline()
      throws Exception {
    addModifiedPatchSet(
        changeId,
        FILE_NAME,
        fileContent -> fileContent.replace("Line 7\n", "Line seven\n").replace("Line 24\n", ""));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    ObjectId commit2 =
        addCommit(
            commit1,
            FILE_NAME,
            FILE_CONTENT
                .replace("Line 2\n", "Line two\n")
                .replace("Line 18\nLine 19\n", "Line eighteen\nLine nineteen\n")
                .replace("Line 50\n", "Line fifty\n"));

    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(
        changeId,
        FILE_NAME,
        fileContent ->
            fileContent
                .replace("Line seven\n", "Line 7\n")
                .replace("Line 9\n", "Line nine\n")
                .replace("Line 60\n", "Line sixty\n"));

    DiffInfo diffInfo =
        gApi.changes()
            .id(changeId)
            .current()
            .file(FILE_NAME)
            .diffRequest()
            .withBase(previousPatchSetId)
            .withIntraline(true)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().hasSize(1);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 2");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line two");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(4);
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line seven");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line 7");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().hasSize(1);
    assertThat(diffInfo).content().element(5).linesOfA().containsExactly("Line 9");
    assertThat(diffInfo).content().element(5).linesOfB().containsExactly("Line nine");
    assertThat(diffInfo).content().element(5).isNotDueToRebase();
    assertThat(diffInfo).content().element(6).commonLines().hasSize(8);
    assertThat(diffInfo).content().element(7).linesOfA().containsExactly("Line 18", "Line 19");
    assertThat(diffInfo)
        .content()
        .element(7)
        .linesOfB()
        .containsExactly("Line eighteen", "Line nineteen");
    assertThat(diffInfo).content().element(7).isDueToRebase();
    assertThat(diffInfo).content().element(8).commonLines().hasSize(29);
    assertThat(diffInfo).content().element(9).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(9).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(9).isDueToRebase();
    assertThat(diffInfo).content().element(10).commonLines().hasSize(9);
    assertThat(diffInfo).content().element(11).linesOfA().containsExactly("Line 60");
    assertThat(diffInfo).content().element(11).linesOfB().containsExactly("Line sixty");
    assertThat(diffInfo).content().element(11).isNotDueToRebase();
    assertThat(diffInfo).content().element(12).commonLines().hasSize(40);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(3);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(3);
  }

  @Test
  public void deletedFileDuringRebaseConsideredAsRegularHunkWhenModifiedInDiff() throws Exception {
    // Modify the file and revert the modifications to allow rebasing.
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 50\n", "Line fifty\n"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line fifty\n", "Line 50\n"));

    ObjectId commit2 = addCommitRemovingFiles(commit1, FILE_NAME);

    rebaseChangeOn(changeId, commit2);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(previousPatchSetId);
    assertThat(diffInfo).changeType().isEqualTo(ChangeType.DELETED);
    assertThat(diffInfo).content().element(0).linesOfA().hasSize(100);
    assertThat(diffInfo).content().element(0).linesOfB().isNull();
    assertThat(diffInfo).content().element(0).isNotDueToRebase();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isNull();
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(100);
  }

  @Test
  public void addedFileDuringRebaseConsideredAsRegularHunkWhenModifiedInDiff() throws Exception {
    String newFilePath = "a_new_file.txt";
    ObjectId commit2 = addCommit(commit1, newFilePath, "1st line\n2nd line\n3rd line\n");

    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(
        changeId, newFilePath, fileContent -> fileContent.replace("1st line\n", "First line\n"));

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(newFilePath).diff(initialPatchSetId);
    assertThat(diffInfo).changeType().isEqualTo(ChangeType.ADDED);
    assertThat(diffInfo).content().element(0).linesOfA().isNull();
    assertThat(diffInfo).content().element(0).linesOfB().hasSize(3);
    assertThat(diffInfo).content().element(0).isNotDueToRebase();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(newFilePath)).linesInserted().isEqualTo(3);
    assertThat(changedFiles.get(newFilePath)).linesDeleted().isNull();
  }

  @Test
  public void rebaseHunkInRenamedFileIsIdentified_WhenFileIsRenamedDuringRebase() throws Exception {
    String renamedFilePath = "renamed_some_file.txt";
    ObjectId commit2 =
        addCommit(commit1, FILE_NAME, FILE_CONTENT.replace("Line 1\n", "Line one\n"));
    ObjectId commit3 = addCommitRenamingFile(commit2, FILE_NAME, renamedFilePath);

    rebaseChangeOn(changeId, commit3);
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line 50\n", "Line fifty\n");
    addModifiedPatchSet(changeId, renamedFilePath, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(renamedFilePath).diff(initialPatchSetId);
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line one");
    assertThat(diffInfo).content().element(0).isDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().hasSize(48);
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(2).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(2).isNotDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().hasSize(50);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(renamedFilePath)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(renamedFilePath)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void rebaseHunkInRenamedFileIsIdentified_WhenFileIsRenamedInPatchSets() throws Exception {
    String renamedFilePath = "renamed_some_file.txt";
    gApi.changes().id(changeId).edit().renameFile(FILE_NAME, renamedFilePath);
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    // Revert the renaming to be able to rebase.
    gApi.changes().id(changeId).edit().renameFile(renamedFilePath, FILE_NAME);
    gApi.changes().id(changeId).edit().publish();

    ObjectId commit2 =
        addCommit(commit1, FILE_NAME, FILE_CONTENT.replace("Line 5\n", "Line five\n"));

    rebaseChangeOn(changeId, commit2);
    gApi.changes().id(changeId).edit().renameFile(FILE_NAME, renamedFilePath);
    gApi.changes().id(changeId).edit().publish();
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line 50\n", "Line fifty\n");
    addModifiedPatchSet(changeId, renamedFilePath, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(renamedFilePath).diff(previousPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(4);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 5");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line five");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(44);
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().hasSize(50);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(renamedFilePath)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(renamedFilePath)).linesDeleted().isEqualTo(1);
  }

  /*
   *                change PS B
   *                   |
   * change PS A    commit4
   *    |              |
   * commit2        commit3
   *    |             /
   * commit1 --------
   */
  @Test
  public void rebaseHunksWhenRebasingOnAnotherChangeOrPatchSetAreIdentified() throws Exception {
    ObjectId commit2 =
        addCommit(commit1, FILE_NAME, FILE_CONTENT.replace("Line 5\n", "Line five\n"));
    rebaseChangeOn(changeId, commit2);
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    String commit3FileContent = FILE_CONTENT.replace("Line 35\n", "Line thirty five\n");
    ObjectId commit3 = addCommit(commit1, FILE_NAME, commit3FileContent);
    ObjectId commit4 =
        addCommit(commit3, FILE_NAME, commit3FileContent.replace("Line 60\n", "Line sixty\n"));

    rebaseChangeOn(changeId, commit4);
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line 20\n", "Line twenty\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        gApi.changes().id(changeId).current().file(FILE_NAME).diff(previousPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(4);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line five");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line 5");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(14);
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 20");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line twenty");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().hasSize(14);
    assertThat(diffInfo).content().element(5).linesOfA().containsExactly("Line 35");
    assertThat(diffInfo).content().element(5).linesOfB().containsExactly("Line thirty five");
    assertThat(diffInfo).content().element(5).isDueToRebase();
    assertThat(diffInfo).content().element(6).commonLines().hasSize(24);
    assertThat(diffInfo).content().element(7).linesOfA().containsExactly("Line 60");
    assertThat(diffInfo).content().element(7).linesOfB().containsExactly("Line sixty");
    assertThat(diffInfo).content().element(7).isDueToRebase();
    assertThat(diffInfo).content().element(8).commonLines().hasSize(40);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  /*
   *                change PS B
   *                   |
   * change PS A    commit4
   *    |              |
   * commit2        commit3
   *    |             /
   * commit1 --------
   */
  @Test
  public void unrelatedFileWhenRebasingOnAnotherChangeOrPatchSetIsIgnored() throws Exception {
    ObjectId commit2 =
        addCommit(commit1, FILE_NAME, FILE_CONTENT.replace("Line 5\n", "Line five\n"));
    rebaseChangeOn(changeId, commit2);
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    ObjectId commit3 =
        addCommit(commit1, FILE_NAME2, FILE_CONTENT2.replace("2nd line\n", "Second line\n"));
    ObjectId commit4 =
        addCommit(commit3, FILE_NAME, FILE_CONTENT.replace("Line 60\n", "Line sixty\n"));

    rebaseChangeOn(changeId, commit4);
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line 20\n", "Line twenty\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, FILE_NAME);
  }

  @Test
  public void rebaseHunksWhenReversingPatchSetOrderAreIdentified() throws Exception {
    ObjectId commit2 =
        addCommit(
            commit1,
            FILE_NAME,
            FILE_CONTENT.replace("Line 5\n", "Line five\n").replace("Line 35\n", ""));

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line 20\n", "Line twenty\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    String currentPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    DiffInfo diffInfo =
        gApi.changes()
            .id(changeId)
            .revision(initialPatchSetId)
            .file(FILE_NAME)
            .diff(currentPatchSetId);
    assertThat(diffInfo).content().element(0).commonLines().hasSize(4);
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line five");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line 5");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().hasSize(14);
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line twenty");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line 20");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().hasSize(14);
    assertThat(diffInfo).content().element(5).linesOfA().isNull();
    assertThat(diffInfo).content().element(5).linesOfB().containsExactly("Line 35");
    assertThat(diffInfo).content().element(5).isDueToRebase();
    assertThat(diffInfo).content().element(6).commonLines().hasSize(65);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).revision(initialPatchSetId).files(currentPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  private void assertDiffForNewFile(
      PushOneCommit.Result pushResult, String path, String expectedContentSideB) throws Exception {
    DiffInfo diff =
        gApi.changes()
            .id(pushResult.getChangeId())
            .revision(pushResult.getCommit().name())
            .file(path)
            .diff();

    List<String> headers = new ArrayList<>();
    if (path.equals(COMMIT_MSG)) {
      RevCommit c = pushResult.getCommit();

      RevCommit parentCommit = c.getParents()[0];
      String parentCommitId =
          testRepo.getRevWalk().getObjectReader().abbreviate(parentCommit.getId(), 8).name();
      headers.add("Parent:     " + parentCommitId + " (" + parentCommit.getShortMessage() + ")");

      SimpleDateFormat dtfmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US);
      PersonIdent author = c.getAuthorIdent();
      dtfmt.setTimeZone(author.getTimeZone());
      headers.add("Author:     " + author.getName() + " <" + author.getEmailAddress() + ">");
      headers.add("AuthorDate: " + dtfmt.format(author.getWhen().getTime()));

      PersonIdent committer = c.getCommitterIdent();
      dtfmt.setTimeZone(committer.getTimeZone());
      headers.add("Commit:     " + committer.getName() + " <" + committer.getEmailAddress() + ">");
      headers.add("CommitDate: " + dtfmt.format(committer.getWhen().getTime()));
      headers.add("");
    }

    if (!headers.isEmpty()) {
      String header = Joiner.on("\n").join(headers);
      expectedContentSideB = header + "\n" + expectedContentSideB;
    }

    assertDiffForNewFile(diff, pushResult.getCommit(), path, expectedContentSideB);
  }

  private void rebaseChangeOn(String changeId, ObjectId newParent) throws Exception {
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.base = newParent.getName();
    gApi.changes().id(changeId).current().rebase(rebaseInput);
  }

  private ObjectId addCommit(ObjectId parentCommit, String filePath, String fileContent)
      throws Exception {
    ImmutableMap<String, String> files = ImmutableMap.of(filePath, fileContent);
    return addCommit(parentCommit, files);
  }

  private ObjectId addCommit(ObjectId parentCommit, ImmutableMap<String, String> files)
      throws Exception {
    testRepo.reset(parentCommit);
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, "Adjust files of repo", files);
    PushOneCommit.Result result = push.to("refs/for/master");
    return result.getCommit();
  }

  private ObjectId addCommitRemovingFiles(ObjectId parentCommit, String... removedFilePaths)
      throws Exception {
    testRepo.reset(parentCommit);
    Map<String, String> files =
        Arrays.stream(removedFilePaths)
            .collect(Collectors.toMap(Function.identity(), path -> "Irrelevant content"));
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, "Remove files from repo", files);
    PushOneCommit.Result result = push.rm("refs/for/master");
    return result.getCommit();
  }

  private ObjectId addCommitRenamingFile(
      ObjectId parentCommit, String oldFilePath, String newFilePath) throws Exception {
    testRepo.reset(parentCommit);
    PushOneCommit.Result result = createEmptyChange();
    String changeId = result.getChangeId();
    gApi.changes().id(changeId).edit().renameFile(oldFilePath, newFilePath);
    gApi.changes().id(changeId).edit().publish();
    String currentRevision = gApi.changes().id(changeId).get().currentRevision;
    GitUtil.fetch(testRepo, "refs/*:refs/*");
    return ObjectId.fromString(currentRevision);
  }

  private Result createEmptyChange() throws Exception {
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, "Test change", ImmutableMap.of());
    return push.to("refs/for/master");
  }

  private void addModifiedPatchSet(
      String changeId, String filePath, Function<String, String> contentModification)
      throws Exception {
    try (BinaryResult content = gApi.changes().id(changeId).current().file(filePath).content()) {
      String newContent = contentModification.apply(content.asString());
      gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(newContent));
    }
    gApi.changes().id(changeId).edit().publish();
  }
}
