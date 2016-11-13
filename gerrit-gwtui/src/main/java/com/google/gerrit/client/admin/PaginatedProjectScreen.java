// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.http.client.URL;

abstract class PaginatedProjectScreen extends ProjectScreen {
  protected int pageSize;
  protected String match = "";
  protected int start;

  PaginatedProjectScreen(Project.NameKey toShow) {
    super(toShow);
    pageSize = Gerrit.getUserPreferences().changesPerPage();
  }

  protected void parseToken(String token) {
    for (String kvPair : token.split("[,;&/?]")) {
      String[] kv = kvPair.split("=", 2);
      if (kv.length != 2 || kv[0].isEmpty()) {
        continue;
      }

      if ("filter".equals(kv[0])) {
        match = URL.decodeQueryString(kv[1]);
      }

      if ("skip".equals(kv[0]) && URL.decodeQueryString(kv[1]).matches("^[\\d]+")) {
        start = Integer.parseInt(URL.decodeQueryString(kv[1]));
      }
    }
  }

  protected void parseToken() {
    parseToken(getToken());
  }

  protected String getTokenForScreen(String filter, int skip) {
    String token = getScreenToken();
    if (filter != null && !filter.isEmpty()) {
      token += "?filter=" + URL.encodeQueryString(filter);
    }
    if (skip > 0) {
      if (token.contains("?filter=")) {
        token += ",";
      } else {
        token += "?";
      }
      token += "skip=" + skip;
    }
    return token;
  }

  protected abstract String getScreenToken();

  protected void setupNavigationLink(Hyperlink link, String filter, int skip) {
    link.setTargetHistoryToken(getTokenForScreen(filter, skip));
    link.setVisible(true);
  }
}
