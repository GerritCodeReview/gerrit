// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.common.data.Permission.EDIT_TOPIC_NAME;
import static com.google.gerrit.common.data.Permission.LABEL;
import static com.google.gerrit.common.data.Permission.OWNER;
import static com.google.gerrit.common.data.Permission.PUSH;
import static com.google.gerrit.common.data.Permission.READ;
import static com.google.gerrit.common.data.Permission.SUBMIT;
import static com.google.gerrit.server.project.Util.ANONYMOUS;
import static com.google.gerrit.server.project.Util.REGISTERED;
import static com.google.gerrit.server.project.Util.CHANGE_OWNER;
import static com.google.gerrit.server.project.Util.ADMIN;
import static com.google.gerrit.server.project.Util.DEVS;
import static com.google.gerrit.server.project.Util.grant;
import static com.google.gerrit.server.project.Util.doNotInherit;

import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.ProjectConfig;

import junit.framework.TestCase;

public class RefControlTest extends TestCase {
  private static void assertOwner(String ref, ProjectControl u) {
    assertTrue("OWN " + ref, u.controlForRef(ref).isOwner());
  }

  private static void assertNotOwner(String ref, ProjectControl u) {
    assertFalse("NOT OWN " + ref, u.controlForRef(ref).isOwner());
  }

  private final AccountGroup.UUID fixers = new AccountGroup.UUID("test.fixers");
  private Project.NameKey localKey = new Project.NameKey("local");
  private ProjectConfig local;
  private final Util util;

  public RefControlTest() {
    util = new Util();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    local = new ProjectConfig(localKey);
    local.createInMemory();
    util.add(local);
  }

  public void testOwnerProject() {
    grant(local, OWNER, ADMIN, "refs/*");

    ProjectControl uBlah = util.user(local, DEVS);
    ProjectControl uAdmin = util.user(local, DEVS, ADMIN);

    assertFalse("not owner", uBlah.isOwner());
    assertTrue("is owner", uAdmin.isOwner());
  }

  public void testBranchDelegation1() {
    grant(local, OWNER, ADMIN, "refs/*");
    grant(local, OWNER, DEVS, "refs/heads/x/*");

    ProjectControl uDev = util.user(local, DEVS);
    assertFalse("not owner", uDev.isOwner());
    assertTrue("owns ref", uDev.isOwnerAnyRef());

    assertOwner("refs/heads/x/*", uDev);
    assertOwner("refs/heads/x/y", uDev);
    assertOwner("refs/heads/x/y/*", uDev);

    assertNotOwner("refs/*", uDev);
    assertNotOwner("refs/heads/master", uDev);
  }

  public void testBranchDelegation2() {
    grant(local, OWNER, ADMIN, "refs/*");
    grant(local, OWNER, DEVS, "refs/heads/x/*");
    grant(local, OWNER, fixers, "refs/heads/x/y/*");
    doNotInherit(local, OWNER, "refs/heads/x/y/*");

    ProjectControl uDev = util.user(local, DEVS);
    assertFalse("not owner", uDev.isOwner());
    assertTrue("owns ref", uDev.isOwnerAnyRef());

    assertOwner("refs/heads/x/*", uDev);
    assertOwner("refs/heads/x/y", uDev);
    assertOwner("refs/heads/x/y/*", uDev);
    assertNotOwner("refs/*", uDev);
    assertNotOwner("refs/heads/master", uDev);

    ProjectControl uFix = util.user(local, fixers);
    assertFalse("not owner", uFix.isOwner());
    assertTrue("owns ref", uFix.isOwnerAnyRef());

    assertOwner("refs/heads/x/y/*", uFix);
    assertOwner("refs/heads/x/y/bar", uFix);
    assertNotOwner("refs/heads/x/*", uFix);
    assertNotOwner("refs/heads/x/y", uFix);
    assertNotOwner("refs/*", uFix);
    assertNotOwner("refs/heads/master", uFix);
  }

