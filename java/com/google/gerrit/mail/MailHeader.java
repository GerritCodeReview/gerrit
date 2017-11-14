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

package com.google.gerrit.mail;

/** Variables used by emails to hold data */
public enum MailHeader {
  // Gerrit metadata holders
  ASSIGNEE("Gerrit-Assignee"),
  BRANCH("Gerrit-Branch"),
  CC("Gerrit-CC"),
  COMMENT_IN_REPLY_TO("Comment-In-Reply-To"),
  COMMENT_DATE("Gerrit-Comment-Date"),
  CHANGE_ID("Gerrit-Change-Id"),
  CHANGE_NUMBER("Gerrit-Change-Number"),
  CHANGE_URL("Gerrit-ChangeURL"),
  COMMIT("Gerrit-Commit"),
  HAS_COMMENTS("Gerrit-HasComments"),
  HAS_LABELS("Gerrit-Has-Labels"),
  MESSAGE_TYPE("Gerrit-MessageType"),
  OWNER("Gerrit-Owner"),
  PATCH_SET("Gerrit-PatchSet"),
  PROJECT("Gerrit-Project"),
  REVIEWER("Gerrit-Reviewer"),

  // Commonly used Email headers
  AUTO_SUBMITTED("Auto-Submitted"),
  PRECEDENCE("Precedence"),
  REFERENCES("References");

  private final String name;
  private final String fieldName;

  MailHeader(String name) {
    boolean customHeader = name.startsWith("Gerrit-");
    this.name = name;

    if (customHeader) {
      this.fieldName = "X-" + name;
    } else {
      this.fieldName = name;
    }
  }

  public String fieldWithDelimiter() {
    return fieldName() + ": ";
  }

  public String withDelimiter() {
    return name + ": ";
  }

  public String fieldName() {
    return fieldName;
  }

  public String getName() {
    return name;
  }
}
