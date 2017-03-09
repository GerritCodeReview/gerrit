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

package com.google.gerrit.server.fixes;

import static com.google.gerrit.server.edit.tree.TreeModificationSubject.assertThat;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Comment.Range;
import com.google.gerrit.reviewdb.client.FixReplacement;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.project.ProjectState;
import org.easymock.EasyMock;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FixReplacementInterpreterTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

  private final FileContentUtil fileContentUtil = createMock(FileContentUtil.class);
  private final Repository repository = createMock(Repository.class);
  private final ProjectState projectState = createMock(ProjectState.class);
  private final ObjectId patchSetCommitId = createMock(ObjectId.class);
  private final String filePath = "an/arbitrary/file.txt";

  private FixReplacementInterpreter fixReplacementInterpreter;

  @Before
  public void setUp() {
    fixReplacementInterpreter = new FixReplacementInterpreter(fileContentUtil);
  }

  @Test
  public void treeModificationTargetsCorrectFile() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath, new Range(1, 1, 3, 2), "Modified content");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);
    TreeModification treeModification = toTreeModification(fixReplacement);
    assertThat(treeModification).asChangeFileContentModification().filePath().isEqualTo(filePath);
  }

  @Test
  public void replacementsCanDeleteALine() throws Exception {
    FixReplacement fixReplacement = new FixReplacement(filePath, new Range(2, 0, 3, 0), "");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);
    TreeModification treeModification = toTreeModification(fixReplacement);
    assertThat(treeModification)
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nThird line\n");
  }

  @Test
  public void replacementsCanAddALine() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath, new Range(2, 0, 2, 0), "A new line\n");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);
    TreeModification treeModification = toTreeModification(fixReplacement);
    assertThat(treeModification)
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nA new line\nSecond line\nThird line\n");
  }

  @Test
  public void replacementsMaySpanMultipleLines() throws Exception {
    FixReplacement fixReplacement = new FixReplacement(filePath, new Range(1, 6, 3, 1), "and t");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);
    TreeModification treeModification = toTreeModification(fixReplacement);
    assertThat(treeModification)
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First and third line\n");
  }

  @Test
  public void replacementsMayOccurOnSameLine() throws Exception {
    FixReplacement fixReplacement1 = new FixReplacement(filePath, new Range(2, 0, 2, 6), "A");
    FixReplacement fixReplacement2 =
        new FixReplacement(filePath, new Range(2, 7, 2, 11), "modification");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);
    TreeModification treeModification = toTreeModification(fixReplacement1, fixReplacement2);
    assertThat(treeModification)
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nA modification\nThird line\n");
  }

  @Test
  public void replacementsMayTouch() throws Exception {
    FixReplacement fixReplacement1 =
        new FixReplacement(filePath, new Range(1, 6, 2, 7), "modified ");
    FixReplacement fixReplacement2 = new FixReplacement(filePath, new Range(2, 7, 3, 5), "content");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);
    TreeModification treeModification = toTreeModification(fixReplacement1, fixReplacement2);
    assertThat(treeModification)
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First modified content line\n");
  }

  @Test
  public void replacementsCanAddContentAtEndOfFile() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath, new Range(4, 0, 4, 0), "New content");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);
    TreeModification treeModification = toTreeModification(fixReplacement);
    assertThat(treeModification)
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nSecond line\nThird line\nNew content");
  }

  @Test
  public void lineSeparatorCanBeChanged() throws Exception {
    FixReplacement fixReplacement = new FixReplacement(filePath, new Range(2, 11, 3, 0), "\r");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);
    TreeModification treeModification = toTreeModification(fixReplacement);
    assertThat(treeModification)
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nSecond line\rThird line\n");
  }

  @Test
  public void replacementsDoNotNeedToBeOrderedAccordingToRange() throws Exception {
    FixReplacement fixReplacement1 =
        new FixReplacement(filePath, new Range(1, 0, 2, 0), "1st modification\n");
    FixReplacement fixReplacement2 =
        new FixReplacement(filePath, new Range(3, 0, 4, 0), "2nd modification\n");
    FixReplacement fixReplacement3 =
        new FixReplacement(filePath, new Range(4, 0, 5, 0), "3rd modification\n");
    mockFileContent(filePath, "First line\nSecond line\nThird line\nFourth line\nFifth line\n");

    replay(fileContentUtil);
    TreeModification treeModification =
        toTreeModification(fixReplacement2, fixReplacement1, fixReplacement3);
    assertThat(treeModification)
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo(
            "1st modification\nSecond line\n2nd modification\n3rd modification\nFifth line\n");
  }

  @Test
  public void replacementsMustNotReferToNotExistingLine() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath, new Range(5, 0, 5, 0), "A new line\n");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);

    expectedException.expect(ResourceConflictException.class);
    toTreeModification(fixReplacement);
  }

  @Test
  public void replacementsMustNotReferToZeroLine() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath, new Range(0, 0, 0, 0), "A new line\n");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);

    expectedException.expect(ResourceConflictException.class);
    toTreeModification(fixReplacement);
  }

  @Test
  public void replacementsMustNotReferToNotExistingOffsetOfIntermediateLine() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath, new Range(1, 0, 1, 11), "modified");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);

    expectedException.expect(ResourceConflictException.class);
    toTreeModification(fixReplacement);
  }

  @Test
  public void replacementsMustNotReferToNotExistingOffsetOfLastLine() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath, new Range(3, 0, 3, 11), "modified");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);

    expectedException.expect(ResourceConflictException.class);
    toTreeModification(fixReplacement);
  }

  @Test
  public void replacementsMustNotReferToNegativeOffset() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath, new Range(1, -1, 1, 5), "modified");
    mockFileContent(filePath, "First line\nSecond line\nThird line\n");

    replay(fileContentUtil);

    expectedException.expect(ResourceConflictException.class);
    toTreeModification(fixReplacement);
  }

  private void mockFileContent(String filePath, String fileContent) throws Exception {
    EasyMock.expect(
            fileContentUtil.getContent(repository, projectState, patchSetCommitId, filePath))
        .andReturn(BinaryResult.create(fileContent));
  }

  private TreeModification toTreeModification(FixReplacement... fixReplacements) throws Exception {
    return fixReplacementInterpreter.toTreeModification(
        repository, projectState, patchSetCommitId, ImmutableList.copyOf(fixReplacements));
  }
}
