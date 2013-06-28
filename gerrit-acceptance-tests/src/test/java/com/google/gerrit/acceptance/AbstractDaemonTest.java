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

import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public abstract class AbstractDaemonTest {
  protected GerritServer server;

  @Rule
  public TestRule testRunner = new TestRule() {
    @Override
    public Statement apply(final Statement base, final Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          beforeTest(config(description));
          base.evaluate();
          afterTest();
        }
      };
    }
  };

  private static Config config(Description description) {
    GerritConfigs cfgs = description.getAnnotation(GerritConfigs.class);
    GerritConfig cfg = description.getAnnotation(GerritConfig.class);
    if (cfgs != null && cfg != null) {
      throw new IllegalStateException("Use either @GerritConfigs or @GerritConfig not both");
    }
    if (cfgs != null) {
      return ConfigAnnotationParser.parse(cfgs);
    } else if (cfg != null) {
      return ConfigAnnotationParser.parse(cfg);
    } else {
      return null;
    }
  }

  private void beforeTest(Config cfg) throws Exception {
    server = startServer(cfg);
    server.getTestInjector().injectMembers(this);
  }

  protected GerritServer startServer(Config cfg) throws Exception {
    return GerritServer.start(cfg);
  }

  private void afterTest() throws Exception {
    server.stop();
  }
}
