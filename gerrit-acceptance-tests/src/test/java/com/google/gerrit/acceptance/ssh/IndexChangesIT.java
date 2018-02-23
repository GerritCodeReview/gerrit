package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import java.util.List;
import java.util.Locale;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.Util;

@UseSsh
public class IndexChangesIT extends AbstractDaemonTest {
  @Test
  public void indexChangeAfterOwnerLosesVisibility() throws Exception {
    // Create a test group with 2 users as members
    TestAccount user2 = accounts.user2();
    String group = createGroup("test");
    gApi.groups().id(group).addMembers("user", user2.username);

    // Create a project and restrict its visibility to the group
    Project.NameKey p = createProject("p");
    ProjectConfig cfg = projectCache.checkedGet(p).getConfig();
    Util.allow(
        cfg,
        Permission.READ,
        groupCache.get(new AccountGroup.NameKey(group)).getGroupUUID(),
        "refs/*");
    Util.block(cfg, Permission.READ, REGISTERED_USERS, "refs/*");
    saveProjectConfig(p, cfg);

    // Clone it and push a change as a regular user
    TestRepository<InMemoryRepository> repo = cloneProject(p, user);
    PushOneCommit push = pushFactory.create(db, user.getIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();
    assertThat(result.getChange().change().getOwner()).isEqualTo(user.id);
    String changeId = result.getChangeId();

    // User can see the change and it is mergeable
    setApiUser(user);
    List<ChangeInfo> changes = gApi.changes().query(changeId).get();
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).mergeable).isNotNull();

    // Other user can see the change and it is mergeable
    setApiUser(user2);
    changes = gApi.changes().query(changeId).get();
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).mergeable).isTrue();

    // Remove the user from the group so they can no longer see the project
    setApiUser(admin);
    gApi.groups().id(group).removeMembers("user");

    // User can no longer see the change
    setApiUser(user);
    changes = gApi.changes().query(changeId).get();
    assertThat(changes).isEmpty();

    // Reindex
    // This fails because there is no online indexer during tests
    String response = adminSshSession.exec("gerrit index start changes --force").toLowerCase(Locale.US);
    assert_()
    .withFailureMessage(adminSshSession.getError())
    .that(adminSshSession.hasError())
    .isFalse();
    assertThat(response).doesNotContain("error");
    assertThat(response).contains("reindexer started");

    // Other user can still see the change and it is still mergeable
    setApiUser(user2);
    changes = gApi.changes().query(changeId).get();
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).mergeable).isTrue();
  }
}
