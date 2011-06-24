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

import static com.google.gerrit.common.data.Permission.OWNER;
import static com.google.gerrit.common.data.Permission.PUSH;
import static com.google.gerrit.common.data.Permission.READ;
import static com.google.gerrit.common.data.Permission.SUBMIT;

import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.cache.ConcurrentHashMapCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.assistedinject.FactoryProvider;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

  public void testInheritRead_OverrideWithDeny() {
    grant(parent, READ, registered, "refs/*");
    grant(local, READ, registered, "refs/*").setDeny(true);

    ProjectControl u = user();
    assertFalse("can't read", u.isVisible());
  }

  public void testInheritRead_AppendWithDenyOfRef() {
    grant(parent, READ, registered, "refs/*");
    grant(local, READ, registered, "refs/heads/*").setDeny(true);

    ProjectControl u = user();
    assertTrue("can read", u.isVisible());
    assertTrue("can read", u.controlForRef("refs/master").isVisible());
    assertTrue("can read", u.controlForRef("refs/tags/foobar").isVisible());
    assertTrue("no master", u.controlForRef("refs/heads/master").isVisible());
  }

  public void testInheritRead_OverridesAndDeniesOfRef() {
    grant(parent, READ, registered, "refs/*");
    grant(local, READ, registered, "refs/*").setDeny(true);
    grant(local, READ, registered, "refs/heads/*");

    ProjectControl u = user();
    assertTrue("can read", u.isVisible());
    assertFalse("can't read", u.controlForRef("refs/foobar").isVisible());
    assertFalse("can't read", u.controlForRef("refs/tags/foobar").isVisible());
    assertTrue("can read", u.controlForRef("refs/heads/foobar").isVisible());
  }

  public void testInheritSubmit_OverridesAndDeniesOfRef() {
    grant(parent, SUBMIT, registered, "refs/*");
    grant(local, SUBMIT, registered, "refs/*").setDeny(true);
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
      public void evict(Project p) {
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
    };

    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Config.class) //
            .annotatedWith(GerritServerConfig.class) //
            .toInstance(new Config());

        bind(CapabilityControl.Factory.class)
            .toProvider(FactoryProvider.newFactory(
              CapabilityControl.Factory.class,
              CapabilityControl.class));

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
    local.getProject().setParentName(parent.getProject().getName());

    sectionSorter =
        new PermissionCollection.Factory(
            new SectionSortCache(
                new ConcurrentHashMapCache<SectionSortCache.EntryKey, SectionSortCache.EntryVal>()));
  }

  private static void assertOwner(String ref, ProjectControl u) {
    assertTrue("OWN " + ref, u.controlForRef(ref).isOwner());
  }

  private static void assertNotOwner(String ref, ProjectControl u) {
    assertFalse("NOT OWN " + ref, u.controlForRef(ref).isOwner());
  }

  private PermissionRule grant(ProjectConfig project, String permissionName,
      AccountGroup.UUID group, String ref) {
    PermissionRule rule = newRule(project, group);
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
    SchemaFactory<ReviewDb> schema = null;
    GroupCache groupCache = null;
    String canonicalWebUrl = "http://localhost";

    return new ProjectControl(Collections.<AccountGroup.UUID> emptySet(),
        Collections.<AccountGroup.UUID> emptySet(), schema, groupCache,
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
        projectCache, allProjectsName, projectControlFactory,
        envFactory, mgr, rulesCache, local));
    all.put(parent.getProject().getNameKey(), new ProjectState(
        projectCache, allProjectsName, projectControlFactory,
        envFactory, mgr, rulesCache, parent));
    return all.get(local.getProject().getNameKey());
  }

  private class MockUser extends CurrentUser {
    private final String username;
    private final Set<AccountGroup.UUID> groups;

    MockUser(String name, AccountGroup.UUID[] groupId) {
      super(RefControlTest.this.capabilityControlFactory, AccessPath.UNKNOWN);
      username = name;
      groups = new HashSet<AccountGroup.UUID>(Arrays.asList(groupId));
      groups.add(registered);
      groups.add(anonymous);
    }

    @Override
    public Set<AccountGroup.UUID> getEffectiveGroups() {
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
