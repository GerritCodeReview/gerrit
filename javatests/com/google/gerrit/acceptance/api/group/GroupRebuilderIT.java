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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.ServerInitiated;
import com.google.gerrit.server.group.db.GroupBundle;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.GroupRebuilder;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gerrit.testing.TestTimeUtil.TempClockStep;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class GroupRebuilderIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config config = new Config();
    // This test is explicitly testing the migration from ReviewDb to NoteDb, and handles reading
    // from NoteDb manually. It should work regardless of the value of writeGroupsToNoteDb, however.
    config.setBoolean("user", null, "readGroupsFromNoteDb", false);
    return config;
  }

  @Inject @GerritServerId private String serverId;
  @Inject @ServerInitiated private Provider<GroupsUpdate> groupsUpdate;
  @Inject private GroupRebuilder rebuilder;
  @Inject private Groups groups;

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void basicGroupProperties() throws Exception {
    GroupInfo createdGroup = gApi.groups().create(name("group")).get();
    InternalGroup reviewDbGroup = groups.getGroup(db, new AccountGroup.UUID(createdGroup.id)).get();
    deleteGroupRefs(reviewDbGroup);

    assertThat(removeRefState(rebuild(reviewDbGroup))).isEqualTo(roundToSecond(reviewDbGroup));
  }

  @Test
  public void logFormat() throws Exception {
    TestAccount user2 = accountCreator.user2();
    GroupInfo group1 = gApi.groups().create(name("group1")).get();
    GroupInfo group2 = gApi.groups().create(name("group2")).get();

    try (TempClockStep step = TestTimeUtil.freezeClock()) {
      gApi.groups().id(group1.id).addMembers(user.id.toString(), user2.id.toString());
    }

    gApi.groups().id(group1.id).addGroups(group2.id);

    InternalGroup reviewDbGroup = groups.getGroup(db, new AccountGroup.UUID(group1.id)).get();
    deleteGroupRefs(reviewDbGroup);

    InternalGroup noteDbGroup = rebuild(reviewDbGroup);
    assertThat(removeRefState(noteDbGroup)).isEqualTo(roundToSecond(reviewDbGroup));

    ImmutableList<CommitInfo> log = log(group1);
    assertThat(log).hasSize(4);

    assertThat(log.get(0)).message().isEqualTo("Create group");
    assertThat(log.get(0)).author().name().isEqualTo(serverIdent.get().getName());
    assertThat(log.get(0)).author().email().isEqualTo(serverIdent.get().getEmailAddress());
    assertThat(log.get(0)).author().date().isEqualTo(noteDbGroup.getCreatedOn());
    assertThat(log.get(0)).author().tz().isEqualTo(serverIdent.get().getTimeZoneOffset());
    assertThat(log.get(0)).committer().isEqualTo(log.get(0).author);

    assertThat(log.get(1))
        .message()
        .isEqualTo("Update group\n\nAdd: Administrator <" + admin.id + "@" + serverId + ">");
    assertThat(log.get(1)).author().name().isEqualTo(admin.fullName);
    assertThat(log.get(1)).author().email().isEqualTo(admin.id + "@" + serverId);
    assertThat(log.get(1)).committer().hasSameDateAs(log.get(1).author);

    assertThat(log.get(2))
        .message()
        .isEqualTo(
            "Update group\n"
                + "\n"
                + ("Add: User <" + user.id + "@" + serverId + ">\n")
                + ("Add: User2 <" + user2.id + "@" + serverId + ">"));
    assertThat(log.get(2)).author().name().isEqualTo(admin.fullName);
    assertThat(log.get(2)).author().email().isEqualTo(admin.id + "@" + serverId);
    assertThat(log.get(2)).committer().hasSameDateAs(log.get(2).author);

    assertThat(log.get(3))
        .message()
        .isEqualTo("Update group\n\nAdd-group: " + group2.name + " <" + group2.id + ">");
    assertThat(log.get(3)).author().name().isEqualTo(admin.fullName);
    assertThat(log.get(3)).author().email().isEqualTo(admin.id + "@" + serverId);
    assertThat(log.get(3)).committer().hasSameDateAs(log.get(3).author);
  }

  private static InternalGroup removeRefState(InternalGroup group) throws Exception {
    return group.toBuilder().setRefState(null).build();
  }

  private void deleteGroupRefs(InternalGroup group) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      String refName = RefNames.refsGroups(group.getGroupUUID());
      RefUpdate ru = repo.updateRef(refName);
      ru.setForceUpdate(true);
      Ref oldRef = repo.exactRef(refName);
      if (oldRef == null) {
        return;
      }
      ru.setExpectedOldObjectId(oldRef.getObjectId());
      ru.setNewObjectId(ObjectId.zeroId());
      assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
    }
  }

  private InternalGroup rebuild(InternalGroup group) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      rebuilder.rebuild(repo, GroupBundle.fromReviewDb(db, group.getId()));
      GroupConfig groupConfig = GroupConfig.loadForGroup(repo, group.getGroupUUID());
      Optional<InternalGroup> result = groupConfig.getLoadedGroup();
      assertThat(result).isPresent();
      return result.get();
    }
  }

  private InternalGroup roundToSecond(InternalGroup g) {
    return InternalGroup.builder()
        .setId(g.getId())
        .setNameKey(g.getNameKey())
        .setDescription(g.getDescription())
        .setOwnerGroupUUID(g.getOwnerGroupUUID())
        .setVisibleToAll(g.isVisibleToAll())
        .setGroupUUID(g.getGroupUUID())
        .setCreatedOn(TimeUtil.roundToSecond(g.getCreatedOn()))
        .setMembers(g.getMembers())
        .setSubgroups(g.getSubgroups())
        .build();
  }

  private ImmutableList<CommitInfo> log(GroupInfo g) throws Exception {
    ImmutableList.Builder<CommitInfo> result = ImmutableList.builder();
    List<Date> commitDates = new ArrayList<>();
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      Ref ref = repo.exactRef(RefNames.refsGroups(new AccountGroup.UUID(g.id)));
      if (ref != null) {
        rw.sort(RevSort.REVERSE);
        rw.setRetainBody(true);
        rw.markStart(rw.parseCommit(ref.getObjectId()));
        for (RevCommit c : rw) {
          result.add(CommitUtil.toCommitInfo(c));
          commitDates.add(c.getCommitterIdent().getWhen());
        }
      }
    }
    assertThat(commitDates).named("commit timestamps for %s", result).isOrdered();
    return result.build();
  }
}
