// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.RevisionsComparator.RelationType;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

/** Test class for {@link RevisionsComparator}. */
public class RevisionsComparatorTest {
  private static final Project.NameKey testProjectName = Project.nameKey("test-project");

  @Inject private GitRepositoryManager repoManager;
  @Inject private RevisionsComparator revisionsComparator;
  private Repository repo;

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
    repo = repoManager.createRepository(testProjectName);
  }

  @Test
  public void identical() throws Exception {
    ObjectId base = createCommit(repo, null);
    assertThat(revisionsComparator.getRelationType(testProjectName, base, base))
        .isEqualTo(RelationType.IDENTICAL);
  }

  @Test
  public void sameParent() throws Exception {
    ObjectId baseCommit = createCommit(repo, null);
    ObjectId c1 = createCommit(repo, baseCommit);
    ObjectId c2 = createCommit(repo, baseCommit);
    assertThat(revisionsComparator.getRelationType(testProjectName, c1, c2))
        .isEqualTo(RelationType.SAME_PARENT);
  }

  @Test
  public void lhsParentOfRhs() throws Exception {
    ObjectId baseCommit = createCommit(repo, null);
    ObjectId c1 = createCommit(repo, baseCommit);
    ObjectId c2 = createCommit(repo, c1);
    assertThat(revisionsComparator.getRelationType(testProjectName, c1, c2))
        .isEqualTo(RelationType.LHS_PARENT_OF_RHS);
  }

  @Test
  public void unrelated() throws Exception {
    ObjectId base1 = createCommit(repo, null);
    ObjectId c1 = createCommit(repo, base1);

    ObjectId base2 = createCommit(repo, null);
    ObjectId c2 = createCommit(repo, base2);

    assertThat(revisionsComparator.getRelationType(testProjectName, c1, c2))
        .isEqualTo(RelationType.OTHER);
  }

  @Test
  public void lhsParentAncestorOfRhsParent() throws Exception {
    ObjectId base = createCommit(repo, null);
    ObjectId t1 = createCommit(repo, base);
    ObjectId c1 = createCommit(repo, t1);
    ObjectId c2 = createCommit(repo, base);

    assertThat(revisionsComparator.getRelationType(testProjectName, c2, c1))
        .isEqualTo(RelationType.LHS_PARENT_ANCESTOR_OF_RHS_PARENT);
  }

  @Test
  public void rhsParentAncestorOfLhsParent() throws Exception {
    ObjectId base = createCommit(repo, null);
    ObjectId t1 = createCommit(repo, base);
    ObjectId c1 = createCommit(repo, t1);
    ObjectId c2 = createCommit(repo, base);

    assertThat(revisionsComparator.getRelationType(testProjectName, c1, c2))
        .isEqualTo(RelationType.RHS_PARENT_ANCESTOR_OF_LHS_PARENT);
  }

  @Test
  public void mergeCommit_againstParent1() throws Exception {
    ObjectId base = createCommit(repo, null);
    ObjectId c1 = createCommit(repo, base);
    ObjectId c2 = createCommit(repo, base);
    ObjectId merge = createCommit(repo, c1, c2);

    assertThat(revisionsComparator.getRelationType(testProjectName, c1, merge))
        .isEqualTo(RelationType.MERGE_COMMIT);
  }

  @Test
  public void mergeCommit_againstParent2() throws Exception {
    ObjectId base = createCommit(repo, null);
    ObjectId c1 = createCommit(repo, base);
    ObjectId c2 = createCommit(repo, base);
    ObjectId merge = createCommit(repo, c1, c2);

    assertThat(revisionsComparator.getRelationType(testProjectName, c2, merge))
        .isEqualTo(RelationType.MERGE_COMMIT);
  }

  @Test
  public void mergeCommit_twoMergeCommitsAgainstEachOther() throws Exception {
    ObjectId base = createCommit(repo, null);
    ObjectId c1 = createCommit(repo, base);
    ObjectId c2 = createCommit(repo, base);
    ObjectId c3 = createCommit(repo, base);
    ObjectId c4 = createCommit(repo, base);
    ObjectId merge1 = createCommit(repo, c1, c2);
    ObjectId merge2 = createCommit(repo, c3, c4);

    assertThat(revisionsComparator.getRelationType(testProjectName, merge1, merge2))
        .isEqualTo(RelationType.MERGE_COMMIT);
  }

  @Test
  public void commonBase() throws Exception {
    ObjectId base = createCommit(repo, null);
    ObjectId t1 = createCommit(repo, base);
    ObjectId c1 = createCommit(repo, t1);
    ObjectId t2 = createCommit(repo, base);
    ObjectId c2 = createCommit(repo, t2);

    assertThat(revisionsComparator.getRelationType(testProjectName, c1, c2))
        .isEqualTo(RelationType.COMMON_BASE);
  }

  private static ObjectId createCommit(Repository repo, @Nullable ObjectId... parentCommits)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter();
        RevWalk rw = new RevWalk(repo)) {
      ObjectId fileObj =
          createBlob(repo, String.format("file %d content", ThreadLocalRandom.current().nextInt()));
      ObjectId treeId =
          createTree(
              repo,
              rw.lookupBlob(fileObj),
              String.format("file%d.txt", ThreadLocalRandom.current().nextInt()));
      PersonIdent committer =
          new PersonIdent(new PersonIdent("Foo Bar", "foo.bar@baz.com"), TimeUtil.now());
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(treeId);
      cb.setCommitter(committer);
      cb.setAuthor(committer);
      cb.setMessage("Test commit");
      if (parentCommits != null) {
        cb.setParentIds(parentCommits);
      }
      ObjectId commitId = oi.insert(cb);
      oi.flush();
      oi.close();
      return commitId;
    }
  }

  private static ObjectId createTree(Repository repo, RevBlob blob, String blobName)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      TreeFormatter formatter = new TreeFormatter();
      if (blob != null) {
        formatter.append(blobName, blob);
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
