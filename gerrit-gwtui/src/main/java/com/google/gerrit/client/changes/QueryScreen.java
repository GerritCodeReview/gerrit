// Copyright (C) 2008 The Android Open Source Project
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
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;

public class QueryScreen extends PagedSingleListScreen implements ChangeListScreen {
  // Legacy numeric identifier.
  private static final RegExp NUMERIC_ID = RegExp.compile("^[1-9][0-9]*$");
  // Commit SHA1 hash
  private static final RegExp COMMIT_SHA1 = RegExp.compile("^([0-9a-fA-F]{4," + RevId.LEN + "})$");
  // Change-Id
  private static final String ID_PATTERN = "[iI][0-9a-f]{4,}$";
  private static final RegExp CHANGE_ID = RegExp.compile("^" + ID_PATTERN);
  private static final RegExp CHANGE_ID_TRIPLET = RegExp.compile("^(.)+~(.)+~" + ID_PATTERN);

  public static QueryScreen forQuery(String query) {
    return forQuery(query, 0);
  }

  public static QueryScreen forQuery(String query, int start) {
    return new QueryScreen(KeyUtil.encode(query), start);
  }

  private final String query;

  public QueryScreen(String encQuery, int start) {
    super(PageLinks.QUERY + encQuery, start);
    query = KeyUtil.decode(encQuery);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setWindowTitle(Util.M.changeQueryWindowTitle(query));
    setPageTitle(Util.M.changeQueryPageTitle(query));
  }

  @Override
  protected AsyncCallback<ChangeList> loadCallback() {
    return new GerritCallback<ChangeList>() {
      @Override
      public void onSuccess(ChangeList result) {
        if (isAttached()) {
          if (result.length() == 1 && isSingleQuery(query)) {
            ChangeInfo c = result.get(0);
            Change.Id id = c.legacyId();
            Gerrit.display(PageLinks.toChange(c.projectNameKey(), id));
          } else {
            display(result);
            QueryScreen.this.display();
          }
        }
      }
    };
  }

  @Override
  public void onShowView() {
    super.onShowView();
    Gerrit.setQueryString(query);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    ChangeList.query(query, ChangeTable.OPTIONS, loadCallback(), start, pageSize);
  }

  private static boolean isSingleQuery(String query) {
    return NUMERIC_ID.test(query)
        || CHANGE_ID.test(query)
        || CHANGE_ID_TRIPLET.test(query)
        || COMMIT_SHA1.test(query);
  }
}
