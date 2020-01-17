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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.notedb.DeleteDeadDraftCommentsRefs;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.TempFileUtil;
import java.io.IOException;
import org.easymock.EasyMockSupport;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class DeleteDeadDraftCommentsRefsTest extends EasyMockSupport {
  private LocalDiskRepositoryManager repoManager;

  @Before
  public void setUp() throws Exception {
    SitePaths site = new SitePaths(TempFileUtil.createTempDirectory().toPath());
    site.resolve("git").toFile().mkdir();
    Config cfg = new Config();
    cfg.setString("gerrit", null, "basePath", "git");
    repoManager = new LocalDiskRepositoryManager(site, cfg);
  }

  @Test
  public void cleanZombieDrafts() throws Exception {
    Project.NameKey allUsersProject = new Project.NameKey("All-Users");
    Repository usersRepo = repoManager.createRepository(allUsersProject);

    RevWalk rw = new RevWalk(usersRepo);

    ObjectId file1 = createBlob(usersRepo, "file 1 content");
    ObjectId file2 = createBlob(usersRepo, "file 2 content");
    ObjectId file3 = createBlob(usersRepo, "file 3 content");

    ObjectId tree1 = createTree(usersRepo, getBlob(rw, file1), "file1.txt");
    ObjectId tree2 = createTree(usersRepo, getBlob(rw, file2), "file2.txt");
    ObjectId tree3 = createTree(usersRepo, getBlob(rw, file3), "file3.txt");
    ObjectId treeEmpty = createTree(usersRepo, null, "");

    ObjectId commit1 = createCommit(usersRepo, tree1, null);
    ObjectId commit2 = createCommit(usersRepo, tree2, commit1);
    ObjectId commit3 = createCommit(usersRepo, tree3, null);
    ObjectId commit4 = createCommit(usersRepo, treeEmpty, commit3);

    /* ref1 points to a non-empty tree. ref2 points to an empty tree */
    Ref ref1 = createRef(usersRepo, commit2, "refs/draft-comments/ref1");
    Ref ref2 = createRef(usersRepo, commit4, "refs/draft-comments/ref2");

    assertThat(ref1.getObjectId()).isEqualTo(commit2);
    assertThat(ref2.getObjectId()).isEqualTo(commit4);

    DeleteDeadDraftCommentsRefs clean = new DeleteDeadDraftCommentsRefs(usersRepo);
    clean.execute();

    /* Check that ref1 still exists, and ref2 is deleted */
    assertThat(usersRepo.exactRef("refs/draft-comments/ref1").getObjectId()).isEqualTo(commit2);
    assertThat(usersRepo.exactRef("refs/draft-comments/ref2")).isNull();

    usersRepo.close();
  }

  private RevBlob getBlob(RevWalk rw, ObjectId objectId) {
    return rw.lookupBlob(objectId);
  }

  private Ref createRef(Repository repo, ObjectId commitId, String refName) throws IOException {
    RefUpdate update = repo.updateRef(refName);
    update.setNewObjectId(commitId);
    update.setForceUpdate(true);
    update.update();
    return repo.exactRef(refName);
  }

  private ObjectId createCommit(Repository repo, ObjectId treeId, ObjectId parentCommit)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      PersonIdent committer =
          new PersonIdent(new PersonIdent("Foo Bar", "foo.bar@baz.com"), TimeUtil.nowTs());
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(treeId);
      cb.setCommitter(committer);
      cb.setAuthor(committer);
      cb.setMessage("Test commit");
      if (parentCommit != null) {
        cb.setParentIds(parentCommit);
      }
      ObjectId commitId = oi.insert(cb);
      oi.flush();
      oi.close();
      return commitId;
    }
  }

  private ObjectId createTree(Repository repo, RevBlob blob, String blobName) throws IOException {
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

  private ObjectId createBlob(Repository repo, String content) throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId blobId = oi.insert(Constants.OBJ_BLOB, content.getBytes(UTF_8));
      oi.flush();
      oi.close();
      return blobId;
    }
  }
}
