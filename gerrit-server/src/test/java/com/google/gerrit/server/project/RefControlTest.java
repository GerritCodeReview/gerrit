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

import static com.google.common.truth.Truth.assertThat;
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

import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.ProjectConfig;

import org.junit.Before;
import org.junit.Test;

public class RefControlTest {
  private void assertAdminsAreOwnersAndDevsAreNot() {
    ProjectControl uBlah = util.user(local, DEVS);
    ProjectControl uAdmin = util.user(local, DEVS, ADMIN);

    assertThat(uBlah.isOwner()).named("not owner").isFalse();
    assertThat(uAdmin.isOwner()).named("is owner").isTrue();
  }

  private void assertOwner(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).isOwner())
      .named("OWN " + ref)
      .isTrue();
  }

  private void assertNotOwner(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).isOwner())
      .named("NOT OWN " + ref)
      .isFalse();
  }

  private void assertCanRead(ProjectControl u) {
    assertThat(u.isVisible())
      .named("can read")
      .isTrue();
  }

  private void assertCannotRead(ProjectControl u) {
    assertThat(u.isVisible())
      .named("cannot read")
      .isFalse();
  }

  private void assertCanRead(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).isVisible())
      .named("can read " + ref)
      .isTrue();
  }

  private void assertCannotRead(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).isVisible())
      .named("cannot read " + ref)
      .isFalse();
  }

  private void assertCanSubmit(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).canSubmit())
      .named("can submit " + ref)
      .isTrue();
  }

  private void assertCannotSubmit(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).canSubmit())
      .named("can submit " + ref)
      .isFalse();
  }

  private void assertCanUpload(ProjectControl u) {
    assertThat(u.canPushToAtLeastOneRef())
      .named("can upload")
      .isEqualTo(Capable.OK);
  }

  private void assertCanUpload(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).canUpload())
      .named("can upload " + ref)
      .isTrue();
  }

  private void assertCannotUpload(ProjectControl u) {
    assertThat(u.canPushToAtLeastOneRef())
      .named("cannot upload")
      .isNotEqualTo(Capable.OK);
  }

  private void assertCannotUpload(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).canUpload())
      .named("cannot upload " + ref)
      .isFalse();
  }

  private void assertBlocked(String p, String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).isBlocked(p))
      .named(p + " is blocked for " + ref)
      .isTrue();
  }

  private void assertNotBlocked(String p, String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).isBlocked(p))
      .named(p + " is blocked for " + ref)
      .isFalse();
  }

  private void assertCanUpdate(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).canUpdate())
      .named("can update " + ref)
      .isTrue();
  }

  private void assertCannotUpdate(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).canUpdate())
      .named("cannot update " + ref)
      .isFalse();
  }

  private void assertCanForceUpdate(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).canForceUpdate())
      .named("can force push " + ref)
      .isTrue();
  }

  private void assertCannotForceUpdate(String ref, ProjectControl u) {
    assertThat(u.controlForRef(ref).canForceUpdate())
      .named("cannot force push " + ref)
      .isFalse();
  }

  private void assertCanVote(int score, PermissionRange range) {
    assertThat(range.contains(score))
      .named("can vote " + score)
      .isTrue();
  }

  private void assertCannotVote(int score, PermissionRange range) {
    assertThat(range.contains(score))
      .named("cannot vote " + score)
      .isFalse();
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
    assertThat(uDev.isOwner()).named("not owner").isFalse();
    assertThat(uDev.isOwnerAnyRef()).named("owns ref").isTrue();

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
    assertThat(uDev.isOwner()).named("not owner").isFalse();
    assertThat(uDev.isOwnerAnyRef()).named("owns ref").isTrue();

    assertOwner("refs/heads/x/*", uDev);
    assertOwner("refs/heads/x/y", uDev);
    assertOwner("refs/heads/x/y/*", uDev);
    assertNotOwner("refs/*", uDev);
    assertNotOwner("refs/heads/master", uDev);

    ProjectControl uFix = util.user(local, fixers);
    assertThat(uFix.isOwner()).isFalse();
    assertThat(uFix.isOwnerAnyRef()).isTrue();

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
    assertCanUpload(u);
    assertCanUpload("refs/heads/master", u);
    assertCannotUpload("refs/heads/foobar", u);
  }

  @Test
  public void testBlockPushDrafts() {
    allow(parent, PUSH, REGISTERED_USERS, "refs/for/refs/*");
    block(parent, PUSH, ANONYMOUS_USERS, "refs/drafts/*");

    ProjectControl u = util.user(local);
    assertCanUpload("refs/heads/master", u);
    assertBlocked(PUSH, "refs/drafts/refs/heads/master", u);
    assertBlocked(PUSH, "refs/drafts/master", u);
  }

  @Test
  public void testBlockPushDraftsUnblockAdmin() {
    block(parent, PUSH, ANONYMOUS_USERS, "refs/drafts/*");
    allow(parent, PUSH, ADMIN, "refs/drafts/*");

    ProjectControl u = util.user(local);
    ProjectControl a = util.user(local, "a", ADMIN);
    assertBlocked(PUSH, "refs/drafts/refs/heads/master", u);
    assertNotBlocked(PUSH, "refs/drafts/refs/heads/master", a);
  }

  @Test
  public void testInheritRead_SingleBranchDoesNotOverrideInherited() {
    allow(parent, READ, REGISTERED_USERS, "refs/*");
    allow(parent, PUSH, REGISTERED_USERS, "refs/for/refs/*");
    allow(local, READ, REGISTERED_USERS, "refs/heads/foobar");

    ProjectControl u = util.user(local);
    assertCanUpload(u);
    assertCanUpload("refs/heads/master", u);
    assertCanUpload("refs/heads/foobar", u);
  }

  @Test
  public void testInheritDuplicateSections() throws Exception {
    allow(parent, READ, ADMIN, "refs/*");
    allow(local, READ, DEVS, "refs/heads/*");
    assertCanRead(util.user(local, "a", ADMIN));

    local = new ProjectConfig(localKey);
    local.load(newRepository(localKey));
    local.getProject().setParentName(parentKey);
    allow(local, READ, DEVS, "refs/*");
    assertCanRead(util.user(local, "d", DEVS));
  }

  @Test
  public void testInheritRead_OverrideWithDeny() {
    allow(parent, READ, REGISTERED_USERS, "refs/*");
    deny(local, READ, REGISTERED_USERS, "refs/*");

    assertCannotRead(util.user(local));
  }

  @Test
  public void testInheritRead_AppendWithDenyOfRef() {
    allow(parent, READ, REGISTERED_USERS, "refs/*");
    deny(local, READ, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local);
    assertCanRead(u);
    assertCanRead("refs/master", u);
    assertCanRead("refs/tags/foobar", u);
    assertCanRead("refs/heads/master", u);
  }

  @Test
  public void testInheritRead_OverridesAndDeniesOfRef() {
    allow(parent, READ, REGISTERED_USERS, "refs/*");
    deny(local, READ, REGISTERED_USERS, "refs/*");
    allow(local, READ, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local);
    assertCanRead(u);
    assertCannotRead("refs/foobar", u);
    assertCannotRead("refs/tags/foobar", u);
    assertCanRead("refs/heads/foobar", u);
  }

  @Test
  public void testInheritSubmit_OverridesAndDeniesOfRef() {
    allow(parent, SUBMIT, REGISTERED_USERS, "refs/*");
    deny(local, SUBMIT, REGISTERED_USERS, "refs/*");
    allow(local, SUBMIT, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local);
    assertCannotSubmit("refs/foobar", u);
    assertCannotSubmit("refs/tags/foobar", u);
    assertCanSubmit("refs/heads/foobar", u);
  }

  @Test
  public void testCannotUploadToAnyRef() {
    allow(parent, READ, REGISTERED_USERS, "refs/*");
    allow(local, READ, DEVS, "refs/heads/*");
    allow(local, PUSH, DEVS, "refs/for/refs/heads/*");

    ProjectControl u = util.user(local);
    assertCannotUpload(u);
    assertCannotUpload("refs/heads/master", u);
  }

  @Test
  public void testUsernamePatternCanUploadToAnyRef() {
    allow(local, PUSH, REGISTERED_USERS, "refs/heads/users/${username}/*");
    ProjectControl u = util.user(local, "a-registered-user");
    assertCanUpload(u);
  }

  @Test
  public void testUsernamePatternNonRegex() {
    allow(local, READ, DEVS, "refs/sb/${username}/heads/*");

    ProjectControl u = util.user(local, "u", DEVS);
    ProjectControl d = util.user(local, "d", DEVS);
    assertCannotRead("refs/sb/d/heads/foobar", u);
    assertCanRead("refs/sb/d/heads/foobar", d);
  }

  @Test
  public void testUsernamePatternWithRegex() {
    allow(local, READ, DEVS, "^refs/sb/${username}/heads/.*");

    ProjectControl u = util.user(local, "d.v", DEVS);
    ProjectControl d = util.user(local, "dev", DEVS);
    assertCannotRead("refs/sb/dev/heads/foobar", u);
    assertCanRead("refs/sb/dev/heads/foobar", d);
  }

  @Test
  public void testUsernameEmailPatternWithRegex() {
    allow(local, READ, DEVS, "^refs/sb/${username}/heads/.*");

    ProjectControl u = util.user(local, "d.v@ger-rit.org", DEVS);
    ProjectControl d = util.user(local, "dev@ger-rit.org", DEVS);
    assertCannotRead("refs/sb/dev@ger-rit.org/heads/foobar", u);
    assertCanRead("refs/sb/dev@ger-rit.org/heads/foobar", d);
  }

  @Test
  public void testSortWithRegex() {
    allow(local, READ, DEVS, "^refs/heads/.*");
    allow(parent, READ, ANONYMOUS_USERS, "^refs/heads/.*-QA-.*");

    ProjectControl u = util.user(local, DEVS);
    ProjectControl d = util.user(local, DEVS);
    assertCanRead("refs/heads/foo-QA-bar", u);
    assertCanRead("refs/heads/foo-QA-bar", d);
  }

  @Test
  public void testBlockRule_ParentBlocksChild() {
    allow(local, PUSH, DEVS, "refs/tags/*");
    block(parent, PUSH, ANONYMOUS_USERS, "refs/tags/*");
    ProjectControl u = util.user(local, DEVS);
    assertCannotUpdate("refs/tags/V10", u);
  }

  @Test
  public void testBlockRule_ParentBlocksChildEvenIfAlreadyBlockedInChild() {
    allow(local, PUSH, DEVS, "refs/tags/*");
    block(local, PUSH, ANONYMOUS_USERS, "refs/tags/*");
    block(parent, PUSH, ANONYMOUS_USERS, "refs/tags/*");

    ProjectControl u = util.user(local, DEVS);
    assertCannotUpdate("refs/tags/V10", u);
  }

  @Test
  public void testBlockLabelRange_ParentBlocksChild() {
    allow(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");
    block(parent, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);

    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-1, range);
    assertCanVote(1, range);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
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
    assertCanVote(-1, range);
    assertCanVote(1, range);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void testInheritSubmit_AllowInChildDoesntAffectUnblockInParent() {
    block(parent, SUBMIT, ANONYMOUS_USERS, "refs/heads/*");
    allow(parent, SUBMIT, REGISTERED_USERS, "refs/heads/*");
    allow(local, SUBMIT, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local);
    assertNotBlocked(SUBMIT, "refs/heads/master", u);
  }

  @Test
  public void testUnblockNoForce() {
    block(local, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, PUSH, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    assertCanUpdate("refs/heads/master", u);
  }

  @Test
  public void testUnblockForce() {
    PermissionRule r = block(local, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    r.setForce(true);
    allow(local, PUSH, DEVS, "refs/heads/*").setForce(true);

    ProjectControl u = util.user(local, DEVS);
    assertCanForceUpdate("refs/heads/master", u);
  }

  @Test
  public void testUnblockForceWithAllowNoForce_NotPossible() {
    PermissionRule r = block(local, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    r.setForce(true);
    allow(local, PUSH, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    assertCannotForceUpdate("refs/heads/master", u);
  }

  @Test
  public void testUnblockMoreSpecificRef_Fails() {
    block(local, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, PUSH, DEVS, "refs/heads/master");

    ProjectControl u = util.user(local, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void testUnblockLargerScope_Fails() {
    block(local, PUSH, ANONYMOUS_USERS, "refs/heads/master");
    allow(local, PUSH, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void testUnblockInLocal_Fails() {
    block(parent, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, PUSH, fixers, "refs/heads/*");

    ProjectControl f = util.user(local, fixers);
    assertCannotUpdate("refs/heads/master", f);
  }

  @Test
  public void testUnblockInParentBlockInLocal() {
    block(parent, PUSH, ANONYMOUS_USERS, "refs/heads/*");
    allow(parent, PUSH, DEVS, "refs/heads/*");
    block(local, PUSH, DEVS, "refs/heads/*");

    ProjectControl d = util.user(local, DEVS);
    assertCannotUpdate("refs/heads/master", d);
  }

  @Test
  public void testUnblockVisibilityByRegisteredUsers() {
    block(local, READ, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, READ, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local, REGISTERED_USERS);
    assertThat(u.controlForRef("refs/heads/master").isVisibleByRegisteredUsers())
      .named("u can read")
      .isTrue();
  }

  @Test
  public void testUnblockInLocalVisibilityByRegisteredUsers_Fails() {
    block(parent, READ, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, READ, REGISTERED_USERS, "refs/heads/*");

    ProjectControl u = util.user(local, REGISTERED_USERS);
    assertThat(u.controlForRef("refs/heads/master").isVisibleByRegisteredUsers())
      .named("u can't read")
      .isFalse();
  }

  @Test
  public void testUnblockForceEditTopicName() {
    block(local, EDIT_TOPIC_NAME, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, EDIT_TOPIC_NAME, DEVS, "refs/heads/*").setForce(true);

    ProjectControl u = util.user(local, DEVS);
    assertThat(u.controlForRef("refs/heads/master").canForceEditTopicName())
      .named("u can edit topic name")
      .isTrue();
  }

  @Test
  public void testUnblockInLocalForceEditTopicName_Fails() {
    block(parent, EDIT_TOPIC_NAME, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, EDIT_TOPIC_NAME, DEVS, "refs/heads/*").setForce(true);

    ProjectControl u = util.user(local, REGISTERED_USERS);
    assertThat(u.controlForRef("refs/heads/master").canForceEditTopicName())
      .named("u can't edit topic name")
      .isFalse();
  }

  @Test
  public void testUnblockRange() {
    block(local, LABEL + "Code-Review", -1, +1, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void testUnblockRangeOnMoreSpecificRef_Fails() {
    block(local, LABEL + "Code-Review", -1, +1, ANONYMOUS_USERS, "refs/heads/*");
    allow(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/master");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void testUnblockRangeOnLargerScope_Fails() {
    block(local, LABEL + "Code-Review", -1, +1, ANONYMOUS_USERS, "refs/heads/master");
    allow(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void testUnblockInLocalRange_Fails() {
    block(parent, LABEL + "Code-Review", -1, 1, ANONYMOUS_USERS,
        "refs/heads/*");
    allow(local, LABEL + "Code-Review", -2, +2, DEVS, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void testUnblockRangeForChangeOwner() {
    allow(local, LABEL + "Code-Review", -2, +2, CHANGE_OWNER, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master")
        .getRange(LABEL + "Code-Review", true);
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void testUnblockRangeForNotChangeOwner() {
    allow(local, LABEL + "Code-Review", -2, +2, CHANGE_OWNER, "refs/heads/*");

    ProjectControl u = util.user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master")
        .getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }
}
