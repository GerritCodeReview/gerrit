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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.OptionalSubject.optionals;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.entities.Patch.COMMIT_MSG;
import static com.google.gerrit.entities.Patch.MERGE_LIST;
import static com.google.gerrit.extensions.api.changes.SubmittedTogetherOption.NON_VISIBLE_CHANGES;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.server.project.testing.TestLabels.label;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.github.rholder.retry.BlockStrategy;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.jimfs.Jimfs;
import com.google.common.primitives.Chars;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.EmailHeader;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.SubmittedTogetherInfo;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.change.BatchAbandon;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.AbstractChangeNotes;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.server.plugins.TestServerPlugin;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.change.Revisions;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.git.DelegateSystemReader;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.FakeEmailSender;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.gerrit.testing.SshMode;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.jcraft.jsch.KeyPair;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportBundleStream;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

@RunWith(ConfigSuite.class)
public abstract class AbstractDaemonTest {

  /**
   * Test methods without special annotations will use a common server for efficiency reasons. The
   * server is torn down after the test class is done.
   */
  private static GerritServer commonServer;

  private static Description firstTest;

  @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

  @ConfigSuite.Parameter public Config baseConfig;
  @ConfigSuite.Name private String configName;

  @Rule
  public TestRule testRunner =
      new TestRule() {
        @Override
        public Statement apply(Statement base, Description description) {
          return new Statement() {
            @Override
            public void evaluate() throws Throwable {
              if (firstTest == null) {
                firstTest = description;
              }
              beforeTest(description);
              ProjectResetter.Config input = requireNonNull(resetProjects());

              try (ProjectResetter resetter =
                  projectResetter != null ? projectResetter.builder().build(input) : null) {
                AbstractDaemonTest.this.resetter = resetter;
                base.evaluate();
              } finally {
                AbstractDaemonTest.this.resetter = null;
                afterTest();
              }
            }
          };
        }
      };

  @Inject @CanonicalWebUrl protected Provider<String> canonicalWebUrl;
  @Inject @GerritPersonIdent protected Provider<PersonIdent> serverIdent;
  @Inject @GerritServerConfig protected Config cfg;
  @Inject @GerritInstanceId @Nullable protected String instanceId;
  @Inject protected AcceptanceTestRequestScope atrScope;
  @Inject protected AccountCache accountCache;
  @Inject protected AccountCreator accountCreator;
  @Inject protected Accounts accounts;
  @Inject protected AllProjectsName allProjects;
  @Inject protected AllUsersName allUsers;
  @Inject protected BatchUpdate.Factory batchUpdateFactory;
  @Inject protected ChangeData.Factory changeDataFactory;
  @Inject protected ChangeFinder changeFinder;
  @Inject protected ChangeIndexer indexer;
  @Inject protected ChangeNoteUtil changeNoteUtil;
  @Inject protected ChangeResource.Factory changeResourceFactory;
  @Inject protected FakeEmailSender sender;
  @Inject protected GerritApi gApi;
  @Inject protected GitRepositoryManager repoManager;
  @Inject protected GroupBackend groupBackend;
  @Inject protected GroupCache groupCache;
  @Inject protected IdentifiedUser.GenericFactory identifiedUserFactory;
  @Inject protected MetaDataUpdate.Server metaDataUpdateFactory;
  @Inject protected PatchSetUtil psUtil;
  @Inject protected ProjectCache projectCache;
  @Inject protected ProjectConfig.Factory projectConfigFactory;
  @Inject protected ProjectResetter.Builder.Factory projectResetter;
  @Inject protected Provider<InternalChangeQuery> queryProvider;
  @Inject protected PushOneCommit.Factory pushFactory;
  @Inject protected PluginConfigFactory pluginConfig;
  @Inject protected Revisions revisions;
  @Inject protected SystemGroupBackend systemGroupBackend;
  @Inject protected ChangeNotes.Factory notesFactory;
  @Inject protected BatchAbandon batchAbandon;
  @Inject protected TestSshKeys sshKeys;

  protected EventRecorder eventRecorder;
  protected GerritServer server;
  protected Project.NameKey project;
  protected RestSession adminRestSession;
  protected RestSession userRestSession;
  protected RestSession anonymousRestSession;
  protected SshSession adminSshSession;
  protected SshSession userSshSession;
  protected TestAccount admin;
  protected TestAccount user;
  protected TestRepository<InMemoryRepository> testRepo;
  protected String resourcePrefix;
  protected Description description;
  protected boolean testRequiresSsh;
  protected BlockStrategy noSleepBlockStrategy = t -> {}; // Don't sleep in tests.

  @Inject private AbstractChangeNotes.Args changeNotesArgs;
  @Inject private AccountIndexCollection accountIndexes;
  @Inject private AccountIndexer accountIndexer;
  @Inject private ChangeIndexCollection changeIndexes;
  @Inject private EventRecorder.Factory eventRecorderFactory;
  @Inject private InProcessProtocol inProcessProtocol;
  @Inject private PluginGuiceEnvironment pluginGuiceEnvironment;
  @Inject private PluginUser.Factory pluginUserFactory;
  @Inject private ProjectIndexCollection projectIndexes;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private SitePaths sitePaths;
  @Inject private ProjectOperations projectOperations;

  private ProjectResetter resetter;
  private List<Repository> toClose;
  private String systemTimeZone;
  private SystemReader oldSystemReader;

  @Before
  public void clearSender() {
    if (sender != null) {
      sender.clear();
    }
  }

  @Before
  public void startEventRecorder() {
    if (eventRecorderFactory != null) {
      eventRecorder = eventRecorderFactory.create(admin);
    }
  }

  @Before
  public void assumeSshIfRequired() {
    if (testRequiresSsh) {
      // If the test uses ssh, we use assume() to make sure ssh is enabled on
      // the test suite. JUnit will skip tests annotated with @UseSsh if we
      // disable them using the command line flag.
      assume().that(SshMode.useSsh()).isTrue();
    }
  }

