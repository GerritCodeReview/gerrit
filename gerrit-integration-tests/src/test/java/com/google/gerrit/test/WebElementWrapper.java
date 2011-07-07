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

import com.google.gerrit.test.util.Check;
import com.google.gerrit.test.util.WaitUtil;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Keys;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the Selenium WebElement that implements workarounds for problems
 * with the Selenium WebElement.
 */
public class WebElementWrapper implements WebElement {

  private static final Logger log = LoggerFactory
      .getLogger(WebElementWrapper.class);

  private final static int EVENT_WAIT = GerritTestProperty.EVENT_WAIT.get();
  private final static int EVENT_POLLING_INTERVAL = GerritTestProperty.EVENT_POLLING_INTERVAL.get();

  private final WebDriver driver;
  private final WebElement wrappedElement;

  static List<WebElement> wrap(final WebDriver driver,
      final List<WebElement> elements) {
    final List<WebElement> wrappedElements =
        new ArrayList<WebElement>(elements.size());
    for (final WebElement element : elements) {
      wrappedElements.add(wrap(driver, element));
    }
    return wrappedElements;
  }

  static WebElement wrap(final WebDriver driver, final WebElement element) {
    return new WebElementWrapper(driver, element);
  }

  private WebElementWrapper(final WebDriver driver, final WebElement element) {
    this.driver = driver;
    this.wrappedElement = element;
  }

  @Override
  public void click() {
    wrappedElement.click();
    waitForEvent();
  }

  @Override
  public void submit() {
    wrappedElement.submit();
  }

  @Override
  public void sendKeys(CharSequence... keysToSend) {
    if (driver instanceof InternetExplorerDriver) {
      // for the InternetExplorerDriver WebElement#sendKeys(String) is very slow
      // if many characters are sent, pasting the text is much faster
      pasteKeys(keysToSend);
    } else {
      final String expectedText = getAsString(keysToSend);
      if (driver instanceof ChromeDriver && expectedText.contains("@")) {
        // when setting a text that contains '@' with
        // WebElement#sendKeys(String) '@' is replaced by 'q', e.g. setting
        // 'admin@test.com' results in 'adminqtest.com', probably this is
        // related to the German Locale since on the German keyboard '@' and 'q'
        // share the same physical key, pasting the text avoids this problem
        pasteKeys(keysToSend);
      } else {
        sendKeysAndWait(expectedText, keysToSend);
      }
    }
  }

  private void pasteKeys(CharSequence... keysToSend) {
    final Transferable contents =
        Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
    try {
      final String text = getAsString(keysToSend);
      Toolkit.getDefaultToolkit().getSystemClipboard()
          .setContents(new StringSelection(text), null);
      sendKeysAndWait(text, Keys.CONTROL + "v");
    } finally {
      if (contents != null) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(contents, null);
      }
    }
  }

  private void sendKeysAndWait(final String expectedText, final CharSequence... keysToSend) {
    wrappedElement.sendKeys(keysToSend);
    if (wrappedElement.getAttribute("value") != null) {
      // wrappedElement has a 'value' attribute -> wait until this attribute was
      // updated
      final Check checkThatTextUpdated = new Check() {
        @Override
        public boolean hasFinished() {
          return wrappedElement.getAttribute("value").equals(expectedText);
        }
      };
      WaitUtil.wait(checkThatTextUpdated, EVENT_WAIT, EVENT_POLLING_INTERVAL,
          log, "Timeout while waiting for text to be set on WebElement. "
              + "Setting the text failed.");
    } else {
      waitForEvent();
    }
  }

  private static String getAsString(final CharSequence... keysToSend) {
    final StringBuilder b = new StringBuilder();
    for (final CharSequence cs : keysToSend) {
      b.append(cs);
    }
    return b.toString();
  }

  @Override
  public void clear() {
    wrappedElement.clear();
  }

  @Override
  public String getTagName() {
    return wrappedElement.getTagName();
  }

  @Override
  public String getAttribute(String name) {
    return wrappedElement.getAttribute(name);
  }

  @SuppressWarnings("deprecation")
  @Override
  public boolean toggle() {
    return wrappedElement.toggle();
  }

  @Override
  public boolean isSelected() {
    return wrappedElement.isSelected();
  }

  @SuppressWarnings("deprecation")
  @Override
  public void setSelected() {
    wrappedElement.setSelected();
  }

  @Override
  public boolean isEnabled() {
    return wrappedElement.isEnabled();
  }

  @Override
  public String getText() {
    return wrappedElement.getText();
  }

  @Override
  public List<WebElement> findElements(By by) {
    return wrap(driver, wrappedElement.findElements(by));
  }

  @Override
  public WebElement findElement(By by) {
    return wrap(driver, wrappedElement.findElement(by));
  }

  @Override
  public boolean isDisplayed() {
    return wrappedElement.isDisplayed();
  }

  @Override
  public Point getLocation() {
    return wrappedElement.getLocation();
  }

  @Override
  public Dimension getSize() {
    return wrappedElement.getSize();
  }

  @Override
  public String getCssValue(String propertyName) {
    return wrappedElement.getCssValue(propertyName);
  }

  private void waitForEvent() {
    // Some methods on WebElement (e.g. {@link #sendKeys(CharSequence...)}
    // {@link #click()}) fire an event which is processed asynchronously. As a
    // result it happens in 1 of 10 cases that the method returns before the
    // event was actually processed. This may lead to test failures since the
    // tests assume that the method was successfully completed. To avoid such
    // problems we have to ensure that the event was processed before we return.
    // Unfortunately there is no way to check that. This is why we wait here for
    // a moment to give the event enough time for being processed.
    try {
      Thread.sleep(EVENT_WAIT);
    } catch (InterruptedException e) {
    }
  }
}
