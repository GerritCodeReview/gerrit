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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.initSsh;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testutil.GerritServerTests.isNoteDbTestEnabled;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.primitives.Chars;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.TempFileUtil;
import com.google.gson.Gson;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Transport;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RunWith(ConfigSuite.class)
public abstract class AbstractDaemonTest {
  private static GerritServer commonServer;

  @ConfigSuite.Parameter
  public Config baseConfig;

  @ConfigSuite.Name
  private String configName;

  @Inject
  protected AllProjectsName allProjects;

  @Inject
  protected AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  protected GerritApi gApi;

  @Inject
  protected AcceptanceTestRequestScope atrScope;

  @Inject
  protected AccountCache accountCache;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  protected PushOneCommit.Factory pushFactory;

  @Inject
  protected MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  protected ProjectCache projectCache;

  @Inject
  protected GroupCache groupCache;

  @Inject
  protected GitRepositoryManager repoManager;

  @Inject
  protected ChangeIndexer indexer;

  @Inject
  protected Provider<InternalChangeQuery> queryProvider;

  @Inject
  @CanonicalWebUrl
  protected Provider<String> canonicalWebUrl;

  @Inject
  @GerritServerConfig
  protected Config cfg;

  @Inject
  private InProcessProtocol inProcessProtocol;

  @Inject
  private Provider<AnonymousUser> anonymousUser;

  @Inject
  @GerritPersonIdent
  protected Provider<PersonIdent> serverIdent;

  protected TestRepository<InMemoryRepository> testRepo;
  protected GerritServer server;
  protected TestAccount admin;
  protected TestAccount user;
  protected RestSession adminSession;
  protected RestSession userSession;
  protected SshSession sshSession;
  protected ReviewDb db;
  protected Project.NameKey project;

  @Inject
  protected NotesMigration notesMigration;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private String resourcePrefix;
  private List<Repository> toClose;

