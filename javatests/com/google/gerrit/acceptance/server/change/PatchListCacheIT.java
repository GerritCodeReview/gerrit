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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.getChangeId;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.patch.IntraLineDiff;
import com.google.gerrit.server.patch.IntraLineDiffArgs;
import com.google.gerrit.server.patch.IntraLineDiffKey;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.Text;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@NoHttpd
public class PatchListCacheIT extends AbstractDaemonTest {
  private static String SUBJECT_1 = "subject 1";
  private static String SUBJECT_2 = "subject 2";
  private static String SUBJECT_3 = "subject 3";
  private static String FILE_A = "a.txt";
  private static String FILE_B = "b.txt";
  private static String FILE_C = "c.txt";
  private static String FILE_D = "d.txt";

  @Inject private PatchListCache patchListCache;

  @Inject
  @Named("diff")
  private Cache<PatchListKey, PatchList> abstractPatchListCache;

  @Test
  public void ensureLegacyBackendIsUsedForFileCacheBackend() throws Exception {
    Field fileCacheField = patchListCache.getClass().getDeclaredField("fileCache");
    fileCacheField.setAccessible(true);
    // Use the reflection to access "localCache" field that is only present in Guava backend.
    assertThat(
            Arrays.stream(fileCacheField.get(patchListCache).getClass().getDeclaredFields())
                .anyMatch(f -> f.getName().equals("localCache")))
        .isTrue();

    // intraCache (and all other cache backends) should use Caffeine backend.
    Field intraCacheField = patchListCache.getClass().getDeclaredField("intraCache");
    intraCacheField.setAccessible(true);
    assertThat(
            Arrays.stream(intraCacheField.get(patchListCache).getClass().getDeclaredFields())
                .noneMatch(f -> f.getName().equals("localCache")))
        .isTrue();
  }

  @Test
  public void listPatchesAgainstBase() throws Exception {
    commitBuilder().add(FILE_D, "4").message(SUBJECT_1).create();
    pushHead(testRepo, "refs/heads/master", false);

    // Change 1, 1 (+FILE_A, -FILE_D)
    RevCommit c =
        commitBuilder().add(FILE_A, "1").rm(FILE_D).message(SUBJECT_2).insertChangeId().create();
    String id = getChangeId(testRepo, c).get();
    pushHead(testRepo, "refs/for/master", false);

    // Compare Change 1,1 with Base (+FILE_A, -FILE_D)
    List<PatchListEntry> entries = getCurrentPatches(id);
    assertThat(entries).hasSize(3);
    assertAdded(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_A, entries.get(1));
    assertDeleted(FILE_D, entries.get(2));

    // Change 1,2 (+FILE_A, +FILE_B, -FILE_D)
    amendBuilder().add(FILE_B, "2").create();
    pushHead(testRepo, "refs/for/master", false);
    entries = getCurrentPatches(id);

    // Compare Change 1,2 with Base (+FILE_A, +FILE_B, -FILE_D)
    assertThat(entries).hasSize(4);
    assertAdded(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_A, entries.get(1));
    assertAdded(FILE_B, entries.get(2));
    assertDeleted(FILE_D, entries.get(3));
  }

  @Test
  public void listPatchesAgainstBaseWithRebase() throws Exception {
    commitBuilder().add(FILE_D, "4").message(SUBJECT_1).create();
    pushHead(testRepo, "refs/heads/master", false);

    // Change 1,1 (+FILE_A, -FILE_D)
    RevCommit c = commitBuilder().add(FILE_A, "1").rm(FILE_D).message(SUBJECT_2).create();
    String id = getChangeId(testRepo, c).get();
    pushHead(testRepo, "refs/for/master", false);
    List<PatchListEntry> entries = getCurrentPatches(id);
    assertThat(entries).hasSize(3);
    assertAdded(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_A, entries.get(1));
    assertDeleted(FILE_D, entries.get(2));

    // Change 2,1 (+FILE_B)
    testRepo.reset("HEAD~1");
    commitBuilder().add(FILE_B, "2").message(SUBJECT_3).create();
    pushHead(testRepo, "refs/for/master", false);

    // Change 1,2 (+FILE_A, -FILE_D))
    testRepo.cherryPick(c);
    pushHead(testRepo, "refs/for/master", false);

    // Compare Change 1,2 with Base (+FILE_A, -FILE_D))
    entries = getCurrentPatches(id);
    assertThat(entries).hasSize(3);
    assertAdded(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_A, entries.get(1));
    assertDeleted(FILE_D, entries.get(2));
  }

