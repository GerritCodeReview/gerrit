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

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

import java.util.List;
import java.util.Set;

/**
 * Wrapper for the Selenium WebDriver that implements workarounds for problems
 * with the Selenium WebDriver.
 */
public class WebDriverWrapper implements WebDriver {

  private final WebDriver wrappedDriver;

  static WebDriver wrap(final WebDriver driver) {
    return new WebDriverWrapper(driver);
  }

  private WebDriverWrapper(final WebDriver driver) {
    wrappedDriver = driver;
    maximizeBrowserWindow();
  }

  private void maximizeBrowserWindow() {
    if (!(wrappedDriver instanceof HtmlUnitDriver)) {
      // Google Chrome does currently not support 'window.resizeTo()' [1]
      // but since it also does no harm, we can anyway try it, may be it will be
      // supported one day
      // [1] http://code.google.com/p/chromium/issues/detail?id=2091
      ((JavascriptExecutor) wrappedDriver)
          .executeScript("if(window.screen) {\n"
              + "  window.moveTo(0, 0);\n"
              + "  window.resizeTo(window.screen.availWidth,window.screen.availHeight);\n"
              + "};\n");
    }
  }

  @Override
  public void get(String url) {
    wrappedDriver.get(url);
    if (wrappedDriver instanceof HtmlUnitDriver) {
      // sometimes after calling driver.get(url) only the url is updated
      // (driver.getCurrentUrl()) but the loaded page is still the old page
      // (driver.getPageSource()), doing a refresh (driver.navigate().refresh())
      // ensures that the new page is loaded
      wrappedDriver.navigate().refresh();
    }
  }

  @Override
  public String getCurrentUrl() {
    return wrappedDriver.getCurrentUrl();
  }

  @Override
  public String getTitle() {
    return wrappedDriver.getTitle();
  }

  @Override
  public List<WebElement> findElements(By by) {
    return WebElementWrapper
        .wrap(wrappedDriver, wrappedDriver.findElements(by));
  }

  @Override
  public WebElement findElement(By by) {
    return WebElementWrapper.wrap(wrappedDriver, wrappedDriver.findElement(by));
  }

  @Override
  public String getPageSource() {
    return wrappedDriver.getPageSource();
  }

  @Override
  public void close() {
    wrappedDriver.close();
  }

  @Override
  public void quit() {
    wrappedDriver.quit();
  }

  @Override
  public Set<String> getWindowHandles() {
    return wrappedDriver.getWindowHandles();
  }

  @Override
  public String getWindowHandle() {
    return wrappedDriver.getWindowHandle();
  }

  @Override
  public TargetLocator switchTo() {
    return wrappedDriver.switchTo();
  }

  @Override
  public Navigation navigate() {
    return wrappedDriver.navigate();
  }

  @Override
  public Options manage() {
    return wrappedDriver.manage();
  }
}
