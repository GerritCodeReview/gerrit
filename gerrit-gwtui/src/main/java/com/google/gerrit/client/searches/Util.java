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

package com.google.gerrit.client.searches;

import com.google.gerrit.common.data.SearchListService;
import com.google.gerrit.common.data.SearchService;
import com.google.gerrit.reviewdb.Owner;
import com.google.gwt.core.client.GWT;
import com.google.gwtjsonrpc.client.JsonUtil;

public class Util {
  public static final SearchConstants C = GWT.create(SearchConstants.class);
  public static final SearchMessages M = GWT.create(SearchMessages.class);

  public static final SearchService SEARCH_SVC;
  public static final SearchListService SEARCH_LIST_SVC;

  static {
    SEARCH_SVC = GWT.create(SearchService.class);
    JsonUtil.bind(SEARCH_SVC, "rpc/SearchService");

    SEARCH_LIST_SVC = GWT.create(SearchListService.class);
    JsonUtil.bind(SEARCH_LIST_SVC, "rpc/SearchListService");
  }

  public static String getType(Owner.Id id) {
    return getType(id.getType());
  }

  public static String getType(Owner.Type type) {
    switch(type) {
      case USER:    return C.ownerTypeUser();
      case GROUP:   return C.ownerTypeGroup();
      case PROJECT: return C.ownerTypeProject();
      case SITE:    return C.ownerTypeSite();
    }
    return "";
  }
}
