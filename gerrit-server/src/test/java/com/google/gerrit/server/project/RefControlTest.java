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
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.CHANGE_OWNER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.Util.ADMIN;
import static com.google.gerrit.server.project.Util.DEVS;
import static com.google.gerrit.server.project.Util.allow;
import static com.google.gerrit.server.project.Util.block;
import static com.google.gerrit.server.project.Util.deny;
import static com.google.gerrit.server.project.Util.doNotInherit;
import static com.google.gerrit.testutil.InMemoryRepositoryManager.newRepository;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.ProjectConfig;

import org.junit.Before;
import org.junit.Test;

public class RefControlTest {
  private static void assertOwner(String ref, ProjectControl u) {
    assertTrue("OWN " + ref, u.controlForRef(ref).isOwner());
  }

  private static void assertNotOwner(String ref, ProjectControl u) {
    assertFalse("NOT OWN " + ref, u.controlForRef(ref).isOwner());
  }

  private final AccountGroup.UUID fixers = new AccountGroup.UUID("test.fixers");
  private Project.NameKey localKey = new Project.NameKey("local");
  private ProjectConfig local;
  private Project.NameKey parentKey = new Project.NameKey("parent");
  private ProjectConfig parent;
  private final Util util;

  public RefControlTest() {
    util = new Util();
  }

  @Before
  public void setUp() throws Exception {
    parent = new ProjectConfig(parentKey);
    parent.load(newRepository(parentKey));
    util.add(parent);

    local = new ProjectConfig(localKey);
    local.load(newRepository(localKey));
    util.add(local);
    local.getProject().setParentName(parentKey);
  }

  @Test
  public void testOwnerProject() {
    allow(local, OWNER, ADMIN, "refs/*");

    assertAdminsAreOwnersAndDevsAreNot();
  }

  @Test
  public void testDenyOwnerProject() {
    allow(local, OWNER, ADMIN, "refs/*");
    deny(local, OWNER, DEVS, "refs/*");

    assertAdminsAreOwnersAndDevsAreNot();
  }

  @Test
  public void testBlockOwnerProject() {
    allow(local, OWNER, ADMIN, "refs/*");
    block(local, OWNER, DEVS, "refs/*");

    assertAdminsAreOwnersAndDevsAreNot();
  }

  @Test
  public void testBranchDelegation1() {
    allow(local, OWNER, ADMIN, "refs/*");
    allow(local, OWNER, DEVS, "refs/heads/x/*");

    ProjectControl uDev = util.user(local, DEVS);
    assertFalse("not owner", uDev.isOwner());
    assertTrue("owns ref", uDev.isOwnerAnyRef());

    assertOwner("refs/heads/x/*", uDev);
    assertOwner("refs/heads/x/y", uDev);
    assertOwner("refs/heads/x/y/*", uDev);

    assertNotOwner("refs/*", uDev);
    assertNotOwner("refs/heads/master", uDev);
  }

