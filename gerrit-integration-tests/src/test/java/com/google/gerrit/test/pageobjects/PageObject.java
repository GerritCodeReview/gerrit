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

package com.google.gerrit.test.pageobjects;

import com.google.common.base.Function;
import com.google.gerrit.test.GerritTestProperty;
import com.google.gerrit.test.TraceLevel;
import com.google.gerrit.test.util.LogFile;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

public abstract class PageObject {

  private static final Integer RELOAD_RETRIES = GerritTestProperty.HTTP_RELOAD_RETRIES.get();

  private static final Logger log = LoggerFactory.getLogger(PageObject.class);

  protected final WebDriver driver;


  public PageObject(final WebDriver driver) {
    this.driver = driver;
  }

  protected abstract String getExpectedTitle();

  private WebDriverWait iWait() {
    final int httpTimeoutInSeconds =
        (int) Math.ceil((double) GerritTestProperty.HTTP_TIMEOUT.get() / 1000);
    return new WebDriverWait(driver, httpTimeoutInSeconds,
        GerritTestProperty.HTTP_POLLING_INTERVAL.get());
  }

  public void waitForPage() {
    waitForPage(RELOAD_RETRIES);
  }

  private void waitForPage(final int reloadRetries) {
    try {
      waitForCurrentPage();
      return;
    } catch (final WebDriverException e) {
      if (reloadRetries == 0) {
        LogFile.logFile(log, TraceLevel.ERROR, "wrong page: page title = '"
            + driver.getTitle() + "'; expected title = '" + getExpectedTitle()
            + "'; url = '" + driver.getCurrentUrl() + "'", "html",
            driver.getPageSource(), e);
        log.error(driver.getPageSource());
        throw e;
      }
    }

    log.info("wrong page: page title = '" + driver.getTitle()
        + "'; expected title = '" + getExpectedTitle() + "'; url = '"
        + driver.getCurrentUrl() + "'");
    attemptRefresh();
    waitForPage(reloadRetries - 1);
  }

  private void attemptRefresh() {
    log.info("reloading page...");
    try {
      driver.navigate().refresh();
    } catch (Exception ex) {
      log.error("reloading page failed", ex);
    }
  }

  private void waitForCurrentPage() {
    iWait().until(new Function<WebDriver, Boolean>() {
      @Override
      public Boolean apply(WebDriver input) {
        final boolean isCurrentPage = isCurrentPage();
        if (isCurrentPage) {
          log.info("current page = '" + driver.getTitle() + "; url = "
              + driver.getCurrentUrl());
        }
        return isCurrentPage;
      }
    });
  }

  protected boolean isCurrentPage() {
    return driver.getTitle().contains(getExpectedTitle());
  }

  protected WebElement waitUntilFound(final String tagName, final String text) {
    return waitUntilFound(getLocator(tagName, text));
  }

  protected WebElement waitUntilFound(final String tagName,
      final String attribute, final String value) {
    return waitUntilFound(getLocator(tagName, attribute, value));
  }

  protected WebElement waitUntilFound(final By locator) {
    iWait().until(new Function<WebDriver, Boolean>() {
      @Override
      public Boolean apply(WebDriver input) {
        try {
          input.findElement(locator);
          return true;
        } catch (Exception e) {
          log.info("waiting for web element: locator = '" + locator + "'");
          return false;
        }
      }
    });
    return driver.findElement(locator);
  }

  protected WebElement find(final String tagName, final String text) {
    return driver.findElement(getLocator(tagName, text));
  }

  protected boolean hasStartingWithValue(final String tagName, final String text) {
    try {
      final WebElement element =
          driver.findElement(getLocatorStartsWith(tagName, text));
      return element.isDisplayed();
    } catch (NoSuchElementException e) {
      return false;
    }
  }

  protected WebElement findStartingWithValue(final String tagName,
      final String text) {
    return driver.findElement(getLocatorStartsWith(tagName, text));
  }

  protected WebElement find(final String tagName, final String attribute,
      final String value) {
    return driver.findElement(getLocator(tagName, attribute, value));
  }

  protected List<WebElement> findListStartingWithValue(final String tagName,
      final String attribute, final String value) {
    return driver.findElements(getLocatorStartsWith(tagName, attribute, value));
  }

  protected List<List<WebElement>> findTableRows(final String title,
      final boolean includeHeaderRow) {
    final WebElement titleElement =
        driver.findElement(getLocator("span", title));
    final List<WebElement> trs =
        titleElement.findElements(By.xpath("..//..//table[1]/tbody/tr"));
    if (!includeHeaderRow && !trs.isEmpty()) {
      trs.remove(0);
    }
    final List<List<WebElement>> rows = new LinkedList<List<WebElement>>();
    for (final WebElement tr : trs) {
      rows.add(tr.findElements(By.xpath("td")));
    }
    return rows;
  }

