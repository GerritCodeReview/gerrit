// Copyright (C) 2011 The Android Open Source Project
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
package com.google.gerrit.httpd.rpc.project.restrictions;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.errors.RuleNotAllowedException;
import com.google.gerrit.reviewdb.AccountGroup;

import org.eclipse.jgit.lib.Config;
import org.junit.BeforeClass;
import org.junit.Test;

public class RestrictionsConfigTest {

  private static RestrictionsConfig NO_RESTRICTIONS;
  private static RestrictionsConfig DENY_TAG_UPDATE;
  private static Permission PUSH_PERMISSION;
  private static PermissionRule ALLOW_FORCE;

  @BeforeClass
  public static void setUpPermissions() {
    NO_RESTRICTIONS = new RestrictionsConfig(new Config());

    Config cfg = new Config();
    cfg.setBoolean("restrictions", null, "denyTagUpdate", true);
    DENY_TAG_UPDATE = new RestrictionsConfig(cfg);

    PUSH_PERMISSION = new Permission(Permission.PUSH);
    ALLOW_FORCE = new PermissionRule();
    AccountGroup registeredUsers = new AccountGroup(
        new AccountGroup.NameKey("Registered Users"),
        new AccountGroup.Id(0),
        AccountGroup.REGISTERED_USERS);
    ALLOW_FORCE.setGroup(GroupReference.forGroup(registeredUsers));
    ALLOW_FORCE.setAction(PermissionRule.Action.ALLOW);
    ALLOW_FORCE.setForce(true);
  }

  @Test
  public void noRestrictionsForcePushTagsAllowed() throws RuleNotAllowedException {
    NO_RESTRICTIONS.checkPermissionRule(new AccessSection("refs/tags/abc"),
        PUSH_PERMISSION, ALLOW_FORCE);

    NO_RESTRICTIONS.checkPermissionRule(new AccessSection("refs/tags/*"),
        PUSH_PERMISSION, ALLOW_FORCE);
    NO_RESTRICTIONS.checkPermissionRule(new AccessSection("refs/tags/abc/*"),
        PUSH_PERMISSION, ALLOW_FORCE);

    NO_RESTRICTIONS.checkPermissionRule(new AccessSection("^refs/tags/.*"),
        PUSH_PERMISSION, ALLOW_FORCE);
    NO_RESTRICTIONS.checkPermissionRule(new AccessSection("^refs/t[aA]gs/.*"),
        PUSH_PERMISSION, ALLOW_FORCE);
    NO_RESTRICTIONS.checkPermissionRule(new AccessSection("^refs/[tags]*/.*"),
        PUSH_PERMISSION, ALLOW_FORCE);
    NO_RESTRICTIONS.checkPermissionRule(new AccessSection("^refs/[tTaAgGsS]*/.*"),
        PUSH_PERMISSION, ALLOW_FORCE);
  }

  @Test(expected = RuleNotAllowedException.class)
  public void denyTagUpdateForcePushTagsDenied_Ref1()
      throws RuleNotAllowedException {
    DENY_TAG_UPDATE.checkPermissionRule(new AccessSection("refs/tags/abc"),
        PUSH_PERMISSION, ALLOW_FORCE);
  }

  @Test(expected = RuleNotAllowedException.class)
  public void denyTagUpdateForcePushTagsDenied_RefPattern1()
      throws RuleNotAllowedException {
    DENY_TAG_UPDATE.checkPermissionRule(new AccessSection("refs/tags/*"),
        PUSH_PERMISSION, ALLOW_FORCE);
  }

  @Test(expected = RuleNotAllowedException.class)
  public void denyTagUpdateForcePushTagsDenied_RegEx1()
      throws RuleNotAllowedException {
    DENY_TAG_UPDATE.checkPermissionRule(new AccessSection("^refs/tags/.*"),
        PUSH_PERMISSION, ALLOW_FORCE);
  }

  @Test(expected = RuleNotAllowedException.class)
  public void denyTagUpdateForcePushTagsDenied_RegEx2()
      throws RuleNotAllowedException {
    DENY_TAG_UPDATE.checkPermissionRule(new AccessSection("^refs/t[aA]gs/.*"),
        PUSH_PERMISSION, ALLOW_FORCE);
  }

  @Test(expected = RuleNotAllowedException.class)
  public void denyTagUpdateForcePushTagsDenied_RegEx3()
      throws RuleNotAllowedException {
    DENY_TAG_UPDATE.checkPermissionRule(new AccessSection("^refs/[tags]*/.*"),
        PUSH_PERMISSION, ALLOW_FORCE);
  }

  @Test(expected = RuleNotAllowedException.class)
  public void denyTagUpdateForcePushTagsDenied_RegEx4()
      throws RuleNotAllowedException {
    DENY_TAG_UPDATE.checkPermissionRule(new AccessSection("^refs/[tTaAgGsS]*/.*"),
        PUSH_PERMISSION, ALLOW_FORCE);
  }

}
