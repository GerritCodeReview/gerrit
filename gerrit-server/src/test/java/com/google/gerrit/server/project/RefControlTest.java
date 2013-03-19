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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.inject.Guice;
import com.google.inject.Injector;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RefControlTest extends TestCase {
  public void testOwnerProject() {
    grant(local, OWNER, admin, "refs/*");

    ProjectControl uBlah = user(devs);
    ProjectControl uAdmin = user(devs, admin);

    assertFalse("not owner", uBlah.isOwner());
    assertTrue("is owner", uAdmin.isOwner());
  }

  public void testBranchDelegation1() {
    grant(local, OWNER, admin, "refs/*");
    grant(local, OWNER, devs, "refs/heads/x/*");

    ProjectControl uDev = user(devs);
    assertFalse("not owner", uDev.isOwner());
    assertTrue("owns ref", uDev.isOwnerAnyRef());

    assertOwner("refs/heads/x/*", uDev);
    assertOwner("refs/heads/x/y", uDev);
    assertOwner("refs/heads/x/y/*", uDev);

    assertNotOwner("refs/*", uDev);
    assertNotOwner("refs/heads/master", uDev);
  }

  public void testBranchDelegation2() {
    grant(local, OWNER, admin, "refs/*");
    grant(local, OWNER, devs, "refs/heads/x/*");
    grant(local, OWNER, fixers, "refs/heads/x/y/*");
    doNotInherit(local, OWNER, "refs/heads/x/y/*");

    ProjectControl uDev = user(devs);
    assertFalse("not owner", uDev.isOwner());
    assertTrue("owns ref", uDev.isOwnerAnyRef());

    assertOwner("refs/heads/x/*", uDev);
    assertOwner("refs/heads/x/y", uDev);
    assertOwner("refs/heads/x/y/*", uDev);
    assertNotOwner("refs/*", uDev);
    assertNotOwner("refs/heads/master", uDev);

    ProjectControl uFix = user(fixers);
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
    grant(parent, READ, registered, "refs/*");
    grant(parent, PUSH, registered, "refs/for/refs/*");
    grant(local, READ, registered, "refs/heads/foobar");
    doNotInherit(local, READ, "refs/heads/foobar");
    doNotInherit(local, PUSH, "refs/for/refs/heads/foobar");

    ProjectControl u = user();
    assertTrue("can upload", u.canPushToAtLeastOneRef() == Capable.OK);

    assertTrue("can upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());

    assertFalse("deny refs/heads/foobar", //
        u.controlForRef("refs/heads/foobar").canUpload());
  }

  public void testInheritRead_SingleBranchDoesNotOverrideInherited() {
    grant(parent, READ, registered, "refs/*");
    grant(parent, PUSH, registered, "refs/for/refs/*");
    grant(local, READ, registered, "refs/heads/foobar");

    ProjectControl u = user();
    assertTrue("can upload", u.canPushToAtLeastOneRef() == Capable.OK);

    assertTrue("can upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());

    assertTrue("can upload refs/heads/foobar", //
        u.controlForRef("refs/heads/foobar").canUpload());
  }

  public void testInheritDuplicateSections() {
    grant(parent, READ, admin, "refs/*");
    grant(local, READ, devs, "refs/heads/*");
    local.getProject().setParentName(parent.getProject().getName());
    assertTrue("a can read", user("a", admin).isVisible());

    local = new ProjectConfig(new Project.NameKey("local"));
    local.createInMemory();
    grant(local, READ, devs, "refs/*");
    assertTrue("d can read", user("d", devs).isVisible());
  }

  public void testInheritRead_OverrideWithDeny() {
    grant(parent, READ, registered, "refs/*");
    grant(local, READ, registered, "refs/*").setDeny();

    ProjectControl u = user();
    assertFalse("can't read", u.isVisible());
  }

  public void testInheritRead_AppendWithDenyOfRef() {
    grant(parent, READ, registered, "refs/*");
    grant(local, READ, registered, "refs/heads/*").setDeny();

    ProjectControl u = user();
    assertTrue("can read", u.isVisible());
    assertTrue("can read", u.controlForRef("refs/master").isVisible());
    assertTrue("can read", u.controlForRef("refs/tags/foobar").isVisible());
    assertTrue("no master", u.controlForRef("refs/heads/master").isVisible());
  }

  public void testInheritRead_OverridesAndDeniesOfRef() {
    grant(parent, READ, registered, "refs/*");
    grant(local, READ, registered, "refs/*").setDeny();
    grant(local, READ, registered, "refs/heads/*");

    ProjectControl u = user();
    assertTrue("can read", u.isVisible());
    assertFalse("can't read", u.controlForRef("refs/foobar").isVisible());
    assertFalse("can't read", u.controlForRef("refs/tags/foobar").isVisible());
    assertTrue("can read", u.controlForRef("refs/heads/foobar").isVisible());
  }

  public void testInheritSubmit_OverridesAndDeniesOfRef() {
    grant(parent, SUBMIT, registered, "refs/*");
    grant(local, SUBMIT, registered, "refs/*").setDeny();
    grant(local, SUBMIT, registered, "refs/heads/*");

    ProjectControl u = user();
    assertFalse("can't submit", u.controlForRef("refs/foobar").canSubmit());
    assertFalse("can't submit", u.controlForRef("refs/tags/foobar").canSubmit());
    assertTrue("can submit", u.controlForRef("refs/heads/foobar").canSubmit());
  }

  public void testCannotUploadToAnyRef() {
    grant(parent, READ, registered, "refs/*");
    grant(local, READ, devs, "refs/heads/*");
    grant(local, PUSH, devs, "refs/for/refs/heads/*");

    ProjectControl u = user();
    assertFalse("cannot upload", u.canPushToAtLeastOneRef() == Capable.OK);
    assertFalse("cannot upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());
  }

  public void testUsernamePatternNonRegex() {
    grant(local, READ, devs, "refs/sb/${username}/heads/*");

    ProjectControl u = user("u", devs), d = user("d", devs);
    assertFalse("u can't read", u.controlForRef("refs/sb/d/heads/foobar").isVisible());
    assertTrue("d can read", d.controlForRef("refs/sb/d/heads/foobar").isVisible());
  }

  public void testUsernamePatternWithRegex() {
    grant(local, READ, devs, "^refs/sb/${username}/heads/.*");

    ProjectControl u = user("d.v", devs), d = user("dev", devs);
    assertFalse("u can't read", u.controlForRef("refs/sb/dev/heads/foobar").isVisible());
    assertTrue("d can read", d.controlForRef("refs/sb/dev/heads/foobar").isVisible());
  }

  public void testSortWithRegex() {
    grant(local, READ, devs, "^refs/heads/.*");
    grant(parent, READ, anonymous, "^refs/heads/.*-QA-.*");

    ProjectControl u = user(devs), d = user(devs);
    assertTrue("u can read", u.controlForRef("refs/heads/foo-QA-bar").isVisible());
    assertTrue("d can read", d.controlForRef("refs/heads/foo-QA-bar").isVisible());
  }

  public void testBlockRule_ParentBlocksChild() {
    grant(local, PUSH, devs, "refs/tags/*");
    grant(parent, PUSH, anonymous, "refs/tags/*").setBlock();

    ProjectControl u = user(devs);
    assertFalse("u can't force update tag", u.controlForRef("refs/tags/V10").canForceUpdate());
  }

  public void testBlockLabelRange_ParentBlocksChild() {
    grant(local, LABEL + "Code-Review", -2, +2, devs, "refs/heads/*");
    grant(parent, LABEL + "Code-Review", -2, +2, devs, "refs/heads/*").setBlock();

    ProjectControl u = user(devs);

    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertTrue("u can vote -1", range.contains(-1));
    assertTrue("u can vote +1", range.contains(1));
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote 2", range.contains(2));
  }

  public void testUnblockNoForce() {
    grant(local, PUSH, anonymous, "refs/heads/*").setBlock();
    grant(local, PUSH, devs, "refs/heads/*");

    ProjectControl u = user(devs);
    assertTrue("u can push", u.controlForRef("refs/heads/master").canUpdate());
  }

  public void testUnblockForce() {
    PermissionRule r = grant(local, PUSH, anonymous, "refs/heads/*");
    r.setBlock();
    r.setForce(true);
    grant(local, PUSH, devs, "refs/heads/*").setForce(true);

    ProjectControl u = user(devs);
    assertTrue("u can force push", u.controlForRef("refs/heads/master").canForceUpdate());
  }

  public void testUnblockForceWithAllowNoForce_NotPossible() {
    PermissionRule r = grant(local, PUSH, anonymous, "refs/heads/*");
    r.setBlock();
    r.setForce(true);
    grant(local, PUSH, devs, "refs/heads/*");

    ProjectControl u = user(devs);
    assertFalse("u can't force push", u.controlForRef("refs/heads/master").canForceUpdate());
  }

  public void testUnblockMoreSpecificRef_Fails() {
    grant(local, PUSH, anonymous, "refs/heads/*").setBlock();
    grant(local, PUSH, devs, "refs/heads/master");

    ProjectControl u = user(devs);
    assertFalse("u can't push", u.controlForRef("refs/heads/master").canUpdate());
  }

  public void testUnblockLargerScope_Fails() {
    grant(local, PUSH, anonymous, "refs/heads/master").setBlock();
    grant(local, PUSH, devs, "refs/heads/*");

    ProjectControl u = user(devs);
    assertFalse("u can't push", u.controlForRef("refs/heads/master").canUpdate());
  }

  public void testUnblockInLocal_Fails() {
    grant(parent, PUSH, anonymous, "refs/heads/*").setBlock();
    grant(local, PUSH, fixers, "refs/heads/*");

    ProjectControl f = user(fixers);
    assertFalse("u can't push", f.controlForRef("refs/heads/master").canUpdate());
  }

  public void testUnblockInParentBlockInLocal() {
    grant(parent, PUSH, anonymous, "refs/heads/*").setBlock();
    grant(parent, PUSH, devs, "refs/heads/*");
    grant(local, PUSH, devs, "refs/heads/*").setBlock();

    ProjectControl d = user(devs);
    assertFalse("u can't push", d.controlForRef("refs/heads/master").canUpdate());
  }

  public void testUnblockVisibilityByRegisteredUsers() {
    grant(local, READ, anonymous, "refs/heads/*").setBlock();
    grant(local, READ, registered, "refs/heads/*");

    ProjectControl u = user(registered);
    assertTrue("u can read", u.controlForRef("refs/heads/master").isVisibleByRegisteredUsers());
  }

  public void testUnblockInLocalVisibilityByRegisteredUsers_Fails() {
    grant(parent, READ, anonymous, "refs/heads/*").setBlock();
    grant(local, READ, registered, "refs/heads/*");

    ProjectControl u = user(registered);
    assertFalse("u can't read", u.controlForRef("refs/heads/master").isVisibleByRegisteredUsers());
  }

  public void testUnblockForceEditTopicName() {
    grant(local, EDIT_TOPIC_NAME, anonymous, "refs/heads/*").setBlock();
    grant(local, EDIT_TOPIC_NAME, devs, "refs/heads/*").setForce(true);

    ProjectControl u = user(devs);
    assertTrue("u can edit topic name", u.controlForRef("refs/heads/master").canForceEditTopicName());
  }

  public void testUnblockInLocalForceEditTopicName_Fails() {
    grant(parent, EDIT_TOPIC_NAME, anonymous, "refs/heads/*").setBlock();
    grant(local, EDIT_TOPIC_NAME, devs, "refs/heads/*").setForce(true);

    ProjectControl u = user(registered);
    assertFalse("u can't edit topic name", u.controlForRef("refs/heads/master").canForceEditTopicName());
  }

  public void testUnblockRange() {
    grant(local, LABEL + "Code-Review", -1, +1, anonymous, "refs/heads/*").setBlock();
    grant(local, LABEL + "Code-Review", -2, +2, devs, "refs/heads/*");

    ProjectControl u = user(devs);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertTrue("u can vote -2", range.contains(-2));
    assertTrue("u can vote +2", range.contains(2));
  }

  public void testUnblockRangeOnMoreSpecificRef_Fails() {
    grant(local, LABEL + "Code-Review", -1, +1, anonymous, "refs/heads/*").setBlock();
    grant(local, LABEL + "Code-Review", -2, +2, devs, "refs/heads/master");

    ProjectControl u = user(devs);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote +2", range.contains(-2));
  }

  public void testUnblockRangeOnLargerScope_Fails() {
    grant(local, LABEL + "Code-Review", -1, +1, anonymous, "refs/heads/master").setBlock();
    grant(local, LABEL + "Code-Review", -2, +2, devs, "refs/heads/*");

    ProjectControl u = user(devs);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote +2", range.contains(-2));
  }

  public void testUnblockInLocalRange_Fails() {
    grant(parent, LABEL + "Code-Review", -1, 1, anonymous, "refs/heads/*").setBlock();
    grant(local, LABEL + "Code-Review", -2, +2, devs, "refs/heads/*");

    ProjectControl u = user(devs);
    PermissionRange range = u.controlForRef("refs/heads/master").getRange(LABEL + "Code-Review");
    assertFalse("u can't vote -2", range.contains(-2));
    assertFalse("u can't vote 2", range.contains(2));
  }
  // -----------------------------------------------------------------------

  private final Map<Project.NameKey, ProjectState> all;
  private final AllProjectsName allProjectsName = new AllProjectsName("parent");
  private final ProjectCache projectCache;

  private ProjectConfig local;
  private ProjectConfig parent;
  private PermissionCollection.Factory sectionSorter;

  private final AccountGroup.UUID admin = new AccountGroup.UUID("test.admin");
  private final AccountGroup.UUID anonymous = AccountGroup.ANONYMOUS_USERS;
  private final AccountGroup.UUID registered = AccountGroup.REGISTERED_USERS;

  private final AccountGroup.UUID devs = new AccountGroup.UUID("test.devs");
  private final AccountGroup.UUID fixers = new AccountGroup.UUID("test.fixers");

  private final CapabilityControl.Factory capabilityControlFactory;

  public RefControlTest() {
    all = new HashMap<Project.NameKey, ProjectState>();
    projectCache = new ProjectCache() {
      @Override
      public ProjectState getAllProjects() {
        return get(allProjectsName);
      }

      @Override
      public ProjectState get(Project.NameKey projectName) {
        return all.get(projectName);
      }

      @Override
      public ProjectState checkedGet(Project.NameKey projectName) {
        return get(projectName);
      }

      @Override
      public void evict(Project p) {
      }

      @Override
      public void evict(Project.NameKey p) {
      }

      @Override
      public void remove(Project p) {
      }

      @Override
      public Iterable<Project.NameKey> all() {
        return Collections.emptySet();
      }

      @Override
      public Iterable<Project.NameKey> byName(String prefix) {
        return Collections.emptySet();
      }

      @Override
      public void onCreateProject(Project.NameKey newProjectName) {
      }

      @Override
      public Set<AccountGroup.UUID> guessRelevantGroupUUIDs() {
        return Collections.emptySet();
      }
    };

    Injector injector = Guice.createInjector(new FactoryModule() {
      @Override
      protected void configure() {
        bind(Config.class)
            .annotatedWith(GerritServerConfig.class)
            .toInstance(new Config());

        factory(CapabilityControl.Factory.class);
        bind(ProjectCache.class).toInstance(projectCache);
      }
    });
    capabilityControlFactory = injector.getInstance(CapabilityControl.Factory.class);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    parent = new ProjectConfig(new Project.NameKey("parent"));
    parent.createInMemory();

    local = new ProjectConfig(new Project.NameKey("local"));
    local.createInMemory();

    Cache<SectionSortCache.EntryKey, SectionSortCache.EntryVal> c =
        CacheBuilder.newBuilder().build();
    sectionSorter = new PermissionCollection.Factory(new SectionSortCache(c));
  }

  private static void assertOwner(String ref, ProjectControl u) {
    assertTrue("OWN " + ref, u.controlForRef(ref).isOwner());
  }

  private static void assertNotOwner(String ref, ProjectControl u) {
    assertFalse("NOT OWN " + ref, u.controlForRef(ref).isOwner());
  }

  private PermissionRule grant(ProjectConfig project, String permissionName,
      AccountGroup.UUID group, String ref) {
    return grant(project, permissionName, newRule(project, group), ref);
  }

  private PermissionRule grant(ProjectConfig project, String permissionName,
      int min, int max, AccountGroup.UUID group, String ref) {
    PermissionRule rule = newRule(project, group);
    rule.setMin(min);
    rule.setMax(max);
    return grant(project, permissionName, rule, ref);
  }


  private PermissionRule grant(ProjectConfig project, String permissionName,
      PermissionRule rule, String ref) {
    project.getAccessSection(ref, true) //
        .getPermission(permissionName, true) //
        .add(rule);
    return rule;
  }

  private void doNotInherit(ProjectConfig project, String permissionName,
      String ref) {
    project.getAccessSection(ref, true) //
        .getPermission(permissionName, true) //
        .setExclusiveGroup(true);
  }

  private PermissionRule newRule(ProjectConfig project, AccountGroup.UUID groupUUID) {
    GroupReference group = new GroupReference(groupUUID, groupUUID.get());
    group = project.resolve(group);

    return new PermissionRule(group);
  }

  private ProjectControl user(AccountGroup.UUID... memberOf) {
    return user(null, memberOf);
  }

  private ProjectControl user(String name, AccountGroup.UUID... memberOf) {
    String canonicalWebUrl = "http://localhost";

    return new ProjectControl(Collections.<AccountGroup.UUID> emptySet(),
        Collections.<AccountGroup.UUID> emptySet(), projectCache,
        sectionSorter,
        canonicalWebUrl, new MockUser(name, memberOf),
        newProjectState());
  }

  private ProjectState newProjectState() {
    PrologEnvironment.Factory envFactory = null;
    GitRepositoryManager mgr = null;
    ProjectControl.AssistedFactory projectControlFactory = null;
    RulesCache rulesCache = null;
    all.put(local.getProject().getNameKey(), new ProjectState(
        null, projectCache, allProjectsName, projectControlFactory,
        envFactory, mgr, rulesCache, null, local));
    all.put(parent.getProject().getNameKey(), new ProjectState(
        null, projectCache, allProjectsName, projectControlFactory,
        envFactory, mgr, rulesCache, null, parent));
    return all.get(local.getProject().getNameKey());
  }

  private class MockUser extends CurrentUser {
    private final String username;
    private final GroupMembership groups;

    MockUser(String name, AccountGroup.UUID[] groupId) {
      super(RefControlTest.this.capabilityControlFactory);
      username = name;
      ArrayList<AccountGroup.UUID> groupIds = Lists.newArrayList(groupId);
      groupIds.add(registered);
      groupIds.add(anonymous);
      groups = new ListGroupMembership(groupIds);
    }

    @Override
    public GroupMembership getEffectiveGroups() {
      return groups;
    }

    @Override
    public String getUserName() {
      return username;
    }

    @Override
    public Set<Change.Id> getStarredChanges() {
      return Collections.emptySet();
    }

    @Override
    public Collection<AccountProjectWatch> getNotificationFilters() {
      return Collections.emptySet();
    }
  }
}
