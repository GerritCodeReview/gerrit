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
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.notedb.DeleteZombieCommentsRefs;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.CommitBuilder;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeleteZombieCommentsRefsTest {
  private InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();

  @Test
  public void cleanZombieDraftsSmall() throws Exception {
    Project.NameKey allUsersProject = new Project.NameKey("All-Users");
    Repository usersRepo = repoManager.createRepository(allUsersProject);

    Ref ref1 = createNonEmptyTreeRef(usersRepo, 1);
    Ref ref2 = createEmptyTreeRef(usersRepo, 1);

    DeleteZombieCommentsRefs clean =
        new DeleteZombieCommentsRefs(new AllUsersName("All-Users"), repoManager);
    clean.execute();

    /* Check that ref1 still exists, and ref2 is deleted */
    assertThat(usersRepo.exactRef(ref1.getName())).isNotNull();
    assertThat(usersRepo.exactRef(ref2.getName())).isNull();

    usersRepo.close();
  }

  @Test
  public void cleanZombieDraftsLarge() throws Exception {
    Project.NameKey allUsersProject = new Project.NameKey("All-Users");
    Repository usersRepo = repoManager.createRepository(allUsersProject);

    final int goodRefsCnt = 550;
    final int zombieRefsCnt = 550;

    List<String> goodRefs = new ArrayList<>();
    List<String> zombieRefs = new ArrayList<>();

    for (int i = 1; i <= goodRefsCnt; i++) {
      goodRefs.add(createNonEmptyTreeRef(usersRepo, i).getName());
    }

    for (int i = 1; i <= zombieRefsCnt; i++) {
      zombieRefs.add(createEmptyTreeRef(usersRepo, i).getName());
    }

    DeleteZombieCommentsRefs clean =
        new DeleteZombieCommentsRefs(new AllUsersName("All-Users"), repoManager);
    clean.execute();

    for (String goodRef : goodRefs) {
      assertThat(usersRepo.exactRef(goodRef)).isNotNull();
    }

    for (String badRef : zombieRefs) {
      assertThat(usersRepo.exactRef(badRef)).isNull();
    }

    usersRepo.close();
  }

  private Ref createNonEmptyTreeRef(Repository usersRepo, int uuid) throws IOException {
    RevWalk rw = new RevWalk(usersRepo);
    ObjectId fileObj = createBlob(usersRepo, String.format("file %d content", uuid));
    ObjectId treeObj =
        createTree(usersRepo, rw.lookupBlob(fileObj), String.format("file%d.txt", uuid));
    ObjectId commitObj = createCommit(usersRepo, treeObj, null);
    Ref refObj =
        createRef(
            usersRepo, commitObj, String.format("refs/draft-comments/%d/%d/1000001", uuid, uuid));
    return refObj;
  }

  private Ref createEmptyTreeRef(Repository usersRepo, int uuid) throws IOException {
    ObjectId treeEmpty = createTree(usersRepo, null, "");
    ObjectId commitObj = createCommit(usersRepo, treeEmpty, null);
    Ref refObj =
        createRef(
            usersRepo, commitObj, String.format("refs/draft-comments/%d/%d/1000002", uuid, uuid));
    return refObj;
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