  public void testInheritRead_SingleBranchDeniesUpload() {
    grant(util.getParentConfig(), READ, REGISTERED, "refs/*");
    grant(util.getParentConfig(), PUSH, REGISTERED, "refs/for/refs/*");
    grant(local, READ, REGISTERED, "refs/heads/foobar");
    doNotInherit(local, READ, "refs/heads/foobar");
    doNotInherit(local, PUSH, "refs/for/refs/heads/foobar");

    ProjectControl u = util.user(local);
    assertTrue("can upload", u.canPushToAtLeastOneRef() == Capable.OK);

    assertTrue("can upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());

    assertFalse("deny refs/heads/foobar", //
        u.controlForRef("refs/heads/foobar").canUpload());
  }

  public void testInheritRead_SingleBranchDoesNotOverrideInherited() {
    grant(util.getParentConfig(), READ, REGISTERED, "refs/*");
    grant(util.getParentConfig(), PUSH, REGISTERED, "refs/for/refs/*");
    grant(local, READ, REGISTERED, "refs/heads/foobar");

    ProjectControl u = util.user(local);
    assertTrue("can upload", u.canPushToAtLeastOneRef() == Capable.OK);

    assertTrue("can upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());

    assertTrue("can upload refs/heads/foobar", //
        u.controlForRef("refs/heads/foobar").canUpload());
  }

  public void testInheritDuplicateSections() {
    grant(util.getParentConfig(), READ, ADMIN, "refs/*");
    grant(local, READ, DEVS, "refs/heads/*");
    local.getProject().setParentName(util.getParentConfig().getProject().getName());
    assertTrue("a can read", util.user(local, "a", ADMIN).isVisible());

    local = new ProjectConfig(new Project.NameKey("local"));
    local.createInMemory();
    grant(local, READ, DEVS, "refs/*");
    assertTrue("d can read", util.user(local, "d", DEVS).isVisible());
  }

  public void testInheritRead_OverrideWithDeny() {
    grant(util.getParentConfig(), READ, REGISTERED, "refs/*");
    grant(local, READ, REGISTERED, "refs/*").setDeny();

    ProjectControl u = util.user(local);
    assertFalse("can't read", u.isVisible());
  }

  public void testInheritRead_AppendWithDenyOfRef() {
    grant(util.getParentConfig(), READ, REGISTERED, "refs/*");
    grant(local, READ, REGISTERED, "refs/heads/*").setDeny();

    ProjectControl u = util.user(local);
    assertTrue("can read", u.isVisible());
    assertTrue("can read", u.controlForRef("refs/master").isVisible());
    assertTrue("can read", u.controlForRef("refs/tags/foobar").isVisible());
    assertTrue("no master", u.controlForRef("refs/heads/master").isVisible());
  }

  public void testInheritRead_OverridesAndDeniesOfRef() {
    grant(util.getParentConfig(), READ, REGISTERED, "refs/*");
    grant(local, READ, REGISTERED, "refs/*").setDeny();
    grant(local, READ, REGISTERED, "refs/heads/*");

    ProjectControl u = util.user(local);
    assertTrue("can read", u.isVisible());
    assertFalse("can't read", u.controlForRef("refs/foobar").isVisible());
    assertFalse("can't read", u.controlForRef("refs/tags/foobar").isVisible());
    assertTrue("can read", u.controlForRef("refs/heads/foobar").isVisible());
  }

  public void testInheritSubmit_OverridesAndDeniesOfRef() {
    grant(util.getParentConfig(), SUBMIT, REGISTERED, "refs/*");
    grant(local, SUBMIT, REGISTERED, "refs/*").setDeny();
    grant(local, SUBMIT, REGISTERED, "refs/heads/*");

    ProjectControl u = util.user(local);
    assertFalse("can't submit", u.controlForRef("refs/foobar").canSubmit());
    assertFalse("can't submit", u.controlForRef("refs/tags/foobar").canSubmit());
    assertTrue("can submit", u.controlForRef("refs/heads/foobar").canSubmit());
  }

  public void testCannotUploadToAnyRef() {
    grant(util.getParentConfig(), READ, REGISTERED, "refs/*");
    grant(local, READ, DEVS, "refs/heads/*");
    grant(local, PUSH, DEVS, "refs/for/refs/heads/*");

    ProjectControl u = util.user(local);
    assertFalse("cannot upload", u.canPushToAtLeastOneRef() == Capable.OK);
    assertFalse("cannot upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());
  }

  public void testUsernamePatternNonRegex() {
    grant(local, READ, DEVS, "refs/sb/${username}/heads/*");

    ProjectControl u = util.user(local, "u", DEVS), d = util.user(local, "d", DEVS);
    assertFalse("u can't read", u.controlForRef("refs/sb/d/heads/foobar").isVisible());
    assertTrue("d can read", d.controlForRef("refs/sb/d/heads/foobar").isVisible());
  }

  public void testUsernamePatternWithRegex() {
    grant(local, READ, DEVS, "^refs/sb/${username}/heads/.*");

    ProjectControl u = util.user(local, "d.v", DEVS), d = util.user(local, "dev", DEVS);
    assertFalse("u can't read", u.controlForRef("refs/sb/dev/heads/foobar").isVisible());
    assertTrue("d can read", d.controlForRef("refs/sb/dev/heads/foobar").isVisible());
  }

  public void testSortWithRegex() {
    grant(local, READ, DEVS, "^refs/heads/.*");
    grant(util.getParentConfig(), READ, ANONYMOUS, "^refs/heads/.*-QA-.*");

    ProjectControl u = util.user(local, DEVS), d = util.user(local, DEVS);
    assertTrue("u can read", u.controlForRef("refs/heads/foo-QA-bar").isVisible());
    assertTrue("d can read", d.controlForRef("refs/heads/foo-QA-bar").isVisible());
  }

  public void testBlockRule_ParentBlocksChild() {
    grant(local, PUSH, DEVS, "refs/tags/*");
    grant(util.getParentConfig(), PUSH, ANONYMOUS, "refs/tags/*").setBlock();

    ProjectControl u = util.user(local, DEVS);
    assertFalse("u can't force update tag", u.controlForRef("refs/tags/V10").canForceUpdate());
  }

  public void testBlockLabelRange_ParentBlocksChild() {
    grant(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");
    grant(util.getParentConfig(), LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*").setBlock();

    ProjectControl u = util.user(local, DEVS);

    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertTrue("u can vote -1", range.contains(-1));
    assertTrue("u can vote +1", range.contains(1));
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote 2", range.contains(2));
  }

  public void testUnblockNoForce() {
    grant(local, PUSH, ANONYMOUS, "refs/heads/*").setBlock();
    grant(local, PUSH, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    assertTrue("u can push", u.controlForRef("refs/heads/master").canUpdate());
  }

  public void testUnblockForce() {
    PermissionRule r = grant(local, PUSH, ANONYMOUS, "refs/heads/*");
    r.setBlock();
    r.setForce(true);
    grant(local, PUSH, DEVS, "refs/heads/*").setForce(true);

    ProjectControl u = util.user(local, DEVS);
    assertTrue("u can force push", u.controlForRef("refs/heads/master").canForceUpdate());
  }

  public void testUnblockForceWithAllowNoForce_NotPossible() {
    PermissionRule r = grant(local, PUSH, ANONYMOUS, "refs/heads/*");
    r.setBlock();
    r.setForce(true);
    grant(local, PUSH, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    assertFalse("u can't force push", u.controlForRef("refs/heads/master").canForceUpdate());
  }

  public void testUnblockMoreSpecificRef_Fails() {
    grant(local, PUSH, ANONYMOUS, "refs/heads/*").setBlock();
    grant(local, PUSH, DEVS, "refs/heads/master");

    ProjectControl u = util.user(local, DEVS);
    assertFalse("u can't push", u.controlForRef("refs/heads/master").canUpdate());
  }

  public void testUnblockLargerScope_Fails() {
    grant(local, PUSH, ANONYMOUS, "refs/heads/master").setBlock();
    grant(local, PUSH, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    assertFalse("u can't push", u.controlForRef("refs/heads/master").canUpdate());
  }

  public void testUnblockInLocal_Fails() {
    grant(util.getParentConfig(), PUSH, ANONYMOUS, "refs/heads/*").setBlock();
    grant(local, PUSH, fixers, "refs/heads/*");

    ProjectControl f = util.user(local, fixers);
    assertFalse("u can't push", f.controlForRef("refs/heads/master").canUpdate());
  }

  public void testUnblockInParentBlockInLocal() {
    grant(util.getParentConfig(), PUSH, ANONYMOUS, "refs/heads/*").setBlock();
    grant(util.getParentConfig(), PUSH, DEVS, "refs/heads/*");
    grant(local, PUSH, DEVS, "refs/heads/*").setBlock();

    ProjectControl d = util.user(local, DEVS);
    assertFalse("u can't push", d.controlForRef("refs/heads/master").canUpdate());
  }

  public void testUnblockVisibilityByREGISTEREDUsers() {
    grant(local, READ, ANONYMOUS, "refs/heads/*").setBlock();
    grant(local, READ, REGISTERED, "refs/heads/*");

    ProjectControl u = util.user(local, REGISTERED);
    assertTrue("u can read", u.controlForRef("refs/heads/master").isVisibleByRegisteredUsers());
  }

  public void testUnblockInLocalVisibilityByRegisteredUsers_Fails() {
    grant(util.getParentConfig(), READ, ANONYMOUS, "refs/heads/*").setBlock();
    grant(local, READ, REGISTERED, "refs/heads/*");

    ProjectControl u = util.user(local, REGISTERED);
    assertFalse("u can't read", u.controlForRef("refs/heads/master").isVisibleByRegisteredUsers());
  }

  public void testUnblockForceEditTopicName() {
    grant(local, EDIT_TOPIC_NAME, ANONYMOUS, "refs/heads/*").setBlock();
    grant(local, EDIT_TOPIC_NAME, DEVS, "refs/heads/*").setForce(true);

    ProjectControl u = util.user(local, DEVS);
    assertTrue("u can edit topic name", u.controlForRef("refs/heads/master")
        .canForceEditTopicName());
  }

  public void testUnblockInLocalForceEditTopicName_Fails() {
    grant(util.getParentConfig(), EDIT_TOPIC_NAME, ANONYMOUS, "refs/heads/*")
        .setBlock();
    grant(local, EDIT_TOPIC_NAME, DEVS, "refs/heads/*").setForce(true);

    ProjectControl u = util.user(local, REGISTERED);
    assertFalse("u can't edit topic name", u.controlForRef("refs/heads/master")
        .canForceEditTopicName());
  }

  public void testUnblockRange() {
    grant(local, LABEL + "Code-Review", -1, +1, ANONYMOUS, "refs/heads/*").setBlock();
    grant(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertTrue("u can vote -2", range.contains(-2));
    assertTrue("u can vote +2", range.contains(2));
  }

  public void testUnblockRangeOnMoreSpecificRef_Fails() {
    grant(local, LABEL + "Code-Review", -1, +1, ANONYMOUS, "refs/heads/*").setBlock();
    grant(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/master");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote +2", range.contains(-2));
  }

  public void testUnblockRangeOnLargerScope_Fails() {
    grant(local, LABEL + "Code-Review", -1, +1, ANONYMOUS, "refs/heads/master").setBlock();
    grant(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote +2", range.contains(-2));
  }

  public void testUnblockInLocalRange_Fails() {
    grant(util.getParentConfig(), LABEL + "Code-Review", -1, 1, ANONYMOUS,
        "refs/heads/*").setBlock();
    grant(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote 2", range.contains(2));
  }

  public void testUnblockRangeForChangeOwner() {
    grant(local, LABEL + "Code-Review", -2, +2, CHANGE_OWNER, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master")
        .getRange(LABEL + "Code-Review", true);
    assertTrue("u can vote -2", range.contains(-2));
    assertTrue("u can vote +2", range.contains(2));
  }

  public void testUnblockRangeForNotChangeOwner() {
    grant(local, LABEL + "Code-Review", -2, +2, CHANGE_OWNER, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master")
        .getRange(LABEL + "Code-Review");
    assertFalse("u can vote -2", range.contains(-2));
    assertFalse("u can vote +2", range.contains(2));
  }
}
