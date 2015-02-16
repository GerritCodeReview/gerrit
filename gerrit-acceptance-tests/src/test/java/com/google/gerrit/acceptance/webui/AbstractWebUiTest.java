// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.acceptance.webui;

import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.webui.WebDriverUtil.getDriver;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.webui.WebDriverUtil.Browser;
import com.google.gerrit.testutil.ConfigSuite;

import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.openqa.selenium.WebDriver;

import java.util.Arrays;

public abstract class AbstractWebUiTest extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config browserChrome() {
    return getConfig(Browser.CHROME);
  }

  @ConfigSuite.Config
  public static Config browserFirefox() {
    return getConfig(Browser.FIREFOX);
  }

  @ConfigSuite.Config
  public static Config browserSafari() {
    return getConfig(Browser.SAFARI);
  }

  @ConfigSuite.Config
  public static Config browserInternetExplorer() {
    return getConfig(Browser.IE);
  }

  protected WebDriver driver;
  protected String url;

  public static Config getConfig(Browser browser) {
    Config cfg = new Config();
    cfg.setEnum("gerrit", "webuitests", "browser", browser);
    return cfg;
  }

  @BeforeClass
  public static void shouldRunUiTests() {
    final String[] RUN_FLAGS = {"yes", "y", "true"};
    String value = System.getenv("GERRIT_DEV_RUN_UI_ACCEPTANCE_TESTS");
    assume().that(value).isNotEmpty();
    assume().that(Arrays.asList(RUN_FLAGS)).contains(value.toLowerCase());
  }

  @Before
  public void beforeTest() {
    driver = getDriver(cfg);
    assume().that(driver).isNotNull();
    url = cfg.getString("gerrit", null, "canonicalWebUrl");
  }

  @After
  public void afterTest() {
    if (driver != null) {
      driver.close();
    }
  }
}
