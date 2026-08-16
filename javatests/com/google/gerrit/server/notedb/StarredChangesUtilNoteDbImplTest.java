// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.StarredChangesReader;
import com.google.gerrit.server.StarredChangesWriter;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class StarredChangesUtilNoteDbImplTest extends AbstractChangeNotesTest {
  @Inject private StarredChangesReader starredChangesReader;
  @Inject private StarredChangesWriter starredChangesWriter;
  @Inject private StarredChangesUtilNoteDbImpl starredChangesUtilNoteDbImpl;

  @Test
  public void isStarred() throws Exception {
    Change.Id changeId1 = Change.id(1);
    Change.Id changeId2 = Change.id(2);

    assertThat(starredChangesReader.isStarred(changeOwnerId, changeId1)).isFalse();
    starredChangesWriter.star(changeOwnerId, changeId1);
    assertThat(starredChangesReader.isStarred(changeOwnerId, changeId1)).isTrue();
    assertThat(starredChangesReader.isStarred(changeOwnerId, changeId2)).isFalse();
    assertThat(starredChangesReader.isStarred(otherUserId, changeId1)).isFalse();

    starredChangesWriter.unstar(changeOwnerId, changeId1);
    assertThat(starredChangesReader.isStarred(changeOwnerId, changeId1)).isFalse();
  }

  @Test
  public void areStarred_withOpenRepository() throws Exception {
    Change.Id changeId1 = Change.id(1);
    Change.Id changeId2 = Change.id(2);
    Change.Id changeId3 = Change.id(3);

    starredChangesWriter.star(changeOwnerId, changeId1);
    starredChangesWriter.star(changeOwnerId, changeId3);
    starredChangesWriter.star(otherUserId, changeId2);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      Set<Change.Id> starred =
          starredChangesReader.areStarred(
              allUsersRepo, ImmutableList.of(changeId1, changeId2, changeId3), changeOwnerId);
      assertThat(starred).containsExactly(changeId1, changeId3);
    }
  }

  @Test
  public void areStarred_batchedWithoutOpenRepository() throws Exception {
    Change.Id changeId1 = Change.id(101);
    Change.Id changeId2 = Change.id(102);
    Change.Id changeId3 = Change.id(103);

    starredChangesWriter.star(changeOwnerId, changeId1);
    starredChangesWriter.star(changeOwnerId, changeId2);

    Set<Change.Id> starred =
        starredChangesReader.areStarred(
            ImmutableList.of(changeId1, changeId2, changeId3), changeOwnerId);
    assertThat(starred).containsExactly(changeId1, changeId2);

    assertThat(starredChangesReader.areStarred(ImmutableList.of(), changeOwnerId)).isEmpty();
  }

  @Test
  public void byChange() throws Exception {
    Change.Id changeId = Change.id(1);
    Account.Id user1 = Account.id(100);
    Account.Id user2 = Account.id(200);
    Account.Id user3 = Account.id(300);

    assertThat(starredChangesReader.byChange(changeId)).isEmpty();

    starredChangesWriter.star(user1, changeId);
    starredChangesWriter.star(user2, changeId);
    starredChangesWriter.star(user3, changeId);

    ImmutableList<Account.Id> accounts = starredChangesReader.byChange(changeId);
    assertThat(accounts).containsExactly(user1, user2, user3);

    starredChangesWriter.unstar(user2, changeId);
    assertThat(starredChangesReader.byChange(changeId)).containsExactly(user1, user3);
  }

  @Test
  public void byAccountId() throws Exception {
    Account.Id user1 = Account.id(100);
    Account.Id user2 = Account.id(200);

    Change.Id change1 = Change.id(1);
    Change.Id change2 = Change.id(2);
    Change.Id change3 = Change.id(3);
    Change.Id change101 = Change.id(101);

    starredChangesWriter.star(user1, change1);
    starredChangesWriter.star(user1, change2);
    starredChangesWriter.star(user1, change101);
    starredChangesWriter.star(user2, change3);

    assertThat(starredChangesReader.byAccountId(user1))
        .containsExactly(change1, change2, change101);
    assertThat(starredChangesReader.byAccountId(user2)).containsExactly(change3);
    assertThat(starredChangesReader.byAccountId(Account.id(999))).isEmpty();
  }

  @Test
  public void byAccountId_skipInvalidChanges() throws Exception {
    Account.Id user = Account.id(500);
    Change.Id validChange = Change.id(42);
    starredChangesWriter.star(user, validChange);

    // Insert an invalid star ref for this user (e.g. shard mismatch)
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        ObjectInserter oi = allUsersRepo.newObjectInserter()) {
      ObjectId blobId = oi.insert(Constants.OBJ_BLOB, "star".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      oi.flush();

      // Ref with wrong shard for change 42: "99/42/500" instead of "42/42/500"
      RefUpdate u = allUsersRepo.updateRef("refs/starred-changes/99/42/500");
      u.setNewObjectId(blobId);
      u.setForceUpdate(true);
      testRefAction(() -> u.update());
    }

    // skipInvalidChanges = true should skip the malformed ref and return validChange
    assertThat(starredChangesReader.byAccountId(user, /* skipInvalidChanges= */ true))
        .containsExactly(validChange);

    // skipInvalidChanges = false should throw StorageException
    assertThrows(
        StorageException.class,
        () -> starredChangesReader.byAccountId(user, /* skipInvalidChanges= */ false));
  }

  @Test
  public void unstarAllForChangeDeletion() throws Exception {
    Change.Id changeId = Change.id(77);
    Account.Id user1 = Account.id(1);
    Account.Id user2 = Account.id(2);
    Account.Id user3 = Account.id(3);

    starredChangesWriter.star(user1, changeId);
    starredChangesWriter.star(user2, changeId);
    starredChangesWriter.star(user3, changeId);

    assertThat(starredChangesReader.byChange(changeId)).containsExactly(user1, user2, user3);

    starredChangesWriter.unstarAllForChangeDeletion(changeId);

    assertThat(starredChangesReader.byChange(changeId)).isEmpty();
    assertThat(starredChangesReader.isStarred(user1, changeId)).isFalse();
    assertThat(starredChangesReader.isStarred(user2, changeId)).isFalse();
    assertThat(starredChangesReader.isStarred(user3, changeId)).isFalse();
  }

  @Test
  public void benchmarkStarLookups() throws Exception {
    int numAccounts = 20;
    int numChanges = 100;
    Account.Id targetUser = Account.id(42);

    List<Change.Id> targetUserChanges = new ArrayList<>();
    List<Change.Id> allChanges = new ArrayList<>();

    for (int c = 1; c <= numChanges; c++) {
      Change.Id changeId = Change.id(c);
      allChanges.add(changeId);
      for (int a = 1; a <= numAccounts; a++) {
        Account.Id accountId = Account.id(a);
        starredChangesWriter.star(accountId, changeId);
      }
      // Star with targetUser on even change IDs
      if (c % 2 == 0) {
        starredChangesWriter.star(targetUser, changeId);
        targetUserChanges.add(changeId);
      }
    }

    // Warmup
    for (int i = 0; i < 50; i++) {
      @SuppressWarnings("unused")
      var unused1 = starredChangesReader.byAccountId(targetUser);
      @SuppressWarnings("unused")
      var unused2 = starredChangesReader.byChange(Change.id(1));
      @SuppressWarnings("unused")
      var unused3 = starredChangesReader.areStarred(allChanges, targetUser);
    }

    // Benchmark byAccountId
    int iterations = 200;
    long startNanos = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      ImmutableSet<Change.Id> result = starredChangesReader.byAccountId(targetUser);
      assertThat(result).hasSize(targetUserChanges.size());
    }
    long elapsedNanos = System.nanoTime() - startNanos;
    double avgMsByAccountId = (double) elapsedNanos / (iterations * 1_000_000.0);
    System.out.printf("Benchmark byAccountId average latency: %.4f ms%n", avgMsByAccountId);

    // Benchmark byChange
    startNanos = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      ImmutableList<Account.Id> result = starredChangesReader.byChange(Change.id(2));
      assertThat(result).hasSize(numAccounts + 1); // numAccounts + targetUser
    }
    elapsedNanos = System.nanoTime() - startNanos;
    double avgMsByChange = (double) elapsedNanos / (iterations * 1_000_000.0);
    System.out.printf("Benchmark byChange average latency: %.4f ms%n", avgMsByChange);

    // Benchmark areStarred
    startNanos = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      Set<Change.Id> result = starredChangesReader.areStarred(allChanges, targetUser);
      assertThat(result).hasSize(targetUserChanges.size());
    }
    elapsedNanos = System.nanoTime() - startNanos;
    double avgMsAreStarred = (double) elapsedNanos / (iterations * 1_000_000.0);
    System.out.printf("Benchmark areStarred average latency: %.4f ms%n", avgMsAreStarred);
  }
}
