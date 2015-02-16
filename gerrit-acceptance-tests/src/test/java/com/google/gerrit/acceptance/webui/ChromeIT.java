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

import com.google.common.base.Strings;
import com.google.gerrit.acceptance.UseGui;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.File;

@UseGui
public class ChromeIT extends AbstractWebUiTest {
  private File getUserHome() {
    String userHome = System.getProperty("user.home");
    if (Strings.isNullOrEmpty(userHome)) {
      userHome = System.getenv("HOME");
    }
    if (!Strings.isNullOrEmpty(userHome)) {
      return new File(userHome);
    }
    return null;
  }

  @Override
  public WebDriver getDriver() {
    File home = getUserHome();
    if (home != null) {
      System.setProperty("webdriver.chrome.driver",
          home.getAbsolutePath() + "/chromedriver");
      return new ChromeDriver();
    }
    return null;
  }
}
