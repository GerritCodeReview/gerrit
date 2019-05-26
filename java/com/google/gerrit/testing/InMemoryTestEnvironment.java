// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.testing;

import com.google.gerrit.entities.Account;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Config;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * An in-memory test environment for integration tests.
 *
 * <p>This test environment emulates the internals of a Gerrit server without starting a Gerrit
 * site. Git repositories, including NoteDb, are stored in memory.
 *
 * <p>Each test is executed with a fresh and clean test environment. Hence, modifications applied in
 * one test don't carry over to subsequent ones.
 */
public final class InMemoryTestEnvironment implements MethodRule {
  private final Provider<Config> configProvider;

  @Inject private AccountManager accountManager;
  @Inject private IdentifiedUser.GenericFactory userFactory;
  @Inject private SchemaCreator schemaCreator;
  @Inject private ThreadLocalRequestContext requestContext;

  private LifecycleManager lifecycle;

  /** Create a test environment using an empty base config. */
  public InMemoryTestEnvironment() {
    this(Config::new);
  }

  /**
   * Create a test environment using the specified base config.
   *
   * <p>The config is passed as a provider so it can be lazily initialized after this rule is
   * instantiated, for example using {@link ConfigSuite}.
   *
   * @param configProvider possibly-lazy provider for the base config.
   */
  public InMemoryTestEnvironment(Provider<Config> configProvider) {
    this.configProvider = configProvider;
  }

  @Override
  public Statement apply(Statement base, FrameworkMethod method, Object target) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          setUp(target);
          base.evaluate();
        } finally {
          tearDown();
        }
      }
    };
  }

  public void setApiUser(Account.Id id) {
    IdentifiedUser user = userFactory.create(id);
    requestContext.setContext(() -> user);
  }

  private void setUp(Object target) throws Exception {
    Config cfg = configProvider.get();
    InMemoryModule.setDefaults(cfg);

    Injector injector = Guice.createInjector(new InMemoryModule(cfg));
    injector.injectMembers(this);
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    lifecycle.start();

    schemaCreator.create();

    // The first user is added to the "Administrators" group. See AccountManager#create().
    setApiUser(accountManager.authenticate(AuthRequest.forUser("admin")).getAccountId());

    // Inject target members after setting API user, so it can @Inject request-scoped objects if it
    // wants.
    injector.injectMembers(target);
  }

  private void tearDown() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    if (requestContext != null) {
      requestContext.setContext(null);
    }
  }
}
