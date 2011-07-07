// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.test;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;

import java.net.URL;

public enum SeleniumWebDriver {
  HTMLUNIT {
    @Override
    protected WebDriver instantiateDriver() {
      return new HtmlUnitDriver(true);
    }
  },

  CHROME {
    @Override
    protected WebDriver instantiateDriver() {
      URL seleniumChromeDriver =
          getClass().getClassLoader().getResource("chromedriver");
      if (seleniumChromeDriver == null) {
        seleniumChromeDriver =
            getClass().getClassLoader().getResource("chromedriver.exe");
      }
      if (seleniumChromeDriver != null) {
        System.setProperty("webdriver.chrome.driver",
            seleniumChromeDriver.getFile());
      }
      return new ChromeDriver();
    }
  },

  FIREFOX {
    @Override
    protected WebDriver instantiateDriver() {
      return new FirefoxDriver();
    }
  },

  IE {
    @Override
    protected WebDriver instantiateDriver() {
      return new InternetExplorerDriver();
    }
  };

  WebDriver createDriver() {
    return WebDriverWrapper.wrap(instantiateDriver());
  }

  protected abstract WebDriver instantiateDriver();
}
