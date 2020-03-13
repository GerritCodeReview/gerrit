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
import static com.google.gerrit.truth.OptionalSubject.assertThat;
import static java.util.Comparator.comparing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.edit.CommitModification;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.project.ProjectState;
import java.util.ArrayList;
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
    CommitModification commitModification = toCommitModification();
    assertThatList(commitModification.treeModifications()).isEmpty();
    assertThat(commitModification.newCommitMessage()).isEmpty();
  }

  @Test
  public void replacementIsTranslatedToTreeModification() throws Exception {
    FixReplacement fixReplacement =
        new FixReplacement(filePath1, new Range(1, 1, 3, 2), "Modified content");
    mockFileContent(filePath1, "First line\nSecond line\nThird line\n");

    CommitModification commitModification = toCommitModification(fixReplacement);
    ImmutableList<TreeModification> treeModifications = commitModification.treeModifications();
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .filePaths()
        .containsExactly(filePath1);
    assertThatList(treeModifications)
        .onlyElement()
        .asChangeFileContentModification()
        .newContent()
        .isEqualTo("FModified contentird line\n");
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

    CommitModification commitModification =
        toCommitModification(fixReplacement, fixReplacement3, fixReplacement2);
    List<TreeModification> sortedTreeModifications =
        getSortedCopy(commitModification.treeModifications());
    assertThatList(sortedTreeModifications)
        .element(0)
        .asChangeFileContentModification()
        .filePaths()
        .containsExactly(filePath1);
    assertThatList(sortedTreeModifications)
        .element(0)
        .asChangeFileContentModification()
        .newContent()
        .startsWith("First");
    assertThatList(sortedTreeModifications)
        .element(1)
        .asChangeFileContentModification()
        .filePaths()
        .containsExactly(filePath2);
    assertThatList(sortedTreeModifications)
        .element(1)
        .asChangeFileContentModification()
        .newContent()
        .startsWith("1st");
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

    CommitModification commitModification =
        toCommitModification(fixReplacement3, fixReplacement1, fixReplacement2);
    List<TreeModification> sortedTreeModifications =
        getSortedCopy(commitModification.treeModifications());
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

  private void mockFileContent(String filePath, String fileContent) throws Exception {
    when(fileContentUtil.getContent(repository, projectState, patchSetCommitId, filePath))
        .thenReturn(BinaryResult.create(fileContent));
  }

  private CommitModification toCommitModification(FixReplacement... fixReplacements)
      throws Exception {
    return fixReplacementInterpreter.toCommitModification(
        repository, projectState, patchSetCommitId, ImmutableList.copyOf(fixReplacements));
  }

  private static List<TreeModification> getSortedCopy(List<TreeModification> treeModifications) {
    List<TreeModification> sortedTreeModifications = new ArrayList<>(treeModifications);
    // The sorting is only necessary to get a deterministic order. The exact order doesn't matter.
    sortedTreeModifications.sort(
        comparing(
            treeModification -> treeModification.getFilePaths().stream().findFirst().orElse("")));
    return sortedTreeModifications;
  }
}
