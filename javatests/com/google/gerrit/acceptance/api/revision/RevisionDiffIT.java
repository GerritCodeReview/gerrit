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
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.entities.Patch.COMMIT_MSG;
import static com.google.gerrit.entities.Patch.MERGE_LIST;
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;
import static com.google.gerrit.extensions.common.testing.DiffInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.FileInfoSubject.assertThat;
import static com.google.gerrit.git.ObjectIds.abbreviateName;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.changes.FileApi;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.webui.EditWebLink;
import com.google.gerrit.extensions.webui.FileWebLink;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class RevisionDiffIT extends AbstractDaemonTest {
  // @RunWith(Parameterized.class) can't be used as AbstractDaemonTest is annotated with another
  // runner. Using different configs is a workaround to achieve the same.
  protected static final String TEST_PARAMETER_MARKER = "test_only_parameter";

  private static final String CURRENT = "current";
  private static final String FILE_NAME = "some_file.txt";
  private static final String FILE_NAME2 = "another_file.txt";
  private static final String FILE_CONTENT =
      IntStream.rangeClosed(1, 100)
          .mapToObj(number -> String.format("Line %d\n", number))
          .collect(joining());
  private static final String FILE_CONTENT2 = "1st line\n2nd line\n3rd line\n";

  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private DiffOperations diffOperations;
  @Inject private ProjectOperations projectOperations;

  private boolean intraline;

  private ObjectId initialCommit;
  private ObjectId commit1;
  private String changeId;
  private String initialPatchSetId;

  @Before
  public void setUp() throws Exception {
    // Reduce flakiness of tests. (If tests aren't fast enough, we would use a fall-back
    // computation, which might yield different results.)
    baseConfig.setString("cache", "git_file_diff", "timeout", "1 minute");
    baseConfig.setString("cache", "diff_intraline", "timeout", "1 minute");

    intraline = baseConfig.getBoolean(TEST_PARAMETER_MARKER, "intraline", false);

    ObjectId headCommit = testRepo.getRepository().resolve("HEAD");
    initialCommit = headCommit;

    commit1 =
        addCommit(headCommit, ImmutableMap.of(FILE_NAME, FILE_CONTENT, FILE_NAME2, FILE_CONTENT2));

    Result result = createEmptyChange();
    changeId = result.getChangeId();
    initialPatchSetId = result.getPatchSetId().getId();
  }

  @Test
  public void diff() throws Exception {
    // The assertions assume that intraline is false.
    assume().that(intraline).isFalse();

    String fileName = "a_new_file.txt";
    String fileContent = "First line\nSecond line\n";
    PushOneCommit.Result result = createChange("Add a file", fileName, fileContent);
    assertDiffForNewFile(result, fileName, fileContent);
    assertDiffForNewFile(result, COMMIT_MSG, result.getCommit().getFullMessage());
  }

  @Test
  public void diffWithRootCommit() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/*").group(adminGroupUuid()).force(true))
        .update();

    testRepo.reset(initialCommit);
    PushOneCommit push =
        pushFactory
            .create(admin.newIdent(), testRepo, "subject", ImmutableMap.of("f.txt", "content"))
            .noParent();
    push.setForce(true);
    PushOneCommit.Result result = push.to("refs/heads/master");

    Map<String, FileDiffOutput> modifiedFiles =
        diffOperations.listModifiedFilesAgainstParent(
            project, result.getCommit(), /* parentNum= */ 0, DiffOptions.DEFAULTS);

    assertThat(modifiedFiles.keySet()).containsExactly("/COMMIT_MSG", "f.txt");
    assertThat(
            modifiedFiles.values().stream()
                .map(FileDiffOutput::oldCommitId)
                .collect(Collectors.toSet()))
        .containsExactly(ObjectId.zeroId());
    assertThat(modifiedFiles.get("/COMMIT_MSG").changeType()).isEqualTo(Patch.ChangeType.ADDED);
    assertThat(modifiedFiles.get("f.txt").changeType()).isEqualTo(Patch.ChangeType.ADDED);
  }

  @Test
  public void patchsetLevelFileDiffIsEmpty() throws Exception {
    PushOneCommit.Result result = createChange();
    DiffInfo diffForPatchsetLevelFile =
        gApi.changes()
            .id(result.getChangeId())
            .revision(result.getCommit().name())
            .file(PATCHSET_LEVEL)
            .diff();
    // This behavior is the same as the behavior for non-existent files.
    assertThat(diffForPatchsetLevelFile).binary().isNull();
    assertThat(diffForPatchsetLevelFile).content().isEmpty();
    assertThat(diffForPatchsetLevelFile).diffHeader().isNull();
    assertThat(diffForPatchsetLevelFile).metaA().isNull();
    assertThat(diffForPatchsetLevelFile).metaB().isNull();
    assertThat(diffForPatchsetLevelFile).webLinks().isNull();
  }

  @Test
  public void editWebLinkIncludedInDiff() throws Exception {
    try (Registration registration = newEditWebLink()) {
      String fileName = "a_new_file.txt";
      String fileContent = "First line\nSecond line\n";
      PushOneCommit.Result result = createChange("Add a file", fileName, fileContent);
      DiffInfo info =
          gApi.changes()
              .id(result.getChangeId())
              .revision(result.getCommit().name())
              .file(fileName)
              .diff();
      assertThat(info.editWebLinks).hasSize(1);
      assertThat(info.editWebLinks.get(0).url).isEqualTo("http://edit/" + project + "/" + fileName);
    }
  }

  @Test
  public void gitwebFileWebLinkIncludedInDiff() throws Exception {
    try (Registration registration = newGitwebFileWebLink()) {
      String fileName = "foo.txt";
      String fileContent = "bar\n";
      PushOneCommit.Result result = createChange("Add a file", fileName, fileContent);
      DiffInfo info =
          gApi.changes()
              .id(result.getChangeId())
              .revision(result.getCommit().name())
              .file(fileName)
              .diff();
      assertThat(info.metaB.webLinks).hasSize(1);
      assertThat(info.metaB.webLinks.get(0).url)
          .isEqualTo(
              String.format(
                  "http://gitweb/?p=%s;hb=%s;f=%s", project, result.getCommit().name(), fileName));
    }
  }

  @Test
  public void deletedFileIsIncludedInDiff() throws Exception {
    gApi.changes().id(changeId).edit().deleteFile(FILE_NAME);
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles = gApi.changes().id(changeId).current().files();
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, FILE_NAME);
  }

  @Test
  public void numberOfLinesInDiffOfDeletedFileWithoutNewlineAtEndIsCorrect() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    gApi.changes().id(changeId).edit().deleteFile(filePath);
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(3);
    assertThat(diffInfo).metaB().isNull();
  }

  @Test
  public void numberOfLinesInFileInfoOfDeletedFileWithoutNewlineAtEndIsCorrect() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    gApi.changes().id(changeId).edit().deleteFile(filePath);
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(filePath)).linesInserted().isNull();
    assertThat(changedFiles.get(filePath)).linesDeleted().isEqualTo(3);
  }

  @Test
  public void numberOfLinesInDiffOfDeletedFileWithNewlineAtEndIsCorrect() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    gApi.changes().id(changeId).edit().deleteFile(filePath);
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(4);
    assertThat(diffInfo).metaB().isNull();
  }

  @Test
  public void numberOfLinesInFileInfoOfDeletedFileWithNewlineAtEndIsCorrect() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    gApi.changes().id(changeId).edit().deleteFile(filePath);
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(filePath)).linesInserted().isNull();
    // Inherited from Git: An empty last line is ignored in the count.
    assertThat(changedFiles.get(filePath)).linesDeleted().isEqualTo(3);
  }

  @Test
  public void deletedFileWithoutNewlineAtEndResultsInOneDiffEntry() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    gApi.changes().id(changeId).edit().deleteFile(filePath);
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo)
        .content()
        .onlyElement()
        .linesOfA()
        .containsExactly("Line 1", "Line 2", "Line 3");
  }

  @Test
  public void deletedFileWithNewlineAtEndResultsInOneDiffEntry() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    gApi.changes().id(changeId).edit().deleteFile(filePath);
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo)
        .content()
        .onlyElement()
        .linesOfA()
        .containsExactly("Line 1", "Line 2", "Line 3", "");
  }

  @Test
  public void addedFileIsIncludedInDiff() throws Exception {
    String newFilePath = "a_new_file.txt";
    String newFileContent = "arbitrary content";
    gApi.changes().id(changeId).edit().modifyFile(newFilePath, RawInputUtil.create(newFileContent));
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles = gApi.changes().id(changeId).current().files();
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, newFilePath);
  }

  @Test
  public void numberOfLinesInDiffOfAddedFileWithoutNewlineAtEndIsCorrect() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine2\nLine 3";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(initialPatchSetId).get();
    assertThat(diffInfo).metaA().isNull();
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(3);
  }

  @Test
  public void numberOfLinesInFileInfoOfAddedFileWithoutNewlineAtEndIsCorrect() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine2\nLine 3";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(filePath)).linesInserted().isEqualTo(3);
    assertThat(changedFiles.get(filePath)).linesDeleted().isNull();
  }

  @Test
  public void numberOfLinesInDiffOfAddedFileWithNewlineAtEndIsCorrect() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine2\nLine 3\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(initialPatchSetId).get();
    assertThat(diffInfo).metaA().isNull();
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(4);
  }

  @Test
  public void numberOfLinesInFileInfoOfAddedFileWithNewlineAtEndIsCorrect() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine2\nLine 3\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    // Inherited from Git: An empty last line is ignored in the count.
    assertThat(changedFiles.get(filePath)).linesInserted().isEqualTo(3);
    assertThat(changedFiles.get(filePath)).linesDeleted().isNull();
  }

  @Test
  public void addedFileWithoutNewlineAtEndResultsInOneDiffEntry() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(initialPatchSetId).get();
    assertThat(diffInfo)
        .content()
        .onlyElement()
        .linesOfB()
        .containsExactly("Line 1", "Line 2", "Line 3");
  }

  @Test
  public void addedFileWithNewlineAtEndResultsInOneDiffEntry() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(initialPatchSetId).get();
    assertThat(diffInfo)
        .content()
        .onlyElement()
        .linesOfB()
        .containsExactly("Line 1", "Line 2", "Line 3", "");
  }

  @Test
  public void renamedFileIsIncludedInDiff() throws Exception {
    String newFilePath = "a_new_file.txt";
    gApi.changes().id(changeId).edit().renameFile(FILE_NAME, newFilePath);
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles = gApi.changes().id(changeId).current().files();
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, newFilePath);
  }

  @Test
  public void copiedFileTreatedAsAddedFileInDiff() throws Exception {
    String copyFilePath = "copy_of_some_file.txt";
    gApi.changes().id(changeId).edit().modifyFile(copyFilePath, RawInputUtil.create(FILE_CONTENT));
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles = gApi.changes().id(changeId).current().files();
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, copyFilePath);
    // If this ever changes, please add tests which cover copied files.
    assertThat(changedFiles.get(copyFilePath)).status().isEqualTo('A');
    assertThat(changedFiles.get(copyFilePath)).linesInserted().isEqualTo(100);
    assertThat(changedFiles.get(copyFilePath)).linesDeleted().isNull();
  }

  @Test
  public void copiedFileDetectedIfOriginalFileIsRenamedInDiff() throws Exception {
    /*
     * Copies are detected when a file is deleted and more than 1 file with the same content are
     * added. In this case, the added file with the closest name to the original file is tagged as a
     * rename and the remaining files are considered copies. This implementation is done by JGit in
     * the RenameDetector component.
     */
    String renamedFileName = "renamed_some_file.txt";
    String copyFileName1 = "copy1_with_different_name.txt";
    String copyFileName2 = "copy2_with_different_name.txt";
    gApi.changes().id(changeId).edit().modifyFile(copyFileName1, RawInputUtil.create(FILE_CONTENT));
    gApi.changes().id(changeId).edit().modifyFile(copyFileName2, RawInputUtil.create(FILE_CONTENT));
    gApi.changes().id(changeId).edit().renameFile(FILE_NAME, renamedFileName);
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles = gApi.changes().id(changeId).current().files();

    assertThat(changedFiles.keySet())
        .containsExactly("/COMMIT_MSG", renamedFileName, copyFileName1, copyFileName2);
    assertThat(changedFiles.get(renamedFileName).status).isEqualTo('R');
    assertThat(changedFiles.get(copyFileName1).status).isEqualTo('C');
    assertThat(changedFiles.get(copyFileName2).status).isEqualTo('C');
  }

  @Test
  public void addedBinaryFileIsIncludedInDiff() throws Exception {
    String imageFileName = "an_image.png";
    byte[] imageBytes = createRgbImage(255, 0, 0);
    gApi.changes().id(changeId).edit().modifyFile(imageFileName, RawInputUtil.create(imageBytes));
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles = gApi.changes().id(changeId).current().files();
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, imageFileName);
  }

  @Test
  public void modifiedBinaryFileIsIncludedInDiff() throws Exception {
    String imageFileName = "an_image.png";
    byte[] imageBytes1 = createRgbImage(255, 100, 0);
    ObjectId commit2 = addCommit(commit1, imageFileName, imageBytes1);

    rebaseChangeOn(changeId, commit2);
    byte[] imageBytes2 = createRgbImage(0, 100, 255);
    gApi.changes().id(changeId).edit().modifyFile(imageFileName, RawInputUtil.create(imageBytes2));
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles = gApi.changes().id(changeId).current().files();
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, imageFileName);
  }

  @Test
  public void diffOnMergeCommitChange() throws Exception {
    PushOneCommit.Result r = createMergeCommitChange("refs/for/master");

    DiffInfo diff;

    // automerge
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "foo").get();
    assertThat(diff.metaA.lines).isEqualTo(6);
    assertThat(diff.metaB.lines).isEqualTo(1);

    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "bar").get();
    assertThat(diff.metaA.lines).isEqualTo(6);
    assertThat(diff.metaB.lines).isEqualTo(1);

    // parent 1
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "bar").withParent(1).get();
    assertThat(diff.metaA.lines).isEqualTo(1);
    assertThat(diff.metaB.lines).isEqualTo(1);

    // parent 2
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "foo").withParent(2).get();
    assertThat(diff.metaA.lines).isEqualTo(1);
    assertThat(diff.metaB.lines).isEqualTo(1);
  }

  @Test
  public void diffWithThreeParentsMergeCommitChange() throws Exception {
    // Create a merge commit of 3 files: foo, bar, baz. The merge commit is pointing to 3 different
    // parents: the merge commit contains foo of parent1, bar of parent2 and baz of parent3.
    PushOneCommit.Result r =
        createNParentsMergeCommitChange("refs/for/master", ImmutableList.of("foo", "bar", "baz"));

    DiffInfo diff;

    // parent 1
    Map<String, FileInfo> changedFiles = gApi.changes().id(r.getChangeId()).current().files(1);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, MERGE_LIST, "bar", "baz");
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "foo").withParent(1).get();
    assertThat(diff.diffHeader).isNull();
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "bar").withParent(1).get();
    assertThat(diff.diffHeader).hasSize(4);
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "baz").withParent(1).get();
    assertThat(diff.diffHeader).hasSize(4);

    // parent 2
    changedFiles = gApi.changes().id(r.getChangeId()).current().files(2);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, MERGE_LIST, "foo", "baz");
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "foo").withParent(2).get();
    assertThat(diff.diffHeader).hasSize(4);
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "bar").withParent(2).get();
    assertThat(diff.diffHeader).isNull();
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "baz").withParent(2).get();
    assertThat(diff.diffHeader).hasSize(4);

    // parent 3
    changedFiles = gApi.changes().id(r.getChangeId()).current().files(3);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, MERGE_LIST, "foo", "bar");
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "foo").withParent(3).get();
    assertThat(diff.diffHeader).hasSize(4);
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "bar").withParent(3).get();
    assertThat(diff.diffHeader).hasSize(4);
    diff = getDiffRequest(r.getChangeId(), r.getCommit().name(), "baz").withParent(3).get();
    assertThat(diff.diffHeader).isNull();
  }

  @Test
  public void diffWithThreeParentsMergeCommitAgainstAutoMergeReturnsCommitMsgAndMergeListOnly()
      throws Exception {
    PushOneCommit.Result r =
        createNParentsMergeCommitChange("refs/for/master", ImmutableList.of("foo", "bar", "baz"));

    // Diff against auto-merge returns COMMIT_MSG and MERGE_LIST only
    Map<String, FileInfo> changedFiles = gApi.changes().id(r.getChangeId()).current().files();
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, MERGE_LIST);
  }

  @Test
  public void diffBetweenPatchSetsOfMergeCommitCanBeRetrievedForCommitMessageAndMergeList()
      throws Exception {
    PushOneCommit.Result result = createMergeCommitChange("refs/for/master", "my_file.txt");
    String changeId = result.getChangeId();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, "my_file.txt", content -> content.concat("Line I\nLine II\n"));

    // Call both of them in succession to ensure that they don't share the same cache keys.
    DiffInfo commitMessageDiffInfo =
        getDiffRequest(changeId, CURRENT, COMMIT_MSG).withBase(previousPatchSetId).get();
    DiffInfo mergeListDiffInfo =
        getDiffRequest(changeId, CURRENT, MERGE_LIST).withBase(previousPatchSetId).get();

    assertThat(commitMessageDiffInfo).content().hasSize(3);
    assertThat(mergeListDiffInfo).content().hasSize(1);
  }

  @Test
  public void diffAgainstAutoMergeCanBeRetrievedForCommitMessageAndMergeList() throws Exception {
    PushOneCommit.Result result = createMergeCommitChange("refs/for/master", "my_file.txt");
    String changeId = result.getChangeId();
    addModifiedPatchSet(changeId, "my_file.txt", content -> content.concat("Line I\nLine II\n"));

    DiffInfo commitMessageDiffInfo =
        getDiffRequest(changeId, CURRENT, COMMIT_MSG)
            .get(); // diff latest PS against base (auto-merge)
    DiffInfo mergeListDiffInfo =
        getDiffRequest(changeId, CURRENT, MERGE_LIST)
            .get(); // diff latest PS against base (auto-merge)

    assertThat(commitMessageDiffInfo).changeType().isEqualTo(ChangeType.ADDED);
    assertThat(commitMessageDiffInfo).content().hasSize(1);

    assertThat(mergeListDiffInfo).changeType().isEqualTo(ChangeType.ADDED);
    assertThat(mergeListDiffInfo).content().hasSize(1);
    assertThat(mergeListDiffInfo)
        .content()
        .element(0)
        .linesOfB()
        .element(0)
        .isEqualTo("Merge List:");
  }

  @Test
  public void diffOfUnmodifiedFileMarksAllLinesAsCommon() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    gApi.changes().id(changeId).edit().modifyCommitMessage(updatedCommitMessage());
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo)
        .content()
        .onlyElement()
        .commonLines()
        .containsExactly("Line 1", "Line 2", "Line 3", "")
        .inOrder();
    assertThat(diffInfo).content().onlyElement().linesOfA().isNull();
    assertThat(diffInfo).content().onlyElement().linesOfB().isNull();
  }

  @Test
  public void diffOfUnmodifiedFileWithNewlineAtEndHasEmptyLineAtEnd() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    gApi.changes().id(changeId).edit().modifyCommitMessage(updatedCommitMessage());
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().onlyElement().commonLines().lastElement().isEqualTo("");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(4);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(4);
  }

  @Test
  public void diffOfUnmodifiedFileWithoutNewlineAtEndEndsWithLastLineContent() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    gApi.changes().id(changeId).edit().modifyCommitMessage(updatedCommitMessage());
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().onlyElement().commonLines().lastElement().isEqualTo("Line 3");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(3);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(3);
  }

  @Test
  public void diffOfModifiedFileWithNewlineAtEndHasEmptyLineAtEnd() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, filePath, content -> content.replace("Line 1\n", "Line one\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().lastElement().commonLines().lastElement().isEqualTo("");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(4);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(4);
  }

  @Test
  public void diffOfModifiedFileWithoutNewlineAtEndEndsWithLastLineContent() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, filePath, content -> content.replace("Line 1\n", "Line one\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().lastElement().commonLines().lastElement().isEqualTo("Line 3");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(3);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(3);
  }

  @Test
  public void diffOfModifiedLastLineWithNewlineAtEndHasEmptyLineAtEnd() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, filePath, content -> content.replace("Line 3\n", "Line three\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().lastElement().commonLines().lastElement().isEqualTo("");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(4);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(4);
  }

  @Test
  public void diffOfModifiedLastLineWithoutNewlineAtEndEndsWithLastLineContent() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, filePath, content -> content.replace("Line 3", "Line three"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().lastElement().linesOfA().containsExactly("Line 3");
    assertThat(diffInfo).content().lastElement().linesOfB().containsExactly("Line three");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(3);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(3);
  }

  @Test
  public void addedNewlineAtEndOfFileIsMarkedInDiffWhenWhitespaceIsConsidered() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .withBase(previousPatchSetId)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 101");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line 101", "");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(102);
  }

  @Test
  public void addedNewlineAtEndOfFileIsMarkedInDiffWhenWhitespaceIsIgnored() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_ALL)
            .withBase(previousPatchSetId)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().isNull();
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(102);
  }

  @Test
  public void addedNewlineAtEndOfFileMeansOneModifiedLine() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("\n"));

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void addedNewlineAtEndOfFileIsMarkedInDiffWhenOtherwiseOnlyEditsDueToRebaseExist()
      throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    String newBaseFileContent = FILE_CONTENT.replace("Line 70\n", "Line seventy\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newBaseFileContent);
    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .withBase(previousPatchSetId)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).isNotNull(); // Line 70 modification
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 101");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line 101", "");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(102);
  }

  @Test
  // TODO: Fix this issue. This test documents the current behavior and ensures that we at least
  //  don't run into an internal server error.
  public void addedNewlineAtEndOfFileIsNotIdentifiedAsDueToRebaseEvenThoughItShould()
      throws Exception {
    String baseFileContent = FILE_CONTENT.concat("Line 101");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, baseFileContent);
    rebaseChangeOn(changeId, commit2);
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    String newBaseFileContent = baseFileContent.concat("\n");
    ObjectId commit3 = addCommit(commit2, FILE_NAME, newBaseFileContent);
    rebaseChangeOn(changeId, commit3);

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_ALL)
            .withBase(previousPatchSetId)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().isNull();
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("");
    // This should actually be isDueToRebase().
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
  }

  @Test
  public void
      addedNewlineAtEndOfFileIsMarkedWhenEditDueToRebaseIncreasedLineCountAndWhitespaceConsidered()
          throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    String newBaseFileContent = FILE_CONTENT.replace("Line 70\n", "Line 70\nLine 70.5\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newBaseFileContent);
    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .withBase(previousPatchSetId)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).isNotNull(); // Line 70.5 insertion
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 101");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line 101", "");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(103);
  }

  @Test
  // TODO: Fix this issue. This test documents the current behavior and ensures that we at least
  //  don't run into an internal server error.
  public void
      addedNewlineAtEndOfFileIsNotMarkedWhenEditDueToRebaseIncreasedLineCountAndWhitespaceIgnoredEvenThoughItShould()
          throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    String newBaseFileContent = FILE_CONTENT.replace("Line 70\n", "Line 70\nLine 70.5\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newBaseFileContent);
    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_ALL)
            .withBase(previousPatchSetId)
            .get();
    assertThat(diffInfo).content().element(0).numberOfSkippedLines().isGreaterThan(0);
  }

  @Test
  public void addedLastLineWithoutNewlineBeforeAndAfterwardsIsMarkedInDiff() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("\nLine 102"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .withBase(previousPatchSetId)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 101");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line 101", "Line 102");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(102);
  }

  @Test
  public void addedLastLineWithoutNewlineBeforeAndAfterwardsMeansTwoModifiedLines()
      throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("\nLine 102"));

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(2);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void addedLastLineWithoutNewlineBeforeButWithOneAfterwardsIsMarkedInDiff()
      throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("\nLine 102\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .withBase(previousPatchSetId)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 101");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line 101", "Line 102", "");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(103);
  }

  @Test
  public void addedLastLineWithoutNewlineBeforeButWithOneAfterwardsMeansTwoModifiedLines()
      throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("\nLine 102\n"));

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    // Inherited from Git: An empty last line is ignored in the count.
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(2);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void addedLastLineWithNewlineBeforeAndAfterwardsIsMarkedInDiff() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().isNull();
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line 101");
    assertThat(diffInfo).content().element(2).commonLines().containsExactly("");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(102);
  }

  @Test
  public void addedLastLineWithNewlineBeforeAndAfterwardsMeansOneInsertedLine() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101\n"));

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isNull();
  }

  @Test
  public void addedLastLineWithNewlineBeforeButWithoutOneAfterwardsIsMarkedInDiff()
      throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line 101");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(101);
  }

  @Test
  public void addedLastLineWithNewlineBeforeButWithoutOneAfterwardsMeansOneInsertedLine()
      throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    // Inherited from Git: An empty last line is ignored in the count.
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isNull();
  }

  @Test
  public void hunkForModifiedLastLineIsCombinedWithHunkForAddedNewlineAtEnd() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 101", "Line one oh one\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 101");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line one oh one", "");
  }

  @Test
  public void intralineEditsAreIdentified() throws Exception {
    // In some corner cases, intra-line diffs produce wrong results. In this case, the algorithm
    // falls back to a single edit covering the whole range.
    // See: https://issues.gerritcodereview.com/issues/40013030

    assume().that(intraline).isTrue();

    String orig = "[-9999,9999]";
    String replace = "[-999,999]";

    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat(orig));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.replace(orig, replace));

    // Intra-line logic wrongly produces
    // replace [-9999{,99}99] with [-999{,}999].
    // which if done, results in an incorrect [-9999,99].
    // the intra-line algorithm detects this case and falls back to a single region edit.

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();

    List<List<Integer>> editsA = diffInfo.content.get(1).editA;
    List<List<Integer>> editsB = diffInfo.content.get(1).editB;
    String reconstructed = transformStringUsingEditList(orig, replace, editsA, editsB);

    assertThat(reconstructed).isEqualTo(replace);
  }

  @Test
  public void intralineEditsForModifiedLastLineArePreservedWhenNewlineIsAlsoAddedAtEnd()
      throws Exception {
    assume().that(intraline).isTrue();

    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 101", "Line one oh one\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo)
        .content()
        .element(1)
        .intralineEditsOfA()
        .containsExactly(ImmutableList.of(5, 3));
    assertThat(diffInfo)
        .content()
        .element(1)
        .intralineEditsOfB()
        .containsExactly(ImmutableList.of(5, 11));
  }

  @Test
  public void hunkForModifiedSecondToLastLineIsNotCombinedWithHunkForAddedNewlineAtEnd()
      throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.concat("Line 101"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(
        changeId,
        FILE_NAME,
        fileContent -> fileContent.replace("Line 100\n", "Line one hundred\n").concat("\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 100");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line one hundred");
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().isNull();
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("");
  }

  @Test
  public void deletedNewlineAtEndOfFileIsMarkedInDiffWhenWhitespaceIsConsidered() throws Exception {
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100\n", "Line 100"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withBase(initialPatchSetId)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 100", "");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line 100");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(100);
  }

  @Test
  public void deletedNewlineAtEndOfFileIsMarkedInDiffWhenWhitespaceIsIgnored() throws Exception {
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100\n", "Line 100"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withBase(initialPatchSetId)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_ALL)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("");
    assertThat(diffInfo).content().element(1).linesOfB().isNull();

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(100);
  }

  @Test
  public void deletedNewlineAtEndOfFileMeansOneModifiedLine() throws Exception {
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100\n", "Line 100"));

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void deletedLastLineWithoutNewlineBeforeAndAfterwardsIsMarkedInDiff() throws Exception {
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100\n", "Line 100"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.replace("\nLine 100", ""));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withBase(previousPatchSetId)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 99", "Line 100");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line 99");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(100);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(99);
  }

  @Test
  public void deletedLastLineWithoutNewlineBeforeAndAfterwardsMeansTwoModifiedLines()
      throws Exception {
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100\n", "Line 100"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.replace("\nLine 100", ""));

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(2);
  }

  @Test
  public void deletedLastLineWithoutNewlineBeforeButWithOneAfterwardsIsMarkedInDiff()
      throws Exception {
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100\n", "Line 100"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100", ""));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withBase(previousPatchSetId)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 100");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(100);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(100);
  }

  @Test
  public void deletedLastLineWithoutNewlineBeforeButWithOneAfterwardsMeansOneDeletedLine()
      throws Exception {
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100\n", "Line 100"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100", ""));

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    // Inherited from Git: An empty last line is ignored in the count.
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isNull();
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void deletedLastLineWithNewlineBeforeAndAfterwardsIsMarkedInDiff() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100\n", ""));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withBase(initialPatchSetId)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 100");
    assertThat(diffInfo).content().element(1).linesOfB().isNull();
    assertThat(diffInfo).content().element(2).commonLines().containsExactly("");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(100);
  }

  @Test
  public void deletedLastLineWithNewlineBeforeAndAfterwardsMeansOneDeletedLine() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100\n", ""));

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isNull();
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void deletedLastLineWithNewlineBeforeButWithoutOneAfterwardsIsMarkedInDiff()
      throws Exception {
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("\nLine 100\n", ""));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withBase(initialPatchSetId)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 99", "Line 100", "");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line 99");

    assertThat(diffInfo).metaA().totalLineCount().isEqualTo(101);
    assertThat(diffInfo).metaB().totalLineCount().isEqualTo(99);
  }

  @Test
  public void deletedLastLineWithNewlineBeforeButWithoutOneAfterwardsMeansTwoModifiedLines()
      throws Exception {
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("\nLine 100\n", ""));

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(2);
  }

  @Test
  public void hunkForModifiedLastLineIsCombinedWithHunkForDeletedNewlineAtEnd() throws Exception {
    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100\n", "Line one hundred"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 100", "");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line one hundred");
  }

  @Test
  public void intralineEditsForModifiedLastLineArePreservedWhenNewlineIsAlsoDeletedAtEnd()
      throws Exception {
    assume().that(intraline).isTrue();

    addModifiedPatchSet(
        changeId, FILE_NAME, fileContent -> fileContent.replace("Line 100\n", "Line one hundred"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo)
        .content()
        .element(1)
        .intralineEditsOfA()
        .containsExactly(ImmutableList.of(5, 4));
    assertThat(diffInfo)
        .content()
        .element(1)
        .intralineEditsOfB()
        .containsExactly(ImmutableList.of(5, 11));
  }

  @Test
  public void hunkForModifiedSecondToLastLineIsNotCombinedWithHunkForDeletedNewlineAtEnd()
      throws Exception {
    addModifiedPatchSet(
        changeId,
        FILE_NAME,
        fileContent ->
            fileContent
                .replace("Line 99\n", "Line ninety-nine\n")
                .replace("Line 100\n", "Line 100"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 99");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line ninety-nine");
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("");
    assertThat(diffInfo).content().element(3).linesOfB().isNull();
  }

  @Test
  public void addedUnrelatedFileIsIgnored_forPatchSetDiffWithRebase() throws Exception {
    ObjectId commit2 = addCommit(commit1, "file_added_in_another_commit.txt", "Some file content");

    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(changeId, FILE_NAME, "Another line\n"::concat);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, FILE_NAME);
  }

  @Test
  public void removedUnrelatedFileIsIgnored_forPatchSetDiffWithRebase() throws Exception {
    ObjectId commit2 = addCommitRemovingFiles(commit1, FILE_NAME2);

    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(changeId, FILE_NAME, "Another line\n"::concat);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, FILE_NAME);
  }

  @Test
  public void renamedUnrelatedFileIsIgnored_forPatchSetDiffWithRebase() throws Exception {
    ObjectId commit2 = addCommitRenamingFile(commit1, FILE_NAME2, "a_new_file_name.txt");

    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(changeId, FILE_NAME, "Another line\n"::concat);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, FILE_NAME);
  }

  @Test
  @Ignore
  public void renamedUnrelatedFileIsIgnored_forPatchSetDiffWithRebase_whenEquallyModifiedInBoth()
      throws Exception {
    // TODO(ghareeb): fix this test for the new diff cache implementation

    Function<String, String> contentModification =
        fileContent -> fileContent.replace("1st line\n", "First line\n");
    addModifiedPatchSet(changeId, FILE_NAME2, contentModification);
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    // Revert the modification to be able to rebase.
    addModifiedPatchSet(
        changeId, FILE_NAME2, fileContent -> fileContent.replace("First line\n", "1st line\n"));

    String renamedFileName = "renamed_file.txt";
    ObjectId commit2 = addCommitRenamingFile(commit1, FILE_NAME2, renamedFileName);
    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(changeId, renamedFileName, contentModification);
    addModifiedPatchSet(changeId, FILE_NAME, "Another line\n"::concat);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, FILE_NAME);
  }

  @Test
  public void renamedUnrelatedFileIsIgnored_forPatchSetDiffWithRebase_whenModifiedDuringRebase()
      throws Exception {
    String renamedFilePath = "renamed_some_file.txt";
    ObjectId commit2 =
        addCommit(commit1, FILE_NAME, FILE_CONTENT.replace("Line 5\n", "Line five\n"));
    ObjectId commit3 = addCommitRenamingFile(commit2, FILE_NAME, renamedFilePath);

    rebaseChangeOn(changeId, commit3);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG);
  }

  @Test
  public void fileRenamedDuringRebaseSameAsInPatchSetIsIgnored() throws Exception {
    String renamedFileName = "renamed_file.txt";
    gApi.changes().id(changeId).edit().renameFile(FILE_NAME, renamedFileName);
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    // Revert the renaming to be able to rebase.
    gApi.changes().id(changeId).edit().renameFile(renamedFileName, FILE_NAME);
    gApi.changes().id(changeId).edit().publish();

    ObjectId commit2 = addCommitRenamingFile(commit1, FILE_NAME, renamedFileName);
    rebaseChangeOn(changeId, commit2);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG);
  }

  @Test
  public void fileWithRebaseHunksRenamedDuringRebaseSameAsInPatchSetIsIgnored() throws Exception {
    String renamedFileName = "renamed_file.txt";
    gApi.changes().id(changeId).edit().renameFile(FILE_NAME, renamedFileName);
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    // Revert the renaming to be able to rebase.
    gApi.changes().id(changeId).edit().renameFile(renamedFileName, FILE_NAME);
    gApi.changes().id(changeId).edit().publish();

    ObjectId commit2 =
        addCommit(commit1, FILE_NAME, FILE_CONTENT.replace("Line 10\n", "Line ten\n"));
    ObjectId commit3 = addCommitRenamingFile(commit2, FILE_NAME, renamedFileName);
    rebaseChangeOn(changeId, commit3);

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG);
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
  @Ignore
  public void filesTouchedByPatchSetsAndContainingOnlyRebaseHunksAreIgnored() throws Exception {
    // TODO(ghareeb): fix this test for the new diff cache implementation

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
  public void singleHunkAtBeginningIsFollowedByCorrectCommonLines() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, filePath, content -> content.replace("Line 1\n", "Line one\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).linesOfA().isNotEmpty();
    assertThat(diffInfo).content().element(0).linesOfB().isNotEmpty();
    assertThat(diffInfo)
        .content()
        .element(1)
        .commonLines()
        .containsExactly("Line 2", "Line 3", "Line 4", "Line 5", "")
        .inOrder();
  }

  @Test
  public void singleHunkAtEndIsPrecededByCorrectCommonLines() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, filePath, content -> content.replace("Line 5\n", "Line five\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo)
        .content()
        .element(0)
        .commonLines()
        .containsExactly("Line 1", "Line 2", "Line 3", "Line 4")
        .inOrder();
    assertThat(diffInfo).content().element(1).linesOfA().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfB().isNotEmpty();
  }

  @Test
  public void singleHunkInTheMiddleIsSurroundedByCorrectCommonLines() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(changeId, filePath, content -> content.replace("Line 3\n", "Line three\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo)
        .content()
        .element(0)
        .commonLines()
        .containsExactly("Line 1", "Line 2")
        .inOrder();
    assertThat(diffInfo).content().element(1).linesOfA().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfB().isNotEmpty();
    assertThat(diffInfo)
        .content()
        .element(2)
        .commonLines()
        .containsExactly("Line 4", "Line 5", "Line 6", "")
        .inOrder();
  }

  @Test
  public void twoHunksAreSeparatedByCorrectCommonLines() throws Exception {
    String filePath = "a_new_file.txt";
    String fileContent = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\n";
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(
        changeId,
        filePath,
        content -> content.replace("Line 2\n", "Line two\n").replace("Line 5\n", "Line five\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, filePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfB().isNotEmpty();
    assertThat(diffInfo)
        .content()
        .element(2)
        .commonLines()
        .containsExactly("Line 3", "Line 4")
        .inOrder();
    assertThat(diffInfo).content().element(3).linesOfA().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfB().isNotEmpty();
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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line one");
    assertThat(diffInfo).content().element(0).isDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 5");
    assertThat(diffInfo).content().element(2).linesOfB().containsExactly("Line five");
    assertThat(diffInfo).content().element(2).isDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(4).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(4).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(4).isNotDueToRebase();
    assertThat(diffInfo).content().element(5).commonLines().isNotEmpty();

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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 60");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line sixty");
    assertThat(diffInfo).content().element(3).isDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().isNotEmpty();
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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line one");
    assertThat(diffInfo).content().element(0).isNotDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo).content().element(2).linesOfB().containsExactly("Line forty");
    assertThat(diffInfo).content().element(2).isDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(4).linesOfA().containsExactly("Line 45");
    assertThat(diffInfo).content().element(4).linesOfB().containsExactly("Line forty five");
    assertThat(diffInfo).content().element(4).isDueToRebase();
    assertThat(diffInfo).content().element(5).commonLines().isNotEmpty();
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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line forty");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).linesOfA().isNull();
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line zero");
    assertThat(diffInfo).content().element(0).isNotDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 10");
    assertThat(diffInfo)
        .content()
        .element(2)
        .linesOfB()
        .containsExactly("Line ten", "Line ten and a half");
    assertThat(diffInfo).content().element(2).isNotDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(4).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo).content().element(4).linesOfB().containsExactly("Line forty");
    assertThat(diffInfo).content().element(4).isDueToRebase();
    assertThat(diffInfo).content().element(5).commonLines().isNotEmpty();

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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line forty");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().isNull();
    assertThat(diffInfo).content().element(0).isNotDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 10", "Line 11");
    assertThat(diffInfo).content().element(2).linesOfB().containsExactly("Line ten");
    assertThat(diffInfo).content().element(2).isNotDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(4).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo).content().element(4).linesOfB().containsExactly("Line forty");
    assertThat(diffInfo).content().element(4).isDueToRebase();
    assertThat(diffInfo).content().element(5).commonLines().isNotEmpty();

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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 40");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line modified after rebase");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();

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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 39", "Line 40");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line thirty nine", "Line forty");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();

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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 41", "Line 42");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line forty one", "Line forty two");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();

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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
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
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();

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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 41", "Line 42");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line forty one", "Line forty two", "Line forty two and a half");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 60");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line sixty");
    assertThat(diffInfo).content().element(3).isDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().isNotEmpty();

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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line one");
    assertThat(diffInfo).content().element(0).isDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 3");
    assertThat(diffInfo).content().element(2).linesOfB().containsExactly("Line three");
    assertThat(diffInfo).content().element(2).isNotDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(4).linesOfA().containsExactly("Line 5");
    assertThat(diffInfo).content().element(4).linesOfB().containsExactly("Line five");
    assertThat(diffInfo).content().element(4).isDueToRebase();
    assertThat(diffInfo).content().element(5).commonLines().isNotEmpty();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void rebaseHunksDirectlyTouchingHunksOfPatchSetsNotModifiedBetweenThemAreIdentified()
      throws Exception {
    // Add to hunks in a patch set and remove them in a further patch set to allow rebasing.
    Function<String, String> contentModification =
        fileContent ->
            fileContent.replace("Line 1\n", "Line one\n").replace("Line 3\n", "Line three\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    Function<String, String> reverseContentModification =
        fileContent ->
            fileContent.replace("Line one\n", "Line 1\n").replace("Line three\n", "Line 3\n");
    addModifiedPatchSet(changeId, FILE_NAME, reverseContentModification);

    String newFileContent = FILE_CONTENT.replace("Line 2\n", "Line two\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);
    rebaseChangeOn(changeId, commit2);

    // Add the hunks again and modify another line so that we get a diff for the file.
    // (Files with only edits due to rebase are filtered out.)
    addModifiedPatchSet(
        changeId,
        FILE_NAME,
        contentModification.andThen(fileContent -> fileContent.replace("Line 10\n", "Line ten\n")));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 2");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line two");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 10");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line ten");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().isNotEmpty();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 2");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line two");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line seven");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line 7");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(5).linesOfA().containsExactly("Line 9");
    assertThat(diffInfo).content().element(5).linesOfB().containsExactly("Line nine");
    assertThat(diffInfo).content().element(5).isNotDueToRebase();
    assertThat(diffInfo).content().element(6).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(7).linesOfA().containsExactly("Line 18", "Line 19");
    assertThat(diffInfo)
        .content()
        .element(7)
        .linesOfB()
        .containsExactly("Line eighteen", "Line nineteen");
    assertThat(diffInfo).content().element(7).isDueToRebase();
    assertThat(diffInfo).content().element(8).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(9).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(9).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(9).isDueToRebase();
    assertThat(diffInfo).content().element(10).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(11).linesOfA().containsExactly("Line 60");
    assertThat(diffInfo).content().element(11).linesOfB().containsExactly("Line sixty");
    assertThat(diffInfo).content().element(11).isNotDueToRebase();
    assertThat(diffInfo).content().element(12).commonLines().isNotEmpty();

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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).changeType().isEqualTo(ChangeType.DELETED);
    assertThat(diffInfo).content().element(0).linesOfA().hasSize(101);
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
        getDiffRequest(changeId, CURRENT, newFilePath).withBase(initialPatchSetId).get();
    assertThat(diffInfo).changeType().isEqualTo(ChangeType.ADDED);
    assertThat(diffInfo).content().element(0).linesOfA().isNull();
    assertThat(diffInfo).content().element(0).linesOfB().hasSize(4);
    assertThat(diffInfo).content().element(0).isNotDueToRebase();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(newFilePath)).linesInserted().isEqualTo(3);
    assertThat(changedFiles.get(newFilePath)).linesDeleted().isNull();
  }

  @Test
  public void rebaseHunkInRenamedFileIsIdentified_whenFileIsRenamedDuringRebase() throws Exception {
    String renamedFilePath = "renamed_some_file.txt";
    ObjectId commit2 =
        addCommit(commit1, FILE_NAME, FILE_CONTENT.replace("Line 1\n", "Line one\n"));
    ObjectId commit3 = addCommitRenamingFile(commit2, FILE_NAME, renamedFilePath);

    rebaseChangeOn(changeId, commit3);
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line 50\n", "Line fifty\n");
    addModifiedPatchSet(changeId, renamedFilePath, contentModification);

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, renamedFilePath).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line one");
    assertThat(diffInfo).content().element(0).isDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(2).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(2).isNotDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().isNotEmpty();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.get(renamedFilePath)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(renamedFilePath)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void rebaseHunkInRenamedFileIsIdentified_whenFileIsRenamedInPatchSets() throws Exception {
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
        getDiffRequest(changeId, CURRENT, renamedFilePath).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 5");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line five");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().isNotEmpty();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(renamedFilePath)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(renamedFilePath)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void renamedFileWithOnlyRebaseHunksIsIdentified_whenRenamedBetweenPatchSets()
      throws Exception {
    String newFilePath1 = "renamed_some_file.txt";
    gApi.changes().id(changeId).edit().renameFile(FILE_NAME, newFilePath1);
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    // Revert the renaming to be able to rebase.
    gApi.changes().id(changeId).edit().renameFile(newFilePath1, FILE_NAME);
    gApi.changes().id(changeId).edit().publish();

    ObjectId commit2 =
        addCommit(commit1, FILE_NAME, FILE_CONTENT.replace("Line 5\n", "Line five\n"));

    rebaseChangeOn(changeId, commit2);
    String newFilePath2 = "renamed_some_file_to_something_else.txt";
    gApi.changes().id(changeId).edit().renameFile(FILE_NAME, newFilePath2);
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, newFilePath2);
    assertThat(changedFiles.get(newFilePath2)).linesInserted().isNull();
    assertThat(changedFiles.get(newFilePath2)).linesDeleted().isNull();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, newFilePath2).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 5");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line five");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
  }

  @Test
  public void renamedFileWithOnlyRebaseHunksIsIdentified_whenRenamedForRebaseAndForPatchSets()
      throws Exception {
    String newFilePath1 = "renamed_some_file.txt";
    gApi.changes().id(changeId).edit().renameFile(FILE_NAME, newFilePath1);
    gApi.changes().id(changeId).edit().publish();
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    // Revert the renaming to be able to rebase.
    gApi.changes().id(changeId).edit().renameFile(newFilePath1, FILE_NAME);
    gApi.changes().id(changeId).edit().publish();

    ObjectId commit2 =
        addCommit(commit1, FILE_NAME, FILE_CONTENT.replace("Line 5\n", "Line five\n"));
    String newFilePath2 = "renamed_some_file_during_rebase.txt";
    ObjectId commit3 = addCommitRenamingFile(commit2, FILE_NAME, newFilePath2);

    rebaseChangeOn(changeId, commit3);
    String newFilePath3 = "renamed_some_file_to_something_else.txt";
    gApi.changes().id(changeId).edit().renameFile(newFilePath2, newFilePath3);
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, newFilePath3);
    assertThat(changedFiles.get(newFilePath3)).linesInserted().isNull();
    assertThat(changedFiles.get(newFilePath3)).linesDeleted().isNull();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, newFilePath3).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 5");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line five");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
  }

  @Test
  public void copiedAndRenamedFilesWithOnlyRebaseHunksAreIdentified() throws Exception {
    String newFileContent = FILE_CONTENT.replace("Line 5\n", "Line five\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    // Copies are only identified by JGit when paired with renaming.
    String copyFileName = "copy_of_some_file.txt";
    String renamedFileName = "renamed_some_file.txt";
    gApi.changes()
        .id(changeId)
        .edit()
        .modifyFile(copyFileName, RawInputUtil.create(newFileContent));
    gApi.changes().id(changeId).edit().renameFile(FILE_NAME, renamedFileName);
    gApi.changes().id(changeId).edit().publish();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(initialPatchSetId);
    assertThat(changedFiles.keySet()).containsExactly(COMMIT_MSG, copyFileName, renamedFileName);

    DiffInfo renamedFileDiffInfo =
        getDiffRequest(changeId, CURRENT, renamedFileName).withBase(initialPatchSetId).get();
    assertThat(renamedFileDiffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(renamedFileDiffInfo).content().element(1).linesOfA().containsExactly("Line 5");
    assertThat(renamedFileDiffInfo).content().element(1).linesOfB().containsExactly("Line five");
    assertThat(renamedFileDiffInfo).content().element(1).isDueToRebase();
    assertThat(renamedFileDiffInfo).content().element(2).commonLines().isNotEmpty();

    DiffInfo copiedFileDiffInfo =
        getDiffRequest(changeId, CURRENT, copyFileName).withBase(initialPatchSetId).get();
    assertThat(copiedFileDiffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(copiedFileDiffInfo).content().element(1).linesOfA().containsExactly("Line 5");
    assertThat(copiedFileDiffInfo).content().element(1).linesOfB().containsExactly("Line five");
    assertThat(copiedFileDiffInfo).content().element(1).isDueToRebase();
    assertThat(copiedFileDiffInfo).content().element(2).commonLines().isNotEmpty();
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
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line five");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line 5");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 20");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line twenty");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(5).linesOfA().containsExactly("Line 35");
    assertThat(diffInfo).content().element(5).linesOfB().containsExactly("Line thirty five");
    assertThat(diffInfo).content().element(5).isDueToRebase();
    assertThat(diffInfo).content().element(6).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(7).linesOfA().containsExactly("Line 60");
    assertThat(diffInfo).content().element(7).linesOfB().containsExactly("Line sixty");
    assertThat(diffInfo).content().element(7).isDueToRebase();
    assertThat(diffInfo).content().element(8).commonLines().isNotEmpty();

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
        getDiffRequest(changeId, initialPatchSetId, FILE_NAME).withBase(currentPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line five");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line 5");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line twenty");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line 20");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(5).linesOfA().isNull();
    assertThat(diffInfo).content().element(5).linesOfB().containsExactly("Line 35");
    assertThat(diffInfo).content().element(5).isDueToRebase();
    assertThat(diffInfo).content().element(6).commonLines().isNotEmpty();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).revision(initialPatchSetId).files(currentPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void intralineEditsInNonRebaseHunksAreIdentified() throws Exception {
    assume().that(intraline).isTrue();

    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line 1\n", "Line one\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line one");
    assertThat(diffInfo)
        .content()
        .element(0)
        .intralineEditsOfA()
        .containsExactly(ImmutableList.of(5, 1));
    assertThat(diffInfo)
        .content()
        .element(0)
        .intralineEditsOfB()
        .containsExactly(ImmutableList.of(5, 3));
    assertThat(diffInfo).content().element(0).isNotDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().isNotEmpty();
  }

  @Test
  public void intralineEditsInRebaseHunksAreIdentified() throws Exception {
    assume().that(intraline).isTrue();

    String newFileContent = FILE_CONTENT.replace("Line 1\n", "Line one\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newFileContent);

    rebaseChangeOn(changeId, commit2);
    Function<String, String> contentModification =
        fileContent -> fileContent.replace("Line 50\n", "Line fifty\n");
    addModifiedPatchSet(changeId, FILE_NAME, contentModification);

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(initialPatchSetId).get();
    assertThat(diffInfo).content().element(0).linesOfA().containsExactly("Line 1");
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("Line one");
    assertThat(diffInfo)
        .content()
        .element(0)
        .intralineEditsOfA()
        .containsExactly(ImmutableList.of(5, 1));
    assertThat(diffInfo)
        .content()
        .element(0)
        .intralineEditsOfB()
        .containsExactly(ImmutableList.of(5, 3));
    assertThat(diffInfo).content().element(0).isDueToRebase();
    assertThat(diffInfo).content().element(1).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(2).linesOfA().containsExactly("Line 50");
    assertThat(diffInfo).content().element(2).linesOfB().containsExactly("Line fifty");
    assertThat(diffInfo).content().element(2).isNotDueToRebase();
    assertThat(diffInfo).content().element(3).commonLines().isNotEmpty();
  }

  @Test
  public void closeNonRebaseHunksAreCombinedForIntralineOptimizations() throws Exception {
    assume().that(intraline).isTrue();

    String fileContent = FILE_CONTENT.replace("Line 5\n", "{\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, fileContent);
    rebaseChangeOn(changeId, commit2);
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    addModifiedPatchSet(
        changeId,
        FILE_NAME,
        content -> content.replace("Line 4\n", "Line four\n").replace("Line 6\n", "Line six\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 4", "{", "Line 6");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line four", "{", "Line six");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    // Lines which weren't modified but are included in a hunk due to optimization don't count for
    // the number of inserted/deleted lines.
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(2);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(2);
  }

  @Test
  public void closeRebaseHunksAreNotCombinedForIntralineOptimizations() throws Exception {
    assume().that(intraline).isTrue();

    String fileContent = FILE_CONTENT.replace("Line 5\n", "{\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, fileContent);
    rebaseChangeOn(changeId, commit2);
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    String newFileContent =
        fileContent.replace("Line 4\n", "Line four\n").replace("Line 6\n", "Line six\n");
    ObjectId commit3 = addCommit(commit1, FILE_NAME, newFileContent);
    rebaseChangeOn(changeId, commit3);

    addModifiedPatchSet(
        changeId, FILE_NAME, content -> content.replace("Line 20\n", "Line twenty\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 4");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line four");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 6");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line six");
    assertThat(diffInfo).content().element(3).isDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(5).linesOfA().containsExactly("Line 20");
    assertThat(diffInfo).content().element(5).linesOfB().containsExactly("Line twenty");
    assertThat(diffInfo).content().element(5).isNotDueToRebase();
    assertThat(diffInfo).content().element(6).commonLines().isNotEmpty();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void closeRebaseAndNonRebaseHunksAreNotCombinedForIntralineOptimizations()
      throws Exception {
    assume().that(intraline).isTrue();

    String fileContent = FILE_CONTENT.replace("Line 5\n", "{\n").replace("Line 7\n", "{\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, fileContent);
    rebaseChangeOn(changeId, commit2);
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    String newFileContent =
        fileContent.replace("Line 4\n", "Line four\n").replace("Line 8\n", "Line eight\n");
    ObjectId commit3 = addCommit(commit1, FILE_NAME, newFileContent);
    rebaseChangeOn(changeId, commit3);

    addModifiedPatchSet(changeId, FILE_NAME, content -> content.replace("Line 6\n", "Line six\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 4");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line four");
    assertThat(diffInfo).content().element(1).isDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 6");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line six");
    assertThat(diffInfo).content().element(3).isNotDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(5).linesOfA().containsExactly("Line 8");
    assertThat(diffInfo).content().element(5).linesOfB().containsExactly("Line eight");
    assertThat(diffInfo).content().element(5).isDueToRebase();
    assertThat(diffInfo).content().element(6).commonLines().isNotEmpty();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(1);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(1);
  }

  @Test
  public void closeNonRebaseHunksNextToRebaseHunksAreCombinedForIntralineOptimizations()
      throws Exception {
    assume().that(intraline).isTrue();

    String fileContent = FILE_CONTENT.replace("Line 5\n", "{\n").replace("Line 7\n", "{\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, fileContent);
    rebaseChangeOn(changeId, commit2);
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;

    String newFileContent = fileContent.replace("Line 8\n", "Line eight!\n");
    ObjectId commit3 = addCommit(commit1, FILE_NAME, newFileContent);
    rebaseChangeOn(changeId, commit3);

    addModifiedPatchSet(
        changeId,
        FILE_NAME,
        content -> content.replace("Line 4\n", "Line four\n").replace("Line 6\n", "Line six\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    assertThat(diffInfo).content().element(0).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 4", "{", "Line 6");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line four", "{", "Line six");
    assertThat(diffInfo).content().element(1).isNotDueToRebase();
    assertThat(diffInfo).content().element(2).commonLines().isNotEmpty();
    assertThat(diffInfo).content().element(3).linesOfA().containsExactly("Line 8");
    assertThat(diffInfo).content().element(3).linesOfB().containsExactly("Line eight!");
    assertThat(diffInfo).content().element(3).isDueToRebase();
    assertThat(diffInfo).content().element(4).commonLines().isNotEmpty();

    Map<String, FileInfo> changedFiles =
        gApi.changes().id(changeId).current().files(previousPatchSetId);
    assertThat(changedFiles.get(FILE_NAME)).linesInserted().isEqualTo(2);
    assertThat(changedFiles.get(FILE_NAME)).linesDeleted().isEqualTo(2);
  }

  @Test
  public void diffOfUnmodifiedFileReturnsAllFileContents() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, content -> content.replace("Line 2\n", "Line two\n"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    addModifiedPatchSet(
        changeId, FILE_NAME2, content -> content.replace("2nd line\n", "Second line\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    // We don't list the full file contents here as that is not the focus of this test.
    assertThat(diffInfo)
        .content()
        .element(0)
        .commonLines()
        .containsAtLeast("Line 1", "Line two", "Line 3", "Line 4", "Line 5")
        .inOrder();
  }

  @Test
  // TODO(ghareeb): Don't exclude diffs which only contain rebase hunks within the diff caches. Only
  // filter such files in the GetFiles REST endpoint.
  @Ignore
  public void diffOfFileWithOnlyRebaseHunksConsideringWhitespaceReturnsFileContents()
      throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, content -> content.replace("Line 2\n", "Line two\n"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    String newBaseFileContent = FILE_CONTENT.replace("Line 70\n", "Line seventy\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newBaseFileContent);
    rebaseChangeOn(changeId, commit2);

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withBase(previousPatchSetId)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .get();
    // We don't list the full file contents here as that is not the focus of this test.
    assertThat(diffInfo)
        .content()
        .element(0)
        .commonLines()
        .containsAtLeast("Line 1", "Line two", "Line 3", "Line 4", "Line 5")
        .inOrder();
    // It's crucial that the line changed in the rebase is reported correctly.
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 70");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line seventy");
    assertThat(diffInfo).content().element(1).isDueToRebase();
  }

  @Test
  // TODO(ghareeb): Don't exclude diffs which only contain rebase hunks within the diff caches. Only
  // filter such files in the GetFiles REST endpoint.
  @Ignore
  public void diffOfFileWithOnlyRebaseHunksAndIgnoringWhitespaceReturnsFileContents()
      throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, content -> content.replace("Line 2\n", "Line two\n"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    String newBaseFileContent = FILE_CONTENT.replace("Line 70\n", "Line seventy\n");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newBaseFileContent);
    rebaseChangeOn(changeId, commit2);

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withBase(previousPatchSetId)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_ALL)
            .get();
    // We don't list the full file contents here as that is not the focus of this test.
    assertThat(diffInfo)
        .content()
        .element(0)
        .commonLines()
        .containsAtLeast("Line 1", "Line two", "Line 3", "Line 4", "Line 5")
        .inOrder();
    // It's crucial that the line changed in the rebase is reported correctly.
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 70");
    assertThat(diffInfo).content().element(1).linesOfB().containsExactly("Line seventy");
    assertThat(diffInfo).content().element(1).isDueToRebase();
  }

  @Test
  // TODO(ghareeb): Don't exclude diffs which only contain rebase hunks within the diff caches. Only
  // filter such files in the GetFiles REST endpoint.
  @Ignore
  public void diffOfFileWithMultilineRebaseHunkAddingNewlineAtEndOfFileReturnsFileContents()
      throws Exception {
    String baseFileContent = FILE_CONTENT.concat("Line 101");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, baseFileContent);
    rebaseChangeOn(changeId, commit2);
    addModifiedPatchSet(changeId, FILE_NAME, content -> content.replace("Line 2\n", "Line two\n"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    String newBaseFileContent = baseFileContent.concat("\nLine 102\nLine 103\n");
    ObjectId commit3 = addCommit(commit2, FILE_NAME, newBaseFileContent);
    rebaseChangeOn(changeId, commit3);

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withBase(previousPatchSetId)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .get();
    // We don't list the full file contents here as that is not the focus of this test.
    assertThat(diffInfo)
        .content()
        .element(0)
        .commonLines()
        .containsAtLeast("Line 1", "Line two", "Line 3", "Line 4", "Line 5")
        .inOrder();
    // It's crucial that the lines changed in the rebase are reported correctly.
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 101");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line 101", "Line 102", "Line 103", "");
    assertThat(diffInfo).content().element(1).isDueToRebase();
  }

  @Test
  // TODO(ghareeb): Don't exclude diffs which only contain rebase hunks within the diff caches. Only
  // filter such files in the GetFiles REST endpoint.
  @Ignore
  public void diffOfFileWithMultilineRebaseHunkRemovingNewlineAtEndOfFileReturnsFileContents()
      throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, content -> content.replace("Line 2\n", "Line two\n"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    String newBaseFileContent = FILE_CONTENT.concat("Line 101\nLine 102\nLine 103");
    ObjectId commit2 = addCommit(commit1, FILE_NAME, newBaseFileContent);
    rebaseChangeOn(changeId, commit2);

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME)
            .withBase(previousPatchSetId)
            .withWhitespace(DiffPreferencesInfo.Whitespace.IGNORE_NONE)
            .get();
    // We don't list the full file contents here as that is not the focus of this test.
    assertThat(diffInfo)
        .content()
        .element(0)
        .commonLines()
        .containsAtLeast("Line 1", "Line two", "Line 3", "Line 4", "Line 5")
        .inOrder();
    // It's crucial that the lines changed in the rebase are reported correctly.
    assertThat(diffInfo).content().element(1).linesOfA().containsExactly("Line 100", "");
    assertThat(diffInfo)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Line 100", "Line 101", "Line 102", "Line 103");
    assertThat(diffInfo).content().element(1).isDueToRebase();
  }

  @Test
  public void addDeleteByJgit_isIdentifiedAsRewritten() throws Exception {
    String target = "file.txt";
    String symlink = "link.lnk";

    // Create a change adding file "FileName" and a symlink "symLink" pointing to the file
    PushOneCommit push =
        pushFactory
            .create(admin.newIdent(), testRepo, "Commit Subject", target, "content")
            .addSymlink(symlink, target);
    PushOneCommit.Result result = push.to("refs/for/master");
    String initialRev = gApi.changes().id(result.getChangeId()).get().currentRevision;
    String cId = result.getChangeId();

    // Delete the symlink with PS2
    gApi.changes().id(cId).edit().deleteFile(symlink);
    gApi.changes().id(cId).edit().publish();

    // Re-add the symlink as a regular file with PS3
    gApi.changes().id(cId).edit().modifyFile(symlink, RawInputUtil.create("new content"));
    gApi.changes().id(cId).edit().publish();

    // Changed files: JGit returns two {DELETED/ADDED} entries for the file.
    // The diff logic combines both into a single REWRITTEN entry.
    Map<String, FileInfo> changedFiles = gApi.changes().id(cId).current().files(initialRev);
    assertThat(changedFiles.keySet()).containsExactly("/COMMIT_MSG", symlink);
    assertThat(changedFiles.get(symlink).status).isEqualTo('W'); // Rewritten

    // Detailed diff: Old diff cache returns ADDED entry. New Diff Cache returns REWRITE.
    DiffInfo diffInfo = gApi.changes().id(cId).current().file(symlink).diff(initialRev);
    assertThat(diffInfo.content).hasSize(1);
    assertThat(diffInfo).content().element(0).linesOfB().containsExactly("new content");
    assertThat(diffInfo.changeType).isEqualTo(ChangeType.REWRITE);
  }

  @Test
  public void renameDeleteByJgit_isIdentifiedAsRewritten() throws Exception {
    String target = "file.txt";
    String symlink = "link.lnk";
    PushOneCommit push =
        pushFactory
            .create(admin.newIdent(), testRepo, "Commit Subject", target, "content")
            .addSymlink(symlink, target);
    PushOneCommit.Result result = push.to("refs/for/master");
    String cId = result.getChangeId();
    String initialRev = gApi.changes().id(cId).get().currentRevision;

    // Delete both symlink and target with PS2
    gApi.changes().id(cId).edit().deleteFile(symlink);
    gApi.changes().id(cId).edit().deleteFile(target);
    gApi.changes().id(cId).edit().publish();

    // Re-create the symlink as a regular file with PS3
    gApi.changes().id(cId).edit().modifyFile(symlink, RawInputUtil.create("content"));
    gApi.changes().id(cId).edit().publish();

    // Changed files: JGit returns two {DELETED/RENAMED} entries for the file.
    // The diff logic combines both into a single REWRITTEN entry.
    Map<String, FileInfo> changedFiles = gApi.changes().id(cId).current().files(initialRev);
    assertThat(changedFiles.keySet()).containsExactly("/COMMIT_MSG", symlink);
    assertThat(changedFiles.get(symlink).status).isEqualTo('W'); // Rewritten

    // Detailed diff: Old diff cache returns RENAMED entry. New Diff Cache returns REWRITE.
    DiffInfo diffInfo = gApi.changes().id(cId).current().file(symlink).diff(initialRev);
    assertThat(diffInfo)
        .diffHeader()
        .containsExactly(
            "diff --git a/file.txt b/link.lnk",
            "similarity index 100%",
            "rename from file.txt",
            "rename to link.lnk");
    assertThat(diffInfo.content).hasSize(1);
    assertThat(diffInfo).content().element(0).commonLines().containsExactly("content");
    assertThat(diffInfo.changeType).isEqualTo(ChangeType.REWRITE);
  }

  @Test
  public void diffOfNonExistentFileIsAnEmptyDiffResult() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, content -> content.replace("Line 2\n", "Line two\n"));

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, "a_non-existent_file.txt")
            .withBase(initialPatchSetId)
            .get();
    assertThat(diffInfo).content().isEmpty();
  }

  @Test
  public void requestingDiffForOldFileNameOfRenamedFileYieldsReasonableResult() throws Exception {
    addModifiedPatchSet(changeId, FILE_NAME, content -> content.replace("Line 2\n", "Line two\n"));
    String previousPatchSetId = gApi.changes().id(changeId).get().currentRevision;
    String newFilePath = "a_new_file.txt";
    gApi.changes().id(changeId).edit().renameFile(FILE_NAME, newFilePath);
    gApi.changes().id(changeId).edit().publish();

    DiffInfo diffInfo =
        getDiffRequest(changeId, CURRENT, FILE_NAME).withBase(previousPatchSetId).get();
    // This behavior has been present in Gerrit for quite some time. It differs from the results
    // returned for other cases (e.g. requesting the diff for an unmodified file; requesting the
    // diff for a non-existent file). After a rename, the original file doesn't exist anymore.
    // Hence, the most reasonable thing would be to match the behavior of requesting the diff for a
    // non-existent file, which returns an empty diff.
    // This test at least guarantees that we don't run into an internal error.
    assertThat(diffInfo).content().element(0).commonLines().isNull();
    assertThat(diffInfo).content().element(0).numberOfSkippedLines().isGreaterThan(0);
  }

  @Test
  public void editNotAllowedAsBase() throws Exception {
    gApi.changes().id(changeId).edit().create();

    BadRequestException e =
        assertThrows(
            BadRequestException.class,
            () -> getDiffRequest(changeId, CURRENT, FILE_NAME).withBase("edit").get());
    assertThat(e).hasMessageThat().isEqualTo("edit not allowed as base");

    e =
        assertThrows(
            BadRequestException.class,
            () -> getDiffRequest(changeId, CURRENT, FILE_NAME).withBase("0").get());
    assertThat(e).hasMessageThat().isEqualTo("edit not allowed as base");
  }

  private Registration newEditWebLink() {
    EditWebLink webLink =
        new EditWebLink() {
          @Override
          public WebLinkInfo getEditWebLink(String projectName, String revision, String fileName) {
            return new WebLinkInfo(
                "name", "imageURL", "http://edit/" + projectName + "/" + fileName);
          }
        };
    return extensionRegistry.newRegistration().add(webLink);
  }

  private Registration newGitwebFileWebLink() {
    FileWebLink fileWebLink =
        new FileWebLink() {
          @Override
          public WebLinkInfo getFileWebLink(
              String projectName, String revision, String hash, String fileName) {
            return new WebLinkInfo(
                "name",
                "imageURL",
                String.format("http://gitweb/?p=%s;hb=%s;f=%s", projectName, hash, fileName));
          }
        };
    return extensionRegistry.newRegistration().add(fileWebLink);
  }

  private String updatedCommitMessage() {
    return "An unchanged patchset\n\nChange-Id: " + changeId;
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
          abbreviateName(parentCommit, 8, testRepo.getRevWalk().getObjectReader());
      headers.add("Parent:     " + parentCommitId + " (" + parentCommit.getShortMessage() + ")");

      PersonIdent author = c.getAuthorIdent();
      DateTimeFormatter fmt =
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
              .withLocale(Locale.US)
              .withZone(author.getZoneId());
      headers.add("Author:     " + author.getName() + " <" + author.getEmailAddress() + ">");
      headers.add("AuthorDate: " + fmt.format(author.getWhenAsInstant()));

      PersonIdent committer = c.getCommitterIdent();
      fmt = fmt.withZone(committer.getZoneId());
      headers.add("Commit:     " + committer.getName() + " <" + committer.getEmailAddress() + ">");
      headers.add("CommitDate: " + fmt.format(committer.getWhenAsInstant()));
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
        pushFactory.create(admin.newIdent(), testRepo, "Adjust files of repo", files);
    PushOneCommit.Result result = push.to("refs/for/master");
    return result.getCommit();
  }

  private ObjectId addCommit(ObjectId parentCommit, String filePath, byte[] fileContent)
      throws Exception {
    testRepo.reset(parentCommit);
    PushOneCommit.Result result = createEmptyChange();
    String changeId = result.getChangeId();
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(fileContent));
    gApi.changes().id(changeId).edit().publish();
    String currentRevision = gApi.changes().id(changeId).get().currentRevision;
    GitUtil.fetch(testRepo, "refs/*:refs/*");
    return ObjectId.fromString(currentRevision);
  }

  private ObjectId addCommitRemovingFiles(ObjectId parentCommit, String... removedFilePaths)
      throws Exception {
    testRepo.reset(parentCommit);
    Map<String, String> files =
        Arrays.stream(removedFilePaths)
            .collect(toMap(Function.identity(), path -> "Irrelevant content"));
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "Remove files from repo", files);
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
        pushFactory.create(admin.newIdent(), testRepo, "Test change", ImmutableMap.of());
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

  private static byte[] createRgbImage(int red, int green, int blue) throws IOException {
    BufferedImage bufferedImage = new BufferedImage(10, 20, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < bufferedImage.getWidth(); x++) {
      for (int y = 0; y < bufferedImage.getHeight(); y++) {
        int rgb = (red << 16) + (green << 8) + blue;
        bufferedImage.setRGB(x, y, rgb);
      }
    }

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    ImageIO.write(bufferedImage, "png", byteArrayOutputStream);
    return byteArrayOutputStream.toByteArray();
  }

  private FileApi.DiffRequest getDiffRequest(String changeId, String revisionId, String fileName)
      throws Exception {
    return gApi.changes()
        .id(changeId)
        .revision(revisionId)
        .file(fileName)
        .diffRequest()
        .withIntraline(intraline);
  }

  /**
   * This method transforms the {@code orig} input String using the list of replace edits {@code
   * editsA}, {@code editsB} and the resulting {@code replace} String. This method currently assumes
   * that all input edits are replace edits, and that the edits are sorted according to their
   * indices.
   *
   * @return The transformed String after applying the list of replace edits to the original String.
   */
  private String transformStringUsingEditList(
      String orig, String replace, List<List<Integer>> editsA, List<List<Integer>> editsB) {
    assertThat(editsA).hasSize(editsB.size());
    StringBuilder process = new StringBuilder(orig);
    // The edits are processed right to left to avoid recomputation of indices when characters
    // are removed.
    for (int i = editsA.size() - 1; i >= 0; i--) {
      List<Integer> leftEdit = editsA.get(i);
      List<Integer> rightEdit = editsB.get(i);
      process.replace(
          leftEdit.get(0),
          leftEdit.get(0) + leftEdit.get(1),
          replace.substring(rightEdit.get(0), rightEdit.get(0) + rightEdit.get(1)));
    }
    return process.toString();
  }
}
