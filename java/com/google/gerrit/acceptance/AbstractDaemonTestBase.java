// Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.truth.TruthJUnit.assume;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.FakeTicker;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context;
import com.google.gerrit.acceptance.config.ConfigAnnotationParser;
import com.google.gerrit.acceptance.config.GerritSystemProperty;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.acceptance.testsuite.request.SshSessionFactory;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.util.git.DelegateSystemReader;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.SshMode;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.io.File;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

@RunWith(ConfigSuite.class)
public abstract class AbstractDaemonTestBase {
  public static final Pattern UNSAFE_PROJECT_NAME = Pattern.compile("[^a-zA-Z0-9._/-]+");

  /**
   * Test methods without special annotations will use a common server for efficiency reasons. The
   * server is torn down after the test class is done.
   */
  private static GerritServer commonServer;

  private static Description firstTest;

  @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

  @ConfigSuite.Parameter public Config baseConfig;
  @ConfigSuite.Name private String configName;

  @Inject protected AllProjectsName allProjects;
  @Inject protected AllUsersName allUsers;
  @Inject protected ProjectResetter.Builder.Factory projectResetter;

  @Inject private InProcessProtocol inProcessProtocol;

  private ProjectResetter resetter;

  private boolean testRequiresSsh;
  protected GerritServer server;

  protected SshSession adminSshSession;
  protected SshSession userSshSession;

  @Inject protected TestTicker testTicker;

  private List<Repository> toClose;
  private String systemTimeZone;
  private SystemReader oldSystemReader;

  protected Description description;
  protected GerritServer.Description testMethodDescription;

  protected String resourcePrefix;

  protected TestAccount admin;
  protected TestAccount user;
  @Inject protected AccountCreator accountCreator;

  @Inject private AccountIndexer accountIndexer;
  protected RestSession adminRestSession;
  protected RestSession userRestSession;
  protected RestSession anonymousRestSession;

  @Inject protected AcceptanceTestRequestScope atrScope;
  @Inject protected IdentifiedUser.GenericFactory identifiedUserFactory;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Inject protected GitRepositoryManager repoManager;

  /** {@link Ticker} implementation for mocking without restarting GerritServer */
  public static class TestTicker extends Ticker {
    Ticker actualTicker;

    public TestTicker() {
      useDefaultTicker();
    }

    /** Switches to system ticker */
    @CanIgnoreReturnValue
    public Ticker useDefaultTicker() {
      this.actualTicker = Ticker.systemTicker();
      return actualTicker;
    }

    /** Switches to {@link FakeTicker} */
    @CanIgnoreReturnValue
    public FakeTicker useFakeTicker() {
      if (!(this.actualTicker instanceof FakeTicker)) {
        this.actualTicker = new FakeTicker();
      }
      return (FakeTicker) actualTicker;
    }