  private By getLocator(final String tagName, final String text) {
    return By.xpath("//" + tagName + "[text()='" + text + "']");
  }

  private By getLocatorStartsWith(final String tagName, final String value) {
    return By.xpath("//" + tagName + "[substring(text(),1," + value.length()
        + ")='" + value + "']");
  }

  private By getLocator(final String tagName, final String attribute,
      final String value) {
    return By.xpath("//" + tagName + "[@" + attribute + "='" + value + "']");
  }

  private By getLocatorStartsWith(final String tagName, final String attribute,
      final String value) {
    return By.xpath("//" + tagName + "[substring(@" + attribute + ",1,"
        + value.length() + ")='" + value + "']");
  }

  protected void clickButton(final String label) {
    log.info("clicking on button '" + label + "'");
    find("button", label).click();
  }

  protected void clickLink(final String text) {
    final WebElement link = find("a", text);
    log.info("clicking on link '" + text + "'; target = '"
        + link.getAttribute("href") + "'");
    link.click();
  }

  protected String getTableField(final String label, final int columnIndex) {
    final WebElement element =
        find("td", label).findElement(
            By.xpath("..//td[" + (columnIndex + 1) + "]"));
    return element.getText();
  }

  protected void setTableTextField(final String label, final String text) {
    setTableTextField(label, text, true);
  }

  protected void setTableTextField(final String label, final String text,
      final boolean logValue) {
    log.info("setting text for '" + label + (logValue?"' to '" + text + "'":""));
    final WebElement element =
        find("td", label).findElement(By.xpath("..//input"));
    element.sendKeys(text);
  }

  protected void setText(final WebElement element, final String text) {
    log.info("setting text for '" + element.getTagName() + "' to '" + text + "'");
    element.sendKeys(text);
  }

  protected <P extends PageObject> P navigateByClick(WebElement webElement,
      Class<P> expectedPage, String... additionalParams) {
    log.info("clicking on '" + webElement.getTagName() + "'");
    webElement.click();
    return PageObject.createAsserting(expectedPage, driver, additionalParams);
  }

  /**
   * Opens a page via a given URL and returns a page object representing the
   * page.
   *
   * @param url the URL of the page to be opened
   * @param expectedPage the class of the expected page object
   * @param driver the web driver to be passed on to instantiate the page object
   *        with
   * @param additionalParams additional String parameters to be passed to the
   *        constructor of the page object
   * @return the page object representing the page
   */
  protected static <P extends PageObject> P get(String url,
      Class<P> expectedPage, WebDriver driver, String... additionalParams) {
    log.info("switching page from " + driver.getCurrentUrl() + " to " + url);
    driver.get(url);
    return createAsserting(expectedPage, driver, additionalParams);
  }

  /**
   * Creates a PageObject instance. It is expected that the Page Object class
   * has a constructor, which takes exactly one WebDriver argument
   *
   * @param expectedPage the class of the expected page object
   * @param driver the web driver to be passed on to instantiate the page object
   *        with
   * @param additionalParams additional String parameters to be passed to the
   *        constructor of the page object
   * @return the newly created page
   */
  private static <P extends PageObject> P instantiatePage(
      Class<P> expectedPage, WebDriver driver, String... additionalParams) {
    try {
      int parameterCount = 1 + additionalParams.length;
      Class<?>[] parameterTypes = new Class[parameterCount];
      Object[] arguments = new Object[parameterCount];
      parameterTypes[0] = WebDriver.class;
      arguments[0] = driver;
      for (int i = 1; i < parameterCount; i++) {
        parameterTypes[i] = String.class;
        arguments[i] = additionalParams[i - 1];
      }
      Constructor<P> constructor =
          expectedPage.getDeclaredConstructor(parameterTypes);
      constructor.setAccessible(true);

      P page = constructor.newInstance(arguments);
      return page;
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates
   *
   * @param driver the web driver to be passed on to instantiate the page object
   *        with
   * @param expectedPage the class of the expected page object
   * @param additionalParams additional String parameters to be passed to the
   *        constructor of the page object
   * @return the page object representing the page
   */

  public static <P extends PageObject> P createAsserting(Class<P> expectedPage,
      WebDriver driver, String... additionalParams) {
    P page = instantiatePage(expectedPage, driver, additionalParams);
    log.info("page object: " + page.getClass().getName());
    page.waitForPage();
    return page;
  }
}
