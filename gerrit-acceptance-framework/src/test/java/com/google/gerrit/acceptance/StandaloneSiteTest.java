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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.fail;

import com.google.common.collect.Streams;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.util.Arrays;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

@RunWith(ConfigSuite.class)
@UseLocalDisk
public abstract class StandaloneSiteTest {
  protected class ServerContext implements RequestContext, AutoCloseable {
    private final GerritServer server;
    private final ManualRequestContext ctx;

    private ServerContext(GerritServer server) throws Exception {
      this.server = server;
      Injector i = server.getTestInjector();
      if (adminId == null) {
        adminId = i.getInstance(AccountCreator.class).admin().getId();
      }
      ctx = i.getInstance(OneOffRequestContext.class).openAs(adminId);
    }

    @Override
    public CurrentUser getUser() {
      return ctx.getUser();
    }

    @Override
    public Provider<ReviewDb> getReviewDbProvider() {
      return ctx.getReviewDbProvider();
    }

    public Injector getInjector() {
      return server.getTestInjector();
    }

    @Override
    public void close() throws Exception {
      try {
        ctx.close();
      } finally {
        server.close();
      }
    }
  }

  @ConfigSuite.Parameter public Config baseConfig;
  @ConfigSuite.Name private String configName;

  private final TemporaryFolder tempSiteDir = new TemporaryFolder();

  private final TestRule testRunner =
      new TestRule() {
        @Override
        public Statement apply(Statement base, Description description) {
          return new Statement() {
            @Override
            public void evaluate() throws Throwable {
              beforeTest(description);
              base.evaluate();
            }
          };
        }
      };

  @Rule public RuleChain ruleChain = RuleChain.outerRule(tempSiteDir).around(testRunner);

  protected SitePaths sitePaths;

  private GerritServer.Description serverDesc;
  private Account.Id adminId;

  private void beforeTest(Description description) throws Exception {
    serverDesc = GerritServer.Description.forTestMethod(description, configName);
    sitePaths = new SitePaths(tempSiteDir.getRoot().toPath());
    GerritServer.init(serverDesc, baseConfig, sitePaths.site_path);
  }

  protected ServerContext startServer() throws Exception {
    return new ServerContext(startImpl());
  }

  protected void assertServerStartupFails() throws Exception {
    try (GerritServer server = startImpl()) {
      fail("expected server startup to fail");
    } catch (GerritServer.StartupException e) {
      // Expected.
    }
  }

  private GerritServer startImpl() throws Exception {
    return GerritServer.start(serverDesc, baseConfig, sitePaths.site_path);
  }

  protected static void runGerrit(String... args) throws Exception {
    assertThat(GerritLauncher.mainImpl(args))
        .named("gerrit.war " + Arrays.stream(args).collect(joining(" ")))
        .isEqualTo(0);
  }

  @SafeVarargs
  protected static void runGerrit(Iterable<String>... multiArgs) throws Exception {
    runGerrit(
        Arrays.stream(multiArgs).flatMap(args -> Streams.stream(args)).toArray(String[]::new));
  }
}
