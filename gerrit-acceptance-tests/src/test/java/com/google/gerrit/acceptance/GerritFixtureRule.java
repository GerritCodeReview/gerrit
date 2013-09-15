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
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.reflect.Field;

public class GerritFixtureRule implements TestRule {

  public GerritFixtureRule(Object sut, GerritFixtureInit init) {
    this.sut = sut;
    this.init = init;
  }

  private final Object sut;
  private GerritFixtureInit init;
  private GerritServer server;

  public Statement apply(final Statement base, Description description) {
    GerritConfig config = description.getAnnotation(GerritConfig.class);
    final String configStr = config != null
        ? config.value()
        : null;
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        Config cfg = null;
        if (configStr != null) {
          cfg = new Config();
          cfg.fromText(configStr);
        }
        server = GerritServer.start(cfg);
        server.getTestInjector().injectMembers(sut);
        // TODO(davido): find a smarter way to set server
        Field field = sut.getClass().getDeclaredField("server");
        if (field != null) {
          field.setAccessible(true);
          field.set(sut, server);
        }
        init.setUp();
        try {
          base.evaluate();
        } finally {
          server.stop();
          init.tearDown();
        }
      }
    };
  }
}
