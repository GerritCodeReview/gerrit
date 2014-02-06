// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testutil.FakeAccountCache;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Providers;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Util {
  public static AccountGroup.UUID ADMIN = new AccountGroup.UUID("test.admin");
  public static AccountGroup.UUID DEVS = new AccountGroup.UUID("test.devs");

  public static final LabelType CR = category("Code-Review",
      value(2, "Looks good to me, approved"),
      value(1, "Looks good to me, but someone else must approve"),
      value(0, "No score"),
      value(-1, "I would prefer this is not merged as is"),
      value(-2, "This shall not be merged"));

  public static LabelValue value(int value, String text) {
    return new LabelValue((short) value, text);
  }

  public static LabelType category(String name, LabelValue... values) {
    return new LabelType(name, Arrays.asList(values));
  }

  static public PermissionRule newRule(ProjectConfig project,
      AccountGroup.UUID groupUUID) {
    GroupReference group = new GroupReference(groupUUID, groupUUID.get());
    group = project.resolve(group);

    return new PermissionRule(group);
  }

  static public PermissionRule grant(ProjectConfig project,
      String permissionName, int min, int max, AccountGroup.UUID group,
      String ref) {
    PermissionRule rule = newRule(project, group);
    rule.setMin(min);
    rule.setMax(max);
    return grant(project, permissionName, rule, ref);
  }

  static public PermissionRule grant(ProjectConfig project,
      String permissionName, AccountGroup.UUID group, String ref) {
    return grant(project, permissionName, newRule(project, group), ref);
  }

  static public void doNotInherit(ProjectConfig project, String permissionName,
      String ref) {
    project.getAccessSection(ref, true) //
        .getPermission(permissionName, true) //
        .setExclusiveGroup(true);
  }

  static private PermissionRule grant(ProjectConfig project,
      String permissionName, PermissionRule rule, String ref) {
    project.getAccessSection(ref, true) //
        .getPermission(permissionName, true) //
        .add(rule);
    return rule;
  }

  private final Map<Project.NameKey, ProjectState> all;
  private final ProjectCache projectCache;
  private final CapabilityControl.Factory capabilityControlFactory;
  private final ChangeControl.AssistedFactory changeControlFactory;
  private final PermissionCollection.Factory sectionSorter;
  private final GitRepositoryManager repoManager;

  private final AllProjectsName allProjectsName = new AllProjectsName("parent");
  private final ProjectConfig parent = new ProjectConfig(allProjectsName);

  public Util() {
    all = new HashMap<Project.NameKey, ProjectState>();
    repoManager = new InMemoryRepositoryManager();
    try {
      Repository repo = repoManager.createRepository(allProjectsName);
      parent.load(repo);
      parent.getLabelSections().put(CR.getName(), CR);
      add(parent);
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException(e);
    }

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

      @Override
      public ProjectState checkedGet(NameKey projectName) throws IOException {
        return all.get(projectName);
      }

      @Override
      public void evict(NameKey p) {
      }
    };

    Injector injector = Guice.createInjector(new FactoryModule() {
      @Override
      protected void configure() {
        bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(
            new Config());
        bind(ReviewDb.class).toProvider(Providers.<ReviewDb> of(null));
        bind(GitRepositoryManager.class).toInstance(repoManager);
        bind(PatchListCache.class)
            .toProvider(Providers.<PatchListCache> of(null));

        factory(CapabilityControl.Factory.class);
        factory(ChangeControl.AssistedFactory.class);
        factory(ChangeData.Factory.class);
        bind(ProjectCache.class).toInstance(projectCache);
        bind(AccountCache.class).toInstance(new FakeAccountCache());
        bind(GroupBackend.class).to(SystemGroupBackend.class);
        bind(String.class).annotatedWith(CanonicalWebUrl.class)
            .toProvider(CanonicalWebUrlProvider.class);
        bind(String.class).annotatedWith(AnonymousCowardName.class)
            .toProvider(AnonymousCowardNameProvider.class);
      }
    });

    Cache<SectionSortCache.EntryKey, SectionSortCache.EntryVal> c =
        CacheBuilder.newBuilder().build();
    sectionSorter = new PermissionCollection.Factory(new SectionSortCache(c));
    capabilityControlFactory =
        injector.getInstance(CapabilityControl.Factory.class);
    changeControlFactory =
      injector.getInstance(ChangeControl.AssistedFactory.class);
  }

  public ProjectConfig getParentConfig() {
    return this.parent;
  }

  public void add(ProjectConfig pc) {
    PrologEnvironment.Factory envFactory = null;
    ProjectControl.AssistedFactory projectControlFactory = null;
    RulesCache rulesCache = null;
    SitePaths sitePaths = null;
    List<CommentLinkInfo> commentLinks = null;

    try {
      repoManager.createRepository(pc.getProject().getNameKey());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    all.put(pc.getProject().getNameKey(), new ProjectState(sitePaths,
        projectCache, allProjectsName, projectControlFactory, envFactory,
        repoManager, rulesCache, commentLinks, pc));
  }

  public ProjectControl user(ProjectConfig local, AccountGroup.UUID... memberOf) {
    return user(local, null, memberOf);
  }

  public ProjectControl user(ProjectConfig local, String name,
      AccountGroup.UUID... memberOf) {
    String canonicalWebUrl = "http://localhost";

    return new ProjectControl(Collections.<AccountGroup.UUID> emptySet(),
        Collections.<AccountGroup.UUID> emptySet(), projectCache,
        sectionSorter, repoManager, changeControlFactory, canonicalWebUrl,
        new MockUser(name, memberOf), newProjectState(local));
  }

  private ProjectState newProjectState(ProjectConfig local) {
    add(local);
    return all.get(local.getProject().getNameKey());
  }

  private class MockUser extends CurrentUser {
    private final String username;
    private final GroupMembership groups;

    MockUser(String name, AccountGroup.UUID[] groupId) {
      super(capabilityControlFactory);
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
