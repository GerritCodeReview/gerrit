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
import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.FakeTicker;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.AbstractDaemonTest.TestSetupRule;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context;
import com.google.gerrit.acceptance.config.ConfigAnnotationParser;
import com.google.gerrit.acceptance.config.GerritSystemProperty;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.acceptance.testsuite.request.SshSessionFactory;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.RawInput;
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
import com.google.inject.Injector;
import com.google.inject.Module;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import org.apache.http.Header;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
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


public class AbstractDaemonTestBase implements TestSetupRule {

  /**
   * Test methods without special annotations will use a common server for efficiency reasons. The
   * server is torn down after the test class is done.
   */
  private static GerritServer commonServer;

  private static Description firstTest;

  @Inject private InProcessProtocol inProcessProtocol;

  private boolean testRequiresSsh;
  private GerritServer server;

  @Inject private TestTicker testTicker;

  private List<Repository> toClose;
  private String systemTimeZone;
  private SystemReader oldSystemReader;

  protected Description description;
  protected GerritServer.Description testMethodDescription;

  @Inject protected AcceptanceTestRequestScope atrScope;
  @Inject protected IdentifiedUser.GenericFactory identifiedUserFactory;


  @Inject private GitRepositoryManager repoManager;

  private Callable<Config> getBaseConfig;

  private TestSshRule testSshRule;

  private TemporaryFolder temporaryFolder;

  public AbstractDaemonTestBase(TemporaryFolder temporaryFolder, Callable<Config> getBaseConfig) {
    this.getBaseConfig = getBaseConfig;
    this.temporaryFolder = temporaryFolder;
  }
    @Override
    public Statement apply(Statement statement, Description description) {
      return testRunner.apply(statement, description);
    }

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

  private TestRule testRunner =
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

