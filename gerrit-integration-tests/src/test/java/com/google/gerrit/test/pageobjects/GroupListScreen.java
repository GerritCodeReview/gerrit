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

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class GroupListScreen extends PageObject {

  public static GroupListScreen open(WebDriver driver, URL gerritUrl) {
    String url = gerritUrl.toString() + "/#/admin/groups";
    GroupListScreen page = PageObject.get(url, GroupListScreen.class, driver);
    return page;
  }

  private GroupListScreen(WebDriver driver) {
    super(driver);
  }

  @Override
  protected String getExpectedTitle() {
    return "Groups";
  }

  public List<String> getGroups() {
    List<WebElement> elements = findListStartingWithValue("a", "href", "#/admin/groups/");
    List<String> groups = new ArrayList<String>(elements.size());
    for (WebElement element : elements)
      groups.add(element.getText());
    return groups;
  }

}
