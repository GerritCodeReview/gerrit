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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.notedb.DeleteZombieCommentsRefs;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.BatchRefUpdate;
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
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeleteZombieCommentsRefsTest {
  private InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();
  private Project.NameKey allUsersProject = new Project.NameKey("All-Users");

  @Test
  public void cleanZombieDraftsSmall() throws Exception {
    try (Repository usersRepo = repoManager.createRepository(allUsersProject)) {
      Ref ref1 = createRefWithNonEmptyTreeCommit(usersRepo, 1, 1000001);
      Ref ref2 = createRefWithEmptyTreeCommit(usersRepo, 1, 1000002);

      DeleteZombieCommentsRefs clean =
          new DeleteZombieCommentsRefs(new AllUsersName("All-Users"), repoManager, null);
      clean.execute();

      /* Check that ref1 still exists, and ref2 is deleted */
      assertThat(usersRepo.exactRef(ref1.getName())).isNotNull();
      assertThat(usersRepo.exactRef(ref2.getName())).isNull();
    }
  }

  @Test
  public void cleanZombieDraftsWithPercentage() throws Exception {
    try (Repository usersRepo = repoManager.createRepository(allUsersProject)) {
      Ref ref1 = createRefWithNonEmptyTreeCommit(usersRepo, 1005, 1000001);
      Ref ref2 = createRefWithEmptyTreeCommit(usersRepo, 1006, 1000002);
      Ref ref3 = createRefWithEmptyTreeCommit(usersRepo, 1060, 1000002);

      assertThat(usersRepo.getRefDatabase().getRefs()).hasSize(3);

      int cleanupPercentage = 50;
      DeleteZombieCommentsRefs clean =
          new DeleteZombieCommentsRefs(
              new AllUsersName("All-Users"), repoManager, cleanupPercentage);
      clean.execute();

      /* ref1 not deleted, ref2 deleted, ref3 not deleted because of the clean percentage */
      assertThat(usersRepo.getRefDatabase().getRefs()).hasSize(2);
      assertThat(usersRepo.exactRef(ref1.getName())).isNotNull();
      assertThat(usersRepo.exactRef(ref2.getName())).isNull();
      assertThat(usersRepo.exactRef(ref3.getName())).isNotNull();

      /* Re-execute the cleanup and make sure nothing's changed */
      clean.execute();
      assertThat(usersRepo.getRefDatabase().getRefs()).hasSize(2);
      assertThat(usersRepo.exactRef(ref1.getName())).isNotNull();
      assertThat(usersRepo.exactRef(ref2.getName())).isNull();
      assertThat(usersRepo.exactRef(ref3.getName())).isNotNull();

      /* Increase the cleanup percentage */
      cleanupPercentage = 70;
      clean =
          new DeleteZombieCommentsRefs(
              new AllUsersName("All-Users"), repoManager, cleanupPercentage);

      clean.execute();

      /* Now ref3 is deleted */
      assertThat(usersRepo.getRefDatabase().getRefs()).hasSize(1);
      assertThat(usersRepo.exactRef(ref1.getName())).isNotNull();
      assertThat(usersRepo.exactRef(ref2.getName())).isNull();
      assertThat(usersRepo.exactRef(ref3.getName())).isNull();
    }
  }

  @Test
  public void cleanZombieDraftsLarge() throws Exception {
    try (Repository usersRepo = repoManager.createRepository(allUsersProject)) {
      int goodRefsCnt = 5000;
      int zombieRefsCnt = 5000;
      int userIdGoodRefs = 1000001;
      int userIdBadRefs = 1000002;

      Ref nonEmptyBaseRef = createRefWithNonEmptyTreeCommit(usersRepo, 1, userIdGoodRefs);
      Ref emptyBaseRef = createRefWithEmptyTreeCommit(usersRepo, 1, userIdBadRefs);

      List<String> goodRefs =
          createNRefsOnCommit(
              usersRepo, nonEmptyBaseRef.getObjectId(), goodRefsCnt, userIdGoodRefs);
      List<String> badRefs =
          createNRefsOnCommit(usersRepo, emptyBaseRef.getObjectId(), zombieRefsCnt, userIdBadRefs);

      goodRefs.add(0, nonEmptyBaseRef.getName());
      badRefs.add(0, emptyBaseRef.getName());

      assertThat(usersRepo.getRefDatabase().getRefs().size())
          .isEqualTo(goodRefs.size() + badRefs.size());

      DeleteZombieCommentsRefs clean =
          new DeleteZombieCommentsRefs(new AllUsersName("All-Users"), repoManager, null);
      clean.execute();

      assertThat(
              usersRepo.getRefDatabase().getRefs().stream()
                  .map(Ref::getName)
                  .collect(toImmutableList()))
          .containsExactlyElementsIn(goodRefs);

      assertThat(
              usersRepo.getRefDatabase().getRefs().stream()
                  .map(Ref::getName)
                  .collect(toImmutableList()))
          .containsNoneIn(badRefs);
    }
  }

  private static List<String> createNRefsOnCommit(
      Repository usersRepo, ObjectId commitId, int n, int uuid) throws IOException {
    List<String> refNames = new ArrayList<>();
    BatchRefUpdate bru = usersRepo.getRefDatabase().newBatchUpdate();
    bru.setAtomic(true);
    for (int i = 2; i <= n + 1; i++) {
      String refName = getRefName(i, uuid);
      bru.addCommand(
          new ReceiveCommand(ObjectId.zeroId(), commitId, refName, ReceiveCommand.Type.CREATE));
      refNames.add(refName);
    }
    RefUpdateUtil.executeChecked(bru, usersRepo);
    return refNames;
  }

  private static String getRefName(int changeId, int userId) {
    Change.Id cId = new Change.Id(changeId);
    Account.Id aId = new Account.Id(userId);
    return RefNames.refsDraftComments(cId, aId);
  }

  private static Ref createRefWithNonEmptyTreeCommit(Repository usersRepo, int changeId, int userId)
      throws IOException {
    RevWalk rw = new RevWalk(usersRepo);
    ObjectId fileObj = createBlob(usersRepo, String.format("file %d content", changeId));
    ObjectId treeObj =
        createTree(usersRepo, rw.lookupBlob(fileObj), String.format("file%d.txt", changeId));
    ObjectId commitObj = createCommit(usersRepo, treeObj, null);
    Ref refObj = createRef(usersRepo, commitObj, getRefName(changeId, userId));
    return refObj;
  }

  private static Ref createRefWithEmptyTreeCommit(Repository usersRepo, int changeId, int userId)
      throws IOException {
    ObjectId treeEmpty = createTree(usersRepo, null, "");
    ObjectId commitObj = createCommit(usersRepo, treeEmpty, null);
    Ref refObj = createRef(usersRepo, commitObj, getRefName(changeId, userId));
    return refObj;
  }

  private static Ref createRef(Repository repo, ObjectId commitId, String refName)
      throws IOException {
    RefUpdate update = repo.updateRef(refName);
    update.setNewObjectId(commitId);
    update.setForceUpdate(true);
    update.update();
    return repo.exactRef(refName);
  }

  private static ObjectId createCommit(Repository repo, ObjectId treeId, ObjectId parentCommit)
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
