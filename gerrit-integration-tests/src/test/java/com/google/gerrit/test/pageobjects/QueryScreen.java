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

import static com.google.gerrit.test.GerritConstants.CHANGE_CONSTANTS;
import static com.google.gerrit.test.GerritConstants.CHANGE_MESSAGES;

import com.google.gerrit.test.Change;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

public class QueryScreen extends PageObject {

  private final static String NEXT_PAGE_LABEL = CHANGE_CONSTANTS
      .pagedChangeListNext().substring(0,
          CHANGE_CONSTANTS.pagedChangeListNext().length() - 8);
  private final static String PREVIOUS_PAGE_LABEL = CHANGE_CONSTANTS
      .pagedChangeListPrev().substring(8);

  private final String query;

  public static QueryScreen open(final WebDriver driver, final URL gerritUrl,
      final String query) {
    final String url = gerritUrl.toString() + "/#/q/" + query + ",n,z";
    return PageObject.get(url, QueryScreen.class, driver, query);
  }

  public QueryScreen(final WebDriver driver, final String query) {
    super(driver);
    this.query = query;
  }

  @Override
  protected String getExpectedTitle() {
    return query;
  }

  /**
   * Returns the changes from the current page. There might be more changes on
   * further pages which are not returned by this method.
   */
  public List<Change> getChanges() {
    final List<Change> changes = new LinkedList<Change>();
    final List<List<WebElement>> changeRows =
        findTableRows(CHANGE_MESSAGES.changeQueryPageTitle(query), false);
    for (final List<WebElement> changeRow : changeRows) {
      changes.add(toChange(changeRow));
    }
    return changes;
  }

  private static Change toChange(final List<WebElement> changeRow) {
    return new Change(changeRow.get(2).getText(), changeRow.get(3).getText(),
        changeRow.get(4).getText(), changeRow.get(5).getText(), changeRow
            .get(6).getText(), changeRow.get(7).getText());
  }

  public boolean hasNextPage() {
    return hasStartingWithValue("a", NEXT_PAGE_LABEL);
  }

  public QueryScreen nextPage() {
    return navigateByClick(findStartingWithValue("a", NEXT_PAGE_LABEL),
        QueryScreen.class, query);
  }

  public boolean hasPreviousPage() {
    return hasStartingWithValue("a", PREVIOUS_PAGE_LABEL);
  }

  public QueryScreen previousPage() {
    return navigateByClick(findStartingWithValue("a", PREVIOUS_PAGE_LABEL),
        QueryScreen.class);
  }
}
