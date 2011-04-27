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

import static com.google.gerrit.reviewdb.ApprovalCategory.OWN;
import static com.google.gerrit.reviewdb.ApprovalCategory.READ;
import static com.google.gerrit.reviewdb.ApprovalCategory.SUBMIT;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.reviewdb.RefRight.RefPattern;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import junit.framework.TestCase;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.jgit.lib.Config;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RefControlTest extends TestCase {

  // "Setup" tests ensure that we have working parents, ancestorslines
  // and rightslines

  public void testSetup_Parents() {
    initProjectStates();

    assertTrue("local == 1", projectCache.get(local).getParents().size() == 1);
    assertTrue("parent == 1", projectCache.get(parent).getParents().size() == 1);
    assertTrue("gparent == 0", projectCache.get(gparent).getParents().size() == 0);
    assertTrue("multi == 2", projectCache.get(multi).getParents().size() == 2);
    assertTrue("parentA == 1", projectCache.get(parentA).getParents().size() == 1);
    assertTrue("parentB == 1", projectCache.get(parentB).getParents().size() == 1);
  }

  public void testSetup_AncestorLines() {
    initProjectStates();

    assertTrue("local == 1", 1==projectCache.get(local).getAncestorLines().size());
    assertTrue("parent == 1", 1==projectCache.get(parent).getAncestorLines().size());
    assertTrue("gparent == 1", 1==projectCache.get(gparent).getAncestorLines().size());
    assertTrue("multi == 2", 2==projectCache.get(multi).getAncestorLines().size());
    assertTrue("parentA == 1", 1==projectCache.get(parentA).getAncestorLines().size());
    assertTrue("parentB == 1", 1==projectCache.get(parentB).getAncestorLines().size());
  }

  public void testSetup_AncestorLine() {
    initProjectStates();

    Set<List<Project.NameKey>> lines = projectCache.get(local).getAncestorLines();
    List<Project.NameKey> line = (List<Project.NameKey>) lines.toArray()[0];
    assertTrue("local == 4", 4==line.size());

    lines = projectCache.get(multi).getAncestorLines();
    line = (List<Project.NameKey>) lines.toArray()[0];
    assertTrue("multi0 == 4", 4==line.size());

    line = (List<Project.NameKey>) lines.toArray()[1];
    assertTrue("multi1 == 4", 4==line.size());

    lines = projectCache.get(parent).getAncestorLines();
    line = (List<Project.NameKey>) lines.toArray()[0];
    assertTrue("parent == 3", 3==line.size());

    lines = projectCache.get(parentA).getAncestorLines();
    line = (List<Project.NameKey>) lines.toArray()[0];
    assertTrue("parentA == 3", 3==line.size());

    lines = projectCache.get(parentB).getAncestorLines();
    line = (List<Project.NameKey>) lines.toArray()[0];
    assertTrue("parentB == 3", 3==line.size());

    lines = projectCache.get(gparent).getAncestorLines();
    line = (List<Project.NameKey>) lines.toArray()[0];
    assertTrue("gparent == 2", 2==line.size());
  }

  public void testSetup_InheritedLines() {
    grant(local, READ, registered, "refs/*", 1);
    grant(multi, READ, registered, "refs/*", 1);
    grant(parent, READ, registered, "refs/*", 1);
    grant(parentA, READ, registered, "refs/*", 1);
    grant(parentB, READ, registered, "refs/*", 1);
    grant(gparent, READ, registered, "refs/*", 1);

    initProjectStates();

    assertTrue("local == 1", 1==projectCache.get(local).getInheritedRightsLines().size());
    assertTrue("parent == 1", 1==projectCache.get(parent).getInheritedRightsLines().size());
    assertTrue("gparent == 1", 1==projectCache.get(gparent).getInheritedRightsLines().size());
    assertTrue("multi == 2", 2==projectCache.get(multi).getInheritedRightsLines().size());
    assertTrue("parentA == 1", 1==projectCache.get(parentA).getInheritedRightsLines().size());
    assertTrue("parentB == 1", 1==projectCache.get(parentB).getInheritedRightsLines().size());
  }

  public void testSetup_InheritedLine() {
    grant(local, READ, registered, "refs/*", 1);
    grant(local, READ, devs, "refs/*", 2);
    grant(local, READ, fixers, "refs/*", 3);
    grant(local, READ, anonymous, "refs/*", 0);
    grant(multi, READ, registered, "refs/*", 1);
    grant(multi, READ, devs, "refs/*", 2);
    grant(multi, READ, fixers, "refs/*", 3);
    grant(multi, READ, anonymous, "refs/*", 0);
    grant(parent, READ, registered, "refs/*", 1);
    grant(parent, READ, devs, "refs/*", 2);
    grant(parentA, READ, registered, "refs/*", 1);
    grant(parentA, READ, devs, "refs/*", 2);
    grant(parentB, READ, registered, "refs/*", 1);
    grant(parentB, READ, devs, "refs/*", 2);
    grant(gparent, READ, registered, "refs/*", 1);

    initProjectStates();

    Set<List<RefRight>> lines = projectCache.get(local).getInheritedRightsLines();
    List<RefRight> line = (List<RefRight>) lines.toArray()[0];
    assertTrue("local == 7", 7==line.size());

    lines = projectCache.get(multi).getInheritedRightsLines();
    line = (List<RefRight>) lines.toArray()[0];
    assertTrue("multi0 == 7", 7==line.size());
    line = (List<RefRight>) lines.toArray()[1];
    assertTrue("multi1 == 7", 7==line.size());

    lines = projectCache.get(parent).getInheritedRightsLines();
    line = (List<RefRight>) lines.toArray()[0];
    assertTrue("parent == 3", 3==line.size());

    lines = projectCache.get(parentA).getInheritedRightsLines();
    line = (List<RefRight>) lines.toArray()[0];
    assertTrue("parentA == 3", 3==line.size());

    lines = projectCache.get(parentB).getInheritedRightsLines();
    line = (List<RefRight>) lines.toArray()[0];
    assertTrue("parentB == 3", 3==line.size());

    lines = projectCache.get(gparent).getInheritedRightsLines();
    line = (List<RefRight>) lines.toArray()[0];
    assertTrue("gparent == 1", 1==line.size());
  }

  public void testOwnerProject() {
    grant(local, OWN, admin, "refs/*", 1);

    ProjectControl uBlah = user(devs);
    ProjectControl uAdmin = user(devs, admin);
    assertFalse("not owner", uBlah.isOwner());
    assertTrue("is owner", uAdmin.isOwner());
  }

  public void testBranchDelegation1() {
    grant(local, OWN, admin, "refs/*", 1);
    grant(local, OWN, devs, "refs/heads/x/*", 1);

    ProjectControl uDev = user(devs);
    assertFalse("not owner", uDev.isOwner());
    assertTrue("owns ref", uDev.isOwnerAnyRef());

    assertOwner("refs/heads/x/*", uDev);
    assertOwner("refs/heads/x/y", uDev);
    assertOwner("refs/heads/x/y/*", uDev);

    assertNotOwner("refs/*", uDev);
    assertNotOwner("refs/heads/master", uDev);
    assertNotOwner("refs/heads/master/x", uDev);

    // Crazy wild stuff
    assertNotOwner("refs/heads/*/x", uDev);
    assertNotOwner("refs/*/x", uDev);
    assertNotOwner("refs/heads/y/../x/*", uDev);
  }

  public void testBranchDelegation2() {
    grant(local, OWN, admin, "refs/*", 1);
    grant(local, OWN, devs, "refs/heads/x/*", 1);
    grant(local, OWN, fixers, "-refs/heads/x/y/*", 1);

    ProjectControl uAdmin = user(devs, admin);
    assertTrue("owns ref", uAdmin.isOwner());
    assertOwner("refs/heads/x/y/*", uAdmin);

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

    assertOwner("refs/heads/x/y/bar", uFix);
    assertOwner("refs/heads/x/y/*", uFix);
    assertNotOwner("refs/heads/x/*", uFix);
    assertNotOwner("refs/heads/x/y", uFix);
    assertNotOwner("refs/*", uFix);
    assertNotOwner("refs/heads/master", uFix);
  }

  public void testInheritRead_SingleBranchDeniesUpload() {
    grant(parent, READ, registered, "refs/*", 1, 2);
    grant(local, READ, registered, "-refs/heads/foobar", 1);

    ProjectControl u = user();
    assertTrue("can upload", u.canPushToAtLeastOneRef());

    assertTrue("can upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());

    assertFalse("deny refs/heads/foobar", //
        u.controlForRef("refs/heads/foobar").canUpload());
  }

  public void testInheritRead_GParent() {
    grant(gparent, READ, registered, "refs/*", 1);

    ProjectControl u = user();
    assertTrue("can read", u.isVisible());
  }

  public void testInheritRead_WildProject() {
    grant(wildProject, READ, registered, "refs/*", 1);

    ProjectControl u = user();
    assertTrue("can read", u.isVisible());
  }

  public void testInheritRead_SingleBranchDoesNotOverrideInherited() {
    grant(parent, READ, registered, "refs/*", 1, 2);
    grant(local, READ, registered, "refs/heads/foobar", 1);

    ProjectControl u = user();
    assertTrue("can upload", u.canPushToAtLeastOneRef());

    assertTrue("can upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());

    assertTrue("can upload refs/heads/foobar", //
        u.controlForRef("refs/heads/foobar").canUpload());
  }

  public void testInheritRead_OverrideWithDeny() {
    grant(parent, READ, registered, "refs/*", 1);
    grant(local, READ, registered, "refs/*", 0);

    ProjectControl u = user();
    assertFalse("can't read", u.isVisible());
  }

  public void testInheritRead_OverrideGParentWithDeny() {
    grant(gparent, READ, registered, "refs/*", 1);
    grant(local, READ, registered, "refs/*", 0);

    ProjectControl u = user();
    assertFalse("can't read", u.isVisible());
  }

  public void testInheritRead_ParentOverridesGParentWithDeny() {
    grant(gparent, READ, registered, "refs/*", 1);
    grant(parent, READ, registered, "refs/*", 0);

    ProjectControl u = user();
    assertFalse("can't read", u.isVisible());
  }

  public void testInheritRead_AllowParentOverrideGParentOverrideParentWithDeny() {
    grant(gparent, READ, registered, "refs/*", 2);
    grant(parent, READ, registered, "refs/*", 1);
    grant(local, READ, registered, "refs/*", 0);

    ProjectControl u = user();
    assertFalse("can't read", u.isVisible());
  }

  public void testInheritRead_AppendWithDenyOfRef() {
    grant(parent, READ, registered, "refs/*", 1);
    grant(local, READ, registered, "refs/heads/*", 0);

    ProjectControl u = user();
    assertTrue("can read", u.isVisible());
    assertTrue("can read", u.controlForRef("refs/master").isVisible());
    assertTrue("can read", u.controlForRef("refs/tags/foobar").isVisible());
    assertTrue("no master", u.controlForRef("refs/heads/master").isVisible());
  }

  public void testInheritRead_OverridesAndDeniesOfRef() {
    grant(parent, READ, registered, "refs/*", 1);
    grant(local, READ, registered, "refs/*", 0);
    grant(local, READ, registered, "refs/heads/*", -1, 1);

    ProjectControl u = user();
    assertTrue("can read", u.isVisible());
    assertFalse("can't read", u.controlForRef("refs/foobar").isVisible());
    assertFalse("can't read", u.controlForRef("refs/tags/foobar").isVisible());
    assertTrue("can read", u.controlForRef("refs/heads/foobar").isVisible());
  }

  public void testInheritSubmit_OverridesAndDeniesOfRef() {
    grant(parent, SUBMIT, registered, "refs/*", 1);
    grant(local, SUBMIT, registered, "refs/*", 0);
    grant(local, SUBMIT, registered, "refs/heads/*", -1, 1);

    ProjectControl u = user();
    assertFalse("can't submit", u.controlForRef("refs/foobar").canSubmit());
    assertFalse("can't submit", u.controlForRef("refs/tags/foobar").canSubmit());
    assertTrue("can submit", u.controlForRef("refs/heads/foobar").canSubmit());
  }

  public void testCannotUploadToAnyRef() {
    grant(parent, READ, registered, "refs/*", 1);
    grant(local, READ, devs, "refs/heads/*", 1, 2);

    ProjectControl u = user();
    assertFalse("cannot upload", u.canPushToAtLeastOneRef());
    assertFalse("cannot upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());
  }

  public void testMultiRead_FromSingleParent() {
    grant(parentA, READ, devs, "refs/*", 1);
    grant(parentB, READ, fixers, "refs/*", 1);

    ProjectControl uDev = multi(devs);
    ProjectControl uFix = multi(fixers);
    assertTrue("can read", uDev.isVisible());
    assertTrue("can read", uFix.isVisible());
  }

  public void testMultiRead_HighestOfSiblings() {
    grant(parentA, READ, devs, "refs/*", 0);
    grant(parentB, READ, devs, "refs/*", 1);
    grant(parentA, READ, fixers, "refs/*", 0);
    grant(parentB, READ, fixers, "refs/*", 1);

    ProjectControl uDev = multi(devs);
    ProjectControl uFix = multi(fixers);
    assertTrue("can read", uDev.isVisible());
    assertTrue("can read", uFix.isVisible());
  }

  public void testMultiRead_SingleParentDoesNotOverrideGParent() {
    grant(gparent, READ, devs, "refs/*", 1);
    grant(parentA, READ, devs, "refs/*", 0);
    grant(gparent, READ, fixers, "refs/*", 1);
    grant(parentB, READ, fixers, "refs/*", 0);

    ProjectControl uDev = multi(devs);
    ProjectControl uFix = multi(fixers);
    assertTrue("can read", uDev.isVisible());
    assertTrue("can read", uFix.isVisible());
  }

  public void testMultiRead_GParentOverridenByParents() {
    grant(gparent, READ, registered, "refs/*", 1);
    grant(parentA, READ, registered, "refs/*", 0);
    grant(parentB, READ, registered, "refs/*", 0);

    ProjectControl u = multi();
    assertFalse("can't read", u.isVisible());
  }

  public void testMultiRead_SingleParentDoesNotOverrideWildProject() {
    grant(wildProject, READ, devs, "refs/*", 1);
    grant(parentA, READ, devs, "refs/*", 0);
    grant(wildProject, READ, fixers, "refs/*", 1);
    grant(parentB, READ, fixers, "refs/*", 0);

    ProjectControl uDev = multi(devs);
    ProjectControl uFix = multi(fixers);
    assertTrue("can read", uDev.isVisible());
    assertTrue("can read", uFix.isVisible());
  }

  public void testMultiRead_ParentDoesOverrideWildProject() {
    grant(wildProject, READ, registered, "refs/*", 1);
    grant(gparent, READ, registered, "refs/*", 0);

    ProjectControl u = multi();
    assertFalse("can't read", u.isVisible());
  }

  // -----------------------------------------------------------------------

  private final Project.NameKey wildProject = new Project.NameKey("-- All Projects --");

  private final Project.NameKey local = new Project.NameKey("single");
  private final Project.NameKey multi = new Project.NameKey("multi");

  private final Project.NameKey parent = new Project.NameKey("parent");
  private final Project.NameKey parentA = new Project.NameKey("parentA");
  private final Project.NameKey parentB = new Project.NameKey("parentB");

  private final Project.NameKey gparent = new Project.NameKey("gparent");

  private final AccountGroup.Id admin = new AccountGroup.Id(1);
  private final AccountGroup.Id anonymous = new AccountGroup.Id(2);
  private final AccountGroup.Id registered = new AccountGroup.Id(3);
  private final AccountGroup.Id owners = new AccountGroup.Id(4);

  private final AccountGroup.Id devs = new AccountGroup.Id(5);
  private final AccountGroup.Id fixers = new AccountGroup.Id(6);

  private final SystemConfig systemConfig;
  private final AuthConfig authConfig;
  private final AnonymousUser anonymousUser;

  private final ProjectControl.AssistedFactory projectControlFactory = null;


  public RefControlTest() {
    systemConfig = SystemConfig.create();
    systemConfig.adminGroupId = admin;
    systemConfig.anonymousGroupId = anonymous;
    systemConfig.registeredGroupId = registered;
    systemConfig.ownerGroupId = owners;
    systemConfig.batchUsersGroupId = anonymous;
    try {
      byte[] bin = "abcdefghijklmnopqrstuvwxyz".getBytes("UTF-8");
      systemConfig.registerEmailPrivateKey = Base64.encodeBase64String(bin);
    } catch (UnsupportedEncodingException err) {
      throw new RuntimeException("Cannot encode key", err);
    }

    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Config.class) //
            .annotatedWith(GerritServerConfig.class) //
            .toInstance(new Config());

        bind(SystemConfig.class).toInstance(systemConfig);
        bind(AuthConfig.class);
        bind(AnonymousUser.class);
      }
    });
    authConfig = injector.getInstance(AuthConfig.class);
    anonymousUser = injector.getInstance(AnonymousUser.class);
  }

  private Map<Project.NameKey,List<RefRight>> rightsByProject;
  private HashProjectCacheImpl projectCache;
  private boolean initedProjectStates;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initedProjectStates = false;
    projectCache = new HashProjectCacheImpl();
    rightsByProject = new HashMap<Project.NameKey,List<RefRight>>();

    rightsByProject.put(wildProject, new ArrayList<RefRight>());

    rightsByProject.put(local, new ArrayList<RefRight>());
    rightsByProject.put(parent, new ArrayList<RefRight>());
    rightsByProject.put(gparent, new ArrayList<RefRight>());

    rightsByProject.put(multi, new ArrayList<RefRight>());
    rightsByProject.put(parentA, new ArrayList<RefRight>());
    rightsByProject.put(parentB, new ArrayList<RefRight>());
  }

  private static void assertOwner(String ref, ProjectControl u) {
    assertTrue("OWN " + ref, u.controlForRef(ref).isOwner());
  }

  private static void assertNotOwner(String ref, ProjectControl u) {
    assertFalse("NOT OWN " + ref, u.controlForRef(ref).isOwner());
  }

  private void grant(Project.NameKey project, ApprovalCategory.Id categoryId,
      AccountGroup.Id group, String ref, int maxValue) {
    grant(project, categoryId, group, ref, maxValue, maxValue);
  }

  private void grant(Project.NameKey project, ApprovalCategory.Id categoryId, AccountGroup.Id group,
      String ref, int minValue, int maxValue) {
    if (initedProjectStates) {
      fail("Project States are already inited, cannot modify " + project);
    } else {
      RefRight right =
          new RefRight(new RefRight.Key(project, new RefPattern(ref),
              categoryId, group));
      right.setMinValue((short) minValue);
      right.setMaxValue((short) maxValue);

      List<RefRight> rights = rightsByProject.get(project);
      if (rights == null) {
        fail("Unknown project key: " + project);
      } else {
        rights.add(right);
      }
    }
  }

  private ProjectControl user(AccountGroup.Id... memberOf) {
    return controlFor(local, memberOf);
  }

  private ProjectControl multi(AccountGroup.Id... memberOf) {
    return controlFor(multi, memberOf);
  }

  private ProjectControl controlFor(Project.NameKey project,
      AccountGroup.Id... memberOf) {
    RefControl.Factory refControlFactory = new RefControl.Factory() {
      @Override
      public RefControl create(final ProjectControl projectControl, final String ref) {
        return new RefControl(systemConfig, projectControl, ref);
      }
    };
    initProjectStates();
    return new ProjectControl(Collections.<AccountGroup.Id> emptySet(),
        Collections.<AccountGroup.Id> emptySet(), refControlFactory,
        new MockUser(memberOf), projectCache.get(project));
  }

  private void initProjectStates() {
    if (! initedProjectStates) {
      initedProjectStates = true;
      initProjectState(wildProject);

      initProjectState(gparent);

      initProjectState(parent, gparent);
      initProjectState(parentA, gparent);
      initProjectState(parentB, gparent);

      initProjectState(local, parent);
      initProjectState(multi, parentA, parentB);
    }
  }

  private void initProjectState(Project.NameKey child, Project.NameKey... parents) {
    Project project = new Project(child);
    Set<Project.NameKey> pKeys = new HashSet<Project.NameKey>(Arrays.asList(parents));
    projectCache.put(child,
        new ProjectState(anonymousUser, projectCache, wildProject,
            projectControlFactory, project, pKeys, rightsByProject.get(child))
      );
  }

  private class MockUser extends CurrentUser {
    private final Set<AccountGroup.Id> groups;

    MockUser(AccountGroup.Id[] groupId) {
      super(AccessPath.UNKNOWN, RefControlTest.this.authConfig);
      groups = new HashSet<AccountGroup.Id>(Arrays.asList(groupId));
      groups.add(registered);
      groups.add(anonymous);
    }

    @Override
    public Set<AccountGroup.Id> getEffectiveGroups() {
      return groups;
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

  private static class HashProjectCacheImpl implements ProjectCache {
    private final Map<Project.NameKey, ProjectState> byName;

    public HashProjectCacheImpl() {
      byName = new HashMap<Project.NameKey, ProjectState>();
    }

    public ProjectState get(Project.NameKey projectName) {
      return byName.get(projectName);
    }

    public void put(Project.NameKey projectName, ProjectState ps) {
      byName.put(projectName, ps);
    }

    public void evict(Project p) {
    }

    public void evictAll() {
    }
  }
}
