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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.admin.AdminConstants;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gwt.core.client.GWT;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class Util {
  public static final UIConstants C = GWT.create(UIConstants.class);
  public static final UIMessages M = GWT.create(UIMessages.class);

  public static String highlight(final String text, final String toHighlight) {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    if (toHighlight == null || "".equals(toHighlight)) {
      b.append(text);
      return b.toSafeHtml().asString();
    }

    int pos = 0;
    int endPos = 0;
    while ((pos = text.toLowerCase().indexOf(toHighlight.toLowerCase(), pos)) > -1) {
      if (pos > endPos) {
        b.append(text.substring(endPos, pos));
      }
      endPos = pos + toHighlight.length();
      b.openElement("b");
      b.append(text.substring(pos, endPos));
      b.closeElement("b");
      pos = endPos;
    }
    if (endPos < text.length()) {
      b.append(text.substring(endPos));
    }
    return b.toSafeHtml().asString();
  }

  public static String toLongString(final SubmitType type) {
    if (type == null) {
      return "";
    }
    switch (type) {
      case FAST_FORWARD_ONLY:
        return AdminConstants.I.projectSubmitType_FAST_FORWARD_ONLY();
      case MERGE_IF_NECESSARY:
        return AdminConstants.I.projectSubmitType_MERGE_IF_NECESSARY();
      case REBASE_IF_NECESSARY:
        return AdminConstants.I.projectSubmitType_REBASE_IF_NECESSARY();
      case REBASE_ALWAYS:
        return AdminConstants.I.projectSubmitType_REBASE_ALWAYS();
      case MERGE_ALWAYS:
        return AdminConstants.I.projectSubmitType_MERGE_ALWAYS();
      case CHERRY_PICK:
        return AdminConstants.I.projectSubmitType_CHERRY_PICK();
      default:
        return type.name();
    }
  }

  public static String toLongString(final ProjectState type) {
    if (type == null) {
      return "";
    }
    switch (type) {
      case ACTIVE:
        return AdminConstants.I.projectState_ACTIVE();
      case READ_ONLY:
        return AdminConstants.I.projectState_READ_ONLY();
      case HIDDEN:
        return AdminConstants.I.projectState_HIDDEN();
      default:
        return type.name();
    }
  }
}
