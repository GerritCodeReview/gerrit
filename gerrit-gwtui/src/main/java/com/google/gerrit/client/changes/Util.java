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

import com.google.gerrit.common.data.ActionService;
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
  public static final ActionService ACTION_SVC;

  private static final int SUBJECT_MAX_LENGTH = 80;
  private static final String SUBJECT_CROP_APPENDIX = "...";
  private static final int SUBJECT_CROP_RANGE = 10;

  static {
    DETAIL_SVC = GWT.create(ChangeDetailService.class);
    JsonUtil.bind(DETAIL_SVC, "rpc/ChangeDetailService");

    LIST_SVC = GWT.create(ChangeListService.class);
    JsonUtil.bind(LIST_SVC, "rpc/ChangeListService");

    MANAGE_SVC = GWT.create(ChangeManageService.class);
    JsonUtil.bind(MANAGE_SVC, "rpc/ChangeManageService");

    ACTION_SVC = GWT.create(ActionService.class);
    JsonUtil.bind(ACTION_SVC, "rpc/ActionService");
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
   * If the given subject is not longer than {@link #SUBJECT_MAX_LENGTH}
   * characters it is returned unchanged.
   *
   * If the length of the given subject exceeds {@link #SUBJECT_MAX_LENGTH}
   * characters it is cropped. In this case {@link #SUBJECT_CROP_APPENDIX} is
   * appended to the cropped subject, the cropped subject including the appendix
   * has at most {@link #SUBJECT_MAX_LENGTH} characters.
   *
   * If cropping is needed, the subject will be cropped after the last space
   * character that is found within the last {@link #SUBJECT_CROP_RANGE}
   * characters of the potentially visible characters. If no such space is
   * found, the subject will be cropped so that the cropped subject including
   * the appendix has exactly {@link #SUBJECT_MAX_LENGTH} characters.
   *
   * @return the subject, cropped if needed
   */
  @SuppressWarnings("deprecation")
  public static String cropSubject(final String subject) {
    if (subject.length() > SUBJECT_MAX_LENGTH) {
      final int maxLength = SUBJECT_MAX_LENGTH - SUBJECT_CROP_APPENDIX.length();
      for (int cropPosition = maxLength; cropPosition > maxLength - SUBJECT_CROP_RANGE; cropPosition--) {
        // Character.isWhitespace(char) can't be used because this method is not supported by GWT,
        // see https://developers.google.com/web-toolkit/doc/1.6/RefJreEmulation#Package_java_lang
        if (Character.isSpace(subject.charAt(cropPosition - 1))) {
          return subject.substring(0, cropPosition) + SUBJECT_CROP_APPENDIX;
        }
      }
      return subject.substring(0, maxLength) + SUBJECT_CROP_APPENDIX;
    }
    return subject;
  }
}
