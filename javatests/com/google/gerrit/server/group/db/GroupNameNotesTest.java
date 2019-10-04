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
import static com.google.gerrit.common.data.testing.GroupReferenceSubject.groupReferences;
import static com.google.gerrit.entities.RefNames.REFS_GROUPNAMES;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.commits;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.testing.GroupReferenceSubject;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.testing.CommitInfoSubject;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.GitTestUtil;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gerrit.truth.ListSubject;
import com.google.gerrit.truth.OptionalSubject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GroupNameNotesTest {
  private static final String SERVER_NAME = "Gerrit Server";
  private static final String SERVER_EMAIL = "noreply@gerritcodereview.com";
  private static final TimeZone TZ = TimeZone.getTimeZone("America/Los_Angeles");

  private final AccountGroup.UUID groupUuid = AccountGroup.uuid("users-XYZ");
  private final AccountGroup.NameKey groupName = AccountGroup.nameKey("users");

  private AtomicInteger idCounter;
  private AllUsersName allUsersName;
  private Repository repo;

  @Before
  public void setUp() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    idCounter = new AtomicInteger();
    allUsersName = new AllUsersName(AllUsersNameProvider.DEFAULT);
    repo = new InMemoryRepository(new DfsRepositoryDescription(AllUsersNameProvider.DEFAULT));
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void newGroupCanBeCreated() throws Exception {
    createGroup(groupUuid, groupName);

    Optional<GroupReference> groupReference = loadGroup(groupName);
    assertThatGroup(groupReference).value().groupUuid().isEqualTo(groupUuid);
    assertThatGroup(groupReference).value().name().isEqualTo(groupName.get());
  }

  @Test
  public void uuidOfNewGroupMustNotBeNull() throws Exception {
    assertThrows(
        NullPointerException.class,
        () -> GroupNameNotes.forNewGroup(allUsersName, repo, null, groupName));
  }

  @Test
  public void nameOfNewGroupMustNotBeNull() throws Exception {
    assertThrows(
        NullPointerException.class,
        () -> GroupNameNotes.forNewGroup(allUsersName, repo, groupUuid, null));
  }

  @Test
  public void nameOfNewGroupMayBeEmpty() throws Exception {
    AccountGroup.NameKey emptyName = AccountGroup.nameKey("");
    createGroup(groupUuid, emptyName);

    Optional<GroupReference> groupReference = loadGroup(emptyName);
    assertThatGroup(groupReference).value().name().isEqualTo("");
  }

  @Test
  public void newGroupMustNotReuseNameOfAnotherGroup() throws Exception {
    createGroup(groupUuid, groupName);

    AccountGroup.UUID anotherGroupUuid = AccountGroup.uuid("AnotherGroup");
    DuplicateKeyException thrown =
        assertThrows(
            DuplicateKeyException.class,
            () -> GroupNameNotes.forNewGroup(allUsersName, repo, anotherGroupUuid, groupName));
    assertThat(thrown).hasMessageThat().contains(groupName.get());
  }

  @Test
  public void newGroupMayReuseUuidOfAnotherGroup() throws Exception {
    createGroup(groupUuid, groupName);

    AccountGroup.NameKey anotherName = AccountGroup.nameKey("admins");
    createGroup(groupUuid, anotherName);

    Optional<GroupReference> group1 = loadGroup(groupName);
    assertThatGroup(group1).value().groupUuid().isEqualTo(groupUuid);
    Optional<GroupReference> group2 = loadGroup(anotherName);
    assertThatGroup(group2).value().groupUuid().isEqualTo(groupUuid);
  }

  @Test
  public void groupCanBeRenamed() throws Exception {
    createGroup(groupUuid, groupName);

    AccountGroup.NameKey anotherName = AccountGroup.nameKey("admins");
    renameGroup(groupUuid, groupName, anotherName);

    Optional<GroupReference> groupReference = loadGroup(anotherName);
    assertThatGroup(groupReference).value().groupUuid().isEqualTo(groupUuid);
    assertThatGroup(groupReference).value().name().isEqualTo(anotherName.get());
  }

  @Test
  public void previousNameOfGroupCannotBeUsedAfterRename() throws Exception {
    createGroup(groupUuid, groupName);

    AccountGroup.NameKey anotherName = AccountGroup.nameKey("admins");
    renameGroup(groupUuid, groupName, anotherName);

    Optional<GroupReference> group = loadGroup(groupName);
    assertThatGroup(group).isAbsent();
  }

  @Test
  public void groupCannotBeRenamedToNull() throws Exception {
    createGroup(groupUuid, groupName);
    assertThrows(
        NullPointerException.class,
        () -> GroupNameNotes.forRename(allUsersName, repo, groupUuid, groupName, null));
  }

  @Test
  public void oldNameOfGroupMustBeSpecifiedForRename() throws Exception {
    createGroup(groupUuid, groupName);

    AccountGroup.NameKey anotherName = AccountGroup.nameKey("admins");
    assertThrows(
        NullPointerException.class,
        () -> GroupNameNotes.forRename(allUsersName, repo, groupUuid, null, anotherName));
  }

  @Test
  public void groupCannotBeRenamedWhenOldNameIsWrong() throws Exception {
    createGroup(groupUuid, groupName);

    AccountGroup.NameKey anotherOldName = AccountGroup.nameKey("contributors");
    AccountGroup.NameKey anotherName = AccountGroup.nameKey("admins");
    ConfigInvalidException thrown =
        assertThrows(
            ConfigInvalidException.class,
            () ->
                GroupNameNotes.forRename(
                    allUsersName, repo, groupUuid, anotherOldName, anotherName));
    assertThat(thrown).hasMessageThat().contains(anotherOldName.get());
  }

  @Test
  public void groupCannotBeRenamedToNameOfAnotherGroup() throws Exception {
    createGroup(groupUuid, groupName);
    AccountGroup.UUID anotherGroupUuid = AccountGroup.uuid("admins-ABC");
    AccountGroup.NameKey anotherGroupName = AccountGroup.nameKey("admins");
    createGroup(anotherGroupUuid, anotherGroupName);

    DuplicateKeyException thrown =
        assertThrows(
            DuplicateKeyException.class,
            () ->
                GroupNameNotes.forRename(
                    allUsersName, repo, groupUuid, groupName, anotherGroupName));
    assertThat(thrown).hasMessageThat().contains(anotherGroupName.get());
  }

  @Test
  public void groupCannotBeRenamedWithoutSpecifiedUuid() throws Exception {
    createGroup(groupUuid, groupName);

    AccountGroup.NameKey anotherName = AccountGroup.nameKey("admins");
    assertThrows(
        NullPointerException.class,
        () -> GroupNameNotes.forRename(allUsersName, repo, null, groupName, anotherName));
  }

  @Test
  public void groupCannotBeRenamedWhenUuidIsWrong() throws Exception {
    createGroup(groupUuid, groupName);

    AccountGroup.UUID anotherGroupUuid = AccountGroup.uuid("admins-ABC");
    AccountGroup.NameKey anotherName = AccountGroup.nameKey("admins");
    ConfigInvalidException thrown =
        assertThrows(
            ConfigInvalidException.class,
            () ->
                GroupNameNotes.forRename(
                    allUsersName, repo, anotherGroupUuid, groupName, anotherName));
    assertThat(thrown).hasMessageThat().contains(groupUuid.get());
  }

  @Test
  public void firstGroupCreationCreatesARootCommit() throws Exception {
    createGroup(groupUuid, groupName);

    Ref ref = repo.exactRef(RefNames.REFS_GROUPNAMES);
    assertThat(ref.getObjectId()).isNotNull();

    try (RevWalk revWalk = new RevWalk(repo)) {
      RevCommit revCommit = revWalk.parseCommit(ref.getObjectId());
      assertThat(revCommit.getParentCount()).isEqualTo(0);
    }
  }

  @Test
  public void furtherGroupCreationAppendsACommit() throws Exception {
    createGroup(groupUuid, groupName);
    ImmutableList<CommitInfo> commitsAfterCreation = log();

    AccountGroup.UUID anotherGroupUuid = AccountGroup.uuid("admins-ABC");
    AccountGroup.NameKey anotherName = AccountGroup.nameKey("admins");
    createGroup(anotherGroupUuid, anotherName);

    ImmutableList<CommitInfo> commitsAfterFurtherGroup = log();
    assertThatCommits(commitsAfterFurtherGroup).containsAtLeastElementsIn(commitsAfterCreation);
    assertThatCommits(commitsAfterFurtherGroup).lastElement().isNotIn(commitsAfterCreation);
  }

  @Test
  public void groupRenamingAppendsACommit() throws Exception {
    createGroup(groupUuid, groupName);
    ImmutableList<CommitInfo> commitsAfterCreation = log();

    AccountGroup.NameKey anotherName = AccountGroup.nameKey("admins");
    renameGroup(groupUuid, groupName, anotherName);

    ImmutableList<CommitInfo> commitsAfterRename = log();
    assertThatCommits(commitsAfterRename).containsAtLeastElementsIn(commitsAfterCreation);
    assertThatCommits(commitsAfterRename).lastElement().isNotIn(commitsAfterCreation);
  }

  @Test
  public void newCommitIsNotCreatedForRedundantNameUpdate() throws Exception {
    createGroup(groupUuid, groupName);
    ImmutableList<CommitInfo> commitsAfterCreation = log();

    renameGroup(groupUuid, groupName, groupName);

    ImmutableList<CommitInfo> commitsAfterRename = log();
    assertThatCommits(commitsAfterRename).isEqualTo(commitsAfterCreation);
  }

  @Test
  public void newCommitIsNotCreatedWhenCommittingGroupCreationTwice() throws Exception {
    GroupNameNotes groupNameNotes =
        GroupNameNotes.forNewGroup(allUsersName, repo, groupUuid, groupName);

    commit(groupNameNotes);
    ImmutableList<CommitInfo> commitsAfterFirstCommit = log();
    commit(groupNameNotes);
    ImmutableList<CommitInfo> commitsAfterSecondCommit = log();

    assertThatCommits(commitsAfterSecondCommit).isEqualTo(commitsAfterFirstCommit);
  }

  @Test
  public void newCommitIsNotCreatedWhenCommittingGroupRenamingTwice() throws Exception {
    createGroup(groupUuid, groupName);

    AccountGroup.NameKey anotherName = AccountGroup.nameKey("admins");
    GroupNameNotes groupNameNotes =
        GroupNameNotes.forRename(allUsersName, repo, groupUuid, groupName, anotherName);

    commit(groupNameNotes);
    ImmutableList<CommitInfo> commitsAfterFirstCommit = log();
    commit(groupNameNotes);
    ImmutableList<CommitInfo> commitsAfterSecondCommit = log();

    assertThatCommits(commitsAfterSecondCommit).isEqualTo(commitsAfterFirstCommit);
  }

  @Test
  public void commitMessageMentionsGroupCreation() throws Exception {
    createGroup(groupUuid, groupName);

    ImmutableList<CommitInfo> commits = log();
    assertThatCommits(commits).lastElement().message().contains("Create");
    assertThatCommits(commits).lastElement().message().contains(groupName.get());
  }

  @Test
  public void commitMessageMentionsGroupRenaming() throws Exception {
    createGroup(groupUuid, groupName);

    AccountGroup.NameKey anotherName = AccountGroup.nameKey("admins");
    renameGroup(groupUuid, groupName, anotherName);

    ImmutableList<CommitInfo> commits = log();
    assertThatCommits(commits).lastElement().message().contains("Rename");
    assertThatCommits(commits).lastElement().message().contains(groupName.get());
    assertThatCommits(commits).lastElement().message().contains(anotherName.get());
  }

  @Test
  public void nonExistentNotesRefIsEquivalentToNonExistentGroup() throws Exception {
    Optional<GroupReference> group = loadGroup(groupName);

    assertThatGroup(group).isAbsent();
  }

  @Test
  public void nonExistentGroupCannotBeLoaded() throws Exception {
    createGroup(AccountGroup.uuid("contributors-MN"), AccountGroup.nameKey("contributors"));
    createGroup(groupUuid, groupName);

    Optional<GroupReference> group = loadGroup(AccountGroup.nameKey("admins"));
    assertThatGroup(group).isAbsent();
  }

  @Test
  public void specificGroupCanBeLoaded() throws Exception {
    createGroup(AccountGroup.uuid("contributors-MN"), AccountGroup.nameKey("contributors"));
    createGroup(groupUuid, groupName);
    createGroup(AccountGroup.uuid("admins-ABC"), AccountGroup.nameKey("admins"));

    Optional<GroupReference> group = loadGroup(groupName);
    assertThatGroup(group).value().groupUuid().isEqualTo(groupUuid);
  }

  @Test
  public void nonExistentNotesRefIsEquivalentToNotAnyExistingGroups() throws Exception {
    ImmutableList<GroupReference> allGroups = GroupNameNotes.loadAllGroups(repo);

    assertThat(allGroups).isEmpty();
  }

  @Test
  public void allGroupsCanBeLoaded() throws Exception {
    AccountGroup.UUID groupUuid1 = AccountGroup.uuid("contributors-MN");
    AccountGroup.NameKey groupName1 = AccountGroup.nameKey("contributors");
    createGroup(groupUuid1, groupName1);
    AccountGroup.UUID groupUuid2 = AccountGroup.uuid("admins-ABC");
    AccountGroup.NameKey groupName2 = AccountGroup.nameKey("admins");
    createGroup(groupUuid2, groupName2);

    ImmutableList<GroupReference> allGroups = GroupNameNotes.loadAllGroups(repo);

    GroupReference group1 = new GroupReference(groupUuid1, groupName1.get());
    GroupReference group2 = new GroupReference(groupUuid2, groupName2.get());
    assertThat(allGroups).containsExactly(group1, group2);
  }

  @Test
  public void loadedGroupsContainGroupsWithDuplicateGroupUuids() throws Exception {
    createGroup(groupUuid, groupName);
    AccountGroup.NameKey anotherGroupName = AccountGroup.nameKey("admins");
    createGroup(groupUuid, anotherGroupName);

    ImmutableList<GroupReference> allGroups = GroupNameNotes.loadAllGroups(repo);

    GroupReference group1 = new GroupReference(groupUuid, groupName.get());
    GroupReference group2 = new GroupReference(groupUuid, anotherGroupName.get());
    assertThat(allGroups).containsExactly(group1, group2);
  }

  @Test
  public void updateGroupNames() throws Exception {
    GroupReference g1 = newGroup("a");
    GroupReference g2 = newGroup("b");

    PersonIdent ident = newPersonIdent();
    updateAllGroups(ident, g1, g2);

    ImmutableList<CommitInfo> log = log();
    assertThat(log).hasSize(1);
    assertThat(log.get(0)).parents().isEmpty();
    assertThat(log.get(0)).message().isEqualTo("Store 2 group names");
    assertThat(log.get(0)).author().matches(ident);
    assertThat(log.get(0)).committer().matches(ident);

    assertThat(GroupNameNotes.loadAllGroups(repo)).containsExactly(g1, g2);

    // Updating the same set of names is a no-op.
    String commit = log.get(0).commit;
    updateAllGroups(newPersonIdent(), g1, g2);
    log = log();
    assertThat(log).hasSize(1);
    assertThat(log.get(0)).commit().isEqualTo(commit);
  }

  @Test
  public void updateGroupNamesOverwritesExistingNotes() throws Exception {
    GroupReference g1 = newGroup("a");
    GroupReference g2 = newGroup("b");

    try (TestRepository<Repository> tr = new TestRepository<>(repo)) {
      ObjectId k1 = getNoteKey(g1);
      ObjectId k2 = getNoteKey(g2);
      ObjectId k3 = GroupNameNotes.getNoteKey(AccountGroup.nameKey("c"));
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
      updateAllGroups(ident, g1, g2);

      assertThat(GroupNameNotes.loadAllGroups(repo)).containsExactly(g1, g2);

      ImmutableList<CommitInfo> log = log();
      assertThat(log).hasSize(2);
      assertThat(log.get(0)).commit().isEqualTo(origCommitId.name());

      assertThat(log.get(1)).message().isEqualTo("Store 2 group names");
      assertThat(log.get(1)).author().matches(ident);
      assertThat(log.get(1)).committer().matches(ident);
    }

    // Old note content was overwritten.
    assertThat(readNameNote(g1)).isEqualTo("[group]\n\tuuid = a-1\n\tname = a\n");
  }

  @Test
  public void updateGroupNamesWithEmptyCollectionClearsAllNotes() throws Exception {
    GroupReference g1 = newGroup("a");
    GroupReference g2 = newGroup("b");

    PersonIdent ident = newPersonIdent();
    updateAllGroups(ident, g1, g2);

    assertThat(GroupNameNotes.loadAllGroups(repo)).containsExactly(g1, g2);

    updateAllGroups(ident);

    assertThat(GroupNameNotes.loadAllGroups(repo)).isEmpty();

    ImmutableList<CommitInfo> log = log();
    assertThat(log).hasSize(2);
    assertThat(log.get(1)).message().isEqualTo("Store 0 group names");
  }

  @Test
  public void updateGroupNamesRejectsNonOneToOneGroupReferences() throws Exception {
    assertIllegalArgument(
        new GroupReference(AccountGroup.uuid("uuid1"), "name1"),
        new GroupReference(AccountGroup.uuid("uuid1"), "name2"));
    assertIllegalArgument(
        new GroupReference(AccountGroup.uuid("uuid1"), "name1"),
        new GroupReference(AccountGroup.uuid("uuid2"), "name1"));
    assertIllegalArgument(
        new GroupReference(AccountGroup.uuid("uuid1"), "name1"),
        new GroupReference(AccountGroup.uuid("uuid1"), "name1"));
  }

  @Test
  public void emptyGroupName() throws Exception {
    GroupReference g = newGroup("");
    updateAllGroups(newPersonIdent(), g);

    assertThat(GroupNameNotes.loadAllGroups(repo)).containsExactly(g);
    assertThat(readNameNote(g)).isEqualTo("[group]\n\tuuid = -1\n\tname = \n");
  }

  private void createGroup(AccountGroup.UUID groupUuid, AccountGroup.NameKey groupName)
      throws Exception {
    GroupNameNotes groupNameNotes =
        GroupNameNotes.forNewGroup(allUsersName, repo, groupUuid, groupName);
    commit(groupNameNotes);
  }

  private void renameGroup(
      AccountGroup.UUID groupUuid, AccountGroup.NameKey oldName, AccountGroup.NameKey newName)
      throws Exception {
    GroupNameNotes groupNameNotes =
        GroupNameNotes.forRename(allUsersName, repo, groupUuid, oldName, newName);
    commit(groupNameNotes);
  }

  private Optional<GroupReference> loadGroup(AccountGroup.NameKey groupName) throws Exception {
    return GroupNameNotes.loadGroup(repo, groupName);
  }

  private void commit(GroupNameNotes groupNameNotes) throws IOException {
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      groupNameNotes.commit(metaDataUpdate);
    }
  }

  private MetaDataUpdate createMetaDataUpdate() {
    PersonIdent serverIdent = newPersonIdent();

    MetaDataUpdate metaDataUpdate =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, Project.nameKey("Test Repository"), repo);
    metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
    metaDataUpdate.getCommitBuilder().setAuthor(serverIdent);
    return metaDataUpdate;
  }

  private GroupReference newGroup(String name) {
    int id = idCounter.incrementAndGet();
    return new GroupReference(AccountGroup.uuid(name + "-" + id), name);
  }

  private static PersonIdent newPersonIdent() {
    return new PersonIdent(SERVER_NAME, SERVER_EMAIL, TimeUtil.nowTs(), TZ);
  }

  private static ObjectId getNoteKey(GroupReference g) {
    return GroupNameNotes.getNoteKey(AccountGroup.nameKey(g.getName()));
  }

  private void updateAllGroups(PersonIdent ident, GroupReference... groupRefs) throws Exception {
    try (ObjectInserter inserter = repo.newObjectInserter()) {
      BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
      GroupNameNotes.updateAllGroups(repo, inserter, bru, Arrays.asList(groupRefs), ident);
      inserter.flush();
      RefUpdateUtil.executeChecked(bru, repo);
    }
  }

  private void assertIllegalArgument(GroupReference... groupRefs) throws Exception {
    try (ObjectInserter inserter = repo.newObjectInserter()) {
      BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
      PersonIdent ident = newPersonIdent();
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  GroupNameNotes.updateAllGroups(
                      repo, inserter, bru, Arrays.asList(groupRefs), ident));
      assertThat(thrown).hasMessageThat().isEqualTo(GroupNameNotes.UNIQUE_REF_ERROR);
    }
  }

  private ImmutableList<CommitInfo> log() throws Exception {
    return GitTestUtil.log(repo, REFS_GROUPNAMES);
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

  private static OptionalSubject<GroupReferenceSubject, GroupReference> assertThatGroup(
      Optional<GroupReference> group) {
    return assertThat(group, groupReferences());
  }

  private static ListSubject<CommitInfoSubject, CommitInfo> assertThatCommits(
      List<CommitInfo> commits) {
    return ListSubject.assertThat(commits, commits());
  }
}
