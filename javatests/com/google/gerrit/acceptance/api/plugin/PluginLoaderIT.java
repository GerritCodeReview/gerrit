// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.plugin;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.config.GerritConfig;
import com.google.gerrit.server.plugins.MissingMandatoryPluginsException;
import org.junit.Test;
import org.junit.runner.Description;

@NoHttpd
public class PluginLoaderIT extends AbstractDaemonTest {

  Description testDescription;

  @Override
  protected void beforeTest(Description description) throws Exception {
    this.testDescription = description;
  }

  @Override
  protected void afterTest() throws Exception {}

  @Test(expected = MissingMandatoryPluginsException.class)
  @GerritConfig(name = "plugins.mandatory", value = "my-mandatory-plugin")
  public void shouldFailToStartGerritWhenMandatoryPluginsAreMissing() throws Exception {
    super.beforeTest(testDescription);
  }
}
