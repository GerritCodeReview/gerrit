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

package com.google.gerrit.testutil;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class SchemaUpgradeTestEnvironment implements TestRule {
  @Inject private AccountManager accountManager;
  @Inject private IdentifiedUser.GenericFactory userFactory;
  @Inject private SchemaFactory<ReviewDb> schemaFactory;
  @Inject private SchemaCreator schemaCreator;
  @Inject private ThreadLocalRequestContext requestContext;
  // Only for use in setting up/tearing down injector.
  @Inject private InMemoryDatabase inMemoryDatabase;

  private ReviewDb db;
  private Injector injector;
  private LifecycleManager lifecycle;

  @Override
  public Statement apply(Statement statement, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        setUp();
        try {
          statement.evaluate();
        } finally {
          tearDown();
        }
      }
    };
  }

  public ReviewDb getDb() {
    return db;
  }

  public Injector getInjector() {
    return injector;
  }

  private void setUp() throws Exception {
    injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    lifecycle.start();

    try (ReviewDb underlyingDb = inMemoryDatabase.getDatabase().open()) {
      schemaCreator.create(underlyingDb);
    }
    db = schemaFactory.open();
    Account.Id userId = accountManager.authenticate(AuthRequest.forUser("user")).getAccountId();
    IdentifiedUser user = userFactory.create(userId);

    requestContext.setContext(
        new RequestContext() {
          @Override
          public CurrentUser getUser() {
            return user;
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return Providers.of(db);
          }
        });
  }

  private void tearDown() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    requestContext.setContext(null);
    if (db != null) {
      db.close();
    }
    InMemoryDatabase.drop(inMemoryDatabase);
  }
}
