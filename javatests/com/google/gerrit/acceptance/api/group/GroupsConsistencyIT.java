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

package com.google.gerrit.acceptance.api.group;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.server.notedb.NoteDbTable.GROUPS;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.group.db.GroupsConsistencyChecker;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class GroupsConsistencyIT extends AbstractDaemonTest {
  @Inject @GerritPersonIdent PersonIdent serverUser;

  @ConfigSuite.Config
  public static Config noteDbConfig() {
    Config config = new Config();
    config.setBoolean(NotesMigration.SECTION_NOTE_DB, GROUPS.key(), NotesMigration.WRITE, true);
    config.setBoolean(NotesMigration.SECTION_NOTE_DB, GROUPS.key(), NotesMigration.READ, true);
    return config;
  }

  @Inject private GroupsConsistencyChecker groupChecker;

  @Inject
  @Named("groups_byuuid")
  private LoadingCache<String, Optional<InternalGroup>> groupsByUUIDCache;

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  protected String createGroup(String name, String owner) throws Exception {
    name = name(name);
    GroupInput in = new GroupInput();
    in.name = name;
    in.ownerId = owner;
    gApi.groups().create(in);
    return name;
  }

  void assertError(String msg) throws Exception {
    assertConsistency(msg, true);
  }

  void assertWarning(String msg) throws Exception {
    assertConsistency(msg, false);
  }

  void assertConsistency(String msg, boolean error) throws Exception {
    ConsistencyProblemInfo.Status want =
        error ? ConsistencyProblemInfo.Status.ERROR : ConsistencyProblemInfo.Status.WARNING;
    List<ConsistencyProblemInfo> problems = groupChecker.check();
    for (ConsistencyProblemInfo i : problems) {
      if (!i.status.equals(want)) {
        continue;
      }
      if (i.message.contains(msg)) {
        return;
      }
    }

    fail(String.format("could not find substring '%s' in %s", msg, problems));
  }

  GroupInfo gAdmin;
  GroupInfo g1;
  GroupInfo g2;

  String bogus = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

  @Before
  public void basicSetup() throws Exception {
    assume().that(groupsInNoteDb()).isTrue();

    String g1 = createGroup("g1", "Administrators");
    String g2 = createGroup("g2", "Administrators");

    gApi.groups().id(g1).addMembers("user");
    gApi.groups().id(g2).addMembers("admin");
    gApi.groups().id(g1).addGroups(g2);

    this.g1 = gApi.groups().id(g1).detail();
    this.g2 = gApi.groups().id(g2).detail();
    this.gAdmin = gApi.groups().id("Administrators").detail();
  }

  public boolean groupsInNoteDb() {
    return cfg.getBoolean(
        NotesMigration.SECTION_NOTE_DB, GROUPS.key(), NotesMigration.WRITE, false);
  }

  @Test
  public void missingGroupNameRef() throws Exception {
    assume().that(groupsInNoteDb()).isTrue();

    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefUpdate ru = repo.updateRef(RefNames.REFS_GROUPNAMES);
      ru.setForceUpdate(true);
      RefUpdate.Result result = ru.delete();
      assertThat(result).isEqualTo(Result.FORCED);
    }

    assertError("refs/meta/group-names does not exist");
  }

  @Test
  public void allGood() throws Exception {
    assertThat(groupChecker.check()).isEmpty();
  }

  @Test
  public void missingGroupRef() throws Exception {
    assume().that(groupsInNoteDb()).isTrue();

    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefUpdate ru = repo.updateRef(RefNames.refsGroups(new AccountGroup.UUID(g1.id)));
      ru.setForceUpdate(true);
      RefUpdate.Result result = ru.delete();
      assertThat(result).isEqualTo(Result.FORCED);
    }

    assertError("missing as group ref");
  }

  @Test
  public void parseGroupRef() throws Exception {
    assume().that(groupsInNoteDb()).isTrue();

    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefRename ru =
          repo.renameRef(
              RefNames.refsGroups(new AccountGroup.UUID(g1.id)), RefNames.REFS_GROUPS + bogus);
      RefUpdate.Result result = ru.rename();
      assertThat(result).isEqualTo(Result.RENAMED);
    }

    assertError("null UUID from " + bogus);
  }

  @Test
  public void missingNameEntry() throws Exception {
    assume().that(groupsInNoteDb()).isTrue();

    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefRename ru =
          repo.renameRef(
              RefNames.refsGroups(new AccountGroup.UUID(g1.id)),
              RefNames.refsGroups(new AccountGroup.UUID(bogus)));
      RefUpdate.Result result = ru.rename();
      assertThat(result).isEqualTo(Result.RENAMED);
    }

    assertError("group " + bogus + " has no entry in name map");
  }

  void updateGroupFile(String refname, String name, String contents) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo);
        TreeWalk tw = new TreeWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      Ref ref = repo.exactRef(refname);
      RevCommit c = rw.parseCommit(ref.getObjectId());

      DirCache dc = DirCache.newInCore();
      DirCacheBuilder dirCacheBuilder = dc.builder();

      // Copy the old bits of the tree.
      // NOSUBMIT - this feels incredibly clumsy.
      tw.addTree(c.getTree());
      tw.setRecursive(true);
      while (tw.next()) {
        if (tw.getPathString().equals(name)) {
          continue;
        }
        DirCacheEntry de = new DirCacheEntry(tw.getPathString());
        de.setFileMode(tw.getFileMode());
        de.setObjectId(tw.getObjectId(0));
        dirCacheBuilder.add(de);
      }

      DirCacheEntry e = new DirCacheEntry(name);
      e.setObjectId(ins.insert(Constants.OBJ_BLOB, contents.getBytes(StandardCharsets.UTF_8)));
      e.setFileMode(FileMode.REGULAR_FILE);

      dirCacheBuilder.add(e);
      dirCacheBuilder.finish();

      CommitBuilder cb = new CommitBuilder();
      cb.setParentId(c);
      cb.setMessage("update group file");
      cb.setTreeId(dc.writeTree(ins));
      cb.setAuthor(serverUser);
      cb.setCommitter(serverUser);

      ObjectId newCommit = ins.insert(Constants.OBJ_COMMIT, cb.build());
      ins.flush();

      RefUpdate ru = repo.updateRef(refname);
      ru.setExpectedOldObjectId(ref.getObjectId());
      ru.setNewObjectId(newCommit);
      assertThat(ru.update()).isEqualTo(Result.FAST_FORWARD);
    }
  }

  @Test
  public void groupRefDoesNotParse() throws Exception {
    assume().that(groupsInNoteDb()).isTrue();
    updateGroupFile(
        RefNames.refsGroups(new AccountGroup.UUID(g1.id)),
        GroupConfig.GROUP_CONFIG_FILE,
        "[this is not valid\n");
    assertError("does not parse");
  }

  @Test
  public void nameRefDoesNotParse() throws Exception {
    updateGroupFile(
        RefNames.REFS_GROUPNAMES,
        GroupNameNotes.getNoteKey(new AccountGroup.NameKey(g1.name)).getName(),
        "[this is not valid\n");
    assertError("does not parse");
  }

  @Test
  public void inconsistentName() throws Exception {
    Config cfg = new Config();
    cfg.setString("group", null, "name", "not really");
    cfg.setString("group", null, "id", "42");
    cfg.setString("group", null, "ownerGroupUuid", gAdmin.id);

    updateGroupFile(
        RefNames.refsGroups(new AccountGroup.UUID(g1.id)),
        GroupConfig.GROUP_CONFIG_FILE,
        cfg.toText());
    assertError("inconsistent name");
  }

  @Test
  public void sharedGroupID() throws Exception {
    Config cfg = new Config();
    cfg.setString("group", null, "name", "not really");
    cfg.setInt("group", null, "id", g2.groupId);
    cfg.setString("group", null, "ownerGroupUuid", gAdmin.id);

    updateGroupFile(
        RefNames.refsGroups(new AccountGroup.UUID(g1.id)),
        GroupConfig.GROUP_CONFIG_FILE,
        cfg.toText());
    assertError("shared group id");
  }

  @Test
  public void unknownOwnerGroup() throws Exception {
    Config cfg = new Config();
    cfg.setString("group", null, "name", g1.name);
    cfg.setInt("group", null, "id", g2.groupId);
    cfg.setString("group", null, "ownerGroupUuid", bogus);

    updateGroupFile(
        RefNames.refsGroups(new AccountGroup.UUID(g1.id)),
        GroupConfig.GROUP_CONFIG_FILE,
        cfg.toText());
    assertError("nonexistent owner group");
  }

  @Test
  public void nameWithoutGroupRef() throws Exception {
    Config config = new Config();
    config.setString("group", null, "uuid", bogus);
    config.setString("group", null, "name", "bogus");

    updateGroupFile(
        RefNames.REFS_GROUPNAMES,
        GroupNameNotes.getNoteKey(new AccountGroup.NameKey("bogus")).getName(),
        config.toText());
    assertError("entry missing as group ref");
  }

  @Test
  public void nonexistentMember() throws Exception {
    updateGroupFile(RefNames.refsGroups(new AccountGroup.UUID(g1.id)), "members", "314159265\n");

    assertError("non-existent member 314159265");
  }

  @Test
  public void nonexistentSubgroup() throws Exception {
    updateGroupFile(RefNames.refsGroups(new AccountGroup.UUID(g1.id)), "subgroups", bogus + "\n");

    assertError("has nonexistent subgroup");
  }

  @Test
  public void cyclicSubgroup() throws Exception {
    updateGroupFile(RefNames.refsGroups(new AccountGroup.UUID(g1.id)), "subgroups", g1.id + "\n");

    assertWarning("cyclic");
  }
}
