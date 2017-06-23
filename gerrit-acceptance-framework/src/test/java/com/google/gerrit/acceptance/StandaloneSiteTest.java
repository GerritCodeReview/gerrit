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

import com.google.common.collect.Streams;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Injector;
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

  private void beforeTest(Description description) throws Exception {
    serverDesc = GerritServer.Description.forTestMethod(description, configName);
    sitePaths = new SitePaths(tempSiteDir.getRoot().toPath());
    GerritServer.init(serverDesc, baseConfig, sitePaths.site_path);
  }

  protected GerritServer startServer() throws Exception {
    return GerritServer.start(serverDesc, baseConfig, sitePaths.site_path);
  }

  protected static ManualRequestContext openContext(GerritServer server) throws Exception {
    Injector i = server.getTestInjector();
    TestAccount a = i.getInstance(AccountCreator.class).admin();
    return openContext(server, a.getId());
  }

  protected static ManualRequestContext openContext(GerritServer server, Account.Id accountId)
      throws Exception {
    return server.getTestInjector().getInstance(OneOffRequestContext.class).openAs(accountId);
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
