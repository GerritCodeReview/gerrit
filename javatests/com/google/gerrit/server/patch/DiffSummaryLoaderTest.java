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
package com.google.gerrit.server.patch;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/** Test class for {@link DiffSummaryLoader}. */
public class DiffSummaryLoaderTest {
  private static final Project.NameKey testProjectName = Project.nameKey("test-project");
  @Inject private DiffSummaryLoader.Factory diffSummaryLoaderFactory;
  @Inject private GitRepositoryManager repoManager;
  private Repository repo;

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
    repo = repoManager.createRepository(testProjectName);
  }

  @Test
  public void mergeCommit_diffAgainst_parent1() throws Exception {
    ObjectId parent1 =
        createCommit(repo, null, ImmutableList.of(new FileEntity("file_1.txt", "file 1 content")));
    ObjectId parent2 =
        createCommit(repo, null, ImmutableList.of(new FileEntity("file_2.txt", "file 2 content")));

    ObjectId merge =
        createMergeCommit(
            repo,
            ImmutableList.of(
                new FileEntity("file_1.txt", "file 1 content"),
                new FileEntity("file_2.txt", "file 2 content")),
            parent1,
            parent2);

    PatchListKey pk = PatchListKey.againstBase(merge, 2);
    DiffSummaryKey key = DiffSummaryKey.fromPatchListKey(pk);
    DiffSummaryLoader loader = diffSummaryLoaderFactory.create(key, testProjectName);
    DiffSummary diffSummary = loader.call();
    assertThat(diffSummary.getPaths()).containsExactly("file_2.txt");
  }

  @Test
  public void mergeCommit_diffAgainst_autoMerge() throws Exception {
    ObjectId parent1 =
        createCommit(repo, null, ImmutableList.of(new FileEntity("file_1.txt", "file 1 content")));
    ObjectId parent2 =
        createCommit(repo, null, ImmutableList.of(new FileEntity("file_2.txt", "file 2 content")));

    ObjectId merge =
        createMergeCommit(
            repo,
            ImmutableList.of(
                new FileEntity("file_1.txt", "file 1 content"),
                new FileEntity("file_2.txt", "file 2 content")),
            parent1,
            parent2);

    PatchListKey pk = PatchListKey.againstDefaultBase(merge, Whitespace.IGNORE_NONE);
    DiffSummaryKey key = DiffSummaryKey.fromPatchListKey(pk);
    DiffSummaryLoader loader = diffSummaryLoaderFactory.create(key, testProjectName);
    DiffSummary diffSummary = loader.call();
    assertThat(diffSummary.getPaths()).isEmpty();
  }

  @Test
  public void nonMergeCommit_diffAgainst_parent() throws Exception {
    String fileName1 = "file_1.txt";
    String fileContent1 = "File content 1";
    String fileName2 = "file_2.txt";
    String fileContent2 = "File content 2";
    ImmutableList<FileEntity> oldFiles =
        ImmutableList.of(
            new FileEntity(fileName1, fileContent1), new FileEntity(fileName2, fileContent2));
    ObjectId oldCommitId = createCommit(repo, null, oldFiles);

    ImmutableList<FileEntity> newFiles =
        ImmutableList.of(
            new FileEntity(fileName1, fileContent1),
            new FileEntity(fileName2, fileContent2 + "\nnew line here"));
    ObjectId newCommitId = createCommit(repo, oldCommitId, newFiles);

    PatchListKey pk = PatchListKey.againstBase(newCommitId, 1);
    DiffSummaryKey key = DiffSummaryKey.fromPatchListKey(pk);
    DiffSummaryLoader loader = diffSummaryLoaderFactory.create(key, testProjectName);
    DiffSummary diffSummary = loader.call();
    assertThat(diffSummary.getPaths()).containsExactly("file_2.txt");

    pk = PatchListKey.againstCommit(oldCommitId, newCommitId, Whitespace.IGNORE_NONE);
    key = DiffSummaryKey.fromPatchListKey(pk);
    assertThat(diffSummaryLoaderFactory.create(key, testProjectName).call().getPaths())
        .containsExactly("file_2.txt");
  }

  static class FileEntity {
    String name;
    String content;
    FileEntity.FileType type;

    enum FileType {
      REGULAR,
      SYMLINK
    }

    FileEntity(String name, String content) {
      this(name, content, FileEntity.FileType.REGULAR);
    }

    FileEntity(String name, String content, FileEntity.FileType type) {
      this.name = name;
      this.content = content;
      this.type = type;
    }
  }

  static ObjectId createCommit(
      Repository repo, @Nullable ObjectId parentCommit, ImmutableList<FileEntity> fileEntities)
      throws IOException {
    ObjectId treeId = createTree(repo, fileEntities);
    return parentCommit == null
        ? createCommitInRepo(repo, treeId)
        : createCommitInRepo(repo, treeId, parentCommit);
  }

  static ObjectId createCommitInRepo(Repository repo, ObjectId treeId, ObjectId... parents)
      throws IOException {
    try (org.eclipse.jgit.lib.ObjectInserter oi = repo.newObjectInserter()) {
      org.eclipse.jgit.lib.PersonIdent committer =
          new org.eclipse.jgit.lib.PersonIdent(
              new org.eclipse.jgit.lib.PersonIdent("Foo Bar", "foo.bar@baz.com"), TimeUtil.now());
      org.eclipse.jgit.lib.CommitBuilder cb = new org.eclipse.jgit.lib.CommitBuilder();
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

  static ObjectId createTree(Repository repo, ImmutableList<FileEntity> fileEntities)
      throws IOException {
    try (org.eclipse.jgit.lib.ObjectInserter oi = repo.newObjectInserter();
        org.eclipse.jgit.lib.ObjectReader reader = repo.newObjectReader();
        org.eclipse.jgit.revwalk.RevWalk rw = new org.eclipse.jgit.revwalk.RevWalk(reader); ) {
      org.eclipse.jgit.lib.TreeFormatter formatter = new org.eclipse.jgit.lib.TreeFormatter();
      for (FileEntity fileEntity : fileEntities) {
        String fileName = fileEntity.name;
        String fileContent = fileEntity.content;
        ObjectId fileObjId = createBlob(repo, fileContent);
        if (fileEntity.type.equals(FileEntity.FileType.REGULAR)) {
          formatter.append(fileName, rw.lookupBlob(fileObjId));
        } else {
          formatter.append(fileName, org.eclipse.jgit.lib.FileMode.SYMLINK, fileObjId);
        }
      }
      ObjectId treeId = oi.insert(formatter);
      oi.flush();
      oi.close();
      return treeId;
    }
  }

  static ObjectId createBlob(Repository repo, String content) throws IOException {
    try (org.eclipse.jgit.lib.ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId blobId = oi.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, content.getBytes(UTF_8));
      oi.flush();
      oi.close();
      return blobId;
    }
  }

  static ObjectId createMergeCommit(
      Repository repo, ImmutableList<FileEntity> fileEntities, ObjectId parent1, ObjectId parent2)
      throws IOException {
    ObjectId treeId = createTree(repo, fileEntities);
    return createCommitInRepo(repo, treeId, parent1, parent2);
  }
}
