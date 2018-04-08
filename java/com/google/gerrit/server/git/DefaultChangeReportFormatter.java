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

import com.google.gerrit.git.ChangeReportFormatter;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;

public class DefaultChangeReportFormatter implements ChangeReportFormatter {
  private final String canonicalWebUrl;

  @Inject
  DefaultChangeReportFormatter(@CanonicalWebUrl String canonicalWebUrl) {
    this.canonicalWebUrl = canonicalWebUrl;
  }

  @Override
  public String newChange(ChangeReportFormatter.Input input) {
    return formatChangeUrl(canonicalWebUrl, input);
  }

  @Override
  public String changeUpdated(ChangeReportFormatter.Input input) {
    return formatChangeUrl(canonicalWebUrl, input);
  }

  @Override
  public String changeClosed(ChangeReportFormatter.Input input) {
    return String.format(
        "change %s closed", ChangeUtil.formatChangeUrl(canonicalWebUrl, input.change()));
  }

  private String formatChangeUrl(String url, Input input) {
    StringBuilder m =
        new StringBuilder()
            .append("  ")
            .append(ChangeUtil.formatChangeUrl(url, input.change()))
            .append(" ")
            .append(ChangeUtil.cropSubject(input.subject()));
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