  @Test
  public void listPatchesAgainstOtherPatchSet() throws Exception {
    commitBuilder().add(FILE_D, "4").message(SUBJECT_1).create();
    pushHead(testRepo, "refs/heads/master", false);

    // Change 1,1 (+FILE_A, +FILE_C, -FILE_D)
    RevCommit a =
        commitBuilder().add(FILE_A, "1").add(FILE_C, "3").rm(FILE_D).message(SUBJECT_2).create();
    pushHead(testRepo, "refs/for/master", false);

    // Change 1,2 (+FILE_A, +FILE_B, -FILE_D)
    RevCommit b = amendBuilder().add(FILE_B, "2").rm(FILE_C).create();
    pushHead(testRepo, "refs/for/master", false);

    // Compare Change 1,1 with Change 1,2 (+FILE_B, -FILE_C)
    List<PatchListEntry> entries = getPatches(a, b);
    assertThat(entries).hasSize(3);
    assertModified(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_B, entries.get(1));
    assertDeleted(FILE_C, entries.get(2));

    // Compare Change 1,2 with Change 1,1 (-FILE_B, +FILE_C)
    List<PatchListEntry> entriesReverse = getPatches(b, a);
    assertThat(entriesReverse).hasSize(3);
    assertModified(Patch.COMMIT_MSG, entriesReverse.get(0));
    assertDeleted(FILE_B, entriesReverse.get(1));
    assertAdded(FILE_C, entriesReverse.get(2));
  }

  @Test
  public void listPatchesAgainstOtherPatchSetWithRebase() throws Exception {
    commitBuilder().add(FILE_D, "4").message(SUBJECT_1).create();
    pushHead(testRepo, "refs/heads/master", false);

    // Change 1,1 (+FILE_A, -FILE_D)
    RevCommit a = commitBuilder().add(FILE_A, "1").rm(FILE_D).message(SUBJECT_2).create();
    pushHead(testRepo, "refs/for/master", false);

    // Change 2,1 (+FILE_B)
    testRepo.reset("HEAD~1");
    commitBuilder().add(FILE_B, "2").message(SUBJECT_3).create();
    pushHead(testRepo, "refs/for/master", false);

    // Change 1,2 (+FILE_A, +FILE_C, -FILE_D)
    testRepo.cherryPick(a);
    RevCommit b = amendBuilder().add(FILE_C, "2").create();
    pushHead(testRepo, "refs/for/master", false);

    // Compare Change 1,1 with Change 1,2 (+FILE_C)
    List<PatchListEntry> entries = getPatches(a, b);
    assertThat(entries).hasSize(2);
    assertModified(Patch.COMMIT_MSG, entries.get(0));
    assertAdded(FILE_C, entries.get(1));

    // Compare Change 1,2 with Change 1,1 (-FILE_C)
    List<PatchListEntry> entriesReverse = getPatches(b, a);
    assertThat(entriesReverse).hasSize(2);
    assertModified(Patch.COMMIT_MSG, entriesReverse.get(0));
    assertDeleted(FILE_C, entriesReverse.get(1));
  }

  @Test
  public void harmfulMutationsOfEditsAreNotPossibleForPatchListEntry() throws Exception {
    RevCommit commit =
        commitBuilder().add("a.txt", "First line\nSecond line\n").message(SUBJECT_1).create();
    pushHead(testRepo, "refs/heads/master", false);

    PatchListKey diffKey = PatchListKey.againstDefaultBase(commit.copy(), Whitespace.IGNORE_NONE);
    PatchList patchList = patchListCache.get(diffKey, project);

    PatchListEntry patchListEntry = getEntryFor(patchList, "a.txt");
    Edit outputEdit = Iterables.getOnlyElement(patchListEntry.getEdits());
    Edit originalEdit =
        new Edit(
            outputEdit.getBeginA(),
            outputEdit.getEndA(),
            outputEdit.getBeginB(),
            outputEdit.getEndB());

    outputEdit.shift(5);

    assertThat(patchListEntry.getEdits()).containsExactly(originalEdit);
  }

