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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.blockLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.labelPermissionKey;
import static com.google.gerrit.entities.Permission.LABEL;
import static com.google.gerrit.entities.RefNames.REFS_CONFIG;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.CHANGE_OWNER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PermissionRange;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.SingleVersionModule.SingleVersionListener;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
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

public class LabelRangeTest {
  private static final AccountGroup.UUID DEVS = AccountGroup.uuid("test.devs");

  private void assertCanVote(int score, PermissionRange range) {
    assertWithMessage("can vote " + score).that(range.contains(score)).isTrue();
  }

  private void assertCannotVote(int score, PermissionRange range) {
    assertWithMessage("cannot vote " + score).that(range.contains(score)).isFalse();
  }

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

    @SuppressWarnings("unused")
    var unused = requestContext.setContext(() -> null);
  }

  @After
  public void tearDown() throws Exception {
    @SuppressWarnings("unused")
    var unused = requestContext.setContext(null);
  }

  @Test
  public void cannotVoteWithoutAllow() throws Exception {
    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCannotVote(+2, range);
    assertCannotVote(+1, range);
    assertCanVote(0, range);
    assertCannotVote(-1, range);
    assertCannotVote(-2, range);
  }

  @Test
  public void allowLocally() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);

    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCanVote(+2, range);
    assertCanVote(+1, range);
    assertCanVote(0, range);
    assertCanVote(-1, range);
    assertCanVote(-2, range);
  }

  @Test
  public void allowPartialLocally() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-1, +1))
        .update();

    ProjectControl u = user(localKey, DEVS);

    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCannotVote(+2, range);
    assertCanVote(+1, range);
    assertCanVote(0, range);
    assertCanVote(-1, range);
    assertCannotVote(-2, range);
  }

  @Test
  public void allowGroupDoesNotAllowOthers() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, REGISTERED_USERS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCannotVote(+2, range);
    assertCannotVote(+1, range);
    assertCanVote(0, range);
    assertCannotVote(-1, range);
    assertCannotVote(-2, range);
  }

  @Test
  public void allowGroupDoesNotExtendOthers() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS).range(-1, +1))
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, REGISTERED_USERS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCannotVote(+2, range);
    assertCanVote(+1, range);
    assertCanVote(0, range);
    assertCanVote(-1, range);
    assertCannotVote(-2, range);
  }

  @Test
  public void blockPartialRangeLocally() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(blockLabel(LabelId.CODE_REVIEW).ref("refs/heads/master").group(DEVS).range(+1, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);

    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCannotVote(2, range);
  }

  @Test
  public void blockLabelRange_ParentBlocksChild() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(blockLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);

    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
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
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .add(blockLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(blockLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);

    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCanVote(-1, range);
    assertCanVote(1, range);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void unblockVoteMoreSpecificRefWithExclusiveFlag() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(
            blockLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, 1))
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/master").group(DEVS).range(-2, 2))
        .setExclusiveGroup(labelPermissionKey(LabelId.CODE_REVIEW).ref("refs/heads/master"), true)
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCanVote(-2, range);
  }

  @Test
  public void unblockRange() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(
            blockLabel(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(ANONYMOUS_USERS)
                .range(-1, +1))
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unblockRangeOnMoreSpecificRef_Fails() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(
            blockLabel(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(ANONYMOUS_USERS)
                .range(-1, +1))
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/master").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void unblockRangeOnLargerScope_Fails() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(
            blockLabel(LabelId.CODE_REVIEW)
                .ref("refs/heads/master")
                .group(ANONYMOUS_USERS)
                .range(-1, +1))
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void nonconfiguredCannotVote() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, REGISTERED_USERS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCannotVote(-1, range);
    assertCannotVote(1, range);
  }

  @Test
  public void unblockInLocalRange_Fails() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(
            blockLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, 1))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void unblockRangeForChangeOwner() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(CHANGE_OWNER).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW, true);
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unblockRangeForNotChangeOwner() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(CHANGE_OWNER).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void blockChangeOwnerVote() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(blockLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(CHANGE_OWNER).range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCannotVote(-2, range);
    assertCannotVote(2, range);
  }

  @Test
  public void unionOfPermissibleVotes() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-1, +1))
        .add(
            allowLabel(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, +2))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unionOfPermissibleVotesPermissionOrder() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(
            allowLabel(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, +2))
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-1, +1))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCanVote(-2, range);
    assertCanVote(2, range);
  }

  @Test
  public void unionOfBlockedVotes() throws Exception {
    projectOperations
        .project(parentKey)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(DEVS).range(-1, +1))
        .add(
            blockLabel(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, +2))
        .update();
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(
            blockLabel(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, +1))
        .update();

    ProjectControl u = user(localKey, DEVS);
    PermissionRange range =
        u.controlForRef("refs/heads/master").getRange(LABEL + LabelId.CODE_REVIEW);
    assertCanVote(-1, range);
    assertCannotVote(1, range);
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
