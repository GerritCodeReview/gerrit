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

package com.google.gerrit.testutil;

import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

@RunWith(ConfigSuite.class)
public class GerritServerTests extends GerritBaseTests {
  @ConfigSuite.Parameter public Config config;

  @ConfigSuite.Name private String configName;

  protected TestNotesMigration notesMigration;

  @Rule
  public TestRule testRunner =
      new TestRule() {
        @Override
        public Statement apply(final Statement base, final Description description) {
          return new Statement() {
            @Override
            public void evaluate() throws Throwable {
              beforeTest();
              try {
                base.evaluate();
              } finally {
                afterTest();
              }
            }
          };
        }
      };

  public void beforeTest() throws Exception {
    notesMigration = new TestNotesMigration().setFromEnv();
  }

  public void afterTest() {}
}
