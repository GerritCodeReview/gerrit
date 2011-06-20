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
import com.google.gerrit.server.git.RegExpCacheImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import junit.framework.TestCase;

import net.sf.ehcache.Cache;


import org.apache.commons.codec.binary.Base64;
import org.eclipse.jgit.lib.Config;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RefControlTest extends TestCase {
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
  }

  public void testBranchDelegation2() {
    grant(local, OWN, admin, "refs/*", 1);
    grant(local, OWN, devs, "refs/heads/x/*", 1);
    grant(local, OWN, fixers, "-refs/heads/x/y/*", 1);

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
    grant(parent, READ, registered, "refs/*", 1, 2);
    grant(local, READ, registered, "-refs/heads/foobar", 1);

    ProjectControl u = user();
    assertTrue("can upload", u.canPushToAtLeastOneRef());

    assertTrue("can upload refs/heads/master", //
        u.controlForRef("refs/heads/master").canUpload());

    assertFalse("deny refs/heads/foobar", //
        u.controlForRef("refs/heads/foobar").canUpload());
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


  // -----------------------------------------------------------------------

  private final Project.NameKey local = new Project.NameKey("test");
  private final Project.NameKey parent = new Project.NameKey("parent");
  private final AccountGroup.Id admin = new AccountGroup.Id(1);
  private final AccountGroup.Id anonymous = new AccountGroup.Id(2);
  private final AccountGroup.Id registered = new AccountGroup.Id(3);
  private final AccountGroup.Id owners = new AccountGroup.Id(4);

  private final AccountGroup.Id devs = new AccountGroup.Id(5);
  private final AccountGroup.Id fixers = new AccountGroup.Id(6);

  private final SystemConfig systemConfig;
  private final AuthConfig authConfig;
  private final AnonymousUser anonymousUser;

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

  private List<RefRight> localRights;
  private List<RefRight> inheritedRights;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    localRights = new ArrayList<RefRight>();
    inheritedRights = new ArrayList<RefRight>();
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
    RefRight right =
        new RefRight(new RefRight.Key(project, new RefPattern(ref),
            categoryId, group));
    right.setMinValue((short) minValue);
    right.setMaxValue((short) maxValue);

    if (project == parent) {
      inheritedRights.add(right);
    } else if (project == local) {
      localRights.add(right);
    } else {
      fail("Unknown project key: " + project);
    }
  }

  private ProjectControl user(AccountGroup.Id... memberOf) {
    RefControl.Factory refControlFactory = new RefControl.Factory() {
      @Override
      public RefControl create(final ProjectControl projectControl, final String ref) {
        return new RefControl(systemConfig, projectControl, ref, null);
      }
    };
    return new ProjectControl(Collections.<AccountGroup.Id> emptySet(),
        Collections.<AccountGroup.Id> emptySet(), refControlFactory,
        new MockUser(memberOf), newProjectState());
  }

  private ProjectState newProjectState() {
    ProjectCache projectCache = null;
    Project.NameKey wildProject = new Project.NameKey("-- All Projects --");
    ProjectControl.AssistedFactory projectControlFactory = null;
    ProjectState ps =
        new ProjectState(anonymousUser, projectCache, wildProject,
            projectControlFactory, new Project(parent), localRights);
    ps.setInheritedRights(inheritedRights);
    return ps;
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
}
