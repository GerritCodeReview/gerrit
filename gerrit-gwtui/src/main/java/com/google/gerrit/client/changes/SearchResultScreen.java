// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.common.data.SingleListChangeInfo;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;

/**
 * Displays on screen the results for changes search by commit messages among
 * all repositories or specific project.
 */
public class SearchResultScreen extends PagedSingleListScreen {
  private final static String SEARCH_PREFIX = "commitmessages:";

  private final String text;
  private final String project;
  private final String searchString;

  public SearchResultScreen(final String text, final String positionToken) {
    super("commitmessages," + text, positionToken);

    searchString = KeyUtil.decode(text);

    final int searchPrefixIndex = searchString.indexOf(SEARCH_PREFIX);

    if (searchString.startsWith("project:")) {
      final int projectPrefixIndex = searchString.indexOf(':');
      project = searchString.substring(projectPrefixIndex + 1, searchPrefixIndex).trim();
    } else {
      project = null;
    }
    this.text =
        searchString.substring(searchPrefixIndex + SEARCH_PREFIX.length())
            .replaceAll("[\"]", "");
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setWindowTitle(Util.M.changeQueryWindowTitle(text));
    if (project != null) {
      setPageTitle(Util.M.changeSearchByProjectPageTitle(text, project));
    } else {
      setPageTitle(Util.M.changeQueryPageTitle(text));
    }
  }

  @Override
  protected AsyncCallback<SingleListChangeInfo> loadCallback() {
    return new GerritCallback<SingleListChangeInfo>() {
      public final void onSuccess(final SingleListChangeInfo result) {
        if (isAttached()) {
          if (result.getChanges().size() == 1) {
            final ChangeInfo c = result.getChanges().get(0);
            Gerrit.display(PageLinks.toChange(c), new ChangeScreen(c));
          } else {
            Gerrit.setQueryString(searchString);
            display(result);
            SearchResultScreen.this.display();
          }
        }
      }
    };
  }

  @Override
  protected void loadNext() {
    Util.LIST_SVC.loadNextSearchChanges(text, project, pos, pageSize,
        loadCallback());
  }

  @Override
  protected void loadPrev() {
    Util.LIST_SVC.loadPrevSearchChanges(text, pos, pageSize, loadCallback());
  }
}
