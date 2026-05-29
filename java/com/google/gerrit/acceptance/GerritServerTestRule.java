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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.TruthJUnit.assume;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.acceptance.GerritServer.TestSshServerAddress;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import com.google.gerrit.acceptance.testsuite.request.SshSessionFactory;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.testing.GitRepositoryReferenceCountingManager;
import com.google.gerrit.testing.SshMode;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class GerritServerTestRule implements ServerTestRule {
  /**
   * Test methods without special annotations will use a common server for efficiency reasons. The
   * server is torn down after the test class is done or when the config is changed.
   */
  private static GerritServer commonServer;

  private static Description firstTest;

  private final TemporaryFolder temporaryFolder;
  @Nullable private final Supplier<Module> testSysModule;
  @Nullable private final Supplier<Module> testAuditModule;
  @Nullable private final Supplier<Module> testSshModule;
  private final TestConfigRule config;

  @Inject private TestSshKeys sshKeys;
  @Inject @Nullable @TestSshServerAddress private InetSocketAddress sshAddress;

  @Inject private AccountOperations accountOperations;

  private boolean sshInitialized;

  private final HashMap<RequestContext, SshSession> sshSessionByContext = new HashMap<>();

  public GerritServer server;

  public GerritServerTestRule(
      TestConfigRule config,
      TemporaryFolder temporaryFolder,
      @Nullable Supplier<Module> testSysModule,
      @Nullable Supplier<Module> testAuditModule,
      @Nullable Supplier<Module> testSshModule) {
    this.config = config;
    this.testSysModule = testSysModule;
    this.testAuditModule = testAuditModule;
    this.testSshModule = testSshModule;
    this.temporaryFolder = temporaryFolder;
  }

  @Override
  public Statement apply(Statement statement, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        if (firstTest == null) {
          firstTest = description;
        }
        if (config.testRequiresSsh()) {
          // If the test uses ssh, we use assume() to make sure ssh is enabled on
          // the test suite. JUnit will skip tests annotated with @UseSsh if we
          // disable them using the command line flag.
          assume().that(SshMode.useSsh()).isTrue();
        }
        statement.evaluate();
        afterTest();
      }
    };
  }

  public void afterTest() throws Exception {
    closeSsh();
    if (server != commonServer) {
      server.close();
      server = null;
    }
  }

  @Override
  public void initServer() throws Exception {

    if (config.classDescription().equals(config.methodDescription())
        && !config.classDescription().sandboxed()
        && !config.methodDescription().sandboxed()) {
      if (commonServer == null) {
        commonServer =
            GerritServer.initAndStart(
                temporaryFolder,
                config.classDescription(),
                config.baseConfig(),
                testSysModule.get(),
                testAuditModule.get(),
                testSshModule.get());
      }
      server = commonServer;
    } else {
      server =
          GerritServer.initAndStart(
              temporaryFolder,
              config.methodDescription(),
              config.baseConfig(),
              testSysModule.get(),
              testAuditModule.get(),
              testSshModule.get());
    }

    GitRepositoryManager repositoryManager =
        server.testInjector.getInstance(GitRepositoryManager.class);
    if (repositoryManager
        instanceof GitRepositoryReferenceCountingManager repositoryCountingManager) {
      repositoryCountingManager.init(config.description());
    }
    getTestInjector().injectMembers(this);
  }

  @Override
  public void initSsh() throws Exception {
    if (config.testRequiresSsh() && SshMode.useSsh()) {
      checkState(!sshInitialized, "The ssh has been alread initialized. Call closeSsh first.");
      // Create Ssh sessions
      SshSessionFactory.initSsh();
      sshInitialized = true;
    }
  }

  @Override
  public boolean sshInitialized() {
    return sshInitialized;
  }

  @Override
  public SshSession getOrCreateSshSessionForContext(RequestContext ctx) {
    checkState(
        config.testRequiresSsh(),
        "The test or the test class must be annotated with @UseSsh to use this method.");
    return sshSessionByContext.computeIfAbsent(
        ctx,
        (c) ->
            SshSessionFactory.createSession(
                sshKeys,
                sshAddress,
                accountOperations.account(ctx.getUser().getAccountId()).get()));
  }

  /**
   * This method is only required for some tests and is not a part of interface.
   *
   * <p>After restarting the server with this method, the caller can still get exit value of the
   * last command executed before restarting (using non-closed sessions). This is used in
   * SshDaemonIT tests.
   */
  public void restartKeepSessionOpen() throws Exception {
    checkState(
        server != commonServer,
        "The commonServer can't be restarted; to use this method, the test must be @Sandboxed");
    server = GerritServer.restart(server, testSysModule.get(), testSshModule.get());
    getTestInjector().injectMembers(this);
  }

  @Override
  public void restart() throws Exception {
    checkState(
        server != commonServer,
        "The commonServer can't be restarted; to use this method, the test must be @Sandboxed");
    closeSsh();
    server = GerritServer.restart(server, testSysModule.get(), testSshModule.get());
    getTestInjector().injectMembers(this);
    initSsh();
  }

  @Override
  public void restartAsSlave() throws Exception {
    checkState(
        server != commonServer,
        "The commonServer can't be restarted; to use this method, the test must be @Sandboxed");
    closeSsh();
    server = GerritServer.restartAsSlave(server);
    getTestInjector().injectMembers(this);
    initSsh();
  }

  protected void closeSsh() {
    sshSessionByContext.values().forEach(SshSession::close);
    sshSessionByContext.clear();
    sshInitialized = false;
  }

  @Override
  public Injector getTestInjector() {
    return server.getTestInjector();
  }

  @Override
  public Optional<Injector> getHttpdInjector() {
    return server.getHttpdInjector();
  }

  @Override
  public RestSession createRestSession(@Nullable TestAccount account) {
    return new GerritServerRestSession(server, account);
  }

  @Nullable
  @Override
  public ProjectResetter createProjectResetter(
      BiFunction<AllProjectsName, AllUsersName, ProjectResetter.Config> resetConfigSupplier)
      throws Exception {
    // Only commonServer can be shared between tests and should be restored after each
    // test. It can be that the commonServer is started, but a test actually don't use
    // it and instead the test uses a separate server instance.
    // In this case, the separate server is stopped after each test and so doesn't require
    // cleanup (for simplicity, the commonServer is always cleaned up even if it is not
    // used in a test).
    if (commonServer == null) {
      return null;
    }
    Injector testInjector = commonServer.testInjector;
    ProjectResetter.Config config =
        requireNonNull(
            resetConfigSupplier.apply(
                testInjector.getInstance(AllProjectsName.class),
                testInjector.getInstance(AllUsersName.class)));
    ProjectResetter.Builder.Factory projectResetterFactory =
        testInjector.getInstance(ProjectResetter.Builder.Factory.class);
    return projectResetterFactory != null ? projectResetterFactory.builder().build(config) : null;
  }

  @Override
  public boolean isReplica() {
    return server.isReplica();
  }

  @Override
  public Optional<InetSocketAddress> getHttpAddress() {
    return server.getHttpAddress();
  }

  @Override
  public String getGitUrl() {
    return server.getUrl();
  }

  @Override
  public boolean isUsernameSupported() {
    return true;
  }

  public static void afterConfigChanged() {
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
}
