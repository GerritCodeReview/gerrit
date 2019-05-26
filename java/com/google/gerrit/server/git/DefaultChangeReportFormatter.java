// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.inject.Inject;
import java.util.Optional;

/** Print a change description for use in git command-line progress. */
public class DefaultChangeReportFormatter implements ChangeReportFormatter {
  private static final int SUBJECT_MAX_LENGTH = 80;
  private static final String SUBJECT_CROP_APPENDIX = "...";
  private static final int SUBJECT_CROP_RANGE = 10;
  private static final String NEW_CHANGE_INDICATOR = " [NEW]";

  protected final DynamicItem<UrlFormatter> urlFormatter;

  @Inject
  public DefaultChangeReportFormatter(DynamicItem<UrlFormatter> urlFormatter) {
    this.urlFormatter = urlFormatter;
  }

  @Override
  public String newChange(ChangeReportFormatter.Input input) {
    return formatChangeUrl(input) + NEW_CHANGE_INDICATOR;
  }

  @Override
  public String changeUpdated(ChangeReportFormatter.Input input) {
    return formatChangeUrl(input);
  }

  @Override
  public String changeClosed(ChangeReportFormatter.Input input) {
    Change c = input.change();
    return String.format(
        "change %s closed",
        urlFormatter
            .get()
            .getChangeViewUrl(c.getProject(), c.getId())
            .orElse(c.getId().toString()));
  }

  protected String cropSubject(String subject) {
    if (subject.length() > SUBJECT_MAX_LENGTH) {
      int maxLength = SUBJECT_MAX_LENGTH - SUBJECT_CROP_APPENDIX.length();
      for (int cropPosition = maxLength;
          cropPosition > maxLength - SUBJECT_CROP_RANGE;
          cropPosition--) {
        if (Character.isWhitespace(subject.charAt(cropPosition - 1))) {
          return subject.substring(0, cropPosition) + SUBJECT_CROP_APPENDIX;
        }
      }
      return subject.substring(0, maxLength) + SUBJECT_CROP_APPENDIX;
    }
    return subject;
  }

  protected String formatChangeUrl(Input input) {
    Change c = input.change();
    Optional<String> changeUrl = urlFormatter.get().getChangeViewUrl(c.getProject(), c.getId());
    checkState(changeUrl.isPresent());

    StringBuilder m =
        new StringBuilder()
            .append("  ")
            .append(changeUrl.get())
            .append(" ")
            .append(cropSubject(input.subject()));
    if (input.isEdit()) {
      m.append(" [EDIT]");
    }
    if (input.isPrivate()) {
      m.append(" [PRIVATE]");
    }
    if (input.isWorkInProgress()) {
      m.append(" [WIP]");
    }
    return m.toString();
  }
}
