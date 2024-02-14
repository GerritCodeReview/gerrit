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

import com.google.gerrit.acceptance.GerritServer.TestSshServerAddress;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.acceptance.testsuite.request.SshSessionFactory;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.testing.SshMode;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.function.Supplier;
import org.eclipse.jgit.lib.Config;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class GerritServerTestRule implements TestRule {
  /**
   * Test methods without special annotations will use a common server for efficiency reasons. The
   * server is torn down after the test class is done or when the config is changed.
   */
  private static GerritServer commonServer;

  private static Description firstTest;

  private TemporaryFolder temporaryFolder;
  @Nullable private Supplier<Module> testSysModule;
  @Nullable private Supplier<Module> testAuditModule;
  @Nullable private Supplier<Module> testSshModule;

  private SshSession adminSshSession;
  private SshSession userSshSession;

  @Inject protected TestSshKeys sshKeys;
  @Inject @Nullable @TestSshServerAddress private InetSocketAddress sshAddress;

  @Inject private AccountOperations accountOperations;

  @Inject private RequestScopeOperations requestScopeOperations;

  private final HashMap<RequestContext, SshSession> sshSessionByContext = new HashMap<>();

  private GerritServer server;
  public boolean testRequiresSsh;

  public AbstractDaemonTest test;

  public GerritServerTestRule(
      AbstractDaemonTest test,
      TemporaryFolder temporaryFolder,
      @Nullable Supplier<Module> testSysModule,
      @Nullable Supplier<Module> testAuditModule,
      @Nullable Supplier<Module> testSshModule) {
    this.testSysModule = testSysModule;
    this.testAuditModule = testAuditModule;
    this.testSshModule = testSshModule;
    this.temporaryFolder = temporaryFolder;
    this.test = test;
  }

  @Override
  public Statement apply(Statement statement, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        if (firstTest == null) {
          firstTest = description;
        }
        statement.evaluate();
      }
    };
  }

  public void beforeTest() {
    if (testRequiresSsh) {
      // If the test uses ssh, we use assume() to make sure ssh is enabled on
      // the test suite. JUnit will skip tests annotated with @UseSsh if we
      // disable them using the command line flag.
      assume().that(SshMode.useSsh()).isTrue();
    }
  }

  public void afterTest() throws Exception {
    closeSsh();
    if (server != commonServer) {
      server.close();
      server = null;
    }
  }

  public void initServer(
      Config baseConfig, GerritServer.Description classDesc, GerritServer.Description methodDesc)
      throws Exception {
    if (classDesc.equals(methodDesc) && !classDesc.sandboxed() && !methodDesc.sandboxed()) {
      if (commonServer == null) {
        commonServer =
            GerritServer.initAndStart(
                temporaryFolder,
                classDesc,
                baseConfig,
                testSysModule.get(),
                testAuditModule.get(),
                testSshModule.get());
      }
      server = commonServer;
    } else {
      server =
          GerritServer.initAndStart(
              temporaryFolder,
              methodDesc,
              baseConfig,
              testSysModule.get(),
              testAuditModule.get(),
              testSshModule.get());
    }
  }

  public void initSsh(TestAccount admin, TestAccount user) throws Exception {
    if (testRequiresSsh
        && SshMode.useSsh()
        && (adminSshSession == null || userSshSession == null)) {
      // Create Ssh sessions
      SshSessionFactory.initSsh();
      try (ManualRequestContext ctx = requestScopeOperations.setNestedApiUser(user.id())) {
        userSshSession = getOrCreateSshSessionForContext(ctx);
        // The session doesn't store any reference to the context and it remains open after the ctx
        // is closed.
        userSshSession.open();
      }

      try (ManualRequestContext ctx = requestScopeOperations.setNestedApiUser(admin.id())) {
        adminSshSession = getOrCreateSshSessionForContext(ctx);
        // The session doesn't store any reference to the context and it remains open after the ctx
        // is closed.
        adminSshSession.open();
      }
    }
  }

  protected SshSession getOrCreateSshSessionForContext(RequestContext ctx) {
    checkState(
        testRequiresSsh,
        "The test or the test class must be annotated with @UseSsh to use this method.");
    return sshSessionByContext.computeIfAbsent(
        ctx,
        (c) ->
            SshSessionFactory.createSession(
                sshKeys,
                sshAddress,
                accountOperations.account(ctx.getUser().getAccountId()).get()));
  }

  void restart(TestAccount admin, TestAccount user) throws Exception {
    checkState(
        server != commonServer,
        "The commonServer can't be restarted; to use this method, the test must be @Sandboxed");
    server = GerritServer.restart(server, testSysModule.get(), testSshModule.get());
    getTestInjector().injectMembers(this);
    getTestInjector().injectMembers(test);
    initSsh(admin, user);
  }

  public void restartAsSlave(TestAccount admin, TestAccount user) throws Exception {
    checkState(
        server != commonServer,
        "The commonServer can't be restarted; to use this method, the test must be @Sandboxed");
    closeSsh();
    server = GerritServer.restartAsSlave(server);
    getTestInjector().injectMembers(this);
    getTestInjector().injectMembers(test);
    initSsh(admin, user);
  }

  protected void closeSsh() {
    sshSessionByContext.values().forEach(SshSession::close);
  }

  public Injector getTestInjector() {
    return server.getTestInjector();
  }

  RestSession createRestSession(@Nullable TestAccount account) {
    return new GerritServerRestSession(server, account);
  }

  @Nullable
  public ProjectResetter createProjectResetter(ProjectResetter.Config config) throws Exception {
    // Only commonServer can be shared between tests and should be restored after each
    // test. It can be that the commonServer is started, but a test actually don't use
    // it and instead the test uses a separate server instance.
    // In this case, the separate server is stopped after each test and so doesn't require
    // cleanup (for simplicity, the commonServer is always cleaned up even if it is not
    // used in a test).
    ProjectResetter.Builder.Factory projectResetterFactory =
        commonServer != null
            ? commonServer.testInjector.getInstance(ProjectResetter.Builder.Factory.class)
            : null;
    return projectResetterFactory != null ? projectResetterFactory.builder().build(config) : null;
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
