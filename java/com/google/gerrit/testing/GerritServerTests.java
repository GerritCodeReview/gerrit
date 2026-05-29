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

package com.google.gerrit.testing;

import com.google.gerrit.acceptance.config.ConfigAnnotationParser;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.config.GerritConfigs;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

@RunWith(ConfigSuite.class)
public class GerritServerTests {
  @ConfigSuite.Parameter public Config config;

  @Rule
  public TestRule testRunner =
      (base, description) -> {
        GerritConfig gerritConfig = description.getAnnotation(GerritConfig.class);
        if (gerritConfig != null) {
          config = ConfigAnnotationParser.parse(config, gerritConfig);
        }
        GerritConfigs gerritConfigs = description.getAnnotation(GerritConfigs.class);
        if (gerritConfigs != null) {
          config = ConfigAnnotationParser.parse(config, gerritConfigs);
        }
        return new Statement() {
          @Override
          public void evaluate() throws Throwable {
            base.evaluate();
          }
        };
      };
}
