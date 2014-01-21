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

import static com.google.gerrit.acceptance.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;

import com.google.common.base.Joiner;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gson.Gson;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;

public abstract class AbstractDaemonTest {
  @Inject
  protected AccountCreator accounts;

  protected GerritServer server;
  protected TestAccount admin;
  protected RestSession adminSession;

  @Rule
  public TestRule testRunner = new TestRule() {
    @Override
    public Statement apply(final Statement base, final Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          boolean mem = description.getAnnotation(UseLocalDisk.class) == null;
          beforeTest(config(description), mem);
          base.evaluate();
          afterTest();
        }
      };
    }
  };

  private static Config config(Description description)
      throws IOException, ConfigInvalidException {
    Config base = ConfigAnnotationParser.parseFromSystemProperty();
    GerritConfigs cfgs = description.getAnnotation(GerritConfigs.class);
    GerritConfig cfg = description.getAnnotation(GerritConfig.class);
    if (cfgs != null && cfg != null) {
      throw new IllegalStateException("Use either @GerritConfigs or @GerritConfig not both");
    }
    if (cfgs != null) {
      return ConfigAnnotationParser.parse(base, cfgs);
    } else if (cfg != null) {
      return ConfigAnnotationParser.parse(base, cfg);
    } else {
      return base;
    }
  }

  private void beforeTest(Config cfg, boolean memory) throws Exception {
    server = startServer(cfg, memory);
    server.getTestInjector().injectMembers(this);
    admin = accounts.admin();
    adminSession = new RestSession(server, admin);
    initSsh(admin);
  }

  protected GerritServer startServer(Config cfg, boolean memory) throws Exception {
    return GerritServer.start(cfg, memory);
  }

  private void afterTest() throws Exception {
    server.stop();
  }

  protected ChangeInfo getChange(String changeId, ListChangesOption... options)
      throws IOException {
    return getChange(adminSession, changeId, options);
  }

  protected ChangeInfo getChange(RestSession session, String changeId,
      ListChangesOption... options) throws IOException {
    String q = options.length > 0 ? "?o=" + Joiner.on("&o=").join(options) : "";
    RestResponse r = session.get("/changes/" + changeId + q);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    return newGson().fromJson(r.getReader(), ChangeInfo.class);
  }

  protected static Gson newGson() {
    return OutputFormat.JSON_COMPACT.newGson();
  }
}
