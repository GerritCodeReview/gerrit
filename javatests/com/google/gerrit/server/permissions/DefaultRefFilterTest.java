// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.entities.Permission.READ;
import static com.google.gerrit.entities.RefNames.REFS_CONFIG;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.DelegateRefDatabase;
import com.google.gerrit.server.git.DelegateRepository;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.SingleVersionModule.SingleVersionListener;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DefaultRefFilterTest {
  private static final AccountGroup.UUID DEVS = AccountGroup.uuid("test.devs");

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

    ProjectConfig.Factory projectConfigFactory = injector.getInstance(ProjectConfig.Factory.class);

    singleVersionListener.start();
    try {
      schemaCreator.create();
    } finally {
      singleVersionListener.stop();
    }

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
  public void tearDown() {
    @SuppressWarnings("unused")
    var unused = requestContext.setContext(null);
  }

  @Test
  public void deferredTagsUsesGetRefsByPrefixWithExclusionsAndSkipsGetRefs() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/main").group(DEVS))
        .update();

    List<Ref> allRefs;
    try (Repository repo = repoManager.openRepository(localKey);
        TestRepository<Repository> tr = new TestRepository<>(repo)) {
      RevCommit mainCommit = tr.commit().message("main").create();
      RevCommit secretCommit = tr.commit().message("secret").create();
      RevCommit changeCommit = tr.commit().message("change").create();
      tr.update("refs/heads/main", mainCommit);
      tr.update("refs/heads/secret", secretCommit);
      tr.update("refs/changes/01/1001/1", changeCommit);
      tr.update("refs/tags/v1.0", tr.tag("v1.0", mainCommit));
      tr.update("refs/tags/v2.0-secret", tr.tag("v2.0-secret", secretCommit));
      tr.update("refs/tags/v3.0-change", tr.tag("v3.0-change", changeCommit));
      allRefs = repo.getRefDatabase().getRefs();
    }

    ImmutableSet<String> expectedExclusions =
        ImmutableSet.<String>builder().addAll(RefNames.GERRIT_REFS).add(RefNames.REFS_TAGS).build();

    try (TrackingRepository trackingRepo =
        new TrackingRepository(repoManager.openRepository(localKey))) {
      Collection<Ref> visible =
          user(localKey, DEVS)
              .asForProject()
              .filter(allRefs, trackingRepo, RefFilterOptions.defaults());

      assertThat(trackingRepo.trackingRefDatabase().getRefsCalls()).isEqualTo(0);
      assertThat(trackingRepo.trackingRefDatabase().prefixExclusionCalls()).isNotEmpty();
      assertThat(trackingRepo.trackingRefDatabase().prefixExclusionCalls().get(0))
          .isEqualTo(Maps.immutableEntry(RefDatabase.ALL, expectedExclusions));
      assertThat(visible.stream().map(Ref::getName).collect(toImmutableList()))
          .containsExactly("refs/heads/main", "refs/tags/v1.0");
    }
  }

  @Test
  public void deferredTagsPreservesRefsMetaPrefixSiblingAsTaggableRef() throws Exception {
    projectOperations
        .project(localKey)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/main").group(DEVS))
        .add(allow(READ).ref(REFS_CONFIG).group(DEVS))
        .add(allow(READ).ref("refs/meta/config-archive").group(DEVS))
        .update();

    try (Repository repo = repoManager.openRepository(localKey);
        TestRepository<Repository> tr = new TestRepository<>(repo)) {
      RevCommit configCommit =
          tr.getRevWalk().parseCommit(repo.exactRef(REFS_CONFIG).getObjectId());
      RevCommit mainCommit = tr.commit().message("main").create();
      RevCommit archiveCommit = tr.commit().message("archive").create();
      tr.update("refs/heads/main", mainCommit);
      tr.update("refs/meta/config-archive", archiveCommit);
      tr.update("refs/tags/v-archive", tr.tag("v-archive", archiveCommit));
      tr.update("refs/tags/v-config", tr.tag("v-config", configCommit));

      Collection<Ref> visible =
          user(localKey, DEVS)
              .asForProject()
              .filter(repo.getRefDatabase().getRefs(), repo, RefFilterOptions.defaults());

      assertThat(visible.stream().map(Ref::getName).collect(toImmutableList()))
          .containsExactly(
              "refs/heads/main", REFS_CONFIG, "refs/meta/config-archive", "refs/tags/v-archive");
    }
  }

  private ProjectState getProjectState(Project.NameKey nameKey) {
    return projectCache.get(nameKey).orElseThrow(illegalState(nameKey));
  }

  private ProjectControl user(Project.NameKey projectKey, AccountGroup.UUID... memberOf) {
    return projectControlFactory.create(
        new MockUser("user1", memberOf), getProjectState(projectKey));
  }

  private static class TrackingRepository extends DelegateRepository {
    private final TrackingRefDatabase refDatabase;

    TrackingRepository(Repository delegate) {
      super(delegate);
      this.refDatabase = new TrackingRefDatabase(delegate);
    }

    @Override
    public RefDatabase getRefDatabase() {
      return refDatabase;
    }

    TrackingRefDatabase trackingRefDatabase() {
      return refDatabase;
    }
  }

  private static class TrackingRefDatabase extends DelegateRefDatabase {
    private int getRefsCalls;
    private final List<Map.Entry<String, ImmutableSet<String>>> prefixExclusionCalls =
        new ArrayList<>();

    TrackingRefDatabase(Repository delegate) {
      super(delegate);
    }

    @Override
    public List<Ref> getRefs() throws IOException {
      getRefsCalls++;
      return super.getRefs();
    }

    @Override
    public List<Ref> getRefsByPrefixWithExclusions(String include, Set<String> excludes)
        throws IOException {
      prefixExclusionCalls.add(Maps.immutableEntry(include, ImmutableSet.copyOf(excludes)));
      return super.getRefsByPrefixWithExclusions(include, excludes);
    }

    int getRefsCalls() {
      return getRefsCalls;
    }

    List<Map.Entry<String, ImmutableSet<String>>> prefixExclusionCalls() {
      return prefixExclusionCalls;
    }
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
