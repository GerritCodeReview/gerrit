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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.assertThat;
import static com.google.gerrit.server.group.db.BatchGroupNameNotes.readNamesByUuid;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.TestTimeUtil;
import java.util.Arrays;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BatchGroupNameNotesTest extends GerritBaseTests {
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
    AccountGroup g1 = newGroup("a");
    AccountGroup g2 = newGroup("b");

    PersonIdent ident = newPersonIdent();
    updateGroupNames(ident, g1, g2);

    ImmutableList<CommitInfo> log = log();
    String commit = log.get(0).commit;
    assertThat(log).hasSize(1);
    assertThat(log.get(0)).parents().isEmpty();
    assertThat(log.get(0)).message().isEqualTo("Update 2 group names");
    assertThat(log.get(0)).author().matches(ident);
    assertThat(log.get(0)).committer().matches(ident);

    assertThat(readNamesByUuid(repo))
        .containsExactly(g1.getGroupUUID(), "a", g2.getGroupUUID(), "b");

    // Updating the same set of names is a no-op.
    updateGroupNames(newPersonIdent(), g1, g2);
    log = log();
    assertThat(log).hasSize(1);
    assertThat(log.get(0)).commit().isEqualTo(commit);
  }

  @Test
  public void updateGroupNamesSkipsExistingNotes() throws Exception {
    AccountGroup g1 = newGroup("a");
    AccountGroup g2 = newGroup("b");

    TestRepository<?> tr = new TestRepository<>(repo);
    ObjectId noteKey = GroupNameNotes.getNoteKey(g1.getNameKey());
    PersonIdent ident = newPersonIdent();
    ObjectId origCommitId =
        tr.branch(RefNames.REFS_GROUPNAMES)
            .commit()
            .message("Prepopulate group name")
            .author(ident)
            .committer(ident)
            .add(noteKey.name(), "[group]\n\tuuid = a-1\n\tname = a\nanotherKey = foo\n")
            .create()
            .copy();

    ident = newPersonIdent();
    updateGroupNames(ident, g1, g2);

    assertThat(readNamesByUuid(repo))
        .containsExactly(g1.getGroupUUID(), "a", g2.getGroupUUID(), "b");

    ImmutableList<CommitInfo> log = log();
    assertThat(log).hasSize(2);
    assertThat(log.get(0)).commit().isEqualTo(origCommitId.name());

    assertThat(log.get(1)).message().isEqualTo("Update 1 group name");
    assertThat(log.get(1)).author().matches(ident);
    assertThat(log.get(1)).committer().matches(ident);
  }

  @Test
  public void updateGroupNamesRemovesOutdatedMappings() throws Exception {
    AccountGroup g = newGroup("old name");
    updateGroupNames(newPersonIdent(), g);
    assertThat(readNamesByUuid(repo)).containsExactly(g.getGroupUUID(), "old name");

    g.setNameKey(new AccountGroup.NameKey("new name"));
    updateGroupNames(newPersonIdent(), g);
    assertThat(readNamesByUuid(repo)).containsExactly(g.getGroupUUID(), "new name");

    ImmutableList<CommitInfo> log = log();
    assertThat(log).hasSize(2);
    assertThat(log.get(0)).message().isEqualTo("Update 1 group name");
    assertThat(log.get(1)).message().isEqualTo("Update 1 group name");
  }

  @Test
  public void updateGroupNamesFailsOnConflictingName() throws Exception {
    AccountGroup g1 = newGroup("a");
    AccountGroup g2 = newGroup("a");
    g2.setGroupUUID(new AccountGroup.UUID("another-uuid"));

    updateGroupNames(newPersonIdent(), g1);
    assertThat(readNamesByUuid(repo)).containsExactly(g1.getGroupUUID(), "a");

    try {
      updateGroupNames(newPersonIdent(), g2);
      assert_().fail("Expected ConfigInvalidException");
    } catch (ConfigInvalidException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Name 'a' points to UUID 'a-1' and not to 'another-uuid'");
    }
  }

  private AccountGroup newGroup(String name) {
    int id = idCounter.incrementAndGet();
    return new AccountGroup(
        new AccountGroup.NameKey(name),
        new AccountGroup.Id(id),
        new AccountGroup.UUID(name + "-" + id),
        TimeUtil.nowTs());
  }

  private static PersonIdent newPersonIdent() {
    return new PersonIdent(SERVER_NAME, SERVER_EMAIL, TimeUtil.nowTs(), TZ);
  }

  private void updateGroupNames(PersonIdent ident, AccountGroup... groups) throws Exception {
    try (ObjectInserter inserter = repo.newObjectInserter()) {
      BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
      BatchGroupNameNotes.updateGroupNames(repo, inserter, bru, Arrays.asList(groups), ident);
      inserter.flush();
      RefUpdateUtil.executeChecked(bru, repo);
    }
  }

  private ImmutableList<CommitInfo> log() throws Exception {
    ImmutableList<CommitInfo> result = ImmutableList.of();
    try (RevWalk rw = new RevWalk(repo)) {
      Ref ref = repo.exactRef(RefNames.REFS_GROUPNAMES);
      if (ref != null) {
        rw.sort(RevSort.REVERSE);
        rw.setRetainBody(true);
        rw.markStart(rw.parseCommit(ref.getObjectId()));
        result = Streams.stream(rw).map(CommitUtil::toCommitInfo).collect(toImmutableList());
      }
    }
    return result;
  }
}