              try {
                assumeSshIfRequired();
                base.evaluate();
              } finally {
                afterTest();
              }
            }
          };
        }
      };

  protected void beforeTest(Description description) throws Exception {
    // SystemReader must be overridden before creating any repos, since they read the user/system
    // configs at initialization time, and are then stored in the RepositoryCache forever.
    oldSystemReader = setFakeSystemReader(temporaryFolder.getRoot());

    this.description = description;
    GerritServer.Description classDesc =
        GerritServer.Description.forTestClass(description);
    GerritServer.Description methodDesc =
        GerritServer.Description.forTestMethod(description);
    testMethodDescription = methodDesc;

    if (methodDesc.systemProperties() != null) {
      ConfigAnnotationParser.parse(methodDesc.systemProperties());
    }

    if (methodDesc.systemProperty() != null) {
      ConfigAnnotationParser.parse(methodDesc.systemProperty());
    }

    testRequiresSsh = classDesc.useSshAnnotation() || methodDesc.useSshAnnotation();
    if (!testRequiresSsh) {
      getBaseConfig.call().setString("sshd", null, "listenAddress", "off");
    }

    getBaseConfig.call().unset("gerrit", null, "canonicalWebUrl");
    getBaseConfig.call().unset("httpd", null, "listenUrl");

    getBaseConfig.call().setInt("index", null, "batchThreads", -1);

    if (enableExperimentsRejectImplicitMergesOnMerge()) {
      // When changes are merged/submitted - reject the operation if there is an implicit merge (
      // even if rejectImplicitMerges is disabled in the project config).
      getBaseConfig.call().setStringList(
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
    initSsh();
  }

  @Override
  public RestSessionInterface createSessionForAccount(TestAccount account) {
    return new RestSession(server, account);
  }

  @Override
  public RestSessionInterface createAnonymousSession() {
    return new RestSession(server, null);
  }

  public interface RestSessionInterface {
    RestResponse get(String endPoint) throws IOException;
    RestResponse getJsonAccept(String endPoint) throws IOException;
    RestResponse getWithHeaders(String endPoint, Header... headers) throws IOException;
    RestResponse head(String endPoint) throws IOException;
    RestResponse put(String endPoint) throws IOException;
    RestResponse put(String endPoint, Object content) throws IOException;
    RestResponse putWithHeaders(String endPoint, Header... headers) throws IOException;
    RestResponse putWithHeaders(String endPoint, Object content, Header... headers) throws IOException;
    RestResponse putRaw(String endPoint, RawInput stream) throws IOException;
    RestResponse post(String endPoint) throws IOException;
    RestResponse post(String endPoint, Object content) throws IOException;
    RestResponse postWithHeaders(String endPoint, Object content, Header... headers) throws IOException;
    RestResponse delete(String endPoint) throws IOException;
    RestResponse deleteWithHeaders(String endPoint, Header... headers) throws IOException;
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
                temporaryFolder, classDesc, getBaseConfig.call(), module, auditModule, sshModule);
      }
      server = commonServer;
    } else {
      server =
          GerritServer.initAndStart(
              temporaryFolder, methodDesc, getBaseConfig.call(), module, auditModule, sshModule);
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

  // @Before
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
        GerritServer.Description.forTestMethod(description);
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

  @Override
  public TestRepository<InMemoryRepository> cloneProject(
      Project.NameKey p, TestAccount testAccount) throws Exception {
    return GitUtil.cloneProject(p, registerRepoConnection(p, testAccount));
  }

  private void initSsh() throws Exception {
    if (testSshRule != null) {
      testSshRule.initSsh();
    }
  }
  private void closeSsh() {
    if(testSshRule != null) {
      testSshRule.closeSsh();
    }
  }

  @Override
  public void setSshRule(TestSshRule testSshRule) {
    this.testSshRule = testSshRule;
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

  public static void configSuiteAfterConfig() {
    stopCommonServer();
  }
  public static void stopCommonServer() {
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

//  protected void restartAsSlave() throws Exception {
//    closeSsh();
//    server = GerritServer.restartAsSlave(server);
//    server.getTestInjector().injectMembers(this);
//    if (resetter != null) {
//      server.getTestInjector().injectMembers(resetter);
//    }
//    initSsh();
//  }

  @Override
  public Injector getTestInjector() {
    return server.getTestInjector();
  }

  public static class TestDescriptionRule implements TestRule {
    private GerritServer.Description classDesc;
    private GerritServer.Description methodDesc;

    private Description testDescription;

    @Override
    public Statement apply(Statement statement, Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          classDesc = GerritServer.Description.forTestClass(description);
          methodDesc = GerritServer.Description.forTestMethod(description);
          testDescription = description;
          statement.evaluate();
          testDescription = null;
          classDesc = null;
          methodDesc = null;
        }
      };
    }

    public GerritServer.Description classDesc() {
      checkState(classDesc != null, "The classDesc is null - probably the method is called outside of the test.");
      return classDesc;
    }
    public GerritServer.Description methodDesc() {
      checkState(methodDesc != null, "The methodDesc is null - probably the method is called outside of the test.");
      return methodDesc;
    }

    public String testClassName() {
      checkState(testDescription != null, "The testDescription is null - probably the method is called outside of the test.");
      return testDescription.getClassName();
    }

    public boolean skipProjectClone() {
      return classDesc().skipProjectClone();
    }

    public boolean verifyNoPiiInChangeNotes() {
      return methodDesc().verifyNoPiiInChangeNotes();
    }
  }

  public static class TestSshRule {
    protected SshSession adminSshSession;
    protected SshSession userSshSession;

    public void initSsh() throws Exception {
//      if (testRequiresSsh
//          && SshMode.useSsh()
//          && (adminSshSession == null || userSshSession == null)) {
//        // Create Ssh sessions
//        SshSessionFactory.initSsh();
//        Context ctx = newRequestContext(user);
//        atrScope.set(ctx);
//        userSshSession = ctx.getSession();
//        userSshSession.open();
//        ctx = newRequestContext(admin);
//        atrScope.set(ctx);
//        adminSshSession = ctx.getSession();
//        adminSshSession.open();
//      }
    }

    public void closeSsh() {
      if (adminSshSession != null) {
        adminSshSession.close();
        adminSshSession = null;
      }
      if (userSshSession != null) {
        userSshSession.close();
        userSshSession = null;
      }
    }
  }

  public interface ConfigSuiteListener {

  }
}
