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

import com.google.gerrit.test.GerritTestProperty;
import com.google.gerrit.test.util.Check;
import com.google.gerrit.test.util.WaitUtil;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ProjectListScreen extends PageObject {

  private final static int HTTP_TIMEOUT = GerritTestProperty.HTTP_TIMEOUT.get();
  private final static int HTTP_POLLING_INTERVAL =
      GerritTestProperty.HTTP_POLLING_INTERVAL.get();
  private static final Logger log = LoggerFactory.getLogger(ProjectListScreen.class);


  public static ProjectListScreen open(final WebDriver driver,
      final URL gerritUrl) {
    final String url = gerritUrl.toString() + "/#/admin/projects";
    return PageObject.get(url, ProjectListScreen.class, driver);
  }

  private ProjectListScreen(final WebDriver driver) {
    super(driver);
  }

  @Override
  protected String getExpectedTitle() {
    return "Projects";
  }

  public List<String> getProjects() {
    final List<WebElement> elements =
        findListStartingWithValue("a", "href", "#/admin/projects/");
    final List<String> projects = new ArrayList<String>(elements.size());
    if (elements.size() > 0 && elements.get(0).getAttribute("role") != null) {
      elements.remove(0);
    }
    for (final WebElement element : elements)
      projects.add(getElementText(element));
    return projects;
  }

  private static String getElementText(final WebElement element) {
    Check elementLoadedCheck = new Check() {
      @Override
      public boolean hasFinished() {
        String text = element.getText();
        return text != null && text.length() > 0;
      }
    };
    WaitUtil.wait(elementLoadedCheck, HTTP_TIMEOUT, HTTP_POLLING_INTERVAL, log,
        "Timeout during waiting for async field content loading ");
    return element.getText();
  }

}