  @After
  public void closeEventRecorder() {
    if (eventRecorder != null) {
      eventRecorder.close();
    }
  }

  @AfterClass
  public static void stopCommonServer() throws Exception {
    if (commonServer != null) {
      try {
        commonServer.close();
      } catch (Throwable t) {
        throw new AssertionError(
            "Error stopping common server in "
                + (firstTest != null ? firstTest.getTestClass().getName() : "unknown test class"),
            t);
      } finally {
        commonServer = null;
      }
    }
  }

  /** Controls which project and branches should be reset after each test case. */
  protected ProjectResetter.Config resetProjects() {
    return new ProjectResetter.Config()
        // Don't reset all refs so that refs/sequences/changes is not touched and change IDs are
        // not reused.
        .reset(allProjects, RefNames.REFS_CONFIG)
        // Don't reset refs/sequences/accounts so that account IDs are not reused.
        .reset(
            allUsers,
            RefNames.REFS_CONFIG,
            RefNames.REFS_USERS + "*",
            RefNames.REFS_EXTERNAL_IDS,
            RefNames.REFS_GROUPNAMES,
            RefNames.REFS_GROUPS + "*",
            RefNames.REFS_STARRED_CHANGES + "*",
            RefNames.REFS_DRAFT_COMMENTS + "*");
  }

  protected void restartAsSlave() throws Exception {
    closeSsh();
    server = GerritServer.restartAsSlave(server);
    server.getTestInjector().injectMembers(this);
    if (resetter != null) {
      server.getTestInjector().injectMembers(resetter);
    }
    initSsh();
  }

  protected void reindexAccount(Account.Id accountId) {
    accountIndexer.index(accountId);
  }

