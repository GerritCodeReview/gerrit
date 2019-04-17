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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.ReflogEntryInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.project.testing.Util;
import com.google.inject.Inject;
import java.io.File;
import java.util.List;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

@UseLocalDisk
public class ReflogIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void guessRestApiInReflog() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    try (Repository repo = repoManager.openRepository(r.getChange().project())) {
      File log = new File(repo.getDirectory(), "logs/" + changeMetaRef(id));
      if (!log.exists()) {
        log.getParentFile().mkdirs();
        assertThat(log.createNewFile()).isTrue();
      }

      gApi.changes().id(id.get()).topic("foo");
      ReflogEntry last = repo.getReflogReader(changeMetaRef(id)).getLastEntry();
      assertThat(last).named("last RefLogEntry").isNotNull();
      assertThat(last.getComment()).isEqualTo("restapi.change.PutTopic");
    }
  }

  @Test
  public void reflogUpdatedBySubmittingChange() throws Exception {
    BranchApi branchApi = gApi.projects().name(project.get()).branch("master");
    List<ReflogEntryInfo> reflog = branchApi.reflog();
    assertThat(reflog).isNotEmpty();

    // Current number of entries in the reflog
    int refLogLen = reflog.size();

    // Create and submit a change
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revision = r.getCommit().name();
    ReviewInput in = ReviewInput.approve();
    gApi.changes().id(changeId).revision(revision).review(in);
    gApi.changes().id(changeId).revision(revision).submit();

    // Submitting the change causes a new entry in the reflog
    reflog = branchApi.reflog();
    assertThat(reflog).hasSize(refLogLen + 1);
  }

  @Test
  public void regularUserIsNotAllowedToGetReflog() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    exception.expect(AuthException.class);
    gApi.projects().name(project.get()).branch("master").reflog();
  }

  @Test
  public void ownerUserIsAllowedToGetReflog() throws Exception {
    GroupApi groupApi = gApi.groups().create(name("get-reflog"));
    groupApi.addMembers("user");

    try (ProjectConfigUpdate u = updateProject(project)) {
      Util.allow(u.getConfig(), Permission.OWNER, AccountGroup.uuid(groupApi.get().id), "refs/*");
      u.save();
    }

    requestScopeOperations.setApiUser(user.id());
    gApi.projects().name(project.get()).branch("master").reflog();
  }

  @Test
  public void adminUserIsAllowedToGetReflog() throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    gApi.projects().name(project.get()).branch("master").reflog();
  }
}
