// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.initSsh;
import static com.google.gerrit.common.data.Permission.LABEL;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class ChangeOwnerIT extends AbstractDaemonTest {

  private TestAccount user2;

  private RestSession sessionOwner;
  private RestSession sessionDev;

  @Before
  public void setUp() throws Exception {
    sessionOwner = new RestSession(server, user);
    SshSession sshSession = new SshSession(server, user);
    initSsh(user);
    sshSession.open();
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();
    user2 = accounts.user2();
    sessionDev = new RestSession(server, user2);
  }

  @Test
  public void testChangeOwner_OwnerACLNotGranted() throws Exception {
    approve(sessionOwner, createMyChange(), HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void testChangeOwner_OwnerACLGranted() throws Exception {
    grantApproveToChangeOwner();
    approve(sessionOwner, createMyChange(), HttpStatus.SC_OK);
  }

  @Test
  public void testChangeOwner_NotOwnerACLGranted() throws Exception {
    grantApproveToChangeOwner();
    approve(sessionDev, createMyChange(), HttpStatus.SC_FORBIDDEN);
  }

  private void approve(RestSession s, String changeId, int expected)
      throws IOException {
    RestResponse r =
        s.post("/changes/" + changeId + "/revisions/current/review",
            new ReviewInput().label("Code-Review", 2));
    assertThat(r.getStatusCode()).isEqualTo(expected);
    r.consume();
  }

  private void grantApproveToChangeOwner() throws IOException,
      ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    md.setMessage(String.format("Grant approve to change owner"));
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection("refs/heads/*", true);
    Permission p = s.getPermission(LABEL + "Code-Review", true);
    PermissionRule rule = new PermissionRule(config
        .resolve(SystemGroupBackend.getGroup(SystemGroupBackend.CHANGE_OWNER)));
    rule.setMin(-2);
    rule.setMax(+2);
    p.add(rule);
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  private String createMyChange() throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, user.getIdent(), git);
    return push.to("refs/for/master").getChangeId();
  }
}
