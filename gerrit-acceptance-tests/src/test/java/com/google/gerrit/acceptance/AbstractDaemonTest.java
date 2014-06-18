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

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static com.google.gerrit.acceptance.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;

import com.google.common.base.Joiner;
import com.google.common.primitives.Chars;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gson.Gson;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.util.Providers;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

@RunWith(ConfigSuite.class)
public abstract class AbstractDaemonTest {
  @ConfigSuite.Parameter
  public Config baseConfig;

  @Inject
  protected AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  protected GerritApi gApi;

  @Inject
  private AcceptanceTestRequestScope atrScope;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  protected PushOneCommit.Factory pushFactory;

  protected Git git;
  protected GerritServer server;
  protected TestAccount admin;
  protected TestAccount user;
  protected RestSession adminSession;
  protected RestSession userSession;
  protected SshSession sshSession;
  protected ReviewDb db;
  protected Project.NameKey project;

  @Rule
  public TestRule testRunner = new TestRule() {
    @Override
    public Statement apply(final Statement base, final Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          boolean mem = description.getAnnotation(UseLocalDisk.class) == null;
          boolean enableHttpd = description.getAnnotation(NoHttpd.class) == null
              && description.getTestClass().getAnnotation(NoHttpd.class) == null;
          beforeTest(config(description), mem, enableHttpd);
          base.evaluate();
          afterTest();
        }
      };
    }
  };

  private Config config(Description description) {
    GerritConfigs cfgs = description.getAnnotation(GerritConfigs.class);
    GerritConfig cfg = description.getAnnotation(GerritConfig.class);
    if (cfgs != null && cfg != null) {
      throw new IllegalStateException("Use either @GerritConfigs or @GerritConfig not both");
    }
    if (cfgs != null) {
      return ConfigAnnotationParser.parse(baseConfig, cfgs);
    } else if (cfg != null) {
      return ConfigAnnotationParser.parse(baseConfig, cfg);
    } else {
      return baseConfig;
    }
  }

  private void beforeTest(Config cfg, boolean memory, boolean enableHttpd) throws Exception {
    server = startServer(cfg, memory, enableHttpd);
    server.getTestInjector().injectMembers(this);
    admin = accounts.admin();
    user = accounts.user();
    adminSession = new RestSession(server, admin);
    userSession = new RestSession(server, user);
    initSsh(admin);
    db = reviewDbProvider.open();
    Context ctx = newRequestContext(admin);
    atrScope.set(ctx);
    sshSession = ctx.getSession();
    project = new Project.NameKey("p");
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
  }

  protected GerritServer startServer(Config cfg, boolean memory,
      boolean enableHttpd) throws Exception {
    return GerritServer.start(cfg, memory, enableHttpd);
  }

  private void afterTest() throws Exception {
    db.close();
    sshSession.close();
    server.stop();
  }

  protected PushOneCommit.Result createChange() throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    return push.to(git, "refs/for/master");
  }

  private static final List<Character> RANDOM =
      Chars.asList(new char[]{'a','b','c','d','e','f','g','h'});
  protected PushOneCommit.Result amendChange(String changeId)
      throws GitAPIException, IOException {
    Collections.shuffle(RANDOM);
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME, new String(Chars.toArray(RANDOM)), changeId);
    return push.to(git, "refs/for/master");
  }

  protected ChangeJson.ChangeInfo getChange(String changeId, ListChangesOption... options)
      throws IOException {
    return getChange(adminSession, changeId, options);
  }

  protected ChangeJson.ChangeInfo getChange(RestSession session, String changeId,
      ListChangesOption... options) throws IOException {
    String q = options.length > 0 ? "?o=" + Joiner.on("&o=").join(options) : "";
    RestResponse r = session.get("/changes/" + changeId + q);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    return newGson().fromJson(r.getReader(), ChangeJson.ChangeInfo.class);
  }

  protected ChangeInfo info(String id)
      throws RestApiException {
    return gApi.changes().id(id).info();
  }

  protected ChangeInfo get(String id)
      throws RestApiException {
    return gApi.changes().id(id).get();
  }

  protected ChangeInfo get(String id, ListChangesOption... options)
      throws RestApiException {
    EnumSet<ListChangesOption> s = EnumSet.noneOf(ListChangesOption.class);
    s.addAll(Arrays.asList(options));
    return gApi.changes().id(id).get(s);
  }

  private Context newRequestContext(TestAccount account) {
    return atrScope.newContext(reviewDbProvider, new SshSession(server, admin),
        identifiedUserFactory.create(Providers.of(db), account.getId()));
  }

  protected Context setAccountContext(TestAccount account) {
    return atrScope.set(newRequestContext(account));
  }

  protected Context setRequestContext(Context ctx) {
    return atrScope.set(ctx);
  }

  protected static Gson newGson() {
    return OutputFormat.JSON_COMPACT.newGson();
  }

  protected RevisionApi revision(PushOneCommit.Result r) throws Exception {
    return gApi.changes()
        .id(r.getChangeId())
        .current();
  }
}