  protected static Config submitWholeTopicEnabledConfig() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    return cfg;
  }

  protected boolean isSubmitWholeTopicEnabled() {
    return cfg.getBoolean("change", null, "submitWholeTopic", false);
  }

  protected boolean isContributorAgreementsEnabled() {
    return cfg.getBoolean("auth", null, "contributorAgreements", false);
  }

  protected void beforeTest(Description description) throws Exception {
    // SystemReader must be overridden before creating any repos, since they read the user/system
    // configs at initialization time, and are then stored in the RepositoryCache forever.
    oldSystemReader = setFakeSystemReader(temporaryFolder.getRoot());

    this.description = description;
    GerritServer.Description classDesc =
        GerritServer.Description.forTestClass(description, configName);
    GerritServer.Description methodDesc =
        GerritServer.Description.forTestMethod(description, configName);

    testRequiresSsh = classDesc.useSshAnnotation() || methodDesc.useSshAnnotation();
    if (!testRequiresSsh) {
      baseConfig.setString("sshd", null, "listenAddress", "off");
    }

    baseConfig.unset("gerrit", null, "canonicalWebUrl");
    baseConfig.unset("httpd", null, "listenUrl");

    baseConfig.setInt("index", null, "batchThreads", -1);

    baseConfig.setInt("receive", null, "changeUpdateThreads", 4);
    Module module = createModule();
    if (classDesc.equals(methodDesc) && !classDesc.sandboxed() && !methodDesc.sandboxed()) {
      if (commonServer == null) {
        commonServer = GerritServer.initAndStart(temporaryFolder, classDesc, baseConfig, module);
      }
      server = commonServer;
    } else {
      server = GerritServer.initAndStart(temporaryFolder, methodDesc, baseConfig, module);
    }

    server.getTestInjector().injectMembers(this);
    Transport.register(inProcessProtocol);
    toClose = Collections.synchronizedList(new ArrayList<>());

    admin = accountCreator.admin();
    user = accountCreator.user();

    // Evict and reindex accounts in case tests modify them.
    reindexAccount(admin.id());
    reindexAccount(user.id());

    adminRestSession = new RestSession(server, admin);
    userRestSession = new RestSession(server, user);
    anonymousRestSession = new RestSession(server, null);

    initSsh();

    resourcePrefix =
        UNSAFE_PROJECT_NAME
            .matcher(description.getClassName() + "_" + description.getMethodName() + "_")
            .replaceAll("");

    Context ctx = newRequestContext(admin);
    atrScope.set(ctx);
    ProjectInput in = projectInput(description);
    gApi.projects().create(in);
    project = Project.nameKey(in.name);
    if (!classDesc.skipProjectClone()) {
      testRepo = cloneProject(project, getCloneAsAccount(description));
    }

    // Set the clock step last, so that the test setup isn't consuming any timestamps after the
    // clock has been set.
    setTimeSettings(classDesc.useSystemTime(), classDesc.useClockStep(), classDesc.useTimezone());
    setTimeSettings(
        methodDesc.useSystemTime(), methodDesc.useClockStep(), methodDesc.useTimezone());
  }

  private static SystemReader setFakeSystemReader(File tempDir) {
    SystemReader oldSystemReader = SystemReader.getInstance();
    SystemReader.setInstance(
        new DelegateSystemReader(oldSystemReader) {
          @Override
          public FileBasedConfig openJGitConfig(Config parent, FS fs) {
            return new FileBasedConfig(parent, new File(tempDir, "jgit.config"), FS.detect());
          }

          @Override
          public FileBasedConfig openUserConfig(Config parent, FS fs) {
            return new FileBasedConfig(parent, new File(tempDir, "user.config"), FS.detect());
          }

          @Override
          public FileBasedConfig openSystemConfig(Config parent, FS fs) {
            return new FileBasedConfig(parent, new File(tempDir, "system.config"), FS.detect());
          }
        });
    return oldSystemReader;
  }

  private void setTimeSettings(
      boolean useSystemTime,
      @Nullable UseClockStep useClockStep,
      @Nullable UseTimezone useTimezone) {
    if (useSystemTime) {
      TestTimeUtil.useSystemTime();
    } else if (useClockStep != null) {
      TestTimeUtil.resetWithClockStep(useClockStep.clockStep(), useClockStep.clockStepUnit());
      if (useClockStep.startAtEpoch()) {
        TestTimeUtil.setClock(Timestamp.from(Instant.EPOCH));
      }
    }
    if (useTimezone != null) {
      systemTimeZone = System.setProperty("user.timezone", useTimezone.timezone());
    }
  }

  private void resetTimeSettings() {
    TestTimeUtil.useSystemTime();
    if (systemTimeZone != null) {
      System.setProperty("user.timezone", systemTimeZone);
      systemTimeZone = null;
    }
  }

  /** Override to bind an additional Guice module */
  public Module createModule() {
    return null;
  }

  protected void initSsh() throws Exception {
    if (testRequiresSsh
        && SshMode.useSsh()
        && (adminSshSession == null || userSshSession == null)) {
      // Create Ssh sessions
      KeyPair adminKeyPair = sshKeys.getKeyPair(admin);
      GitUtil.initSsh(adminKeyPair);
      Context ctx = newRequestContext(user);
      atrScope.set(ctx);
      userSshSession = ctx.getSession();
      userSshSession.open();
      ctx = newRequestContext(admin);
      atrScope.set(ctx);
      adminSshSession = ctx.getSession();
      adminSshSession.open();
    }
  }

  private TestAccount getCloneAsAccount(Description description) {
    TestProjectInput ann = description.getAnnotation(TestProjectInput.class);
    return accountCreator.get(ann != null ? ann.cloneAs() : "admin");
  }

  /** Generate default project properties based on test description */
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
      in.rejectEmptyCommit = ann.rejectEmptyCommit();
      in.enableSignedPush = ann.enableSignedPush();
      in.requireSignedPush = ann.requireSignedPush();
    } else {
      // Defaults should match TestProjectConfig, omitting nullable values.
      in.createEmptyCommit = true;
    }
    updateProjectInput(in);
    return in;
  }

  /**
   * Modify a project input before creating the initial test project.
   *
   * @param in input; may be modified in place.
   */
  protected void updateProjectInput(ProjectInput in) {
    // Default implementation does nothing.
  }

  private static final Pattern UNSAFE_PROJECT_NAME = Pattern.compile("[^a-zA-Z0-9._/-]+");

  protected Git git() {
    return testRepo.git();
  }

  protected InMemoryRepository repo() {
    return testRepo.getRepository();
  }

  /**
   * Return a resource name scoped to this test method.
   *
   * <p>Test methods in a single class by default share a running server. For any resource name you
   * require to be unique to a test method, wrap it in a call to this method.
   *
   * @param name resource name (group, project, topic, etc.)
   * @return name prefixed by a string unique to this test method.
   */
  protected String name(String name) {
    return resourcePrefix + name;
  }

  protected Project.NameKey createProjectOverAPI(
      String nameSuffix, Project.NameKey parent, boolean createEmptyCommit, SubmitType submitType)
      throws RestApiException {
    ProjectInput in = new ProjectInput();
    in.name = name(nameSuffix);
    in.parent = parent != null ? parent.get() : null;
    in.submitType = submitType;
    in.createEmptyCommit = createEmptyCommit;
    gApi.projects().create(in);
    return Project.nameKey(in.name);
  }

  protected TestRepository<InMemoryRepository> cloneProject(Project.NameKey p) throws Exception {
    return cloneProject(p, admin);
  }

  protected TestRepository<InMemoryRepository> cloneProject(
      Project.NameKey p, TestAccount testAccount) throws Exception {
    return GitUtil.cloneProject(p, registerRepoConnection(p, testAccount));
  }

  /**
   * Register a repository connection over the test protocol.
   *
   * @return a URI string that can be used to connect to this repository for both fetch and push.
   */
  protected String registerRepoConnection(Project.NameKey p, TestAccount testAccount)
      throws Exception {
    InProcessProtocol.Context ctx =
        new InProcessProtocol.Context(identifiedUserFactory, testAccount.id(), p);
    Repository repo = repoManager.openRepository(p);
    toClose.add(repo);
    return inProcessProtocol.register(ctx, repo).toString();
  }

  protected void afterTest() throws Exception {
    Transport.unregister(inProcessProtocol);
    for (Repository repo : toClose) {
      repo.close();
    }
    closeSsh();
    resetTimeSettings();
    if (server != commonServer) {
      server.close();
      server = null;
    }
    SystemReader.setInstance(oldSystemReader);
    oldSystemReader = null;
  }

  protected void closeSsh() {
    if (adminSshSession != null) {
      adminSshSession.close();
      adminSshSession = null;
    }
    if (userSshSession != null) {
      userSshSession.close();
      userSshSession = null;
    }
  }

  protected TestRepository<?>.CommitBuilder commitBuilder() throws Exception {
    return testRepo.branch("HEAD").commit().insertChangeId();
  }

  protected TestRepository<?>.CommitBuilder amendBuilder() throws Exception {
    ObjectId head = repo().exactRef("HEAD").getObjectId();
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
    return createChange("refs/for/master");
  }

  protected PushOneCommit.Result createChange(String ref) throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result result = push.to(ref);
    result.assertOkStatus();
    return result;
  }

  protected PushOneCommit.Result createChange(TestRepository<InMemoryRepository> repo)
      throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();
    return result;
  }

  protected PushOneCommit.Result createMergeCommitChange(String ref) throws Exception {
    return createMergeCommitChange(ref, "foo");
  }

  protected PushOneCommit.Result createMergeCommitChange(String ref, String file) throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();

    PushOneCommit.Result p1 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "parent 1",
                ImmutableMap.of(file, "foo-1", "bar", "bar-1"))
            .to(ref);

    // reset HEAD in order to create a sibling of the first change
    testRepo.reset(initial);

    PushOneCommit.Result p2 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "parent 2",
                ImmutableMap.of(file, "foo-2", "bar", "bar-2"))
            .to(ref);

    PushOneCommit m =
        pushFactory.create(
            admin.newIdent(), testRepo, "merge", ImmutableMap.of(file, "foo-1", "bar", "bar-2"));
    m.setParents(ImmutableList.of(p1.getCommit(), p2.getCommit()));
    PushOneCommit.Result result = m.to(ref);
    result.assertOkStatus();
    return result;
  }

  protected PushOneCommit.Result createCommitAndPush(
      TestRepository<InMemoryRepository> repo,
      String ref,
      String commitMsg,
      String fileName,
      String content)
      throws Exception {
    PushOneCommit.Result result =
        pushFactory.create(admin.newIdent(), repo, commitMsg, fileName, content).to(ref);
    result.assertOkStatus();
    return result;
  }

  protected PushOneCommit.Result createChangeWithTopic(
      TestRepository<InMemoryRepository> repo,
      String topic,
      String commitMsg,
      String fileName,
      String content)
      throws Exception {
    assertThat(topic).isNotEmpty();
    return createCommitAndPush(
        repo, "refs/for/master%topic=" + name(topic), commitMsg, fileName, content);
  }

  protected PushOneCommit.Result createChange(String subject, String fileName, String content)
      throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, subject, fileName, content);
    return push.to("refs/for/master");
  }

  protected PushOneCommit.Result createChange(
      TestRepository<?> repo,
      String branch,
      String subject,
      String fileName,
      String content,
      String topic)
      throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), repo, subject, fileName, content);
    return push.to("refs/for/" + branch + "%topic=" + name(topic));
  }

  protected BranchApi createBranch(BranchNameKey branch) throws Exception {
    return gApi.projects()
        .name(branch.project().get())
        .branch(branch.branch())
        .create(new BranchInput());
  }

  protected BranchApi createBranchWithRevision(BranchNameKey branch, String revision)
      throws Exception {
    BranchInput in = new BranchInput();
    in.revision = revision;
    return gApi.projects().name(branch.project().get()).branch(branch.branch()).create(in);
  }

  private static final List<Character> RANDOM =
      Chars.asList('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h');

  protected PushOneCommit.Result amendChange(String changeId) throws Exception {
    return amendChange(changeId, "refs/for/master", admin, testRepo);
  }

  protected PushOneCommit.Result amendChange(
      String changeId, String ref, TestAccount testAccount, TestRepository<?> repo)
      throws Exception {
    Collections.shuffle(RANDOM);
    return amendChange(
        changeId,
        ref,
        testAccount,
        repo,
        PushOneCommit.SUBJECT,
        PushOneCommit.FILE_NAME,
        new String(Chars.toArray(RANDOM)));
  }

  protected PushOneCommit.Result amendChange(
      String changeId, String subject, String fileName, String content) throws Exception {
    return amendChange(changeId, "refs/for/master", admin, testRepo, subject, fileName, content);
  }

  protected PushOneCommit.Result amendChange(
      String changeId,
      String ref,
      TestAccount testAccount,
      TestRepository<?> repo,
      String subject,
      String fileName,
      String content)
      throws Exception {
    PushOneCommit push =
        pushFactory.create(testAccount.newIdent(), repo, subject, fileName, content, changeId);
    return push.to(ref);
  }

  protected void merge(PushOneCommit.Result r) throws Exception {
    revision(r).review(ReviewInput.approve());
    revision(r).submit();
  }

  protected ChangeInfo info(String id) throws RestApiException {
    return gApi.changes().id(id).info();
  }

  protected ChangeApi change(Result r) throws RestApiException {
    return gApi.changes().id(r.getChange().getId().get());
  }

  protected Optional<EditInfo> getEdit(String id) throws RestApiException {
    return gApi.changes().id(id).edit().get();
  }

  protected ChangeInfo get(String id, ListChangesOption... options) throws RestApiException {
    return gApi.changes().id(id).get(options);
  }

  protected List<ChangeInfo> query(String q) throws RestApiException {
    return gApi.changes().query(q).get();
  }

  private Context newRequestContext(TestAccount account) {
    requestScopeOperations.setApiUser(account.id());
    return atrScope.get();
  }

  protected Account getAccount(Account.Id accountId) {
    return getAccountState(accountId).account();
  }

  protected AccountState getAccountState(Account.Id accountId) {
    Optional<AccountState> accountState = accountCache.get(accountId);
    assertWithMessage("account %s", accountId.get())
        .about(optionals())
        .that(accountState)
        .isPresent();
    return accountState.get();
  }

  protected AutoCloseable disableNoteDb() {
    changeNotesArgs.failOnLoadForTest.set(true);
    Context oldContext = atrScope.disableNoteDb();
    return () -> {
      changeNotesArgs.failOnLoadForTest.set(false);
      atrScope.set(oldContext);
    };
  }

  protected void disableChangeIndexWrites() {
    for (ChangeIndex i : changeIndexes.getWriteIndexes()) {
      if (!(i instanceof ReadOnlyChangeIndex)) {
        changeIndexes.addWriteIndex(new ReadOnlyChangeIndex(i));
      }
    }
  }

  protected void enableChangeIndexWrites() {
    for (ChangeIndex i : changeIndexes.getWriteIndexes()) {
      if (i instanceof ReadOnlyChangeIndex) {
        changeIndexes.addWriteIndex(((ReadOnlyChangeIndex) i).unwrap());
      }
    }
  }

  protected AutoCloseable disableChangeIndex() {
    disableChangeIndexWrites();
    ChangeIndex maybeDisabledSearchIndex = changeIndexes.getSearchIndex();
    if (!(maybeDisabledSearchIndex instanceof DisabledChangeIndex)) {
      changeIndexes.setSearchIndex(new DisabledChangeIndex(maybeDisabledSearchIndex), false);
    }

    return () -> {
      enableChangeIndexWrites();
      ChangeIndex maybeEnabledSearchIndex = changeIndexes.getSearchIndex();
      if (maybeEnabledSearchIndex instanceof DisabledChangeIndex) {
        changeIndexes.setSearchIndex(
            ((DisabledChangeIndex) maybeEnabledSearchIndex).unwrap(), false);
      }
    };
  }

  protected AutoCloseable disableAccountIndex() {
    AccountIndex maybeDisabledSearchIndex = accountIndexes.getSearchIndex();
    if (!(maybeDisabledSearchIndex instanceof DisabledAccountIndex)) {
      accountIndexes.setSearchIndex(new DisabledAccountIndex(maybeDisabledSearchIndex), false);
    }

    return () -> {
      AccountIndex maybeEnabledSearchIndex = accountIndexes.getSearchIndex();
      if (maybeEnabledSearchIndex instanceof DisabledAccountIndex) {
        accountIndexes.setSearchIndex(
            ((DisabledAccountIndex) maybeEnabledSearchIndex).unwrap(), false);
      }
    };
  }

  protected AutoCloseable disableProjectIndex() {
    disableProjectIndexWrites();
    ProjectIndex maybeDisabledSearchIndex = projectIndexes.getSearchIndex();
    if (!(maybeDisabledSearchIndex instanceof DisabledProjectIndex)) {
      projectIndexes.setSearchIndex(new DisabledProjectIndex(maybeDisabledSearchIndex), false);
    }

    return () -> {
      enableProjectIndexWrites();
      ProjectIndex maybeEnabledSearchIndex = projectIndexes.getSearchIndex();
      if (maybeEnabledSearchIndex instanceof DisabledProjectIndex) {
        projectIndexes.setSearchIndex(
            ((DisabledProjectIndex) maybeEnabledSearchIndex).unwrap(), false);
      }
    };
  }

  protected void disableProjectIndexWrites() {
    for (ProjectIndex i : projectIndexes.getWriteIndexes()) {
      if (!(i instanceof DisabledProjectIndex)) {
        projectIndexes.addWriteIndex(new DisabledProjectIndex(i));
      }
    }
  }

  protected void enableProjectIndexWrites() {
    for (ProjectIndex i : projectIndexes.getWriteIndexes()) {
      if (i instanceof DisabledProjectIndex) {
        projectIndexes.addWriteIndex(((DisabledProjectIndex) i).unwrap());
      }
    }
  }

  protected static Gson newGson() {
    return OutputFormat.JSON_COMPACT.newGson();
  }

  protected RevisionApi revision(PushOneCommit.Result r) throws Exception {
    return gApi.changes().id(r.getChangeId()).current();
  }

  protected void setUseSignedOffBy(InheritableBoolean value) throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      ProjectConfig config = projectConfigFactory.read(md);
      config.updateProject(p -> p.setBooleanConfig(BooleanProjectConfig.USE_SIGNED_OFF_BY, value));
      config.commit(md);
      projectCache.evict(config.getProject());
    }
  }

  protected void setRequireChangeId(InheritableBoolean value) throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      ProjectConfig config = projectConfigFactory.read(md);
      config.updateProject(p -> p.setBooleanConfig(BooleanProjectConfig.REQUIRE_CHANGE_ID, value));
      config.commit(md);
      projectCache.evict(config.getProject());
    }
  }

  protected void blockAnonymousRead() throws Exception {
    String allRefs = RefNames.REFS + "*";
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(allRefs).group(ANONYMOUS_USERS))
        .add(allow(Permission.READ).ref(allRefs).group(REGISTERED_USERS))
        .update();
  }

  protected PushOneCommit.Result pushTo(String ref) throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    return push.to(ref);
  }

  protected void approve(String id) throws Exception {
    gApi.changes().id(id).current().review(ReviewInput.approve());
  }

  protected void recommend(String id) throws Exception {
    gApi.changes().id(id).current().review(ReviewInput.recommend());
  }

  protected void assertSubmittedTogether(String chId, String... expected) throws Exception {
    List<ChangeInfo> actual = gApi.changes().id(chId).submittedTogether();
    SubmittedTogetherInfo info =
        gApi.changes().id(chId).submittedTogether(EnumSet.of(NON_VISIBLE_CHANGES));

    assertThat(info.nonVisibleChanges).isEqualTo(0);
    assertThat(Iterables.transform(actual, i1 -> i1.changeId))
        .containsExactly((Object[]) expected)
        .inOrder();
    assertThat(Iterables.transform(info.changes, i -> i.changeId))
        .containsExactly((Object[]) expected)
        .inOrder();
  }

  protected PatchSet getPatchSet(PatchSet.Id psId) {
    return changeDataFactory.create(project, psId.changeId()).patchSet(psId);
  }

  protected IdentifiedUser user(TestAccount testAccount) {
    return identifiedUserFactory.create(testAccount.id());
  }

  protected RevisionResource parseCurrentRevisionResource(String changeId) throws Exception {
    ChangeResource cr = parseChangeResource(changeId);
    int psId = cr.getChange().currentPatchSetId().get();
    return revisions.parse(cr, IdString.fromDecoded(Integer.toString(psId)));
  }

  protected RevisionResource parseRevisionResource(String changeId, int n) throws Exception {
    return revisions.parse(
        parseChangeResource(changeId), IdString.fromDecoded(Integer.toString(n)));
  }

  protected RevisionResource parseRevisionResource(PushOneCommit.Result r) throws Exception {
    PatchSet.Id psId = r.getPatchSetId();
    return parseRevisionResource(psId.changeId().toString(), psId.get());
  }

  protected ChangeResource parseChangeResource(String changeId) throws Exception {
    List<ChangeNotes> notes = changeFinder.find(changeId);
    assertThat(notes).hasSize(1);
    return changeResourceFactory.create(notes.get(0), atrScope.get().getUser());
  }

  protected RevCommit getHead(Repository repo, String name) throws Exception {
    try (RevWalk rw = new RevWalk(repo)) {
      Ref r = repo.exactRef(name);
      return rw.parseCommit(r.getObjectId());
    }
  }

  protected void assertMailReplyTo(Message message, String email) throws Exception {
    assertThat(message.headers()).containsKey("Reply-To");
    EmailHeader.String replyTo = (EmailHeader.String) message.headers().get("Reply-To");
    assertThat(replyTo.getString()).contains(email);
  }

  protected Map<BranchNameKey, ObjectId> fetchFromSubmitPreview(String changeId) throws Exception {
    try (BinaryResult result = gApi.changes().id(changeId).current().submitPreview()) {
      return fetchFromBundles(result);
    }
  }

  /**
   * Fetches each bundle into a newly cloned repository, then it applies the bundle, and returns the
   * resulting tree id.
   *
   * <p>Omits NoteDb meta refs.
   */
  protected Map<BranchNameKey, ObjectId> fetchFromBundles(BinaryResult bundles) throws Exception {
    assertThat(bundles.getContentType()).isEqualTo("application/x-zip");

    FileSystem fs = Jimfs.newFileSystem();
    Path previewPath = fs.getPath("preview.zip");
    try (OutputStream out = Files.newOutputStream(previewPath)) {
      bundles.writeTo(out);
    }
    Map<BranchNameKey, ObjectId> ret = new HashMap<>();
    try (FileSystem zipFs = FileSystems.newFileSystem(previewPath, (ClassLoader) null);
        DirectoryStream<Path> dirStream =
            Files.newDirectoryStream(Iterables.getOnlyElement(zipFs.getRootDirectories()))) {
      for (Path p : dirStream) {
        if (!Files.isRegularFile(p)) {
          continue;
        }
        String bundleName = p.getFileName().toString();
        int len = bundleName.length();
        assertThat(bundleName).endsWith(".git");
        String repoName = bundleName.substring(0, len - 4);
        Project.NameKey proj = Project.nameKey(repoName);
        TestRepository<?> localRepo = cloneProject(proj);

        try (InputStream bundleStream = Files.newInputStream(p);
            TransportBundleStream tbs =
                new TransportBundleStream(
                    localRepo.getRepository(), new URIish(bundleName), bundleStream)) {
          FetchResult fr =
              tbs.fetch(
                  NullProgressMonitor.INSTANCE,
                  Arrays.asList(new RefSpec("refs/*:refs/preview/*")));
          for (Ref r : fr.getAdvertisedRefs()) {
            String refName = r.getName();
            if (RefNames.isNoteDbMetaRef(refName)) {
              continue;
            }
            RevCommit c = localRepo.getRevWalk().parseCommit(r.getObjectId());
            ret.put(BranchNameKey.create(proj, refName), c.getTree().copy());
          }
        }
      }
    }
    assertThat(ret).isNotEmpty();
    return ret;
  }

  /** Assert that the given branches have the given tree ids. */
  protected void assertTrees(Project.NameKey proj, Map<BranchNameKey, ObjectId> trees)
      throws Exception {
    TestRepository<?> localRepo = cloneProject(proj);
    GitUtil.fetch(localRepo, "refs/*:refs/*");
    Map<BranchNameKey, RevTree> refValues = new HashMap<>();

    for (BranchNameKey b : trees.keySet()) {
      if (!b.project().equals(proj)) {
        continue;
      }

      Ref r = localRepo.getRepository().exactRef(b.branch());
      assertThat(r).isNotNull();
      RevWalk rw = localRepo.getRevWalk();
      RevCommit c = rw.parseCommit(r.getObjectId());
      refValues.put(b, c.getTree());

      assertThat(trees.get(b)).isEqualTo(refValues.get(b));
    }
    assertThat(refValues.keySet()).containsAnyIn(trees.keySet());
  }

  protected void assertDiffForNewFile(
      DiffInfo diff, RevCommit commit, String path, String expectedContentSideB) throws Exception {
    List<String> expectedLines = ImmutableList.copyOf(expectedContentSideB.split("\n", -1));

    assertThat(diff.binary).isNull();
    assertThat(diff.changeType).isEqualTo(ChangeType.ADDED);
    assertThat(diff.diffHeader).isNotNull();
    assertThat(diff.intralineStatus).isNull();
    assertThat(diff.webLinks).isNull();

    assertThat(diff.metaA).isNull();
    assertThat(diff.metaB).isNotNull();
    assertThat(diff.metaB.commitId).isEqualTo(commit.name());

    String expectedContentType = "text/plain";
    if (COMMIT_MSG.equals(path)) {
      expectedContentType = FileContentUtil.TEXT_X_GERRIT_COMMIT_MESSAGE;
    } else if (MERGE_LIST.equals(path)) {
      expectedContentType = FileContentUtil.TEXT_X_GERRIT_MERGE_LIST;
    }
    assertThat(diff.metaB.contentType).isEqualTo(expectedContentType);

    assertThat(diff.metaB.lines).isEqualTo(expectedLines.size());
    assertThat(diff.metaB.name).isEqualTo(path);
    assertThat(diff.metaB.webLinks).isNull();

    assertThat(diff.content).hasSize(1);
    DiffInfo.ContentEntry contentEntry = diff.content.get(0);
    assertThat(contentEntry.b).containsExactlyElementsIn(expectedLines).inOrder();
    assertThat(contentEntry.a).isNull();
    assertThat(contentEntry.ab).isNull();
    assertThat(contentEntry.common).isNull();
    assertThat(contentEntry.editA).isNull();
    assertThat(contentEntry.editB).isNull();
    assertThat(contentEntry.skip).isNull();
  }

  protected void assertPermitted(ChangeInfo info, String label, Integer... expected) {
    assertThat(info.permittedLabels).isNotNull();
    Collection<String> strs = info.permittedLabels.get(label);
    if (expected.length == 0) {
      assertThat(strs).isNull();
    } else {
      assertThat(strs.stream().map(s -> Integer.valueOf(s.trim())).collect(toList()))
          .containsExactlyElementsIn(Arrays.asList(expected));
    }
  }

  protected void assertPermissions(
      Project.NameKey project,
      GroupReference groupReference,
      String ref,
      boolean exclusive,
      String... permissionNames) {
    Optional<AccessSection> accessSection =
        projectCache
            .get(project)
            .orElseThrow(illegalState(project))
            .getConfig()
            .getAccessSection(ref);
    assertThat(accessSection).isPresent();
    for (String permissionName : permissionNames) {
      Permission permission = accessSection.get().getPermission(permissionName);
      assertPermission(permission, permissionName, exclusive, null);
      assertPermissionRule(
          permission.getRule(groupReference), groupReference, Action.ALLOW, false, 0, 0);
    }
  }

  protected void assertPermission(
      Permission permission,
      String expectedName,
      boolean expectedExclusive,
      @Nullable String expectedLabelName) {
    assertThat(permission).isNotNull();
    assertThat(permission.getName()).isEqualTo(expectedName);
    assertThat(permission.getExclusiveGroup()).isEqualTo(expectedExclusive);
    assertThat(permission.getLabel()).isEqualTo(expectedLabelName);
  }

  protected void assertPermissionRule(
      PermissionRule rule,
      GroupReference expectedGroupReference,
      Action expectedAction,
      boolean expectedForce,
      int expectedMin,
      int expectedMax) {
    assertThat(rule.getGroup()).isEqualTo(expectedGroupReference);
    assertThat(rule.getAction()).isEqualTo(expectedAction);
    assertThat(rule.getForce()).isEqualTo(expectedForce);
    assertThat(rule.getMin()).isEqualTo(expectedMin);
    assertThat(rule.getMax()).isEqualTo(expectedMax);
  }

  protected InternalGroup group(AccountGroup.UUID groupUuid) {
    InternalGroup group = groupCache.get(groupUuid).orElse(null);
    assertWithMessage(groupUuid.get()).that(group).isNotNull();
    return group;
  }

  protected GroupReference groupRef(AccountGroup.UUID groupUuid) {
    GroupDescription.Basic groupDescription = groupBackend.get(groupUuid);
    return GroupReference.create(groupDescription.getGroupUUID(), groupDescription.getName());
  }

  protected InternalGroup group(String groupName) {
    InternalGroup group = groupCache.get(AccountGroup.nameKey(groupName)).orElse(null);
    assertWithMessage(groupName).that(group).isNotNull();
    return group;
  }

  protected GroupReference groupRef(String groupName) {
    InternalGroup group = groupCache.get(AccountGroup.nameKey(groupName)).orElse(null);
    assertThat(group).isNotNull();
    return GroupReference.create(group.getGroupUUID(), group.getName());
  }

  protected AccountGroup.UUID groupUuid(String groupName) {
    return group(groupName).getGroupUUID();
  }

  protected InternalGroup adminGroup() {
    return group("Administrators");
  }

  protected GroupReference adminGroupRef() {
    return groupRef("Administrators");
  }

  protected AccountGroup.UUID adminGroupUuid() {
    return groupUuid("Administrators");
  }

  protected void assertGroupDoesNotExist(String groupName) {
    InternalGroup group = groupCache.get(AccountGroup.nameKey(groupName)).orElse(null);
    assertWithMessage(groupName).that(group).isNull();
  }

  protected void assertNotifyTo(TestAccount expected) {
    assertNotifyTo(expected.email(), expected.fullName());
  }

  protected void assertNotifyTo(String expectedEmail, String expectedFullname) {
    Address expectedAddress = Address.create(expectedFullname, expectedEmail);
    assertThat(sender.getMessages()).hasSize(1);
    Message m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(expectedAddress);
    assertThat(((EmailHeader.AddressList) m.headers().get("To")).getAddressList())
        .containsExactly(expectedAddress);
    assertThat(m.headers().get("Cc").isEmpty()).isTrue();
  }

  protected void assertNotifyCc(TestAccount expected) {
    assertNotifyCc(expected.getNameEmail());
  }

  protected void assertNotifyCc(String expectedEmail, String expectedFullname) {
    Address expectedAddress = Address.create(expectedFullname, expectedEmail);
    assertNotifyCc(expectedAddress);
  }

  protected void assertNotifyCc(Address expectedAddress) {
    assertThat(sender.getMessages()).hasSize(1);
    Message m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(expectedAddress);
    assertThat(m.headers().get("To").isEmpty()).isTrue();
    assertThat(((EmailHeader.AddressList) m.headers().get("Cc")).getAddressList())
        .containsExactly(expectedAddress);
  }

  protected void assertNotifyBcc(TestAccount expected) {
    assertThat(sender.getMessages()).hasSize(1);
    Message m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(expected.getNameEmail());
    assertThat(m.headers().get("To").isEmpty()).isTrue();
    assertThat(m.headers().get("Cc").isEmpty()).isTrue();
  }

  protected void assertNotifyBcc(String expectedEmail, String expectedFullName) {
    assertThat(sender.getMessages()).hasSize(1);
    Message m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(Address.create(expectedFullName, expectedEmail));
    assertThat(m.headers().get("To").isEmpty()).isTrue();
    assertThat(m.headers().get("Cc").isEmpty()).isTrue();
  }

  protected interface ProjectWatchInfoConfiguration {
    void configure(ProjectWatchInfo pwi);
  }

  protected void watch(String project, ProjectWatchInfoConfiguration config)
      throws RestApiException {
    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = project;
    config.configure(pwi);
    gApi.accounts().self().setWatchedProjects(ImmutableList.of(pwi));
  }

  protected void watch(PushOneCommit.Result r, ProjectWatchInfoConfiguration config)
      throws RestApiException {
    watch(r.getChange().project().get(), config);
  }

  protected void watch(String project, String filter) throws RestApiException {
    watch(
        project,
        pwi -> {
          pwi.filter = filter;
          pwi.notifyAbandonedChanges = true;
          pwi.notifyNewChanges = true;
          pwi.notifyAllComments = true;
        });
  }

  protected void watch(String project) throws RestApiException {
    watch(project, (String) null);
  }

  protected void assertContent(PushOneCommit.Result pushResult, String path, String expectedContent)
      throws Exception {
    BinaryResult bin =
        gApi.changes()
            .id(pushResult.getChangeId())
            .revision(pushResult.getCommit().name())
            .file(path)
            .content();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    bin.writeTo(os);
    String res = new String(os.toByteArray(), UTF_8);
    assertThat(res).isEqualTo(expectedContent);
  }

  protected RevCommit createNewCommitWithoutChangeId(String branch, String file, String content)
      throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk walk = new RevWalk(repo)) {
      Ref ref = repo.exactRef(branch);
      RevCommit tip = null;
      if (ref != null) {
        tip = walk.parseCommit(ref.getObjectId());
      }
      TestRepository<?> testSrcRepo = new TestRepository<>(repo);
      TestRepository<?>.BranchBuilder builder = testSrcRepo.branch(branch);
      RevCommit revCommit =
          tip == null
              ? builder.commit().message("commit 1").add(file, content).create()
              : builder.commit().parent(tip).message("commit 1").add(file, content).create();
      assertThat(GitUtil.getChangeId(testSrcRepo, revCommit)).isEmpty();
      return revCommit;
    }
  }

  protected RevCommit parseCurrentRevision(RevWalk rw, String changeId) throws Exception {
    return rw.parseCommit(
        ObjectId.fromString(get(changeId, ListChangesOption.CURRENT_REVISION).currentRevision));
  }

  protected void configLabel(String label, LabelFunction func) throws Exception {
    configLabel(label, func, ImmutableList.of());
  }

  protected void configLabel(String label, LabelFunction func, List<String> refPatterns)
      throws Exception {
    configLabel(
        project,
        label,
        func,
        refPatterns,
        value(1, "Passes"),
        value(0, "No score"),
        value(-1, "Failed"));
  }

  protected void configLabel(
      Project.NameKey project, String label, LabelFunction func, LabelValue... value)
      throws Exception {
    configLabel(project, label, func, ImmutableList.of(), value);
  }

  private void configLabel(
      Project.NameKey project,
      String label,
      LabelFunction func,
      List<String> refPatterns,
      LabelValue... value)
      throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder labelType = label(label, value).toBuilder();
      labelType.setFunction(func);
      labelType.setRefPatterns(ImmutableList.copyOf(refPatterns));
      u.getConfig().upsertLabelType(labelType.build());
      u.save();
    }
  }

  protected void enableCreateNewChangeForAllNotInTarget() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateProject(
              p ->
                  p.setBooleanConfig(
                      BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET,
                      InheritableBoolean.TRUE));
      u.save();
    }
  }

  protected ProjectConfigUpdate updateProject(Project.NameKey projectName) throws Exception {
    return new ProjectConfigUpdate(projectName);
  }

  protected class ProjectConfigUpdate implements AutoCloseable {
    private final ProjectConfig projectConfig;
    private MetaDataUpdate metaDataUpdate;

    private ProjectConfigUpdate(Project.NameKey projectName) throws Exception {
      metaDataUpdate = metaDataUpdateFactory.create(projectName);
      projectConfig = projectConfigFactory.read(metaDataUpdate);
    }

    public ProjectConfig getConfig() {
      return projectConfig;
    }

    public void save() throws Exception {
      metaDataUpdate.setAuthor(identifiedUserFactory.create(admin.id()));
      projectConfig.commit(metaDataUpdate);
      metaDataUpdate.close();
      metaDataUpdate = null;
      projectCache.evict(projectConfig.getProject());
    }

    @Override
    public void close() {
      if (metaDataUpdate != null) {
        metaDataUpdate.close();
      }
    }
  }

  protected List<RevCommit> getChangeMetaCommitsInReverseOrder(Change.Id changeId)
      throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repo)) {
      revWalk.sort(RevSort.TOPO);
      revWalk.sort(RevSort.REVERSE);
      Ref metaRef = repo.exactRef(RefNames.changeMetaRef(changeId));
      revWalk.markStart(revWalk.parseCommit(metaRef.getObjectId()));
      return Lists.newArrayList(revWalk);
    }
  }

  protected List<CommentInfo> getChangeSortedComments(int changeNum) throws Exception {
    List<CommentInfo> comments = new ArrayList<>();
    Map<String, List<CommentInfo>> commentsMap = gApi.changes().id(changeNum).comments(false);
    for (Map.Entry<String, List<CommentInfo>> e : commentsMap.entrySet()) {
      for (CommentInfo c : e.getValue()) {
        c.path = e.getKey(); // Set the comment's path field.
        comments.add(c);
      }
    }
    comments.sort(Comparator.comparing(c -> c.id));
    return comments;
  }

  protected AutoCloseable installPlugin(String pluginName, Class<? extends Module> sysModuleClass)
      throws Exception {
    return installPlugin(pluginName, sysModuleClass, null, null);
  }

  protected AutoCloseable installPlugin(
      String pluginName,
      @Nullable Class<? extends Module> sysModuleClass,
      @Nullable Class<? extends Module> httpModuleClass,
      @Nullable Class<? extends Module> sshModuleClass)
      throws Exception {
    checkStatic(sysModuleClass);
    checkStatic(httpModuleClass);
    checkStatic(sshModuleClass);
    TestServerPlugin plugin =
        new TestServerPlugin(
            pluginName,
            "http://example.com/" + pluginName,
            pluginUserFactory.create(pluginName),
            getClass().getClassLoader(),
            sysModuleClass != null ? sysModuleClass.getName() : null,
            httpModuleClass != null ? httpModuleClass.getName() : null,
            sshModuleClass != null ? sshModuleClass.getName() : null,
            sitePaths.data_dir.resolve(pluginName));
    plugin.start(pluginGuiceEnvironment);
    pluginGuiceEnvironment.onStartPlugin(plugin);
    return () -> {
      plugin.stop(pluginGuiceEnvironment);
      pluginGuiceEnvironment.onStopPlugin(plugin);
    };
  }

  private static void checkStatic(@Nullable Class<? extends Module> moduleClass) {
    if (moduleClass != null) {
      checkArgument(
          (moduleClass.getModifiers() & Modifier.STATIC) != 0,
          "module must be static: %s",
          moduleClass.getName());
    }
  }
}
