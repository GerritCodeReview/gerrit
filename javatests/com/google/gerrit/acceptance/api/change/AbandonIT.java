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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.AbandonUtil;
import com.google.gerrit.server.config.ChangeCleanupConfig;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

public class AbandonIT extends AbstractDaemonTest {
  @Inject private AbandonUtil abandonUtil;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ChangeCleanupConfig cleanupConfig;

  @Test
  public void abandon() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes().id(changeId).abandon();
    ChangeInfo info = get(changeId, MESSAGES);
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("abandoned");

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("change is abandoned");
    gApi.changes().id(changeId).abandon();
  }

  @Test
  public void batchAbandon() throws Exception {
    CurrentUser user = atrScope.get().getUser();
    PushOneCommit.Result a = createChange();
    PushOneCommit.Result b = createChange();
    List<ChangeData> list = ImmutableList.of(a.getChange(), b.getChange());
    batchAbandon.batchAbandon(batchUpdateFactory, a.getChange().project(), user, list, "deadbeef");

    ChangeInfo info = get(a.getChangeId(), MESSAGES);
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("abandoned");
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("deadbeef");

    info = get(b.getChangeId(), MESSAGES);
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("abandoned");
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("deadbeef");
  }

  @Test
  public void batchAbandonChangeProject() throws Exception {
    String project1Name = name("Project1");
    String project2Name = name("Project2");
    gApi.projects().create(project1Name);
    gApi.projects().create(project2Name);
    TestRepository<InMemoryRepository> project1 = cloneProject(new Project.NameKey(project1Name));
    TestRepository<InMemoryRepository> project2 = cloneProject(new Project.NameKey(project2Name));

    CurrentUser user = atrScope.get().getUser();
    PushOneCommit.Result a = createChange(project1, "master", "x", "x", "x", "");
    PushOneCommit.Result b = createChange(project2, "master", "x", "x", "x", "");
    List<ChangeData> list = ImmutableList.of(a.getChange(), b.getChange());
    exception.expect(ResourceConflictException.class);
    exception.expectMessage(
        String.format("Project name \"%s\" doesn't match \"%s\"", project2Name, project1Name));
    batchAbandon.batchAbandon(batchUpdateFactory, new Project.NameKey(project1Name), user, list);
  }

  @Test
  @GerritConfig(name = "changeCleanup.abandonAfter", value = "1w")
  public void abandonInactiveOpenChanges() throws Exception {
    TestTimeUtil.resetWithClockStep(1, SECONDS);

    // create 2 changes which will be abandoned ...
    int id1 = createChange().getChange().getId().get();
    int id2 = createChange().getChange().getId().get();

    // ... because they are older than 1 week
    TestTimeUtil.incrementClock(7 * 24, HOURS);

    // create 1 new change that will not be abandoned
    ChangeData cd = createChange().getChange();
    int id3 = cd.getId().get();

    assertThat(toChangeNumbers(query("is:open"))).containsExactly(id1, id2, id3);
    assertThat(query("is:abandoned")).isEmpty();

    abandonUtil.abandonInactiveOpenChanges(batchUpdateFactory);
    assertThat(toChangeNumbers(query("is:open"))).containsExactly(id3);
    assertThat(toChangeNumbers(query("is:abandoned"))).containsExactly(id1, id2);
  }

  @Test
  public void changeCleanupConfigDefaultAbandonMessage() throws Exception {
    assertThat(cleanupConfig.getAbandonMessage())
        .startsWith(
            "Auto-Abandoned due to inactivity, see "
                + canonicalWebUrl.get()
                + "Documentation/user-change-cleanup.html#auto-abandon");
  }

  @Test
  @GerritConfig(name = "changeCleanup.abandonMessage", value = "XX ${URL} XX")
  public void changeCleanupConfigCustomAbandonMessageWithUrlReplacement() throws Exception {
    assertThat(cleanupConfig.getAbandonMessage())
        .isEqualTo(
            "XX "
                + canonicalWebUrl.get()
                + "Documentation/user-change-cleanup.html#auto-abandon XX");
  }

  @Test
  @GerritConfig(name = "changeCleanup.abandonMessage", value = "XX YYY XX")
  public void changeCleanupConfigCustomAbandonMessageWithoutUrlReplacement() throws Exception {
    assertThat(cleanupConfig.getAbandonMessage()).isEqualTo("XX YYY XX");
  }

  @Test
  public void abandonNotAllowedWithoutPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    requestScopeOperations.setApiUser(user.id());
    exception.expect(AuthException.class);
    exception.expectMessage("abandon not permitted");
    gApi.changes().id(changeId).abandon();
  }

  @Test
  public void abandonAndRestoreAllowedWithPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    grant(project, "refs/heads/master", Permission.ABANDON, false, REGISTERED_USERS);
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId).abandon();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.ABANDONED);
    gApi.changes().id(changeId).restore();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void restore() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes().id(changeId).abandon();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.ABANDONED);

    gApi.changes().id(changeId).restore();
    ChangeInfo info = get(changeId, MESSAGES);
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("restored");

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("change is new");
    gApi.changes().id(changeId).restore();
  }

  @Test
  public void restoreNotAllowedWithoutPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes().id(changeId).abandon();
    requestScopeOperations.setApiUser(user.id());
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.ABANDONED);
    exception.expect(AuthException.class);
    exception.expectMessage("restore not permitted");
    gApi.changes().id(changeId).restore();
  }

  private List<Integer> toChangeNumbers(List<ChangeInfo> changes) {
    return changes.stream().map(i -> i._number).collect(toList());
  }
}
