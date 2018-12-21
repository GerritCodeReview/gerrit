// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.common.base.CaseFormat;

public enum SoyTemplate {
  ABANDONED,
  ADD_KEY,
  CHANGE_FOOTER,
  CHANGE_SUBJECT(false),
  COMMENT,
  COMMENT_FOOTER,
  DELETE_REVIEWER,
  DELETE_VOTE,
  INBOUND_EMAIL_REJECTION,
  FOOTER,
  HEADER,
  MERGED,
  NEW_CHANGE,
  NO_REPLY_FOOTER,
  PRIVATE(false),
  REGISTER_NEW_EMAIL,
  REPLACE_PATCH_SET,
  RESTORED,
  REVERTED,
  SET_ASSIGNEE;

  private final boolean hasHtml;

  SoyTemplate() {
    this(true);
  }

  SoyTemplate(boolean hasHtml) {
    this.hasHtml = hasHtml;
  }

  public boolean hasHtml() {
    return hasHtml;
  }

  public String templateName() {
    return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name());
  }

  public String htmlTemplateName() {
    if (!hasHtml) {
      throw new UnsupportedOperationException("template " + this + " has no HTML variant");
    }
    return templateName() + "Html";
  }

  public String fileName() {
    return templateName() + ".soy";
  }

  public String htmlFileName() {
    return htmlTemplateName() + ".soy";
  }
}