  @Test
  public void harmfulMutationsOfEditsAreNotPossibleForIntraLineDiffArgsAndCachedValue() {
    String a = "First line\nSecond line\n";
    String b = "1st line\n2nd line\n";
    Text aText = new Text(a.getBytes(UTF_8));
    Text bText = new Text(b.getBytes(UTF_8));
    Edit inputEdit = new Edit(0, 2, 0, 2);
    List<Edit> inputEdits = new ArrayList<>(ImmutableList.of(inputEdit));
    Set<Edit> inputEditsDueToRebase = new HashSet<>(ImmutableSet.of(inputEdit));

    IntraLineDiffKey diffKey =
        IntraLineDiffKey.create(ObjectId.zeroId(), ObjectId.zeroId(), Whitespace.IGNORE_NONE);
    IntraLineDiffArgs diffArgs =
        IntraLineDiffArgs.create(
            aText,
            bText,
            inputEdits,
            inputEditsDueToRebase,
            project,
            ObjectId.zeroId(),
            "file.txt");
    IntraLineDiff intraLineDiff = patchListCache.getIntraLineDiff(diffKey, diffArgs);

    Edit outputEdit = Iterables.getOnlyElement(intraLineDiff.getEdits());

    outputEdit.shift(5);
    inputEdit.shift(7);
    inputEdits.add(new Edit(43, 47, 50, 51));
    inputEditsDueToRebase.add(new Edit(53, 57, 60, 61));

    Edit originalEdit = new Edit(0, 2, 0, 2);
    assertThat(diffArgs.edits()).containsExactly(originalEdit);
    assertThat(diffArgs.editsDueToRebase()).containsExactly(originalEdit);
    assertThat(intraLineDiff.getEdits()).containsExactly(originalEdit);
  }

  @Test
  public void largeObjectTombstoneGetsCached() {
    PatchListKey key = PatchListKey.againstDefaultBase(ObjectId.zeroId(), Whitespace.IGNORE_ALL);
    PatchListCacheImpl.LargeObjectTombstone tombstone =
        new PatchListCacheImpl.LargeObjectTombstone();
    abstractPatchListCache.put(key, tombstone);
    assertThat(abstractPatchListCache.getIfPresent(key)).isSameInstanceAs(tombstone);
  }

  private static void assertAdded(String expectedNewName, PatchListEntry e) {
    assertName(expectedNewName, e);
    assertThat(e.getChangeType()).isEqualTo(ChangeType.ADDED);
  }

  private static void assertModified(String expectedNewName, PatchListEntry e) {
    assertName(expectedNewName, e);
    assertThat(e.getChangeType()).isEqualTo(ChangeType.MODIFIED);
  }

  private static void assertDeleted(String expectedNewName, PatchListEntry e) {
    assertName(expectedNewName, e);
    assertThat(e.getChangeType()).isEqualTo(ChangeType.DELETED);
  }

  private static void assertName(String expectedNewName, PatchListEntry e) {
    assertThat(e.getNewName()).isEqualTo(expectedNewName);
    assertThat(e.getOldName()).isNull();
  }

  private List<PatchListEntry> getCurrentPatches(String changeId) throws Exception {
    return patchListCache.get(getKey(null, getCurrentRevisionId(changeId)), project).getPatches();
  }

  private List<PatchListEntry> getPatches(ObjectId revisionIdA, ObjectId revisionIdB)
      throws Exception {
    return patchListCache.get(getKey(revisionIdA, revisionIdB), project).getPatches();
  }

  private PatchListKey getKey(ObjectId revisionIdA, ObjectId revisionIdB) {
    return PatchListKey.againstCommit(revisionIdA, revisionIdB, Whitespace.IGNORE_NONE);
  }

  private ObjectId getCurrentRevisionId(String changeId) throws Exception {
    return ObjectId.fromString(gApi.changes().id(changeId).get().currentRevision);
  }

  private static PatchListEntry getEntryFor(PatchList patchList, String filePath) {
    Optional<PatchListEntry> patchListEntry =
        patchList.getPatches().stream()
            .filter(entry -> entry.getNewName().equals(filePath))
            .findAny();
    return patchListEntry.orElseThrow(
        () -> new IllegalStateException("No PatchListEntry for " + filePath + " exists"));
  }
}
