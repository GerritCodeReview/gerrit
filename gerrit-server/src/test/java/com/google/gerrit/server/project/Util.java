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
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.FakeRealm;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.gerrit.server.change.MergeabilityCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.DisableReverseDnsLookup;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testutil.FakeAccountCache;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
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
  public static final AccountGroup.UUID ADMIN = new AccountGroup.UUID("test.admin");
  public static final AccountGroup.UUID DEVS = new AccountGroup.UUID("test.devs");

  public static final LabelType codeReview() {
    return category("Code-Review",
        value(2, "Looks good to me, approved"),
        value(1, "Looks good to me, but someone else must approve"),
        value(0, "No score"),
        value(-1, "I would prefer this is not merged as is"),
        value(-2, "This shall not be merged"));
  }

  public static final LabelType patchSetLock() {
    LabelType label = category("Patch-Set-Lock",
        value(1, "Patch Set Locked"),
        value(0, "Patch Set Unlocked"));
    label.setFunctionName("PatchSetLock");
    return label;
  }

  public static LabelValue value(int value, String text) {
    return new LabelValue((short) value, text);
  }

  public static LabelType category(String name, LabelValue... values) {
    return new LabelType(name, Arrays.asList(values));
  }

  public static PermissionRule newRule(ProjectConfig project,
      AccountGroup.UUID groupUUID) {
    GroupReference group = new GroupReference(groupUUID, groupUUID.get());
    group = project.resolve(group);

    return new PermissionRule(group);
  }

  public static PermissionRule allow(ProjectConfig project,
      String permissionName, int min, int max, AccountGroup.UUID group,
      String ref) {
    PermissionRule rule = newRule(project, group);
    rule.setMin(min);
    rule.setMax(max);
    return grant(project, permissionName, rule, ref);
  }

  public static PermissionRule block(ProjectConfig project,
      String permissionName, int min, int max, AccountGroup.UUID group,
      String ref) {
    PermissionRule rule = newRule(project, group);
    rule.setMin(min);
    rule.setMax(max);
    PermissionRule r = grant(project, permissionName, rule, ref);
    r.setBlock();
    return r;
  }

  public static PermissionRule allow(ProjectConfig project,
      String permissionName, AccountGroup.UUID group, String ref) {
    return grant(project, permissionName, newRule(project, group), ref);
  }

  public static PermissionRule allow(ProjectConfig project,
      String capabilityName, AccountGroup.UUID group) {
    PermissionRule rule = newRule(project, group);
    project.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true)
        .getPermission(capabilityName, true)
        .add(rule);
      if (GlobalCapability.hasRange(capabilityName)) {
        PermissionRange.WithDefaults range =
            GlobalCapability.getRange(capabilityName);
        if (range != null) {
          rule.setRange(range.getDefaultMin(), range.getDefaultMax());
        }
      }
    return rule;
  }

  public static PermissionRule remove(ProjectConfig project,
      String capabilityName, AccountGroup.UUID group) {
    PermissionRule rule = newRule(project, group);
    project.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true)
        .getPermission(capabilityName, true)
        .remove(rule);
    return rule;
  }

  public static PermissionRule block(ProjectConfig project,
      String capabilityName, AccountGroup.UUID group) {
    PermissionRule rule = newRule(project, group);
    project.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true)
        .getPermission(capabilityName, true)
        .add(rule);
    return rule;
  }

  public static PermissionRule block(ProjectConfig project,
      String permissionName, AccountGroup.UUID group, String ref) {
    PermissionRule r = grant(project, permissionName, newRule(project, group), ref);
    r.setBlock();
    return r;
  }

  public static PermissionRule blockLabel(ProjectConfig project,
      String labelName, AccountGroup.UUID group, String ref) {
    PermissionRule r =
        grant(project, Permission.LABEL + labelName, newRule(project, group),
            ref);
    r.setBlock();
    r.setRange(-1, 1);
    return r;
  }

  public static PermissionRule deny(ProjectConfig project,
      String permissionName, AccountGroup.UUID group, String ref) {
    PermissionRule r = grant(project, permissionName, newRule(project, group), ref);
    r.setDeny();
    return r;
  }

  public static void doNotInherit(ProjectConfig project, String permissionName,
      String ref) {
    project.getAccessSection(ref, true) //
        .getPermission(permissionName, true) //
        .setExclusiveGroup(true);
  }

  private static PermissionRule grant(ProjectConfig project,
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
  private final InMemoryRepositoryManager repoManager;

  private final AllProjectsName allProjectsName =
      new AllProjectsName("All-Projects");
  private final ProjectConfig allProjects;

  public Util() {
    all = new HashMap<>();
    repoManager = new InMemoryRepositoryManager();
    try {
      Repository repo = repoManager.createRepository(allProjectsName);
      allProjects = new ProjectConfig(new Project.NameKey(allProjectsName.get()));
      allProjects.load(repo);
      LabelType cr = codeReview();
      allProjects.getLabelSections().put(cr.getName(), cr);
      add(allProjects);
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException(e);
    }

    projectCache = new ProjectCache() {
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
      public ProjectState checkedGet(Project.NameKey projectName)
          throws IOException {
        return all.get(projectName);
      }

      @Override
      public void evict(Project.NameKey p) {
      }
    };

    Injector injector = Guice.createInjector(new FactoryModule() {
      @SuppressWarnings({"rawtypes", "unchecked"})
      @Override
      protected void configure() {
        Provider nullProvider = Providers.of(null);
        bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(
            new Config());
        bind(ReviewDb.class).toProvider(nullProvider);
        bind(GitRepositoryManager.class).toInstance(repoManager);
        bind(PatchListCache.class).toProvider(nullProvider);
        bind(Realm.class).to(FakeRealm.class);

        factory(CapabilityControl.Factory.class);
        factory(ChangeControl.AssistedFactory.class);
        factory(ChangeData.Factory.class);
        factory(MergeUtil.Factory.class);
        bind(ProjectCache.class).toInstance(projectCache);
        bind(AccountCache.class).toInstance(new FakeAccountCache());
        bind(GroupBackend.class).to(SystemGroupBackend.class);
        bind(String.class).annotatedWith(CanonicalWebUrl.class)
            .toProvider(CanonicalWebUrlProvider.class);
        bind(Boolean.class).annotatedWith(DisableReverseDnsLookup.class)
            .toInstance(Boolean.FALSE);
        bind(String.class).annotatedWith(AnonymousCowardName.class)
            .toProvider(AnonymousCowardNameProvider.class);
        bind(ChangeKindCache.class).to(ChangeKindCacheImpl.NoCache.class);
        bind(MergeabilityCache.class)
          .to(MergeabilityCache.NotImplemented.class);
        bind(StarredChangesUtil.class)
          .toProvider(Providers.<StarredChangesUtil> of(null));
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

  public InMemoryRepository add(ProjectConfig pc) {
    PrologEnvironment.Factory envFactory = null;
    ProjectControl.AssistedFactory projectControlFactory = null;
    RulesCache rulesCache = null;
    SitePaths sitePaths = null;
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
    all.put(pc.getName(), new ProjectState(sitePaths,
        projectCache, allProjectsName, projectControlFactory, envFactory,
        repoManager, rulesCache, commentLinks, pc));
    return repo;
  }

  public ProjectControl user(ProjectConfig local, AccountGroup.UUID... memberOf) {
    return user(local, null, memberOf);
  }

  public ProjectControl user(ProjectConfig local, String name,
      AccountGroup.UUID... memberOf) {
    String canonicalWebUrl = "http://localhost";

    return new ProjectControl(Collections.<AccountGroup.UUID> emptySet(),
        Collections.<AccountGroup.UUID> emptySet(), projectCache,
        sectionSorter, repoManager, changeControlFactory, null, null,
        canonicalWebUrl, new MockUser(name, memberOf), newProjectState(local));
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
