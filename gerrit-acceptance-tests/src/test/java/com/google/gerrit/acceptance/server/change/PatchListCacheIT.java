// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.change;

import static com.google.gerrit.acceptance.GitUtil.add;
import static com.google.gerrit.acceptance.GitUtil.amendCommit;
import static com.google.gerrit.acceptance.GitUtil.createCommit;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.GitUtil.rm;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil.Commit;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class PatchListCacheIT extends AbstractDaemonTest {
  private static String SUBJECT_1 = "subject 1";
  private static String SUBJECT_2 = "subject 2";
  private static String SUBJECT_3 = "subject 3";
  private static String FILE_A = "a.txt";
  private static String FILE_B = "b.txt";
  private static String FILE_C = "c.txt";
  private static String FILE_D = "d.txt";

  @Inject
  private PatchListCache patchListCache;

  @Test
  public void listPatchesAgainstBase() throws GitAPIException, IOException,
      PatchListNotAvailableException, OrmException, RestApiException {
    add(git, FILE_D, "4");
    createCommit(git, admin.getIdent(), SUBJECT_1);
    pushHead(git, "refs/heads/master", false);

    // Change 1, 1 (+FILE_A, -FILE_D)
    add(git, FILE_A, "1");
    rm(git, FILE_D);
    Commit c = createCommit(git, admin.getIdent(), SUBJECT_2);
    pushHead(git, "refs/for/master", false);

    // Compare Change 1,1 with Base (+FILE_A, -FILE_D)
    List<PatchListEntry> entries = getCurrentPatches(c.getChangeId());
    assertEquals(3, entries.size());
    assertAdded(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_A, entries.get(1));
    assertDeleted(FILE_D, entries.get(2));

    // Change 1,2 (+FILE_B)
    add(git, FILE_B, "2");
    c = amendCommit(git, admin.getIdent(), SUBJECT_2, c.getChangeId());
    pushHead(git, "refs/for/master", false);

    // Compare Change 1,2 with Base (+FILE_A, +FILE_B, -FILE_D)
    entries = getCurrentPatches(c.getChangeId());
    assertEquals(4, entries.size());
    assertAdded(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_A, entries.get(1));
    assertAdded(FILE_B, entries.get(2));
    assertDeleted(FILE_D, entries.get(3));
  }

  @Test
  public void listPatchesAgainstBaseWithRebase() throws GitAPIException,
      IOException, PatchListNotAvailableException, OrmException,
      RestApiException {
    add(git, FILE_D, "4");
    createCommit(git, admin.getIdent(), SUBJECT_1);
    pushHead(git, "refs/heads/master", false);

    // Change 1,1 (+FILE_A, -FILE_D)
    add(git, FILE_A, "1");
    rm(git, FILE_D);
    Commit c = createCommit(git, admin.getIdent(), SUBJECT_2);
    pushHead(git, "refs/for/master", false);
    List<PatchListEntry> entries = getCurrentPatches(c.getChangeId());
    assertEquals(3, entries.size());
    assertAdded(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_A, entries.get(1));
    assertDeleted(FILE_D, entries.get(2));

    // Change 2,1 (+FILE_B)
    git.reset().setMode(ResetType.HARD).setRef("HEAD~1").call();
    add(git, FILE_B, "2");
    createCommit(git, admin.getIdent(), SUBJECT_3);
    pushHead(git, "refs/for/master", false);

    // Change 1,2 (+FILE_A, -FILE_D))
    git.cherryPick().include(c.getCommit()).call();
    pushHead(git, "refs/for/master", false);

    // Compare Change 1,2 with Base (+FILE_A, -FILE_D))
    entries = getCurrentPatches(c.getChangeId());
    assertEquals(3, entries.size());
    assertAdded(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_A, entries.get(1));
    assertDeleted(FILE_D, entries.get(2));
  }

  @Test
  public void listPatchesAgainstOtherPatchSetFail() throws GitAPIException,
      IOException, PatchListNotAvailableException, OrmException,
      RestApiException {
    add(git, FILE_D, "4");
    createCommit(git, admin.getIdent(), SUBJECT_1);
    pushHead(git, "refs/heads/master", false);

    // Change 1,1 (+FILE_A, +FILE_C, -FILE_D)
    add(git, FILE_A, "1");
    add(git, FILE_C, "3");
    rm(git, FILE_D);
    Commit c = createCommit(git, admin.getIdent(), SUBJECT_2);
    pushHead(git, "refs/for/master", false);
    ObjectId a = getCurrentRevisionId(c.getChangeId());

    // Change 1,2 (+FILE_B, -FILE_C)
    add(git, FILE_B, "2");
    rm(git, FILE_C);
    c = amendCommit(git, admin.getIdent(), SUBJECT_2, c.getChangeId());
    pushHead(git, "refs/for/master", false);
    ObjectId b = getCurrentRevisionId(c.getChangeId());

    // Compare Change 1,1 with Change 1,2 (+FILE_B, -FILE_C)
    List<PatchListEntry>  entries = getPatches(a, b);
    assertEquals(2, entries.size());
    assertModified(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_B, entries.get(1));

    // Compare Change 1,2 with Change 1,1 (-FILE_B, +FILE_C)
    List<PatchListEntry>  entriesReverse = getPatches(b, a);
    assertEquals(2, entriesReverse.size());
    assertModified(Patch.COMMIT_MSG, entriesReverse.get(0));
    assertDeleted(FILE_B, entriesReverse.get(1));
   }

  @Test
  public void listPatchesAgainstOtherPatchSet() throws GitAPIException,
      IOException, PatchListNotAvailableException, OrmException,
      RestApiException {
    add(git, FILE_D, "4");
    createCommit(git, admin.getIdent(), SUBJECT_1);
    pushHead(git, "refs/heads/master", false);

    // Change 1,1 (+FILE_A, +FILE_C, -FILE_D)
    add(git, FILE_A, "1");
    add(git, FILE_C, "3");
    rm(git, FILE_D);
    Commit c = createCommit(git, admin.getIdent(), SUBJECT_2);
    pushHead(git, "refs/for/master", false);
    ObjectId a = getCurrentRevisionId(c.getChangeId());

    // Change 1,2 (+FILE_B, -FILE_C)
    add(git, FILE_B, "2");
    rm(git, FILE_C);
    c = amendCommit(git, admin.getIdent(), SUBJECT_2, c.getChangeId());
    pushHead(git, "refs/for/master", false);
    ObjectId b = getCurrentRevisionId(c.getChangeId());

    // Compare Change 1,1 with Change 1,2 (+FILE_B, -FILE_C)
    List<PatchListEntry>  entries = getPatches(a, b, 1, 2);
    assertEquals(3, entries.size());
    assertModified(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_B, entries.get(1));
    assertDeleted(FILE_C, entries.get(2));

    // Compare Change 1,2 with Change 1,1 (-FILE_B, +FILE_C)
    List<PatchListEntry>  entriesReverse = getPatches(b, a, 2, 1);
    assertEquals(3, entriesReverse.size());
    assertModified(Patch.COMMIT_MSG, entriesReverse.get(0));
    assertDeleted(FILE_B, entriesReverse.get(1));
    assertAdded(FILE_C, entriesReverse.get(2));
  }

  @Test
  public void listPatchesAgainstOtherPatchSetWithRebaseFail()
      throws GitAPIException, IOException, PatchListNotAvailableException,
      OrmException, RestApiException {
    add(git, FILE_D, "4");
    createCommit(git, admin.getIdent(), SUBJECT_1);
    pushHead(git, "refs/heads/master", false);

    // Change 1,1 (+FILE_A, -FILE_D)
    add(git, FILE_A, "1");
    rm(git, FILE_D);
    Commit c = createCommit(git, admin.getIdent(), SUBJECT_2);
    pushHead(git, "refs/for/master", false);
    ObjectId a = getCurrentRevisionId(c.getChangeId());

    // Change 2,1 (+FILE_B)
    git.reset().setMode(ResetType.HARD).setRef("HEAD~1").call();
    add(git, FILE_B, "2");
    createCommit(git, admin.getIdent(), SUBJECT_3);
    pushHead(git, "refs/for/master", false);

    // Change 1,2 (+FILE_C)
    git.cherryPick().include(c.getCommit()).call();
    add(git, FILE_C, "2");
    c = amendCommit(git, admin.getIdent(), SUBJECT_2, c.getChangeId());
    pushHead(git, "refs/for/master", false);
    ObjectId b = getCurrentRevisionId(c.getChangeId());

    // Compare Change 1,1 with Change 1,2 (+FILE_C)
    List<PatchListEntry>  entries = getPatches(a, b);
    assertEquals(2, entries.size());
    assertModified(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_C, entries.get(1));

    // Compare Change 1,2 with Change 1,1 (-FILE_C)
    List<PatchListEntry>  entriesReverse = getPatches(b, a);
    assertEquals(2, entriesReverse.size());
    assertDeleted(FILE_C, entries.get(1));
  }

  @Test
  public void listPatchesAgainstOtherPatchSetWithRebase()
      throws GitAPIException, IOException, PatchListNotAvailableException,
      OrmException, RestApiException {
    add(git, FILE_D, "4");
    createCommit(git, admin.getIdent(), SUBJECT_1);
    pushHead(git, "refs/heads/master", false);

    // Change 1,1 (+FILE_A, -FILE_D)
    add(git, FILE_A, "1");
    rm(git, FILE_D);
    Commit c = createCommit(git, admin.getIdent(), SUBJECT_2);
    pushHead(git, "refs/for/master", false);
    ObjectId a = getCurrentRevisionId(c.getChangeId());

    // Change 2,1 (+FILE_B)
    git.reset().setMode(ResetType.HARD).setRef("HEAD~1").call();
    add(git, FILE_B, "2");
    createCommit(git, admin.getIdent(), SUBJECT_3);
    pushHead(git, "refs/for/master", false);

    // Change 1,2 (+FILE_C)
    git.cherryPick().include(c.getCommit()).call();
    add(git, FILE_C, "2");
    c = amendCommit(git, admin.getIdent(), SUBJECT_2, c.getChangeId());
    pushHead(git, "refs/for/master", false);
    ObjectId b = getCurrentRevisionId(c.getChangeId());

    // Compare Change 1,1 with Change 1,2 (+FILE_C)
    List<PatchListEntry>  entries = getPatches(a, b, 1, 2);
    assertEquals(2, entries.size());
    assertModified(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_C, entries.get(1));

    // Compare Change 1,2 with Change 1,1 (-FILE_C)
    List<PatchListEntry>  entriesReverse = getPatches(b, a, 2, 1);
    assertEquals(2, entriesReverse.size());
    assertModified(Patch.COMMIT_MSG, entriesReverse.get(0));
    assertDeleted(FILE_C, entriesReverse.get(1));
  }

  private static void assertAdded(String expectedNewName, PatchListEntry e) {
    assertName(expectedNewName, e);
    assertEquals(ChangeType.ADDED, e.getChangeType());
  }

  private static void assertModified(String expectedNewName, PatchListEntry e) {
    assertName(expectedNewName, e);
    assertEquals(ChangeType.MODIFIED, e.getChangeType());
  }

  private static void assertDeleted(String expectedNewName, PatchListEntry e) {
    assertName(expectedNewName, e);
    assertEquals(ChangeType.DELETED, e.getChangeType());
  }

  private static void assertName(String expectedNewName, PatchListEntry e) {
    assertEquals(expectedNewName, e.getNewName());
    assertNull(e.getOldName());
  }

  private List<PatchListEntry> getCurrentPatches(String changeId)
      throws PatchListNotAvailableException, OrmException, RestApiException {
    return patchListCache.get(getKey(null, getCurrentRevisionId(changeId))).getPatches();
  }

  private List<PatchListEntry> getPatches(ObjectId revisionIdA,
      ObjectId revisionIdB, int aPatchSetId, int bPatchSetId)
      throws PatchListNotAvailableException, OrmException {
    return patchListCache.get(getKey(revisionIdA, revisionIdB, aPatchSetId,
        bPatchSetId)).getPatches();
  }

  private List<PatchListEntry> getPatches(ObjectId revisionIdA,
      ObjectId revisionIdB)
      throws PatchListNotAvailableException, OrmException {
    return patchListCache.get(getKey(revisionIdA, revisionIdB)).getPatches();
  }

  private PatchListKey getKey(ObjectId revisionIdA, ObjectId revisionIdB,
      int aPatchSetId, int bPatchSetId) {
    return new PatchListKey(project, revisionIdA, revisionIdB, aPatchSetId,
        bPatchSetId, Whitespace.IGNORE_NONE);
  }

  private PatchListKey getKey(ObjectId revisionIdA, ObjectId revisionIdB) throws OrmException {
    return new PatchListKey(project, revisionIdA, revisionIdB, Whitespace.IGNORE_NONE);
  }

  private ObjectId getCurrentRevisionId(String changeId) throws RestApiException {
    return ObjectId.fromString(gApi.changes().id(changeId).get().currentRevision);
  }
}