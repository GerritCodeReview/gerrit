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

package com.google.gerrit.server.patch;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffOperationsTest.FileEntity.FileType;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

/** Test class for diff related logic of {@link DiffOperations}. */
public class DiffOperationsTest {
  @Inject private GitRepositoryManager repoManager;
  @Inject private DiffOperations diffOperations;

  private static final Project.NameKey testProjectName = Project.nameKey("test-project");
  private Repository repo;

  private final String fileName1 = "file_1.txt";
  private final String fileContent1 = "File content 1";
  private final String fileName2 = "file_2.txt";
  private final String fileContent2 = "File content 2";

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
    repo = repoManager.createRepository(testProjectName);
  }

  @Test
  public void diffModifiedFileAgainstParent() throws Exception {
    ImmutableList<FileEntity> oldFiles =
        ImmutableList.of(
            new FileEntity(fileName1, fileContent1), new FileEntity(fileName2, fileContent2));
    ObjectId oldCommitId = createCommit(repo, null, oldFiles);

    ImmutableList<FileEntity> newFiles =
        ImmutableList.of(
            new FileEntity(fileName1, fileContent1),
            new FileEntity(fileName2, fileContent2 + "\nnew line here"));
    ObjectId newCommitId = createCommit(repo, oldCommitId, newFiles);

    FileDiffOutput diffOutput =
        diffOperations.getModifiedFileAgainstParent(
            testProjectName, newCommitId, /* parentNum=*/ 0, fileName2, /* whitespace=*/ null);

    assertThat(diffOutput.oldCommitId()).isEqualTo(oldCommitId);
    assertThat(diffOutput.newCommitId()).isEqualTo(newCommitId);
    assertThat(diffOutput.comparisonType().isAgainstParent()).isTrue();
    assertThat(diffOutput.edits()).hasSize(1);
  }

  @Test
  public void diffAgainstAutoMergeDoesNotPersistAutoMergeInRepo() throws Exception {
    ObjectId parent1 =
        createCommit(repo, null, ImmutableList.of(new FileEntity("file_1.txt", "file 1 content")));
    ObjectId parent2 =
        createCommit(repo, null, ImmutableList.of(new FileEntity("file_2.txt", "file 2 content")));

    ObjectId merge =
        createMergeCommit(
            repo,
            ImmutableList.of(
                new FileEntity("file_1.txt", "file 1 content"),
                new FileEntity("file_2.txt", "file 2 content"),
                new FileEntity("file_3.txt", "file 3 content")),
            parent1,
            parent2);

    String autoMergeRef = RefNames.refsCacheAutomerge(merge.name());
    assertThat(repo.getRefDatabase().exactRef(autoMergeRef)).isNull();

    Map<String, FileDiffOutput> changedFiles =
        diffOperations.listModifiedFilesAgainstParent(
            testProjectName, merge, /* parentNum=*/ 0, DiffOptions.DEFAULTS);
    assertThat(changedFiles.keySet()).containsExactly("/COMMIT_MSG", "/MERGE_LIST", "file_3.txt");

    assertThat(repo.getRefDatabase().exactRef(autoMergeRef)).isNull();
  }

  @Test
  public void loadModifiedFiles() throws Exception {
    ImmutableList<FileEntity> oldFiles =
        ImmutableList.of(
            new FileEntity(fileName1, fileContent1), new FileEntity(fileName2, fileContent2));
    ObjectId oldCommitId = createCommit(repo, null, oldFiles);

    ImmutableList<FileEntity> newFiles =
        ImmutableList.of(
            new FileEntity(fileName1, fileContent1),
            new FileEntity(fileName2, fileContent2 + "\nnew line here"));
    ObjectId newCommitId = createCommit(repo, oldCommitId, newFiles);

    Repository repository = repoManager.openRepository(testProjectName);
    ObjectReader objectReader = repository.newObjectReader();
    RevWalk rw = new RevWalk(objectReader);
    StoredConfig repoConfig = repository.getConfig();

    // This call loads modified files directly without going through the diff cache.
    Map<String, ModifiedFile> modifiedFiles =
        diffOperations.loadModifiedFiles(
            testProjectName, newCommitId, oldCommitId, DiffOptions.DEFAULTS, rw, repoConfig);

    assertThat(modifiedFiles)
        .containsExactly(
            fileName2,
            ModifiedFile.builder()
                .changeType(ChangeType.MODIFIED)
                .oldPath(Optional.of(fileName2))
                .newPath(Optional.of(fileName2))
                .build());
  }

  @Test
  public void loadModifiedFiles_withSymlinkConvertedToRegularFile() throws Exception {
    // Commit 1: Create a regular fileName1 with fileContent1
    ImmutableList<FileEntity> oldFiles = ImmutableList.of(new FileEntity(fileName1, fileContent1));
    ObjectId oldCommitId = createCommit(repo, null, oldFiles);

    // Commit 2: Create a symlink with name FileName1 pointing to target file "target"
    ImmutableList<FileEntity> newFiles =
        ImmutableList.of(new FileEntity(fileName1, "target", FileType.SYMLINK));
    ObjectId newCommitId = createCommit(repo, oldCommitId, newFiles);

    Repository repository = repoManager.openRepository(testProjectName);
    ObjectReader objectReader = repository.newObjectReader();

    Map<String, ModifiedFile> modifiedFiles =
        diffOperations.loadModifiedFiles(
            testProjectName,
            newCommitId,
            oldCommitId,
            DiffOptions.DEFAULTS,
            new RevWalk(objectReader),
            repository.getConfig());

    assertThat(modifiedFiles)
        .containsExactly(
            fileName1,
            ModifiedFile.builder()
                .changeType(ChangeType.REWRITE)
                .oldPath(Optional.empty())
                .newPath(Optional.of(fileName1))
                .build());
  }

  @Test
  public void loadModifiedFilesAgainstParent() throws Exception {
    ImmutableList<FileEntity> oldFiles =
        ImmutableList.of(
            new FileEntity(fileName1, fileContent1), new FileEntity(fileName2, fileContent2));
    ObjectId oldCommitId = createCommit(repo, null, oldFiles);

    ImmutableList<FileEntity> newFiles =
        ImmutableList.of(
            new FileEntity(fileName1, fileContent1),
            new FileEntity(fileName2, fileContent2 + "\nnew line here"));
    ObjectId newCommitId = createCommit(repo, oldCommitId, newFiles);

    Repository repository = repoManager.openRepository(testProjectName);
    ObjectReader objectReader = repository.newObjectReader();
    RevWalk rw = new RevWalk(objectReader);
    StoredConfig repoConfig = repository.getConfig();

    // This call loads modified files directly without going through the diff cache.
    Map<String, ModifiedFile> modifiedFiles =
        diffOperations.loadModifiedFilesAgainstParent(
            testProjectName, newCommitId, /* parentNum=*/ 0, DiffOptions.DEFAULTS, rw, repoConfig);

    assertThat(modifiedFiles)
        .containsExactly(
            fileName2,
            ModifiedFile.builder()
                .changeType(ChangeType.MODIFIED)
                .oldPath(Optional.of(fileName2))
                .newPath(Optional.of(fileName2))
                .build());
  }

  static class FileEntity {
    String name;
    String content;
    FileType type;

    enum FileType {
      REGULAR,
      SYMLINK
    }

    FileEntity(String name, String content) {
      this(name, content, FileType.REGULAR);
    }

    FileEntity(String name, String content, FileType type) {
      this.name = name;
      this.content = content;
      this.type = type;
    }
  }

  private ObjectId createMergeCommit(
      Repository repo, ImmutableList<FileEntity> fileEntities, ObjectId parent1, ObjectId parent2)
      throws IOException {
    ObjectId treeId = createTree(repo, fileEntities);
    return createCommitInRepo(repo, treeId, parent1, parent2);
  }

  private ObjectId createCommit(
      Repository repo, @Nullable ObjectId parentCommit, ImmutableList<FileEntity> fileEntities)
      throws IOException {
    ObjectId treeId = createTree(repo, fileEntities);
    return parentCommit == null
        ? createCommitInRepo(repo, treeId)
        : createCommitInRepo(repo, treeId, parentCommit);
  }

  private static ObjectId createCommitInRepo(Repository repo, ObjectId treeId, ObjectId... parents)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      PersonIdent committer =
          new PersonIdent(new PersonIdent("Foo Bar", "foo.bar@baz.com"), TimeUtil.now());
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(treeId);
      cb.setCommitter(committer);
      cb.setAuthor(committer);
      cb.setMessage("Test commit");
      if (parents != null && parents.length > 0) {
        cb.setParentIds(parents);
      }
      ObjectId commitId = oi.insert(cb);
      oi.flush();
      oi.close();
      return commitId;
    }
  }

  private static ObjectId createTree(Repository repo, ImmutableList<FileEntity> fileEntities)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader); ) {
      TreeFormatter formatter = new TreeFormatter();
      for (FileEntity fileEntity : fileEntities) {
        String fileName = fileEntity.name;
        String fileContent = fileEntity.content;
        ObjectId fileObjId = createBlob(repo, fileContent);
        if (fileEntity.type.equals(FileType.REGULAR)) {
          formatter.append(fileName, rw.lookupBlob(fileObjId));
        } else {
          formatter.append(fileName, FileMode.SYMLINK, fileObjId);
        }
      }
      ObjectId treeId = oi.insert(formatter);
      oi.flush();
      oi.close();
      return treeId;
    }
  }

  private static ObjectId createBlob(Repository repo, String content) throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId blobId = oi.insert(Constants.OBJ_BLOB, content.getBytes(UTF_8));
      oi.flush();
      oi.close();
      return blobId;
    }
  }
}
