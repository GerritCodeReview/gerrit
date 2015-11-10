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
import com.google.gerrit.client.documentation.DocInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;

public class QueryScreen extends PagedSingleListScreen implements
    ChangeListScreen {
  private static final String DOCS_SEARCH_URI = "/Documentation/";

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
      public final void onSuccess(ChangeList result) {
        if (isAttached()) {
          if (result.length() == 1 && isSingleQuery(query)) {
            ChangeInfo c = result.get(0);
            Change.Id id = c.legacyId();
            Gerrit.display(PageLinks.toChange(id));
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
    ChangeList.query(
        query, ChangeTable.OPTIONS, loadCallback(), start, pageSize);
    doDocsQuery();
  }

  private AsyncCallback<JsArray<DocInfo>> loadDocsCallback() {
    return new GerritCallback<JsArray<DocInfo>>() {
      @Override
      public void onSuccess(JsArray<DocInfo> result) {
        displayDocsResults(result);
        display();
      }
    };
  }

  private void doDocsQuery() {
    RestApi call = new RestApi(DOCS_SEARCH_URI);
    call.addParameterRaw("q", query);
    call.get(loadDocsCallback());
  }

  private static boolean isSingleQuery(String query) {
    if (query.matches("^[1-9][0-9]*$")) {
      // Legacy numeric identifier.
      //
      return true;
    }

    if (query.matches("^[iI][0-9a-f]{4,}$")) {
      // Newer style Change-Id.
      //
      return true;
    }

    if (query.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      // Commit SHA-1 of any change.
      //
      return true;
    }

    return false;
  }
}
