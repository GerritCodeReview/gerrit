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

package com.google.gerrit.server.edit.tree;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.RawInputUtil;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TreeCreatorTest {

  private Repository repository;
  private TestRepository<?> testRepository;

  @Before
  public void setUp() throws Exception {
    repository = new InMemoryRepository(new DfsRepositoryDescription("Test Repository"));
    testRepository = new TestRepository<>(repository);
  }

  @After
  public void tearDown() throws Exception {
    if (testRepository != null) {
      testRepository.close();
    }
  }

  @Test
  public void fileContentModificationWorksWithEmptyTree() throws Exception {
    TreeCreator treeCreator = TreeCreator.basedOnEmptyTree();
    treeCreator.addTreeModifications(
        ImmutableList.of(
            new ChangeFileContentModification("file.txt", RawInputUtil.create("Line 1"))));
    ObjectId newTreeId = treeCreator.createNewTreeAndGetId(repository);

    String fileContent = getFileContent(newTreeId, "file.txt");
    assertThat(fileContent).isEqualTo("Line 1");
  }

  @Test
  public void renameFileModificationDoesNotComplainAboutEmptyTree() throws Exception {
    TreeCreator treeCreator = TreeCreator.basedOnEmptyTree();
    treeCreator.addTreeModifications(
        ImmutableList.of(new RenameFileModification("oldfileName", "newFileName")));
    ObjectId newTreeId = treeCreator.createNewTreeAndGetId(repository);

    assertThat(isEmptyTree(newTreeId)).isTrue();
  }

  @Test
  public void deleteFileModificationDoesNotComplainAboutEmptyTree() throws Exception {
    TreeCreator treeCreator = TreeCreator.basedOnEmptyTree();
    treeCreator.addTreeModifications(ImmutableList.of(new DeleteFileModification("file.txt")));
    ObjectId newTreeId = treeCreator.createNewTreeAndGetId(repository);

    assertThat(isEmptyTree(newTreeId)).isTrue();
  }

  @Test
  public void restoreFileModificationDoesNotComplainAboutEmptyTree() throws Exception {
    TreeCreator treeCreator = TreeCreator.basedOnEmptyTree();
    treeCreator.addTreeModifications(ImmutableList.of(new RestoreFileModification("file.txt")));
    ObjectId newTreeId = treeCreator.createNewTreeAndGetId(repository);

    assertThat(isEmptyTree(newTreeId)).isTrue();
  }

  @Test
  public void modificationsMustNotReferToSameFilePaths() {
    TreeCreator treeCreator = TreeCreator.basedOnEmptyTree();
    treeCreator.addTreeModifications(
        ImmutableList.of(
            new RenameFileModification("oldFileName", "newFileName"),
            new ChangeFileContentModification(
                "newFileName", RawInputUtil.create("Different content"))));
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> treeCreator.createNewTreeAndGetId(repository));

    assertThat(exception).hasMessageThat().contains("oldFileName");
    assertThat(exception).hasMessageThat().contains("newFileName");
  }

  @Test
  public void fileContentModificationRefersToModifiedFile() {
    ChangeFileContentModification contentModification =
        new ChangeFileContentModification("myFileName", RawInputUtil.create("Some content"));
    assertThat(contentModification.getFilePaths()).containsExactly("myFileName");
  }

  @Test
  public void renameFileModificationRefersToOldAndNewFilePath() {
    RenameFileModification fileModification =
        new RenameFileModification("oldFileName", "newFileName");
    assertThat(fileModification.getFilePaths()).containsExactly("oldFileName", "newFileName");
  }

  @Test
  public void deleteFileModificationRefersToDeletedFile() {
    DeleteFileModification fileModification = new DeleteFileModification("myFileName");
    assertThat(fileModification.getFilePaths()).containsExactly("myFileName");
  }

  @Test
  public void restoreFileModificationRefersToRestoredFile() {
    RestoreFileModification fileModification = new RestoreFileModification("myFileName");
    assertThat(fileModification.getFilePaths()).containsExactly("myFileName");
  }

  private String getFileContent(ObjectId treeId, String filePath) throws Exception {
    try (RevWalk revWalk = new RevWalk(repository);
        ObjectReader reader = revWalk.getObjectReader()) {
      RevTree revTree = revWalk.parseTree(treeId);
      RevObject revObject = testRepository.get(revTree, filePath);
      return new String(reader.open(revObject, OBJ_BLOB).getBytes(), UTF_8);
    }
  }

  private boolean isEmptyTree(ObjectId treeId) throws Exception {
    try (TreeWalk treeWalk = new TreeWalk(repository)) {
      treeWalk.reset(treeId);
      return !treeWalk.next();
    }
  }
}
