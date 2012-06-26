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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwtjsonrpc.client.JsonUtil;

public class Util {
  public static final ChangeConstants C = GWT.create(ChangeConstants.class);
  public static final ChangeMessages M = GWT.create(ChangeMessages.class);
  public static final ChangeResources R = GWT.create(ChangeResources.class);

  public static final ChangeDetailService DETAIL_SVC;
  public static final ChangeListService LIST_SVC;
  public static final ChangeManageService MANAGE_SVC;

  private static final int SUBJECT_MAX_LENGTH = 80;
  private static final String SUBJECT_CROP_APPENDIX = "...";

  static {
    DETAIL_SVC = GWT.create(ChangeDetailService.class);
    JsonUtil.bind(DETAIL_SVC, "rpc/ChangeDetailService");

    LIST_SVC = GWT.create(ChangeListService.class);
    JsonUtil.bind(LIST_SVC, "rpc/ChangeListService");

    MANAGE_SVC = GWT.create(ChangeManageService.class);
    JsonUtil.bind(MANAGE_SVC, "rpc/ChangeManageService");
  }

  public static String toLongString(final Change.Status status) {
    if (status == null) {
      return "";
    }
    switch (status) {
      case DRAFT:
        return C.statusLongDraft();
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

  /**
   * Crops the given change subject if needed so that it has at most
   * {@link #SUBJECT_MAX_LENGTH} characters.
   *
   * @return if the given subject is not longer than {@link #SUBJECT_MAX_LENGTH}
   *         characters it is returned unchanged, otherwise it is cropped and
   *         {@link #SUBJECT_CROP_APPENDIX} is appended to the cropped subject,
   *         the cropped subject including the appendix has
   *         {@link #SUBJECT_MAX_LENGTH} characters
   */
  public static String cropSubject(final String subject) {
    if (subject.length() > SUBJECT_MAX_LENGTH) {
      return subject.substring(0,
          SUBJECT_MAX_LENGTH - SUBJECT_CROP_APPENDIX.length())
          + SUBJECT_CROP_APPENDIX;
    }
    return subject;
  }
}
