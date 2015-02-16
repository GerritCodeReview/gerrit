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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.WebDriver;

public class AbstractWebUiTest extends AbstractDaemonTest {

  private WebDriver driver;
  private String url;

  protected WebDriver getDriver() {
    return new HtmlUnitDriver();
  }

  @Before
  public void initDriver() {
    driver = getDriver();
    url = cfg.getString("gerrit", null, "canonicalWebUrl");
  }

  @After
  public void closeDriver() {
    if (driver != null) {
      driver.close();
    }
  }

  @Test
  public void test() {
    driver.get(url);
    String title = driver.getTitle();
    assertThat(title).isNotNull();
  }
}
