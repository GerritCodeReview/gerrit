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

import com.google.gerrit.common.data.ChangeDetailService;
import com.google.gerrit.common.data.ChangeListService;
import com.google.gerrit.common.data.ChangeManageService;
import com.google.gerrit.common.data.TopicDetailService;
import com.google.gerrit.common.data.TopicManageService;
import com.google.gerrit.reviewdb.AbstractEntity.Status;
import com.google.gwt.core.client.GWT;
import com.google.gwtjsonrpc.client.JsonUtil;

public class Util {
  public static final ChangeConstants C = GWT.create(ChangeConstants.class);
  public static final ChangeMessages M = GWT.create(ChangeMessages.class);
  public static final ChangeResources R = GWT.create(ChangeResources.class);

  public static final TopicMessages TM = GWT.create(TopicMessages.class);
  public static final TopicConstants TC = GWT.create(TopicConstants.class);

  public static final ChangeDetailService DETAIL_SVC;
  public static final ChangeListService LIST_SVC;
  public static final ChangeManageService MANAGE_SVC;

  public static final TopicDetailService T_DETAIL_SVC;
  public static final TopicManageService T_MANAGE_SVC;

  static {
    DETAIL_SVC = GWT.create(ChangeDetailService.class);
    JsonUtil.bind(DETAIL_SVC, "rpc/ChangeDetailService");

    LIST_SVC = GWT.create(ChangeListService.class);
    JsonUtil.bind(LIST_SVC, "rpc/ChangeListService");

    MANAGE_SVC = GWT.create(ChangeManageService.class);
    JsonUtil.bind(MANAGE_SVC, "rpc/ChangeManageService");

    T_DETAIL_SVC = GWT.create(TopicDetailService.class);
    JsonUtil.bind(T_DETAIL_SVC, "rpc/TopicDetailService");

    T_MANAGE_SVC = GWT.create(TopicManageService.class);
    JsonUtil.bind(T_MANAGE_SVC, "rpc/TopicManageService");
  }

  public static String toLongString(final Status status) {
    if (status == null) {
      return "";
    }
    switch (status) {
      case NEW:
        return C.statusLongNew();
      case SUBMITTED:
        return C.statusLongSubmitted();
      case MERGED:
        return C.statusLongMerged();
      case ABANDONED:
        return C.statusLongAbandoned();
      default:
        return status.name();
    }
  }
}
