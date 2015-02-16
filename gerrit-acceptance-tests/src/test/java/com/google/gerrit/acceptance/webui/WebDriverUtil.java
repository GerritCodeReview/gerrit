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

import org.eclipse.jgit.lib.Config;
import org.openqa.selenium.Platform;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.safari.SafariDriver;

import java.io.File;

public class WebDriverUtil {
  public enum Browser {
    NONE, CHROME, FIREFOX, SAFARI, IE
  }

  private static File getUserHome() {
    String userHome = System.getProperty("user.home");
    if (Strings.isNullOrEmpty(userHome)) {
      userHome = System.getenv("HOME");
    }
    if (!Strings.isNullOrEmpty(userHome)) {
      return new File(userHome);
    }
    return null;
  }

  public static WebDriver getDriver(Config cfg) {
    Browser browser =
        cfg.getEnum("gerrit", "webuitests", "browser", Browser.NONE);
    Platform platform = Platform.getCurrent();
    switch (browser) {
      case CHROME:
        File home = getUserHome();
        if (home != null) {
          System.setProperty("webdriver.chrome.driver",
              home.getAbsolutePath() + "/chromedriver");
          try {
            return new ChromeDriver();
          } catch (Exception e) {
            // Browser not available.
          }
        }
        break;
      case FIREFOX:
        try {
          return new FirefoxDriver();
        } catch (Exception e) {
          // Browser not available
        }
        break;
      case SAFARI:
        if (platform.is(Platform.MAC) || platform.is(Platform.WINDOWS)) {
          try {
            return new SafariDriver();
          } catch (Exception e) {
            // Browser not available.
          }
        }
        break;
      case IE:
        if (platform.is(Platform.WINDOWS)) {
          try {
            return new InternetExplorerDriver();
          } catch (Exception e) {
            // Browser not available.
          }
        }
        break;
      case NONE:
      default:
        break;
    }

    return null;
  }

}