  @Rule
  public TestRule testRunner = new TestRule() {
    @Override
    public Statement apply(final Statement base, final Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          beforeTest(description);
          try {
            base.evaluate();
          } finally {
            afterTest();
          }
        }
      };
    }
  };

  @Rule
  public TemporaryFolder tempSiteDir = new TemporaryFolder();

  @AfterClass
  public static void stopCommonServer() throws Exception {
    if (commonServer != null) {
      commonServer.stop();
      commonServer = null;
    }
    TempFileUtil.cleanup();
  }

  protected static Config submitWholeTopicEnabledConfig() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    return cfg;
  }

  protected static Config allowDraftsDisabledConfig() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "allowDrafts", false);
    return cfg;
  }

  protected boolean isAllowDrafts() {
    return cfg.getBoolean("change", "allowDrafts", true);
  }

  protected boolean isSubmitWholeTopicEnabled() {
    return cfg.getBoolean("change", null, "submitWholeTopic", false);
  }

  protected void beforeTest(Description description) throws Exception {
    GerritServer.Description classDesc =
      GerritServer.Description.forTestClass(description, configName);
    GerritServer.Description methodDesc =
      GerritServer.Description.forTestMethod(description, configName);

    if (isNoteDbTestEnabled()) {
      NotesMigration.setAllEnabledConfig(baseConfig);
    }
    baseConfig.setString("gerrit", null, "tempSiteDir",
        tempSiteDir.getRoot().getPath());
    if (classDesc.equals(methodDesc)) {
      if (commonServer == null) {
        commonServer = GerritServer.start(classDesc, baseConfig);
      }
      server = commonServer;
    } else {
      server = GerritServer.start(methodDesc, baseConfig);
    }

    server.getTestInjector().injectMembers(this);
    Transport.register(inProcessProtocol);
    toClose = Collections.synchronizedList(new ArrayList<Repository>());
    admin = accounts.admin();
    user = accounts.user();

    // Evict cached user state in case tests modify it.
    accountCache.evict(admin.getId());
    accountCache.evict(user.getId());

    adminSession = new RestSession(server, admin);
    userSession = new RestSession(server, user);
    initSsh(admin);
    db = reviewDbProvider.open();
    Context ctx = newRequestContext(admin);
    atrScope.set(ctx);
    sshSession = ctx.getSession();
    sshSession.open();
    resourcePrefix = UNSAFE_PROJECT_NAME.matcher(
        description.getClassName() + "_"
        + description.getMethodName() + "_").replaceAll("");

    project = createProject(projectInput(description));
    testRepo = cloneProject(project, getCloneAsAccount(description));
  }

  private TestAccount getCloneAsAccount(Description description) {
    TestProjectInput ann = description.getAnnotation(TestProjectInput.class);
    return accounts.get(ann != null ? ann.cloneAs() : "admin");
  }

  private ProjectInput projectInput(Description description) {
    ProjectInput in = new ProjectInput();
    TestProjectInput ann = description.getAnnotation(TestProjectInput.class);
    in.name = name("project");
    if (ann != null) {
      in.parent = Strings.emptyToNull(ann.parent());
      in.description = Strings.emptyToNull(ann.description());
      in.createEmptyCommit = ann.createEmptyCommit();
      in.submitType = ann.submitType();
      in.useContentMerge = ann.useContributorAgreements();
      in.useSignedOffBy = ann.useSignedOffBy();
      in.useContentMerge = ann.useContentMerge();
    } else {
      // Defaults should match TestProjectConfig, omitting nullable values.
      in.createEmptyCommit = true;
    }
    updateProjectInput(in);
    return in;
  }

  private static final Pattern UNSAFE_PROJECT_NAME =
      Pattern.compile("[^a-zA-Z0-9._/-]+");

  protected Git git() {
    return testRepo.git();
  }

  protected InMemoryRepository repo() {
    return testRepo.getRepository();
  }

  /**
   * Return a resource name scoped to this test method.
   * <p>
   * Test methods in a single class by default share a running server. For any
   * resource name you require to be unique to a test method, wrap it in a call
   * to this method.
   *
   * @param name resource name (group, project, topic, etc.)
   * @return name prefixed by a string unique to this test method.
   */
  protected String name(String name) {
    return resourcePrefix + name;
  }

  protected Project.NameKey createProject(String nameSuffix)
      throws RestApiException {
    return createProject(nameSuffix, null);
  }

  protected Project.NameKey createProject(String nameSuffix,
      Project.NameKey parent) throws RestApiException {
    // Default for createEmptyCommit should match TestProjectConfig.
    return createProject(nameSuffix, parent, true);
  }

  protected Project.NameKey createProject(String nameSuffix,
      Project.NameKey parent, boolean createEmptyCommit)
      throws RestApiException {
    ProjectInput in = new ProjectInput();
    in.name = name(nameSuffix);
    in.parent = parent != null ? parent.get() : null;
    in.createEmptyCommit = createEmptyCommit;
    return createProject(in);
  }

  private Project.NameKey createProject(ProjectInput in)
      throws RestApiException {
    gApi.projects().create(in);
    return new Project.NameKey(in.name);
  }

  /**
   * Modify a project input before creating the initial test project.
   *
   * @param in input; may be modified in place.
   */
  protected void updateProjectInput(ProjectInput in) {
    // Default implementation does nothing.
  }

  protected TestRepository<InMemoryRepository> cloneProject(Project.NameKey p)
      throws Exception {
    return cloneProject(p, admin);
  }

  protected TestRepository<InMemoryRepository> cloneProject(Project.NameKey p,
      TestAccount testAccount) throws Exception {
    InProcessProtocol.Context ctx = new InProcessProtocol.Context(
        reviewDbProvider, identifiedUserFactory, testAccount.getId(), p);
    Repository repo = repoManager.openRepository(p);
    toClose.add(repo);
    return GitUtil.cloneProject(
        p, inProcessProtocol.register(ctx, repo).toString());
  }

  private void afterTest() throws Exception {
    Transport.unregister(inProcessProtocol);
    for (Repository repo : toClose) {
      repo.close();
    }
    db.close();
    sshSession.close();
    if (server != commonServer) {
      server.stop();
    }
  }

  protected TestRepository<?>.CommitBuilder commitBuilder() throws Exception {
    return testRepo.branch("HEAD").commit().insertChangeId();
  }

  protected TestRepository<?>.CommitBuilder amendBuilder() throws Exception {
    ObjectId head = repo().getRef("HEAD").getObjectId();
    TestRepository<?>.CommitBuilder b = testRepo.amendRef("HEAD");
    Optional<String> id = GitUtil.getChangeId(testRepo, head);
    // TestRepository behaves like "git commit --amend -m foo", which does not
    // preserve an existing Change-Id. Tests probably want this.
    if (id.isPresent()) {
      b.insertChangeId(id.get().substring(1));
    } else {
      b.insertChangeId();
    }
    return b;
  }

  protected PushOneCommit.Result createChange() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();
    return result;
  }

  protected BranchApi createBranch(Branch.NameKey branch) throws Exception {
    return gApi.projects()
        .name(branch.getParentKey().get())
        .branch(branch.get())
        .create(new BranchInput());
  }

  private static final List<Character> RANDOM =
      Chars.asList(new char[]{'a','b','c','d','e','f','g','h'});
  protected PushOneCommit.Result amendChange(String changeId)
      throws Exception {
    return amendChange(changeId, "refs/for/master");
  }

  protected PushOneCommit.Result amendChange(String changeId, String ref)
      throws Exception {
    Collections.shuffle(RANDOM);
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME, new String(Chars.toArray(RANDOM)), changeId);
    return push.to(ref);
  }

  protected void merge(PushOneCommit.Result r) throws Exception {
    revision(r).review(ReviewInput.approve());
    revision(r).submit();
  }

  protected ChangeInfo info(String id)
      throws RestApiException {
    return gApi.changes().id(id).info();
  }

  protected ChangeInfo get(String id)
      throws RestApiException {
    return gApi.changes().id(id).get();
  }

  protected EditInfo getEdit(String id)
      throws RestApiException {
    return gApi.changes().id(id).getEdit();
  }

  protected ChangeInfo get(String id, ListChangesOption... options)
      throws RestApiException {
    return gApi.changes().id(id).get(
        Sets.newEnumSet(Arrays.asList(options), ListChangesOption.class));
  }

  protected List<ChangeInfo> query(String q) throws RestApiException {
    return gApi.changes().query(q).get();
  }

  private Context newRequestContext(TestAccount account) {
    return atrScope.newContext(reviewDbProvider, new SshSession(server, admin),
        identifiedUserFactory.create(Providers.of(db), account.getId()));
  }

  protected Context setApiUser(TestAccount account) {
    return atrScope.set(newRequestContext(account));
  }

  protected Context setApiUserAnonymous() {
    return atrScope.set(
        atrScope.newContext(reviewDbProvider, null, anonymousUser.get()));
  }

  protected static Gson newGson() {
    return OutputFormat.JSON_COMPACT.newGson();
  }

  protected RevisionApi revision(PushOneCommit.Result r) throws Exception {
    return gApi.changes()
        .id(r.getChangeId())
        .current();
  }

  protected void allow(String permission, AccountGroup.UUID id, String ref)
      throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.allow(cfg, permission, id, ref);
    saveProjectConfig(project, cfg);
  }

  protected void allowGlobalCapabilities(AccountGroup.UUID id,
      String... capabilityNames) throws Exception {
    allowGlobalCapabilities(id, Arrays.asList(capabilityNames));
  }

  protected void allowGlobalCapabilities(AccountGroup.UUID id,
      Iterable<String> capabilityNames) throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    for (String capabilityName : capabilityNames) {
      Util.allow(cfg, capabilityName, id);
    }
    saveProjectConfig(allProjects, cfg);
  }

  protected void removeGlobalCapabilities(AccountGroup.UUID id,
      String... capabilityNames) throws Exception {
    removeGlobalCapabilities(id, Arrays.asList(capabilityNames));
  }

  protected void removeGlobalCapabilities(AccountGroup.UUID id,
      Iterable<String> capabilityNames) throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    for (String capabilityName : capabilityNames) {
      Util.remove(cfg, capabilityName, id);
    }
    saveProjectConfig(allProjects, cfg);
  }

  protected void setUseContributorAgreements(InheritableBoolean value)
      throws Exception {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    ProjectConfig config = ProjectConfig.read(md);
    config.getProject().setUseContributorAgreements(value);
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  protected void setUseSignedOffBy(InheritableBoolean value)
      throws Exception {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    ProjectConfig config = ProjectConfig.read(md);
    config.getProject().setUseSignedOffBy(value);
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  protected void deny(String permission, AccountGroup.UUID id, String ref)
      throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.deny(cfg, permission, id, ref);
    saveProjectConfig(project, cfg);
  }

  protected PermissionRule block(String permission, AccountGroup.UUID id, String ref)
      throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    PermissionRule rule = Util.block(cfg, permission, id, ref);
    saveProjectConfig(project, cfg);
    return rule;
  }

  protected void saveProjectConfig(Project.NameKey p, ProjectConfig cfg)
      throws Exception {
    MetaDataUpdate md = metaDataUpdateFactory.create(p);
    try {
      cfg.commit(md);
    } finally {
      md.close();
    }
    projectCache.evict(cfg.getProject());
  }

  protected void grant(String permission, Project.NameKey project, String ref)
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {
    grant(permission, project, ref, false);
  }

  protected void grant(String permission, Project.NameKey project, String ref,
      boolean force) throws RepositoryNotFoundException, IOException,
      ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    md.setMessage(String.format("Grant %s on %s", permission, ref));
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection(ref, true);
    Permission p = s.getPermission(permission, true);
    AccountGroup adminGroup = groupCache.get(new AccountGroup.NameKey("Administrators"));
    PermissionRule rule = new PermissionRule(config.resolve(adminGroup));
    rule.setForce(force);
    p.add(rule);
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  protected void blockRead(String ref) throws Exception {
    block(Permission.READ, REGISTERED_USERS, ref);
  }

  protected void blockForgeCommitter(Project.NameKey project, String ref)
      throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.block(cfg, Permission.FORGE_COMMITTER, REGISTERED_USERS, ref);
    saveProjectConfig(project, cfg);
  }

  protected PushOneCommit.Result pushTo(String ref) throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    return push.to(ref);
  }

  protected void approve(String id) throws Exception {
    gApi.changes()
      .id(id)
      .revision("current")
      .review(ReviewInput.approve());
  }

  protected Map<String, ActionInfo> getActions(String id) throws Exception {
    return gApi.changes()
      .id(id)
      .revision(1)
      .actions();
  }

  protected void assertSubmittedTogether(String chId, String... expected)
      throws Exception {
    List<ChangeInfo> actual = gApi.changes().id(chId).submittedTogether();
    assertThat(actual).hasSize(expected.length);
    assertThat(Iterables.transform(actual,
        new Function<ChangeInfo, String>() {
      @Override
      public String apply(ChangeInfo input) {
        return input.changeId;
      }
    })).containsExactly((Object[])expected).inOrder();
  }
}
