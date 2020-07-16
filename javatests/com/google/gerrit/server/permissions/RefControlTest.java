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

package com.google.gerrit.server.permissions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.blockLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.labelPermissionKey;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.entities.Permission.EDIT_TOPIC_NAME;
import static com.google.gerrit.entities.Permission.LABEL;
import static com.google.gerrit.entities.Permission.OWNER;
import static com.google.gerrit.entities.Permission.PUSH;
import static com.google.gerrit.entities.Permission.READ;
import static com.google.gerrit.entities.Permission.SUBMIT;
import static com.google.gerrit.entities.RefNames.REFS_CONFIG;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.CHANGE_OWNER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PermissionRange;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.InvalidNameException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.SingleVersionModule.SingleVersionListener;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefPattern;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testing.InMemoryModule;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RefControlTest {
  private static final AccountGroup.UUID ADMIN = AccountGroup.uuid("test.admin");
  private static final AccountGroup.UUID DEVS = AccountGroup.uuid("test.devs");

  private void assertAdminsAreOwnersAndDevsAreNot() throws Exception {
    ProjectControl uBlah = user(localKey, DEVS);
    ProjectControl uAdmin = user(localKey, DEVS, ADMIN);

    assertWithMessage("not owner").that(uBlah.isOwner()).isFalse();
    assertWithMessage("is owner").that(uAdmin.isOwner()).isTrue();
  }

  private void assertOwner(String ref, ProjectControl u) {
    assertWithMessage("OWN " + ref).that(u.controlForRef(ref).isOwner()).isTrue();
  }

  private void assertNotOwner(ProjectControl u) {
    assertWithMessage("not owner").that(u.isOwner()).isFalse();
  }

  private void assertNotOwner(String ref, ProjectControl u) {
    assertWithMessage("NOT OWN " + ref).that(u.controlForRef(ref).isOwner()).isFalse();
  }

  private void assertCanAccess(ProjectControl u) {
    boolean access = u.asForProject().testOrFalse(ProjectPermission.ACCESS);
    assertWithMessage("can access").that(access).isTrue();
  }

  private void assertAccessDenied(ProjectControl u) {
    boolean access = u.asForProject().testOrFalse(ProjectPermission.ACCESS);
    assertWithMessage("cannot access").that(access).isFalse();
  }

  private void assertCanRead(String ref, ProjectControl u) {
    assertWithMessage("can read " + ref).that(u.controlForRef(ref).isVisible()).isTrue();
  }

  private void assertCannotRead(String ref, ProjectControl u) {
    assertWithMessage("cannot read " + ref).that(u.controlForRef(ref).isVisible()).isFalse();
  }

  private void assertCanSubmit(String ref, ProjectControl u) {
    assertWithMessage("can submit " + ref).that(u.controlForRef(ref).canSubmit(false)).isTrue();
  }

  private void assertCannotSubmit(String ref, ProjectControl u) {
    assertWithMessage("can submit " + ref).that(u.controlForRef(ref).canSubmit(false)).isFalse();
  }

  private void assertCanUpload(ProjectControl u) {
    assertWithMessage("can upload").that(u.canPushToAtLeastOneRef()).isTrue();
  }

  private void assertCreateChange(String ref, ProjectControl u) {
    boolean create = u.asForProject().ref(ref).testOrFalse(RefPermission.CREATE_CHANGE);
    assertWithMessage("can create change " + ref).that(create).isTrue();
  }

  private void assertCannotUpload(ProjectControl u) {
    assertWithMessage("cannot upload").that(u.canPushToAtLeastOneRef()).isFalse();
  }

  private void assertCannotCreateChange(String ref, ProjectControl u) {
    boolean create = u.asForProject().ref(ref).testOrFalse(RefPermission.CREATE_CHANGE);
    assertWithMessage("cannot create change " + ref).that(create).isFalse();
  }

  private void assertCanUpdate(String ref, ProjectControl u) {
    boolean update = u.asForProject().ref(ref).testOrFalse(RefPermission.UPDATE);
    assertWithMessage("can update " + ref).that(update).isTrue();
  }

  private void assertCannotUpdate(String ref, ProjectControl u) {
    boolean update = u.asForProject().ref(ref).testOrFalse(RefPermission.UPDATE);
    assertWithMessage("cannot update " + ref).that(update).isFalse();
  }

  private void assertCanForceUpdate(String ref, ProjectControl u) {
    boolean update = u.asForProject().ref(ref).testOrFalse(RefPermission.FORCE_UPDATE);
    assertWithMessage("can force push " + ref).that(update).isTrue();
  }

  private void assertCannotForceUpdate(String ref, ProjectControl u) {
    boolean update = u.asForProject().ref(ref).testOrFalse(RefPermission.FORCE_UPDATE);
    assertWithMessage("cannot force push " + ref).that(update).isFalse();
  }

  private void assertCanVote(int score, PermissionRange range) {
    assertWithMessage("can vote " + score).that(range.contains(score)).isTrue();
  }

  private void assertCannotVote(int score, PermissionRange range) {
    assertWithMessage("cannot vote " + score).that(range.contains(score)).isFalse();
  }

  private final AccountGroup.UUID fixers = AccountGroup.uuid("test.fixers");
  private final Project.NameKey localKey = Project.nameKey("local");
  private final Project.NameKey parentKey = Project.nameKey("parent");

  @Inject private AllProjectsName allProjectsName;
  @Inject private InMemoryRepositoryManager repoManager;
  @Inject private MetaDataUpdate.Server metaDataUpdateFactory;
  @Inject private ProjectCache projectCache;
  @Inject private ProjectControl.Factory projectControlFactory;
  @Inject private ProjectOperations projectOperations;
  @Inject private SchemaCreator schemaCreator;
  @Inject private SingleVersionListener singleVersionListener;
  @Inject private ThreadLocalRequestContext requestContext;

  @Before
  public void setUp() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);

    // Tests previously used ProjectConfig.Factory to create ProjectConfigs without going through
    // the ProjectCache, which was wrong. Manually call getInstance so we don't store it in a
    // field that is accessible to test methods.
    ProjectConfig.Factory projectConfigFactory = injector.getInstance(ProjectConfig.Factory.class);

    singleVersionListener.start();
    try {
      schemaCreator.create();
    } finally {
      singleVersionListener.stop();
    }

    // Clear out All-Projects and use the lowest-level API possible for project creation, so the
    // only ACL entries are exactly what is initialized by this test, and we aren't subject to
    // changing defaults in SchemaCreator or ProjectCreator.
    try (Repository allProjectsRepo = repoManager.createRepository(allProjectsName);
        TestRepository<Repository> tr = new TestRepository<>(allProjectsRepo)) {
      tr.delete(REFS_CONFIG);
      try (MetaDataUpdate md = metaDataUpdateFactory.create(allProjectsName)) {
        ProjectConfig allProjectsConfig = projectConfigFactory.create(allProjectsName);
        allProjectsConfig.load(md);
        LabelType cr = TestLabels.codeReview();
        allProjectsConfig.upsertLabelType(cr);
        allProjectsConfig.commit(md);
      }
    }

    repoManager.createRepository(parentKey).close();
    repoManager.createRepository(localKey).close();
    try (MetaDataUpdate md = metaDataUpdateFactory.create(localKey)) {
      ProjectConfig newLocal = projectConfigFactory.create(localKey);
      newLocal.load(md);
      newLocal.updateProject(p -> p.setParent(parentKey));
      newLocal.commit(md);
    }

    requestContext.setContext(() -> null);
  }

  @After
  public void tearDown() throws Exception {
    requestContext.setContext(null);
  }

  @Test
  public void ownerProject() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(ADMIN))
        .update();
    assertAdminsAreOwnersAndDevsAreNot();
  }

  @Test
  public void denyOwnerProject() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(ADMIN))
        .add(deny(OWNER).ref("refs/*").group(DEVS))
        .update();
    assertAdminsAreOwnersAndDevsAreNot();
  }

  @Test
  public void blockOwnerProject() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(ADMIN))
        .add(block(OWNER).ref("refs/*").group(DEVS))
        .update();
    assertAdminsAreOwnersAndDevsAreNot();
  }

  @Test
  public void branchDelegation1() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(ADMIN))
        .add(allow(OWNER).ref("refs/heads/x/*").group(DEVS))
        .update();

    ProjectControl uDev = user(localKey, DEVS);
    assertNotOwner(uDev);

    assertOwner("refs/heads/x/*", uDev);
    assertOwner("refs/heads/x/y", uDev);
    assertOwner("refs/heads/x/y/*", uDev);

    assertNotOwner("refs/*", uDev);
    assertNotOwner("refs/heads/master", uDev);
  }

  @Test
  public void branchDelegation2() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(ADMIN))
        .add(allow(OWNER).ref("refs/heads/x/*").group(DEVS))
        .add(allow(OWNER).ref("refs/heads/x/y/*").group(fixers))
        .setExclusiveGroup(permissionKey(OWNER).ref("refs/heads/x/y/*"), true)
        .update();

    ProjectControl uDev = user(localKey, DEVS);
    assertNotOwner(uDev);

    assertOwner("refs/heads/x/*", uDev);
    assertOwner("refs/heads/x/y", uDev);
    assertOwner("refs/heads/x/y/*", uDev);
    assertNotOwner("refs/*", uDev);
    assertNotOwner("refs/heads/master", uDev);

    ProjectControl uFix = user(localKey, fixers);
    assertNotOwner(uFix);

    assertOwner("refs/heads/x/y/*", uFix);
    assertOwner("refs/heads/x/y/bar", uFix);
    assertNotOwner("refs/heads/x/*", uFix);
    assertNotOwner("refs/heads/x/y", uFix);
    assertNotOwner("refs/*", uFix);
    assertNotOwner("refs/heads/master", uFix);
  }

  @Test
  public void inheritRead_SingleBranchDeniesUpload() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(PUSH).ref("refs/for/refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/foobar").group(REGISTERED_USERS))
        .setExclusiveGroup(permissionKey(READ).ref("refs/heads/foobar"), true)
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/for/refs/heads/foobar"), true)
        .update();

    ProjectControl u = user(localKey);
    assertCanUpload(u);
    assertCreateChange("refs/heads/master", u);
    assertCannotCreateChange("refs/heads/foobar", u);
  }

  @Test
  public void inheritRead_SingleBranchDoesNotOverrideInherited() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(PUSH).ref("refs/for/refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/foobar").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(localKey);
    assertCanUpload(u);
    assertCreateChange("refs/heads/master", u);
    assertCreateChange("refs/heads/foobar", u);
  }

  @Test
  public void inheritDuplicateSections() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(ADMIN))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(DEVS))
        .update();
    assertCanAccess(user(localKey, "a", ADMIN));
    assertCanAccess(user(localKey, "d", DEVS));
  }

  @Test
  public void inheritRead_OverrideWithDeny() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(deny(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    assertAccessDenied(user(localKey));
  }

  @Test
  public void inheritRead_AppendWithDenyOfRef() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(deny(READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(localKey);
    assertCanAccess(u);
    assertCanRead("refs/master", u);
    assertCanRead("refs/tags/foobar", u);
    assertCanRead("refs/heads/master", u);
  }

  @Test
  public void inheritRead_OverridesAndDeniesOfRef() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(deny(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(localKey);
    assertCanAccess(u);
    assertCannotRead("refs/foobar", u);
    assertCannotRead("refs/tags/foobar", u);
    assertCanRead("refs/heads/foobar", u);
  }

  @Test
  public void inheritSubmit_OverridesAndDeniesOfRef() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allow(SUBMIT).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(deny(SUBMIT).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(SUBMIT).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(localKey);
    assertCannotSubmit("refs/foobar", u);
    assertCannotSubmit("refs/tags/foobar", u);
    assertCanSubmit("refs/heads/foobar", u);
  }

  @Test
  public void cannotUploadToAnyRef() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/*").group(DEVS))
        .add(allow(PUSH).ref("refs/for/refs/heads/*").group(DEVS))
        .update();

    ProjectControl u = user(localKey);
    assertCannotUpload(u);
    assertCannotCreateChange("refs/heads/master", u);
  }

  @Test
  public void usernamePatternCanUploadToAnyRef() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(PUSH).ref("refs/heads/users/${username}/*").group(REGISTERED_USERS))
        .update();
    ProjectControl u = user(localKey, "a-registered-user");
    assertCanUpload(u);
  }

  @Test
  public void usernamePatternRegExpCanUploadToAnyRef() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(
            allow(PUSH)
                .ref("^refs/heads/users/${username}/(public|private)/.+")
                .group(REGISTERED_USERS))
        .update();
    ProjectControl u = user(localKey, "a-registered-user");
    assertCanUpload(u);
    assertCanUpdate("refs/heads/users/a-registered-user/private/a", u);
  }

  @Test
  public void usernamePatternNonRegex() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(READ).ref("refs/sb/${username}/heads/*").group(DEVS))
        .update();

    ProjectControl u = user(localKey, "u", DEVS);
    ProjectControl d = user(localKey, "d", DEVS);
    assertCannotRead("refs/sb/d/heads/foobar", u);
    assertCanRead("refs/sb/d/heads/foobar", d);
  }

  @Test
  public void usernamePatternWithRegex() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(READ).ref("^refs/sb/${username}/heads/.*").group(DEVS))
        .update();

    ProjectControl u = user(localKey, "d.v", DEVS);
    ProjectControl d = user(localKey, "dev", DEVS);
    assertCanAccess(u);
    assertCanAccess(d);
    assertCannotRead("refs/sb/dev/heads/foobar", u);
    assertCanRead("refs/sb/dev/heads/foobar", d);
  }

  @Test
  public void usernameEmailPatternWithRegex() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(READ).ref("^refs/sb/${username}/heads/.*").group(DEVS))
        .update();

    ProjectControl u = user(localKey, "d.v@ger-rit.org", DEVS);
    ProjectControl d = user(localKey, "dev@ger-rit.org", DEVS);
    assertCannotRead("refs/sb/dev@ger-rit.org/heads/foobar", u);
    assertCanRead("refs/sb/dev@ger-rit.org/heads/foobar", d);
  }

  @Test
  public void sortWithRegex() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(READ).ref("^refs/heads/.*").group(DEVS))
        .update();
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allow(READ).ref("^refs/heads/.*-QA-.*").group(ANONYMOUS_USERS))
        .update();

    ProjectControl u = user(localKey, DEVS);
    ProjectControl d = user(localKey, DEVS);
    assertCanRead("refs/heads/foo-QA-bar", u);
    assertCanRead("refs/heads/foo-QA-bar", d);
  }

  @Test
  public void blockRule_ParentBlocksChild() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(PUSH).ref("refs/tags/*").group(DEVS))
        .update();
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/tags/*").group(ANONYMOUS_USERS))
        .update();
    ProjectControl u = user(localKey, DEVS);
    assertCannotUpdate("refs/tags/V10", u);
  }

  @Test
  public void blockRule_ParentBlocksChildEvenIfAlreadyBlockedInChild() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(PUSH).ref("refs/tags/*").group(DEVS))
        .add(block(PUSH).ref("refs/tags/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/tags/*").group(ANONYMOUS_USERS))
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCannotUpdate("refs/tags/V10", u);
  }

  @Test
  public void blockPartialRangeLocally() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/master").group(DEVS).range(+1, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);

    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(2, range);
  }

  @Test
  public void blockLabelRange_ParentBlocksChild() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);

    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-1, range);
    assertCanVote(1, range);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void blockLabelRange_ParentBlocksChildEvenIfAlreadyBlockedInChild() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);

    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-1, range);
    assertCanVote(1, range);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void inheritSubmit_AllowInChildDoesntAffectUnblockInParent() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(block(SUBMIT).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(SUBMIT).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(SUBMIT).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(localKey);
    assertWithMessage("submit is allowed")
        .that(u.controlForRef("refs/heads/master").canPerform(SUBMIT))
        .isTrue();
  }

  @Test
  public void unblockNoForce() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS))
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCanUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockForce() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS).force(true))
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS).force(true))
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCanForceUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockRead_NotPossible() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(block(READ).ref("refs/*").group(ANONYMOUS_USERS))
        .add(allow(READ).ref("refs/*").group(ADMIN))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(ANONYMOUS_USERS))
        .add(allow(READ).ref("refs/*").group(ADMIN))
        .update();

    ProjectControl u = user(localKey);
    assertCannotRead("refs/heads/master", u);
  }

  @Test
  public void unblockForceWithAllowNoForce_NotPossible() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS).force(true))
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS))
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCannotForceUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockMoreSpecificRef_Fails() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockMoreSpecificRefInLocal_Fails() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockMoreSpecificRefWithExclusiveFlag() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/heads/master"), true)
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCanUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockVoteMoreSpecificRefWithExclusiveFlag() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, 1))
        .add(allowLabel("Code-Review").ref("refs/heads/master").group(DEVS).range(-2, 2))
        .setExclusiveGroup(labelPermissionKey("Code-Review").ref("refs/heads/master"), true)
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-2, range);
  }

  @Test
  public void unblockFromParentDoesNotAffectChild() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/heads/master"), true)
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/master").group(DEVS))
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockFromParentDoesNotAffectChildDifferentGroups() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/heads/master"), true)
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/master").group(ANONYMOUS_USERS))
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockMoreSpecificRefInLocalWithExclusiveFlag_Fails() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/heads/master"), true)
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void blockMoreSpecificRefWithinProject() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/secret").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS))
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/heads/*"), true)
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCannotUpdate("refs/heads/secret", u);
    assertCanUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockOtherPermissionWithMoreSpecificRefAndExclusiveFlag_Fails() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .add(allow(SUBMIT).ref("refs/heads/master").group(DEVS))
        .setExclusiveGroup(permissionKey(SUBMIT).ref("refs/heads/master"), true)
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockLargerScope_Fails() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/master").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS))
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockInLocal_Fails() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(PUSH).ref("refs/heads/*").group(fixers))
        .update();

    ProjectControl f = user(localKey, fixers);
    assertCannotUpdate("refs/heads/master", f);
  }

  @Test
  public void unblockInParentBlockInLocal() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(DEVS))
        .update();

    ProjectControl d = user(localKey, DEVS);
    assertCannotUpdate("refs/heads/master", d);
  }

  @Test
  public void unblockForceEditTopicName() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(block(EDIT_TOPIC_NAME).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(EDIT_TOPIC_NAME).ref("refs/heads/*").group(DEVS).force(true))
        .update();

    ProjectControl u = user(localKey, DEVS);
    assertWithMessage("u can edit topic name")
        .that(u.controlForRef("refs/heads/master").canForceEditTopicName())
        .isTrue();
  }

  @Test
  public void unblockInLocalForceEditTopicName_Fails() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(block(EDIT_TOPIC_NAME).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(EDIT_TOPIC_NAME).ref("refs/heads/*").group(DEVS).force(true))
        .update();

    ProjectControl u = user(localKey, REGISTERED_USERS);
    assertWithMessage("u can't edit topic name")
        .that(u.controlForRef("refs/heads/master").canForceEditTopicName())
        .isFalse();
  }

  @Test
  public void unblockRange() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, +1))
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unblockRangeOnMoreSpecificRef_Fails() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, +1))
        .add(allowLabel("Code-Review").ref("refs/heads/master").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void unblockRangeOnLargerScope_Fails() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(
            blockLabel("Code-Review").ref("refs/heads/master").group(ANONYMOUS_USERS).range(-1, +1))
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void nonconfiguredCannotVote() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, REGISTERED_USERS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-1, range);
    assertCannotVote(1, range);
  }

  @Test
  public void unblockInLocalRange_Fails() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, 1))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void unblockRangeForChangeOwner() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(CHANGE_OWNER).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review", true);
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unblockRangeForNotChangeOwner() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(CHANGE_OWNER).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void blockChangeOwnerVote() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(CHANGE_OWNER).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void unionOfPermissibleVotes() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-1, +1))
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unionOfPermissibleVotesPermissionOrder() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-1, +1))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unionOfBlockedVotes() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-1, +1))
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +1))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-1, range);
    assertCannotVote(1, range);
  }

  @Test
  public void blockOwner() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(block(OWNER).ref("refs/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(DEVS))
        .update();

    assertThat(user(localKey, DEVS).isOwner()).isFalse();
  }

  @Test
  public void validateRefPatternsOK() throws Exception {
    RefPattern.validate("refs/*");
    RefPattern.validate("^refs/heads/*");
    RefPattern.validate("^refs/tags/[0-9a-zA-Z-_.]+");
    RefPattern.validate("refs/heads/review/${username}/*");
    RefPattern.validate("^refs/heads/review/${username}/.+");
  }

  @Test
  public void testValidateBadRefPatternDoubleCaret() throws Exception {
    assertThrows(InvalidNameException.class, () -> RefPattern.validate("^^refs/*"));
  }

  @Test
  public void testValidateBadRefPatternDanglingCharacter() throws Exception {
    assertThrows(
        InvalidNameException.class,
        () -> RefPattern.validate("^refs/heads/tmp/sdk/[0-9]{3,3}_R[1-9][A-Z][0-9]{3,3}*"));
  }

  @Test
  public void validateRefPatternNoDanglingCharacter() throws Exception {
    RefPattern.validate("^refs/heads/tmp/sdk/[0-9]{3,3}_R[1-9][A-Z][0-9]{3,3}");
  }

  private ProjectState getProjectState(Project.NameKey nameKey) throws Exception {
    return projectCache.get(nameKey).orElseThrow(illegalState(nameKey));
  }

  private ProjectControl user(Project.NameKey localKey, AccountGroup.UUID... memberOf)
      throws Exception {
    return user(localKey, null, memberOf);
  }

  private ProjectControl user(
      Project.NameKey localKey, @Nullable String name, AccountGroup.UUID... memberOf)
      throws Exception {
    return projectControlFactory.create(new MockUser(name, memberOf), getProjectState(localKey));
  }

  private static class MockUser extends CurrentUser {
    @Nullable private final String username;
    private final GroupMembership groups;

    MockUser(@Nullable String name, AccountGroup.UUID[] groupId) {
      username = name;
      ArrayList<AccountGroup.UUID> groupIds = Lists.newArrayList(groupId);
      groupIds.add(REGISTERED_USERS);
      groupIds.add(ANONYMOUS_USERS);
      groups = new ListGroupMembership(groupIds);
    }

    @Override
    public GroupMembership getEffectiveGroups() {
      return groups;
    }

    @Override
    public Object getCacheKey() {
      return new Object();
    }

    @Override
    public Optional<String> getUserName() {
      return Optional.ofNullable(username);
    }
  }
}
