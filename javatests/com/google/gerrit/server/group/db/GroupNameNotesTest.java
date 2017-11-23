// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.group.db;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.assertThat;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_GROUPNAMES;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.group.db.testing.GroupTestUtil;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.TestTimeUtil;
import java.util.Arrays;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GroupNameNotesTest extends GerritBaseTests {
  private static final String SERVER_NAME = "Gerrit Server";
  private static final String SERVER_EMAIL = "noreply@gerritcodereview.com";
  private static final TimeZone TZ = TimeZone.getTimeZone("America/Los_Angeles");

  private AtomicInteger idCounter;
  private Repository repo;

  @Before
  public void setUp() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    idCounter = new AtomicInteger();
    repo = new InMemoryRepository(new DfsRepositoryDescription(AllUsersNameProvider.DEFAULT));
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void updateGroupNames() throws Exception {
    GroupReference g1 = newGroup("a");
    GroupReference g2 = newGroup("b");

    PersonIdent ident = newPersonIdent();
    updateGroupNames(ident, g1, g2);

    ImmutableList<CommitInfo> log = log();
    assertThat(log).hasSize(1);
    assertThat(log.get(0)).parents().isEmpty();
    assertThat(log.get(0)).message().isEqualTo("Store 2 group names");
    assertThat(log.get(0)).author().matches(ident);
    assertThat(log.get(0)).committer().matches(ident);

    assertThat(GroupTestUtil.readNameToUuidMap(repo)).containsExactly("a", "a-1", "b", "b-2");

    // Updating the same set of names is a no-op.
    String commit = log.get(0).commit;
    updateGroupNames(newPersonIdent(), g1, g2);
    log = log();
    assertThat(log).hasSize(1);
    assertThat(log.get(0)).commit().isEqualTo(commit);
  }

  @Test
  public void updateGroupNamesOverwritesExistingNotes() throws Exception {
    GroupReference g1 = newGroup("a");
    GroupReference g2 = newGroup("b");

    TestRepository<?> tr = new TestRepository<>(repo);
    ObjectId k1 = getNoteKey(g1);
    ObjectId k2 = getNoteKey(g2);
    ObjectId k3 = GroupNameNotes.getNoteKey(new AccountGroup.NameKey("c"));
    PersonIdent ident = newPersonIdent();
    ObjectId origCommitId =
        tr.branch(REFS_GROUPNAMES)
            .commit()
            .message("Prepopulate group name")
            .author(ident)
            .committer(ident)
            .add(k1.name(), "[group]\n\tuuid = a-1\n\tname = a\nanotherKey = foo\n")
            .add(k2.name(), "[group]\n\tuuid = a-1\n\tname = b\n")
            .add(k3.name(), "[group]\n\tuuid = c-3\n\tname = c\n")
            .create()
            .copy();

    ident = newPersonIdent();
    updateGroupNames(ident, g1, g2);

    assertThat(GroupTestUtil.readNameToUuidMap(repo)).containsExactly("a", "a-1", "b", "b-2");

    ImmutableList<CommitInfo> log = log();
    assertThat(log).hasSize(2);
    assertThat(log.get(0)).commit().isEqualTo(origCommitId.name());

    assertThat(log.get(1)).message().isEqualTo("Store 2 group names");
    assertThat(log.get(1)).author().matches(ident);
    assertThat(log.get(1)).committer().matches(ident);

    // Old note content was overwritten.
    assertThat(readNameNote(g1)).isEqualTo("[group]\n\tuuid = a-1\n\tname = a\n");
  }

  @Test
  public void updateGroupNamesWithEmptyCollectionClearsAllNotes() throws Exception {
    GroupReference g1 = newGroup("a");
    GroupReference g2 = newGroup("b");

    PersonIdent ident = newPersonIdent();
    updateGroupNames(ident, g1, g2);

    assertThat(GroupTestUtil.readNameToUuidMap(repo)).containsExactly("a", "a-1", "b", "b-2");

    updateGroupNames(ident);

    assertThat(GroupTestUtil.readNameToUuidMap(repo)).isEmpty();

    ImmutableList<CommitInfo> log = log();
    assertThat(log).hasSize(2);
    assertThat(log.get(1)).message().isEqualTo("Store 0 group names");
  }

  @Test
  public void updateGroupNamesRejectsNonOneToOneGroupReferences() throws Exception {
    assertIllegalArgument(
        new GroupReference(new AccountGroup.UUID("uuid1"), "name1"),
        new GroupReference(new AccountGroup.UUID("uuid1"), "name2"));
    assertIllegalArgument(
        new GroupReference(new AccountGroup.UUID("uuid1"), "name1"),
        new GroupReference(new AccountGroup.UUID("uuid2"), "name1"));
    assertIllegalArgument(
        new GroupReference(new AccountGroup.UUID("uuid1"), "name1"),
        new GroupReference(new AccountGroup.UUID("uuid1"), "name1"));
  }

  @Test
  public void emptyGroupName() throws Exception {
    GroupReference g = newGroup("");
    updateGroupNames(newPersonIdent(), g);

    assertThat(GroupTestUtil.readNameToUuidMap(repo)).containsExactly("", "-1");
    assertThat(readNameNote(g)).isEqualTo("[group]\n\tuuid = -1\n\tname = \n");
  }

  private GroupReference newGroup(String name) {
    int id = idCounter.incrementAndGet();
    return new GroupReference(new AccountGroup.UUID(name + "-" + id), name);
  }

  private static PersonIdent newPersonIdent() {
    return new PersonIdent(SERVER_NAME, SERVER_EMAIL, TimeUtil.nowTs(), TZ);
  }

  private static ObjectId getNoteKey(GroupReference g) {
    return GroupNameNotes.getNoteKey(new AccountGroup.NameKey(g.getName()));
  }

  private void updateGroupNames(PersonIdent ident, GroupReference... groupRefs) throws Exception {
    try (ObjectInserter inserter = repo.newObjectInserter()) {
      BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
      GroupNameNotes.updateGroupNames(repo, inserter, bru, Arrays.asList(groupRefs), ident);
      inserter.flush();
      RefUpdateUtil.executeChecked(bru, repo);
    }
  }

  private void assertIllegalArgument(GroupReference... groupRefs) throws Exception {
    try (ObjectInserter inserter = repo.newObjectInserter()) {
      BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
      PersonIdent ident = newPersonIdent();
      try {
        GroupNameNotes.updateGroupNames(repo, inserter, bru, Arrays.asList(groupRefs), ident);
        assert_().fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        assertThat(e).hasMessageThat().isEqualTo(GroupNameNotes.UNIQUE_REF_ERROR);
      }
    }
  }

  private ImmutableList<CommitInfo> log() throws Exception {
    return GroupTestUtil.log(repo, REFS_GROUPNAMES);
  }

  private String readNameNote(GroupReference g) throws Exception {
    ObjectId k = getNoteKey(g);
    try (RevWalk rw = new RevWalk(repo)) {
      ObjectReader reader = rw.getObjectReader();
      Ref ref = repo.exactRef(RefNames.REFS_GROUPNAMES);
      NoteMap noteMap = NoteMap.read(reader, rw.parseCommit(ref.getObjectId()));
      return new String(reader.open(noteMap.get(k), OBJ_BLOB).getCachedBytes(), UTF_8);
    }
  }
}
