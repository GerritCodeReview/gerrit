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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
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
      GerritApi gApi = i.getInstance(GerritApi.class);

      try {
        // ServerContext ctor is called multiple times but the group can be only created once
        gApi.groups().id("Group");
      } catch (ResourceNotFoundException e) {
        GroupInput in = new GroupInput();
        in.members = Collections.singletonList("admin");
        in.name = "Group";
        gApi.groups().create(in);
      }
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
              try {
                beforeTest(description);
                base.evaluate();
              } finally {
                afterTest();
              }
            }
          };
        }
      };

  @Rule public RuleChain ruleChain = RuleChain.outerRule(tempSiteDir).around(testRunner);

  protected SitePaths sitePaths;
  protected Account.Id adminId;

  private GerritServer.Description serverDesc;
  private SystemReader oldSystemReader;

  private void beforeTest(Description description) throws Exception {
    // SystemReader must be overridden before creating any repos, since they read the user/system
    // configs at initialization time, and are then stored in the RepositoryCache forever.
    oldSystemReader = setFakeSystemReader(tempSiteDir.getRoot());

    serverDesc = GerritServer.Description.forTestMethod(description, configName);
    sitePaths = new SitePaths(tempSiteDir.getRoot().toPath());
    GerritServer.init(serverDesc, baseConfig, sitePaths.site_path);
  }

  private static SystemReader setFakeSystemReader(File tempDir) {
    SystemReader oldSystemReader = SystemReader.getInstance();
    SystemReader.setInstance(
        new SystemReader() {
          @Override
          public String getHostname() {
            return oldSystemReader.getHostname();
          }

          @Override
          public String getenv(String variable) {
            return oldSystemReader.getenv(variable);
          }

          @Override
          public String getProperty(String key) {
            return oldSystemReader.getProperty(key);
          }

          @Override
          public FileBasedConfig openUserConfig(Config parent, FS fs) {
            return new FileBasedConfig(parent, new File(tempDir, "user.config"), FS.detect());
          }

          @Override
          public FileBasedConfig openSystemConfig(Config parent, FS fs) {
            return new FileBasedConfig(parent, new File(tempDir, "system.config"), FS.detect());
          }

          @Override
          public long getCurrentTime() {
            return oldSystemReader.getCurrentTime();
          }

          @Override
          public int getTimezone(long when) {
            return oldSystemReader.getTimezone(when);
          }
        });
    return oldSystemReader;
  }

  private void afterTest() throws Exception {
    SystemReader.setInstance(oldSystemReader);
    oldSystemReader = null;
  }

  protected ServerContext startServer() throws Exception {
    return startServer((Module) null);
  }

  protected ServerContext startServer(
      @Nullable List<Module> testSysModules, String... additionalArgs) throws Exception {
    return new ServerContext(startImpl(testSysModules, additionalArgs));
  }

  protected ServerContext startServer(@Nullable Module testSysModule, String... additionalArgs)
      throws Exception {
    return new ServerContext(startImpl(ImmutableList.of(testSysModule), additionalArgs));
  }

  protected void assertServerStartupFails() throws Exception {
    try (GerritServer server = startImpl(null)) {
      fail("expected server startup to fail");
    } catch (GerritServer.StartupException e) {
      // Expected.
    }
  }

  private GerritServer startImpl(@Nullable List<Module> testSysModules, String... additionalArgs)
      throws Exception {
    return GerritServer.start(
        serverDesc, baseConfig, sitePaths.site_path, testSysModules, null, null, additionalArgs);
  }

  protected static void runGerrit(String... args) throws Exception {
    // Use invokeProgram with the current classloader, rather than mainImpl, which would create a
    // new classloader. This is necessary so that static state, particularly the SystemReader, is
    // shared with the test method.
    assertThat(GerritLauncher.invokeProgram(StandaloneSiteTest.class.getClassLoader(), args))
        .named("gerrit.war " + Arrays.stream(args).collect(joining(" ")))
        .isEqualTo(0);
  }

  @SafeVarargs
  protected static void runGerrit(Iterable<String>... multiArgs) throws Exception {
    runGerrit(
        Arrays.stream(multiArgs).flatMap(args -> Streams.stream(args)).toArray(String[]::new));
  }
}