    @Override
    public long read() {
      return actualTicker.read();
    }
  }

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
                AbstractDaemonTestBase.this.resetter = resetter;
                base.evaluate();
              } finally {
                AbstractDaemonTestBase.this.resetter = null;
                afterTest();
              }
            }
          };
        }
      };

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

  protected void beforeTest(Description description) throws Exception {
    // SystemReader must be overridden before creating any repos, since they read the user/system
    // configs at initialization time, and are then stored in the RepositoryCache forever.
    oldSystemReader = setFakeSystemReader(temporaryFolder.getRoot());

    this.description = description;
    GerritServer.Description classDesc =
        GerritServer.Description.forTestClass(description, configName);
    GerritServer.Description methodDesc =
        GerritServer.Description.forTestMethod(description, configName);
    testMethodDescription = methodDesc;

    if (methodDesc.systemProperties() != null) {
      ConfigAnnotationParser.parse(methodDesc.systemProperties());
    }

    if (methodDesc.systemProperty() != null) {
      ConfigAnnotationParser.parse(methodDesc.systemProperty());
    }

    testRequiresSsh = classDesc.useSshAnnotation() || methodDesc.useSshAnnotation();
    if (!testRequiresSsh) {
      baseConfig.setString("sshd", null, "listenAddress", "off");
    }

    baseConfig.unset("gerrit", null, "canonicalWebUrl");
    baseConfig.unset("httpd", null, "listenUrl");

    baseConfig.setInt("index", null, "batchThreads", -1);

    if (enableExperimentsRejectImplicitMergesOnMerge()) {
      // When changes are merged/submitted - reject the operation if there is an implicit merge (
      // even if rejectImplicitMerges is disabled in the project config).
      baseConfig.setStringList(
          "experiments",
          null,
          "enabled",
          ImmutableList.of(
              "GerritBackendFeature__check_implicit_merges_on_merge",
              "GerritBackendFeature__reject_implicit_merges_on_merge",
              "GerritBackendFeature__always_reject_implicit_merges_on_merge"));
    }

    initServer(classDesc, methodDesc);

    server.getTestInjector().injectMembers(this);
    Transport.register(inProcessProtocol);
    toClose = Collections.synchronizedList(new ArrayList<>());

    setUpDatabase(classDesc);

    // Set the clock step last, so that the test setup isn't consuming any timestamps after the
    // clock has been set.
    setTimeSettings(classDesc.useSystemTime(), classDesc.useClockStep(), classDesc.useTimezone());
    setTimeSettings(
        methodDesc.useSystemTime(), methodDesc.useClockStep(), methodDesc.useTimezone());
  }

  protected boolean enableExperimentsRejectImplicitMergesOnMerge() {
    // By default any attempt to make an explicit merge is rejected. This allows to check
    // that existing workflows continue to work even if gerrit rejects implicit merges on merge.
    return true;
  }

  protected void setUpDatabase(GerritServer.Description classDesc) throws Exception {
    admin = accountCreator.admin();
    user = accountCreator.user1();

    // Evict and reindex accounts in case tests modify them.
    reindexAccount(admin.id());
    reindexAccount(user.id());

    adminRestSession = new RestSession(server, admin);
    userRestSession = new RestSession(server, user);
    anonymousRestSession = new RestSession(server, null);

    initSsh();

    String testMethodName = description.getMethodName();
    resourcePrefix =
        UNSAFE_PROJECT_NAME
            .matcher(description.getClassName() + "_" + testMethodName + "_")
            .replaceAll("");

    setRequestScope(admin);
    createProject(description, !classDesc.skipProjectClone());
  }

  protected abstract void createProject(Description description, boolean cloneProject)
      throws Exception;

  public void reindexAccount(Account.Id accountId) {
    accountIndexer.index(accountId);
  }

  protected void initServer(GerritServer.Description classDesc, GerritServer.Description methodDesc)
      throws Exception {
    Module module = createModule();
    Module auditModule = createAuditModule();
    Module sshModule = createSshModule();
    if (classDesc.equals(methodDesc) && !classDesc.sandboxed() && !methodDesc.sandboxed()) {
      if (commonServer == null) {
        commonServer =
            GerritServer.initAndStart(
                temporaryFolder, classDesc, baseConfig, module, auditModule, sshModule);
      }
      server = commonServer;
    } else {
      server =
          GerritServer.initAndStart(
              temporaryFolder, methodDesc, baseConfig, module, auditModule, sshModule);
    }
  }

  /** Override to bind an additional Guice module */
  public Module createModule() {
    return null;
  }

  /** Override to bind an alternative audit Guice module */
  public Module createAuditModule() {
    return null;
  }

  /** Override to bind an additional Guice module for SSH injector */
  public Module createSshModule() {
    return null;
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

  protected void initSsh() throws Exception {
    if (testRequiresSsh
        && SshMode.useSsh()
        && (adminSshSession == null || userSshSession == null)) {
      // Create Ssh sessions
      SshSessionFactory.initSsh();
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

  /** Sets up {@code account} as a caller in tests. */
  public void setRequestScope(TestAccount account) {
    Context ctx = newRequestContext(account);
    atrScope.set(ctx);
  }

  protected Context newRequestContext(TestAccount account) {
    requestScopeOperations.setApiUser(account.id());
    return atrScope.get();
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

    GerritServer.Description methodDesc =
        GerritServer.Description.forTestMethod(description, configName);
    if (methodDesc.systemProperties() != null) {
      for (GerritSystemProperty sysProp : methodDesc.systemProperties().value()) {
        System.clearProperty(sysProp.name());
      }
    }

    if (methodDesc.systemProperty() != null) {
      System.clearProperty(methodDesc.systemProperty().name());
    }

    SystemReader.setInstance(oldSystemReader);
    oldSystemReader = null;
    // Set useDefaultTicker in afterTest, so the next beforeTest will use the default ticker
    testTicker.useDefaultTicker();
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

  @ConfigSuite.AfterConfig
  public static void stopCommonServer() throws Exception {
    if (commonServer != null) {
      try {
        commonServer.close();
      } catch (Exception e) {
        throw new AssertionError(
            "Error stopping common server in "
                + (firstTest != null ? firstTest.getTestClass().getName() : "unknown test class"),
            e);
      } finally {
        commonServer = null;
      }
    }
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

  protected void restart() throws Exception {
    server = GerritServer.restart(server, createModule(), createSshModule());
    server.getTestInjector().injectMembers(this);
    if (resetter != null) {
      server.getTestInjector().injectMembers(resetter);
    }
    initSsh();
  }
}
