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

import static com.google.gerrit.server.edit.tree.TreeModificationSubject.assertThatList;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.project.ProjectState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class FixReplacementInterpreterTest {
  private final FileContentUtil fileContentUtil = mock(FileContentUtil.class);
  private final Repository repository = mock(Repository.class);
  private final ProjectState projectState = mock(ProjectState.class);
  private final ObjectId patchSetCommitId = mock(ObjectId.class);
  private final String filePath1 = "an/arbitrary/file.txt";
  private final String filePath2 = "another/arbitrary/file.txt";

  private FixReplacementInterpreter fixReplacementInterpreter;

  @Before
  public void setUp() {
    fixReplacementInterpreter = new FixReplacementInterpreter(fileContentUtil);
  }

  @Test
  public void noReplacementsResultInNoTreeModifications() throws Exception {
    List<TreeModification> treeModifications = toTreeModifications();
    assertThatList(treeModifications).isEmpty();
  }

  @Test
  public void treeModificationsTargetCorrectFiles() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(1, 6, 3, 2), "Modified content");
    FixReplacement fixReplacement2 =
        new FixReplacement(filePath1, new Range(3, 5, 3, 5), "Second modification");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");
    FixReplacement fixReplacement3 =
        new FixReplacement(filePath2, new Range(2, 0, 3, 0), "Another modified content");
    mockFileContent(filePath2, "1st line\n2nd line\n3rd line\n");

    List<TreeModification> treeModifications =
        toTreeModifications(fixReplacement, fixReplacement3, fixReplacement2);
    List<TreeModification> sortedTreeModifications = getSortedCopy(treeModifications);
    assertThatList(sortedTreeModifications)
        .element(0)
        .asChangeFileContentModification()
        .filePath()
        .isEqualTo(filePath1);
    assertThatList(sortedTreeModifications)
        .element(0)
        .asChangeFileContentModification()
        .newContent()
        .startsWith("First");
    assertThatList(sortedTreeModifications)
        .element(1)
        .asChangeFileContentModification()
        .filePath()
        .isEqualTo(filePath2);
    assertThatList(sortedTreeModifications)
        .element(1)
        .asChangeFileContentModification()
        .newContent()
        .startsWith("1st");
  }

  @Test
  public void replacementsCanDeleteALine() throws Exception {
    FixReplacement fixReplacement = new FixReplacement(filePath1, new Range(2, 0, 3, 0), "");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    List<TreeModification> treeModifications = toTreeModifications(fixReplacement);
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nThird line\n");
  }

  @Test
  public void replacementsCanAddALine() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(2, 0, 2, 0), "A new line\n");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    List<TreeModification> treeModifications = toTreeModifications(fixReplacement);
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nA new line\nSecond line\nThird line\n");
  }

  @Test
  public void replacementsMaySpanMultipleLines() throws Exception {
    FixReplacement fixReplacement = new FixReplacement(filePath1, new Range(1, 6, 3, 1), "and t");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    List<TreeModification> treeModifications = toTreeModifications(fixReplacement);
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First and third line\n");
  }

  @Test
  public void replacementsMayOccurOnSameLine() throws Exception {
    FixReplacement fixReplacement1 = new FixReplacement(filePath1, new Range(2, 0, 2, 6), "A");
    FixReplacement fixReplacement2 =
        new FixReplacement(filePath1, new Range(2, 7, 2, 11), "modification");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    List<TreeModification> treeModifications =
        toTreeModifications(fixReplacement1, fixReplacement2);
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nA modification\nThird line\n");
  }

  @Test()
  public void startAfterEndOfLineMarkThrowsAnException() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(1, 11, 2, 6), "A modification");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");
    assertThrows(ResourceConflictException.class, () -> toTreeModifications(fixReplacement));
  }

  @Test()
  public void endAfterEndOfLineMarkThrowsAnException() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(2, 0, 2, 12), "A modification");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");
    assertThrows(ResourceConflictException.class, () -> toTreeModifications(fixReplacement));
  }

  @Test
  public void replacementsMayTouch() throws Exception {
    FixReplacement fixReplacement1 =
        new FixReplacement(filePath1, new Range(1, 6, 2, 7), "modified ");
    FixReplacement fixReplacement2 =
        new FixReplacement(filePath1, new Range(2, 7, 3, 5), "content");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    List<TreeModification> treeModifications =
        toTreeModifications(fixReplacement1, fixReplacement2);
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First modified content line\n");
  }

  @Test
  public void replacementsCanAddContentAtEndOfFile() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(4, 0, 4, 0), "New content");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    List<TreeModification> treeModifications = toTreeModifications(fixReplacement);
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nSecond line\nThird line\nNew content");
  }

  @Test
  public void replacementsCanChangeLastLine() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(3, 0, 4, 0), "New content\n");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    List<TreeModification> treeModifications = toTreeModifications(fixReplacement);
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nSecond line\nNew content\n");
  }

  @Test
  public void replacementsCanChangeLastLineWithoutEOLMark() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(3, 0, 3, 10), "New content\n");
    mockFileContent(filePath1, "First line\nSecond line\nThird line");

    List<TreeModification> treeModifications = toTreeModifications(fixReplacement);
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nSecond line\nNew content\n");
  }

  @Test
  public void replacementsCanModifySeveralFilesInAnyOrder() throws Exception {
    FixReplacement fixReplacement1 =
        new FixReplacement(filePath1, new Range(1, 1, 3, 2), "Modified content");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");
    FixReplacement fixReplacement2 =
        new FixReplacement(filePath2, new Range(2, 0, 3, 0), "First modification\n");
    FixReplacement fixReplacement3 =
        new FixReplacement(filePath2, new Range(3, 0, 4, 0), "Second modification\n");
    mockFileContent(filePath2, "1st line\n2nd line\n3rd line\n");

    List<TreeModification> treeModifications =
        toTreeModifications(fixReplacement3, fixReplacement1, fixReplacement2);
    List<TreeModification> sortedTreeModifications = getSortedCopy(treeModifications);
    assertThatList(sortedTreeModifications)
        .element(0)
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("FModified contentird line\n");
    assertThatList(sortedTreeModifications)
        .element(1)
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("1st line\nFirst modification\nSecond modification\n");
  }

  @Test
  public void lineSeparatorCanBeChanged() throws Exception {
    FixReplacement fixReplacement = new FixReplacement(filePath1, new Range(2, 11, 3, 0), "\r");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    List<TreeModification> treeModifications = toTreeModifications(fixReplacement);
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("First line\nSecond line\rThird line\n");
  }

  @Test
  public void replacementsDoNotNeedToBeOrderedAccordingToRange() throws Exception {
    FixReplacement fixReplacement1 =
        new FixReplacement(filePath1, new Range(1, 0, 2, 0), "1st modification\n");
    FixReplacement fixReplacement2 =
        new FixReplacement(filePath1, new Range(3, 0, 4, 0), "2nd modification\n");
    FixReplacement fixReplacement3 =
        new FixReplacement(filePath1, new Range(4, 0, 5, 0), "3rd modification\n");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\nFourth line\nFifth line\n");

    List<TreeModification> treeModifications =
        toTreeModifications(fixReplacement2, fixReplacement1, fixReplacement3);
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo(
            "1st modification\nSecond line\n2nd modification\n3rd modification\nFifth line\n");
  }

  @Test
  public void replacementsMustNotReferToNotExistingLine() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(5, 0, 5, 0), "A new line\n");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    assertThrows(ResourceConflictException.class, () -> toTreeModifications(fixReplacement));
  }

  @Test
  public void replacementsMustNotReferToZeroLine() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(0, 0, 0, 0), "A new line\n");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    assertThrows(ResourceConflictException.class, () -> toTreeModifications(fixReplacement));
  }

  @Test
  public void replacementsMustNotReferToNotExistingOffsetOfIntermediateLine() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(1, 0, 1, 11), "modified");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    assertThrows(ResourceConflictException.class, () -> toTreeModifications(fixReplacement));
  }

  @Test
  public void replacementsMustNotReferToNotExistingOffsetOfLastLine() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(3, 0, 3, 11), "modified");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    assertThrows(ResourceConflictException.class, () -> toTreeModifications(fixReplacement));
  }

  @Test
  public void replacementsMustNotReferToNegativeOffset() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(1, -1, 1, 5), "modified");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    assertThrows(ResourceConflictException.class, () -> toTreeModifications(fixReplacement));
  }

  private void mockFileContent(String filePath, String fileContent) throws Exception {
    when(fileContentUtil.getContent(repository, projectState, patchSetCommitId, filePath))
        .thenReturn(BinaryResult.create(fileContent));
  }

  private List<TreeModification> toTreeModifications(FixReplacement... fixReplacements)
      throws Exception {
    return fixReplacementInterpreter.toTreeModifications(
        repository, projectState, patchSetCommitId, ImmutableList.copyOf(fixReplacements));
  }

  private static List<TreeModification> getSortedCopy(List<TreeModification> treeModifications) {
    List<TreeModification> sortedTreeModifications = new ArrayList<>(treeModifications);
    sortedTreeModifications.sort(Comparator.comparing(TreeModification::getFilePath));
    return sortedTreeModifications;
  }
}
