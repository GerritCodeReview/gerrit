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
import static com.google.gerrit.common.data.Permission.EDIT_TOPIC_NAME;
import static com.google.gerrit.common.data.Permission.LABEL;
import static com.google.gerrit.common.data.Permission.OWNER;
import static com.google.gerrit.common.data.Permission.PUSH;
import static com.google.gerrit.common.data.Permission.READ;
import static com.google.gerrit.common.data.Permission.SUBMIT;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.CHANGE_OWNER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.testing.InMemoryRepositoryManager.newRepository;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.exceptions.InvalidNameException;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityCollection;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.TransferConfig;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RefControlTest {
  private static final AccountGroup.UUID ADMIN = AccountGroup.uuid("test.admin");
  private static final AccountGroup.UUID DEVS = AccountGroup.uuid("test.devs");

  private void assertAdminsAreOwnersAndDevsAreNot() {
    ProjectControl uBlah = user(local, DEVS);
    ProjectControl uAdmin = user(local, DEVS, ADMIN);

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

  private final AllProjectsName allProjectsName =
      new AllProjectsName(AllProjectsNameProvider.DEFAULT);
  private final AllUsersName allUsersName = new AllUsersName(AllUsersNameProvider.DEFAULT);
  private final AccountGroup.UUID fixers = AccountGroup.uuid("test.fixers");
  private final Map<Project.NameKey, ProjectState> all = new HashMap<>();
  private Project.NameKey localKey = Project.nameKey("local");
  private ProjectConfig local;
  private Project.NameKey parentKey = Project.nameKey("parent");
  private ProjectConfig parent;
  private InMemoryRepositoryManager repoManager;
  private ProjectCache projectCache;
  private PermissionCollection.Factory sectionSorter;
  private ChangeControl.Factory changeControlFactory;

  @Inject private PermissionBackend permissionBackend;
  @Inject private CapabilityCollection.Factory capabilityCollectionFactory;
  @Inject private SchemaCreator schemaCreator;
  @Inject private SingleVersionListener singleVersionListener;
  @Inject private ThreadLocalRequestContext requestContext;
  @Inject private DefaultRefFilter.Factory refFilterFactory;
  @Inject private TransferConfig transferConfig;
  @Inject private MetricMaker metricMaker;
  @Inject private ProjectConfig.Factory projectConfigFactory;
  @Inject private ProjectOperations projectOperations;

  @Before
  public void setUp() throws Exception {
    repoManager = new InMemoryRepositoryManager();
    projectCache =
        new ProjectCache() {
          @Override
          public ProjectState getAllProjects() {
            return get(allProjectsName);
          }

          @Override
          public ProjectState getAllUsers() {
            return null;
          }

          @Override
          public ProjectState get(Project.NameKey projectName) {
            return all.get(projectName);
          }

          @Override
          public void evict(Project p) {}

          @Override
          public void remove(Project p) {}

          @Override
          public void remove(Project.NameKey name) {}

          @Override
          public ImmutableSortedSet<Project.NameKey> all() {
            return ImmutableSortedSet.of();
          }

          @Override
          public ImmutableSortedSet<Project.NameKey> byName(String prefix) {
            return ImmutableSortedSet.of();
          }

          @Override
          public void onCreateProject(Project.NameKey newProjectName) {}

          @Override
          public Set<AccountGroup.UUID> guessRelevantGroupUUIDs() {
            return Collections.emptySet();
          }

          @Override
          public ProjectState checkedGet(Project.NameKey projectName) throws IOException {
            return all.get(projectName);
          }

          @Override
          public void evict(Project.NameKey p) {}

          @Override
          public ProjectState checkedGet(Project.NameKey projectName, boolean strict)
              throws Exception {
            return all.get(projectName);
          }
        };

    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);

    try {
      Repository repo = repoManager.createRepository(allProjectsName);
      ProjectConfig allProjects =
          projectConfigFactory.create(Project.nameKey(allProjectsName.get()));
      allProjects.load(repo);
      LabelType cr = TestLabels.codeReview();
      allProjects.getLabelSections().put(cr.getName(), cr);
      add(allProjects);
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException(e);
    }

    singleVersionListener.start();
    try {
      schemaCreator.create();
    } finally {
      singleVersionListener.stop();
    }

    Cache<SectionSortCache.EntryKey, SectionSortCache.EntryVal> c =
        CacheBuilder.newBuilder().build();
    sectionSorter = new PermissionCollection.Factory(new SectionSortCache(c), metricMaker);

    parent = projectConfigFactory.create(parentKey);
    parent.load(newRepository(parentKey));
    add(parent);

    local = projectConfigFactory.create(localKey);
    local.load(newRepository(localKey));
    add(local);
    local.getProject().setParentName(parentKey);

    requestContext.setContext(() -> null);

    changeControlFactory = injector.getInstance(ChangeControl.Factory.class);
  }

  @After
  public void tearDown() {
    requestContext.setContext(null);
  }

  @Test
  public void ownerProject() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(ADMIN))
        .update();
    assertAdminsAreOwnersAndDevsAreNot();
  }

  @Test
  public void denyOwnerProject() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(ADMIN))
        .add(deny(OWNER).ref("refs/*").group(DEVS))
        .update();
    assertAdminsAreOwnersAndDevsAreNot();
  }

  @Test
  public void blockOwnerProject() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(ADMIN))
        .add(block(OWNER).ref("refs/*").group(DEVS))
        .update();
    assertAdminsAreOwnersAndDevsAreNot();
  }

  @Test
  public void branchDelegation1() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(ADMIN))
        .add(allow(OWNER).ref("refs/heads/x/*").group(DEVS))
        .update();

    ProjectControl uDev = user(local, DEVS);
    assertNotOwner(uDev);

    assertOwner("refs/heads/x/*", uDev);
    assertOwner("refs/heads/x/y", uDev);
    assertOwner("refs/heads/x/y/*", uDev);

    assertNotOwner("refs/*", uDev);
    assertNotOwner("refs/heads/master", uDev);
  }

  @Test
  public void branchDelegation2() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(ADMIN))
        .add(allow(OWNER).ref("refs/heads/x/*").group(DEVS))
        .add(allow(OWNER).ref("refs/heads/x/y/*").group(fixers))
        .setExclusiveGroup(permissionKey(OWNER).ref("refs/heads/x/y/*"), true)
        .update();

    ProjectControl uDev = user(local, DEVS);
    assertNotOwner(uDev);

    assertOwner("refs/heads/x/*", uDev);
    assertOwner("refs/heads/x/y", uDev);
    assertOwner("refs/heads/x/y/*", uDev);
    assertNotOwner("refs/*", uDev);
    assertNotOwner("refs/heads/master", uDev);

    ProjectControl uFix = user(local, fixers);
    assertNotOwner(uFix);

    assertOwner("refs/heads/x/y/*", uFix);
    assertOwner("refs/heads/x/y/bar", uFix);
    assertNotOwner("refs/heads/x/*", uFix);
    assertNotOwner("refs/heads/x/y", uFix);
    assertNotOwner("refs/*", uFix);
    assertNotOwner("refs/heads/master", uFix);
  }

  @Test
  public void inheritRead_SingleBranchDeniesUpload() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(PUSH).ref("refs/for/refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/heads/foobar").group(REGISTERED_USERS))
        .setExclusiveGroup(permissionKey(READ).ref("refs/heads/foobar"), true)
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/for/refs/heads/foobar"), true)
        .update();

    ProjectControl u = user(local);
    assertCanUpload(u);
    assertCreateChange("refs/heads/master", u);
    assertCannotCreateChange("refs/heads/foobar", u);
  }

  @Test
  public void blockPushDrafts() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(PUSH).ref("refs/for/refs/*").group(REGISTERED_USERS))
        .add(block(PUSH).ref("refs/drafts/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(PUSH).ref("refs/drafts/*").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(local);
    assertCreateChange("refs/heads/master", u);
    assertThat(u.controlForRef("refs/drafts/master").canPerform(PUSH)).isFalse();
  }

  @Test
  public void blockPushDraftsUnblockAdmin() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/drafts/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/drafts/*").group(ADMIN))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(PUSH).ref("refs/drafts/*").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(local);
    ProjectControl a = user(local, "a", ADMIN);

    assertWithMessage("push is allowed")
        .that(a.controlForRef("refs/drafts/master").canPerform(PUSH))
        .isTrue();
    assertWithMessage("push is not allowed")
        .that(u.controlForRef("refs/drafts/master").canPerform(PUSH))
        .isFalse();
  }

  @Test
  public void inheritRead_SingleBranchDoesNotOverrideInherited() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(PUSH).ref("refs/for/refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/heads/foobar").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(local);
    assertCanUpload(u);
    assertCreateChange("refs/heads/master", u);
    assertCreateChange("refs/heads/foobar", u);
  }

  @Test
  public void inheritDuplicateSections() throws Exception {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(ADMIN))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/heads/*").group(DEVS))
        .update();
    assertCanAccess(user(local, "a", ADMIN));

    local = projectConfigFactory.create(localKey);
    local.load(newRepository(localKey));
    local.getProject().setParentName(parentKey);
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(DEVS))
        .update();
    assertCanAccess(user(local, "d", DEVS));
  }

  @Test
  public void inheritRead_OverrideWithDeny() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(deny(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    assertAccessDenied(user(local));
  }

  @Test
  public void inheritRead_AppendWithDenyOfRef() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(deny(READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(local);
    assertCanAccess(u);
    assertCanRead("refs/master", u);
    assertCanRead("refs/tags/foobar", u);
    assertCanRead("refs/heads/master", u);
  }

  @Test
  public void inheritRead_OverridesAndDeniesOfRef() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(deny(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(local);
    assertCanAccess(u);
    assertCannotRead("refs/foobar", u);
    assertCannotRead("refs/tags/foobar", u);
    assertCanRead("refs/heads/foobar", u);
  }

  @Test
  public void inheritSubmit_OverridesAndDeniesOfRef() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(SUBMIT).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(deny(SUBMIT).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(SUBMIT).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(local);
    assertCannotSubmit("refs/foobar", u);
    assertCannotSubmit("refs/tags/foobar", u);
    assertCanSubmit("refs/heads/foobar", u);
  }

  @Test
  public void cannotUploadToAnyRef() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/heads/*").group(DEVS))
        .add(allow(PUSH).ref("refs/for/refs/heads/*").group(DEVS))
        .update();

    ProjectControl u = user(local);
    assertCannotUpload(u);
    assertCannotCreateChange("refs/heads/master", u);
  }

  @Test
  public void usernamePatternCanUploadToAnyRef() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(PUSH).ref("refs/heads/users/${username}/*").group(REGISTERED_USERS))
        .update();
    ProjectControl u = user(local, "a-registered-user");
    assertCanUpload(u);
  }

  @Test
  public void usernamePatternNonRegex() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/sb/${username}/heads/*").group(DEVS))
        .update();

    ProjectControl u = user(local, "u", DEVS);
    ProjectControl d = user(local, "d", DEVS);
    assertCannotRead("refs/sb/d/heads/foobar", u);
    assertCanRead("refs/sb/d/heads/foobar", d);
  }

  @Test
  public void usernamePatternWithRegex() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(READ).ref("^refs/sb/${username}/heads/.*").group(DEVS))
        .update();

    ProjectControl u = user(local, "d.v", DEVS);
    ProjectControl d = user(local, "dev", DEVS);
    assertCannotRead("refs/sb/dev/heads/foobar", u);
    assertCanRead("refs/sb/dev/heads/foobar", d);
  }

  @Test
  public void usernameEmailPatternWithRegex() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(READ).ref("^refs/sb/${username}/heads/.*").group(DEVS))
        .update();

    ProjectControl u = user(local, "d.v@ger-rit.org", DEVS);
    ProjectControl d = user(local, "dev@ger-rit.org", DEVS);
    assertCannotRead("refs/sb/dev@ger-rit.org/heads/foobar", u);
    assertCanRead("refs/sb/dev@ger-rit.org/heads/foobar", d);
  }

  @Test
  public void sortWithRegex() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(READ).ref("^refs/heads/.*").group(DEVS))
        .update();
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(READ).ref("^refs/heads/.*-QA-.*").group(ANONYMOUS_USERS))
        .update();

    ProjectControl u = user(local, DEVS);
    ProjectControl d = user(local, DEVS);
    assertCanRead("refs/heads/foo-QA-bar", u);
    assertCanRead("refs/heads/foo-QA-bar", d);
  }

  @Test
  public void blockRule_ParentBlocksChild() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(PUSH).ref("refs/tags/*").group(DEVS))
        .update();
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/tags/*").group(ANONYMOUS_USERS))
        .update();
    ProjectControl u = user(local, DEVS);
    assertCannotUpdate("refs/tags/V10", u);
  }

  @Test
  public void blockRule_ParentBlocksChildEvenIfAlreadyBlockedInChild() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(PUSH).ref("refs/tags/*").group(DEVS))
        .add(block(PUSH).ref("refs/tags/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/tags/*").group(ANONYMOUS_USERS))
        .update();

    ProjectControl u = user(local, DEVS);
    assertCannotUpdate("refs/tags/V10", u);
  }

  @Test
  public void blockLabelRange_ParentBlocksChild() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(local, DEVS);

    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-1, range);
    assertCanVote(1, range);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void blockLabelRange_ParentBlocksChildEvenIfAlreadyBlockedInChild() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(local, DEVS);

    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-1, range);
    assertCanVote(1, range);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void inheritSubmit_AllowInChildDoesntAffectUnblockInParent() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(block(SUBMIT).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(SUBMIT).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(SUBMIT).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    ProjectControl u = user(local);
    assertWithMessage("submit is allowed")
        .that(u.controlForRef("refs/heads/master").canPerform(SUBMIT))
        .isTrue();
  }

  @Test
  public void unblockNoForce() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS))
        .update();

    ProjectControl u = user(local, DEVS);
    assertCanUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockForce() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS).force(true))
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS).force(true))
        .update();
    ProjectControl u = user(local, DEVS);
    assertCanForceUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockRead_NotPossible() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(block(READ).ref("refs/*").group(ANONYMOUS_USERS))
        .add(allow(READ).ref("refs/*").group(ADMIN))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(ANONYMOUS_USERS))
        .add(allow(READ).ref("refs/*").group(ADMIN))
        .update();
    ProjectControl u = user(local);
    assertCannotRead("refs/heads/master", u);
  }

  @Test
  public void unblockForceWithAllowNoForce_NotPossible() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS))
        .update();

    ProjectControl u = user(local, DEVS);
    assertCannotForceUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockMoreSpecificRef_Fails() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .update();

    ProjectControl u = user(local, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockMoreSpecificRefInLocal_Fails() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .update();

    ProjectControl u = user(local, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockMoreSpecificRefWithExclusiveFlag() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/heads/master"), true)
        .update();

    ProjectControl u = user(local, DEVS);
    assertCanUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockVoteMoreSpecificRefWithExclusiveFlag() {

    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, 1))
        .add(allowLabel("Code-Review").ref("refs/heads/master").group(DEVS).range(-2, 2))
        .setExclusiveGroup(labelPermissionKey("Code-Review").ref("refs/heads/master"), true)
        .update();

    ProjectControl u = user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-2, range);
  }

  @Test
  public void unblockFromParentDoesNotAffectChild() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .add(block(PUSH).ref("refs/heads/master").group(DEVS))
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/heads/master"), true)
        .update();

    ProjectControl u = user(local, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockFromParentDoesNotAffectChildDifferentGroups() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/heads/master"), true)
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/master").group(ANONYMOUS_USERS))
        .update();

    ProjectControl u = user(local, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockMoreSpecificRefInLocalWithExclusiveFlag_Fails() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/heads/master"), true)
        .update();

    ProjectControl u = user(local, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void blockMoreSpecificRefWithinProject() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/secret").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS))
        .setExclusiveGroup(permissionKey(PUSH).ref("refs/heads/*"), true)
        .update();

    ProjectControl u = user(local, DEVS);
    assertCannotUpdate("refs/heads/secret", u);
    assertCanUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockOtherPermissionWithMoreSpecificRefAndExclusiveFlag_Fails() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/master").group(DEVS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(SUBMIT).ref("refs/heads/master").group(DEVS))
        .setExclusiveGroup(permissionKey(SUBMIT).ref("refs/heads/master"), true)
        .update();

    ProjectControl u = user(local, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockLargerScope_Fails() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/master").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS))
        .update();

    ProjectControl u = user(local, DEVS);
    assertCannotUpdate("refs/heads/master", u);
  }

  @Test
  public void unblockInLocal_Fails() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/*").group(fixers))
        .update();

    ProjectControl f = user(local, fixers);
    assertCannotUpdate("refs/heads/master", f);
  }

  @Test
  public void unblockInParentBlockInLocal() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(PUSH).ref("refs/heads/*").group(DEVS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(block(PUSH).ref("refs/heads/*").group(DEVS))
        .update();

    ProjectControl d = user(local, DEVS);
    assertCannotUpdate("refs/heads/master", d);
  }

  @Test
  public void unblockForceEditTopicName() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(block(EDIT_TOPIC_NAME).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(EDIT_TOPIC_NAME).ref("refs/heads/*").group(DEVS).force(true))
        .update();

    ProjectControl u = user(local, DEVS);
    assertWithMessage("u can edit topic name")
        .that(u.controlForRef("refs/heads/master").canForceEditTopicName())
        .isTrue();
  }

  @Test
  public void unblockInLocalForceEditTopicName_Fails() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(block(EDIT_TOPIC_NAME).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(EDIT_TOPIC_NAME).ref("refs/heads/*").group(DEVS).force(true))
        .update();

    ProjectControl u = user(local, REGISTERED_USERS);
    assertWithMessage("u can't edit topic name")
        .that(u.controlForRef("refs/heads/master").canForceEditTopicName())
        .isFalse();
  }

  @Test
  public void unblockRange() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, +1))
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unblockRangeOnMoreSpecificRef_Fails() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, +1))
        .add(allowLabel("Code-Review").ref("refs/heads/master").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void unblockRangeOnLargerScope_Fails() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(
            blockLabel("Code-Review").ref("refs/heads/master").group(ANONYMOUS_USERS).range(-1, +1))
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void nonconfiguredCannotVote() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(local, REGISTERED_USERS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-1, range);
    assertCannotVote(1, range);
  }

  @Test
  public void unblockInLocalRange_Fails() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, 1))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void unblockRangeForChangeOwner() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(CHANGE_OWNER).range(-2, +2))
        .update();

    ProjectControl u = user(local, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review", true);
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unblockRangeForNotChangeOwner() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(CHANGE_OWNER).range(-2, +2))
        .update();

    ProjectControl u = user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void blockChangeOwnerVote() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(CHANGE_OWNER).range(-2, +2))
        .update();

    ProjectControl u = user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void unionOfPermissibleVotes() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-1, +1))
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();

    ProjectControl u = user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unionOfPermissibleVotesPermissionOrder() {
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-1, +1))
        .update();

    ProjectControl u = user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unionOfBlockedVotes() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(DEVS).range(-1, +1))
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +1))
        .update();

    ProjectControl u = user(local, DEVS);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertCanVote(-1, range);
    assertCannotVote(1, range);
  }

  @Test
  public void blockOwner() {
    projectOperations
        .project(parent.getName())
        .forUpdate()
        .add(block(OWNER).ref("refs/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(local.getName())
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(DEVS))
        .update();

    assertThat(user(local, DEVS).isOwner()).isFalse();
  }

  @Test
  public void validateRefPatternsOK() throws Exception {
    RefPattern.validate("refs/*");
    RefPattern.validate("^refs/heads/*");
    RefPattern.validate("^refs/tags/[0-9a-zA-Z-_.]+");
    RefPattern.validate("refs/heads/review/${username}/*");
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

  private InMemoryRepository add(ProjectConfig pc) {
    List<CommentLinkInfo> commentLinks = null;

    InMemoryRepository repo;
    try {
      repo = repoManager.createRepository(pc.getName());
      if (pc.getProject() == null) {
        pc.load(repo);
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException(e);
    }
    all.put(
        pc.getName(),
        new ProjectState(
            projectCache,
            allProjectsName,
            allUsersName,
            repoManager,
            commentLinks,
            capabilityCollectionFactory,
            transferConfig,
            metricMaker,
            pc));
    return repo;
  }

  private ProjectControl user(ProjectConfig local, AccountGroup.UUID... memberOf) {
    return user(local, null, memberOf);
  }

  private ProjectControl user(
      ProjectConfig local, @Nullable String name, AccountGroup.UUID... memberOf) {
    return new ProjectControl(
        Collections.emptySet(),
        Collections.emptySet(),
        sectionSorter,
        changeControlFactory,
        permissionBackend,
        refFilterFactory,
        new MockUser(name, memberOf),
        newProjectState(local));
  }

  private ProjectState newProjectState(ProjectConfig local) {
    add(local);
    return all.get(local.getProject().getNameKey());
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