  @Test
  public void testBranchDelegation2() {
    allow(local, OWNER, ADMIN, "refs/*");
    allow(local, OWNER, DEVS, "refs/heads/x/*");
    allow(local, OWNER, fixers, "refs/heads/x/y/*");
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

  @Test
  public void testInheritRead_SingleBranchDeniesUpload() {
    allow(parent, READ, REGISTERED_USERS, "refs/*");
    allow(parent, PUSH, REGISTERED_USERS, "refs/for/refs/*");
    allow(local, READ, REGISTERED_USERS, "refs/heads/foobar");
    doNotInherit(local, READ, "refs/heads/foobar");
    doNotInherit(local, PUSH, "refs/for/refs/heads/foobar");

    ProjectControl u = util.user(local);
    assertTrue("can upload", u.canPushToAtLeastOneRef() == Capable.OK);

    assertTrue("can upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());

    assertFalse("deny refs/heads/foobar", //
        u.controlForRef("refs/heads/foobar").canUpload());
  }

  @Test
  public void testBlockPushDrafts() {
    allow(parent, PUSH, REGISTERED_USERS, "refs/for/refs/*");
    block(parent, PUSH, ANONYMOUS_USERS, "refs/drafts/*");

    ProjectControl u = util.user(local);
    assertTrue("can upload refs/heads/master",
        u.controlForRef("refs/heads/master").canUpload());
    assertTrue("push is blocked to refs/drafts/master",
        u.controlForRef("refs/drafts/refs/heads/master").isBlocked(PUSH));
  }

  @Test
  public void testBlockPushDraftsUnblockAdmin() {
    block(parent, PUSH, ANONYMOUS_USERS, "refs/drafts/*");
    allow(parent, PUSH, ADMIN, "refs/drafts/*");

    assertTrue("push is blocked for anonymous to refs/drafts/master",
        util.user(local).controlForRef("refs/drafts/refs/heads/master")
            .isBlocked(PUSH));
    assertFalse("push is blocked for admin refs/drafts/master",
        util.user(local, "a", ADMIN).controlForRef("refs/drafts/refs/heads/master")
            .isBlocked(PUSH));
  }

  @Test
  public void testInheritRead_SingleBranchDoesNotOverrideInherited() {
    allow(parent, READ, REGISTERED_USERS, "refs/*");
    allow(parent, PUSH, REGISTERED_USERS, "refs/for/refs/*");
    allow(local, READ, REGISTERED_USERS, "refs/heads/foobar");

    ProjectControl u = util.user(local);
    assertTrue("can upload", u.canPushToAtLeastOneRef() == Capable.OK);

    assertTrue("can upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());

    assertTrue("can upload refs/heads/foobar", //
        u.controlForRef("refs/heads/foobar").canUpload());
  }

  @Test
  public void testInheritDuplicateSections() throws Exception {
    allow(parent, READ, ADMIN, "refs/*");
    allow(local, READ, DEVS, "refs/heads/*");
    assertTrue("a can read", util.user(local, "a", ADMIN).isVisible());

    local = new ProjectConfig(localKey);
    local.load(newRepository(localKey));
    local.getProject().setParentName(parentKey);
    allow(local, READ, DEVS, "refs/*");
    assertTrue("d can read", util.user(local, "d", DEVS).isVisible());
  }

  @Test
  public void testInheritRead_OverrideWithDeny() {
    allow(parent, READ, REGISTERED_USERS, "refs/*");
    deny(local, READ, REGISTERED_USERS, "refs/*");

    ProjectControl u = util.user(local);
    assertFalse("can't read", u.isVisible());
  }

  @Test
  public void testInheritRead_AppendWithDenyOfRef() {
    allow(parent, READ, REGISTERED_USERS, "refs/*");
    deny(local, READ, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local);
    assertTrue("can read", u.isVisible());
    assertTrue("can read", u.controlForRef("refs/master").isVisible());
    assertTrue("can read", u.controlForRef("refs/tags/foobar").isVisible());
    assertTrue("no master", u.controlForRef("refs/heads/master").isVisible());
  }

  @Test
  public void testInheritRead_OverridesAndDeniesOfRef() {
    allow(parent, READ, REGISTERED_USERS, "refs/*");
    deny(local, READ, REGISTERED_USERS, "refs/*");
    allow(local, READ, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local);
    assertTrue("can read", u.isVisible());
    assertFalse("can't read", u.controlForRef("refs/foobar").isVisible());
    assertFalse("can't read", u.controlForRef("refs/tags/foobar").isVisible());
    assertTrue("can read", u.controlForRef("refs/heads/foobar").isVisible());
  }

  @Test
  public void testInheritSubmit_OverridesAndDeniesOfRef() {
    allow(parent, SUBMIT, REGISTERED_USERS, "refs/*");
    deny(local, SUBMIT, REGISTERED_USERS, "refs/*");
    allow(local, SUBMIT, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local);
    assertFalse("can't submit", u.controlForRef("refs/foobar").canSubmit());
    assertFalse("can't submit", u.controlForRef("refs/tags/foobar").canSubmit());
    assertTrue("can submit", u.controlForRef("refs/heads/foobar").canSubmit());
  }

  @Test
  public void testCannotUploadToAnyRef() {
    allow(parent, READ, REGISTERED_USERS, "refs/*");
    allow(local, READ, DEVS, "refs/heads/*");
    allow(local, PUSH, DEVS, "refs/for/refs/heads/*");

    ProjectControl u = util.user(local);
    assertFalse("cannot upload", u.canPushToAtLeastOneRef() == Capable.OK);
    assertFalse("cannot upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());
  }

  @Test
  public void testUsernamePatternCanUploadToAnyRef() {
    allow(local, PUSH, REGISTERED_USERS, "refs/heads/users/${username}/*");
    ProjectControl u = util.user(local, "a-registered-user");
    assertTrue("can upload", u.canPushToAtLeastOneRef() == Capable.OK);
  }

  @Test
  public void testUsernamePatternNonRegex() {
    allow(local, READ, DEVS, "refs/sb/${username}/heads/*");

    ProjectControl u = util.user(local, "u", DEVS);
    ProjectControl d = util.user(local, "d", DEVS);
    assertFalse("u can't read", u.controlForRef("refs/sb/d/heads/foobar").isVisible());
    assertTrue("d can read", d.controlForRef("refs/sb/d/heads/foobar").isVisible());
  }

  @Test
  public void testUsernamePatternWithRegex() {
    allow(local, READ, DEVS, "^refs/sb/${username}/heads/.*");

    ProjectControl u = util.user(local, "d.v", DEVS);
    ProjectControl d = util.user(local, "dev", DEVS);
    assertFalse("u can't read", u.controlForRef("refs/sb/dev/heads/foobar").isVisible());
    assertTrue("d can read", d.controlForRef("refs/sb/dev/heads/foobar").isVisible());
  }

  @Test
  public void testUsernameEmailPatternWithRegex() {
    allow(local, READ, DEVS, "^refs/sb/${username}/heads/.*");

    ProjectControl u = util.user(local, "d.v@ger-rit.org", DEVS);
    ProjectControl d = util.user(local, "dev@ger-rit.org", DEVS);
    assertFalse("u can't read",
        u.controlForRef("refs/sb/dev@ger-rit.org/heads/foobar").isVisible());
    assertTrue("d can read",
        d.controlForRef("refs/sb/dev@ger-rit.org/heads/foobar").isVisible());
  }

  @Test
  public void testSortWithRegex() {
    allow(local, READ, DEVS, "^refs/heads/.*");
    allow(parent, READ, ANONYMOUS_USERS, "^refs/heads/.*-QA-.*");

    ProjectControl u = util.user(local, DEVS);
    ProjectControl d = util.user(local, DEVS);
    assertTrue("u can read", u.controlForRef("refs/heads/foo-QA-bar").isVisible());
    assertTrue("d can read", d.controlForRef("refs/heads/foo-QA-bar").isVisible());
  }

  @Test
  public void testBlockRule_ParentBlocksChild() {
    allow(local, PUSH, DEVS, "refs/tags/*");
    block(parent, PUSH, ANONYMOUS_USERS, "refs/tags/*");
    ProjectControl u = util.user(local, DEVS);
    assertFalse("u can't update tag", u.controlForRef("refs/tags/V10").canUpdate());
  }

  @Test
  public void testBlockRule_ParentBlocksChildEvenIfAlreadyBlockedInChild() {
    allow(local, PUSH, DEVS, "refs/tags/*");
    block(local, PUSH, ANONYMOUS_USERS, "refs/tags/*");
    block(parent, PUSH, ANONYMOUS_USERS, "refs/tags/*");

    ProjectControl u = util.user(local, DEVS);
    assertFalse("u can't update tag", u.controlForRef("refs/tags/V10").canUpdate());
  }

  @Test
  public void testBlockLabelRange_ParentBlocksChild() {
    allow(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");
    block(parent, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);

    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertTrue("u can vote -1", range.contains(-1));
    assertTrue("u can vote +1", range.contains(1));
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote 2", range.contains(2));
  }

  @Test
  public void testBlockLabelRange_ParentBlocksChildEvenIfAlreadyBlockedInChild() {
    allow(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");
    block(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");
    block(parent, LABEL + "Code-Review", -2, +2, DEVS,
        "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);

    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertTrue("u can vote -1", range.contains(-1));
    assertTrue("u can vote +1", range.contains(1));
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote 2", range.contains(2));
  }

  @Test
  public void testInheritSubmit_AllowInChildDoesntAffectUnblockInParent() {
    block(parent, SUBMIT, ANONYMOUS_USERS, "refs/heads/*");
    allow(parent, SUBMIT, REGISTERED_USERS, "refs/heads/*");
    allow(local, SUBMIT, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local);
    assertFalse("not blocked from submitting", u.controlForRef(
        "refs/heads/master").isBlocked(SUBMIT));
  }

  @Test
  public void testUnblockNoForce() {
    block(local, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, PUSH, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    assertTrue("u can push", u.controlForRef("refs/heads/master").canUpdate());
  }

  @Test
  public void testUnblockForce() {
    PermissionRule r = block(local, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    r.setForce(true);
    allow(local, PUSH, DEVS, "refs/heads/*").setForce(true);

    ProjectControl u = util.user(local, DEVS);
    assertTrue("u can force push", u.controlForRef("refs/heads/master").canForceUpdate());
  }

  @Test
  public void testUnblockForceWithAllowNoForce_NotPossible() {
    PermissionRule r = block(local, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    r.setForce(true);
    allow(local, PUSH, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    assertFalse("u can't force push", u.controlForRef("refs/heads/master").canForceUpdate());
  }

  @Test
  public void testUnblockMoreSpecificRef_Fails() {
    block(local, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, PUSH, DEVS, "refs/heads/master");

    ProjectControl u = util.user(local, DEVS);
    assertFalse("u can't push", u.controlForRef("refs/heads/master").canUpdate());
  }

  @Test
  public void testUnblockLargerScope_Fails() {
    block(local, PUSH, ANONYMOUS_USERS, "refs/heads/master");
    allow(local, PUSH, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    assertFalse("u can't push", u.controlForRef("refs/heads/master").canUpdate());
  }

  @Test
  public void testUnblockInLocal_Fails() {
    block(parent, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, PUSH, fixers, "refs/heads/*");

    ProjectControl f = util.user(local, fixers);
    assertFalse("u can't push", f.controlForRef("refs/heads/master").canUpdate());
  }

  @Test
  public void testUnblockInParentBlockInLocal() {
    block(parent, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    allow(parent, PUSH, DEVS, "refs/heads/*");
    block(local, PUSH, DEVS, "refs/heads/*");

    ProjectControl d = util.user(local, DEVS);
    assertFalse("u can't push", d.controlForRef("refs/heads/master").canUpdate());
  }

  @Test
  public void testUnblockVisibilityByRegisteredUsers() {
    block(local, READ, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, READ, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local, REGISTERED_USERS);
    assertTrue("u can read", u.controlForRef("refs/heads/master").isVisibleByRegisteredUsers());
  }

  @Test
  public void testUnblockInLocalVisibilityByRegisteredUsers_Fails() {
    block(parent, READ, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, READ, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local, REGISTERED_USERS);
    assertFalse("u can't read", u.controlForRef("refs/heads/master").isVisibleByRegisteredUsers());
  }

  @Test
  public void testUnblockForceEditTopicName() {
    block(local, EDIT_TOPIC_NAME, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, EDIT_TOPIC_NAME, DEVS, "refs/heads/*").setForce(true);

    ProjectControl u = util.user(local, DEVS);
    assertTrue("u can edit topic name", u.controlForRef("refs/heads/master")
        .canForceEditTopicName());
  }

  @Test
  public void testUnblockInLocalForceEditTopicName_Fails() {
    block(parent, EDIT_TOPIC_NAME, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, EDIT_TOPIC_NAME, DEVS, "refs/heads/*").setForce(true);

    ProjectControl u = util.user(local, REGISTERED_USERS);
    assertFalse("u can't edit topic name", u.controlForRef("refs/heads/master")
        .canForceEditTopicName());
  }

  @Test
  public void testUnblockRange() {
    block(local, LABEL + "Code-Review", -1, +1, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertTrue("u can vote -2", range.contains(-2));
    assertTrue("u can vote +2", range.contains(2));
  }

  @Test
  public void testUnblockRangeOnMoreSpecificRef_Fails() {
    block(local, LABEL + "Code-Review", -1, +1, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/master");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote +2", range.contains(-2));
  }

  @Test
  public void testUnblockRangeOnLargerScope_Fails() {
    block(local, LABEL + "Code-Review", -1, +1, ANONYMOUS_USERS, "refs/heads/master");
    allow(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote +2", range.contains(-2));
  }

  @Test
  public void testUnblockInLocalRange_Fails() {
    block(parent, LABEL + "Code-Review", -1, 1, ANONYMOUS_USERS,
        "refs/heads/*");
    allow(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote 2", range.contains(2));
  }

  public void testUnblockRangeForChangeOwner() {
    allow(local, LABEL + "Code-Review", -2, +2, CHANGE_OWNER, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master")
        .getRange(LABEL + "Code-Review", true);
    assertTrue("u can vote -2", range.contains(-2));
    assertTrue("u can vote +2", range.contains(2));
  }

  public void testUnblockRangeForNotChangeOwner() {
    allow(local, LABEL + "Code-Review", -2, +2, CHANGE_OWNER, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master")
        .getRange(LABEL + "Code-Review");
    assertFalse("u can vote -2", range.contains(-2));
    assertFalse("u can vote +2", range.contains(2));
  }

  private void assertAdminsAreOwnersAndDevsAreNot() {
    ProjectControl uBlah = util.user(local, DEVS);
    ProjectControl uAdmin = util.user(local, DEVS, ADMIN);

    assertFalse("not owner", uBlah.isOwner());
    assertTrue("is owner", uAdmin.isOwner());
  }
}
