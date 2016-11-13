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

import static com.google.gerrit.common.data.Permission.LABEL;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import org.junit.Before;
import org.junit.Test;

public class ChangeOwnerIT extends AbstractDaemonTest {

  private TestAccount user2;

  @Before
  public void setUp() throws Exception {
    setApiUser(user);
    user2 = accounts.user2();
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void testChangeOwner_OwnerACLNotGranted() throws Exception {
    assertApproveFails(user, createMyChange());
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void testChangeOwner_OwnerACLGranted() throws Exception {
    grantApproveToChangeOwner();
    approve(user, createMyChange());
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void testChangeOwner_NotOwnerACLGranted() throws Exception {
    grantApproveToChangeOwner();
    assertApproveFails(user2, createMyChange());
  }

  private void approve(TestAccount a, String changeId) throws Exception {
    Context old = setApiUser(a);
    try {
      gApi.changes().id(changeId).current().review(ReviewInput.approve());
    } finally {
      atrScope.set(old);
    }
  }

  private void assertApproveFails(TestAccount a, String changeId) throws Exception {
    exception.expect(AuthException.class);
    approve(a, changeId);
  }

  private void grantApproveToChangeOwner() throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      md.setMessage(String.format("Grant approve to change owner"));
      ProjectConfig config = ProjectConfig.read(md);
      AccessSection s = config.getAccessSection("refs/heads/*", true);
      Permission p = s.getPermission(LABEL + "Code-Review", true);
      PermissionRule rule =
          new PermissionRule(
              config.resolve(SystemGroupBackend.getGroup(SystemGroupBackend.CHANGE_OWNER)));
      rule.setMin(-2);
      rule.setMax(+2);
      p.add(rule);
      config.commit(md);
      projectCache.evict(config.getProject());
    }
  }

  private String createMyChange() throws Exception {
    PushOneCommit push = pushFactory.create(db, user.getIdent(), testRepo);
    return push.to("refs/for/master").getChangeId();
  }
}
