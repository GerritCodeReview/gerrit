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

package com.google.gerrit.server.mail;

/**
 * Variables used by emails to hold data
 **/
public enum Metadata {
  // Gerrit metadata holders
  CHANGE_NUMBER("Gerrit-Change-Number", true),
  PATCH_SET("Gerrit-PatchSet", true),
  MESSAGE_TYPE("Gerrit-MessageType", true),
  TIMESTAMP("Gerrit-Comment-Date", true),

  // Commonly used Email headers
  PRECEDENCE("Precedence", false),
  AUTO_SUBMITTED("Auto-Submitted", false);

  private final String name;
  private final String fieldName;

  Metadata(String name, boolean customHeader) {
    this.name = name;

    if (customHeader) {
      this.fieldName = "X-" + name;
    } else {
      this.fieldName = name;
    }
  }

  public String withDelimiter() {
    return fieldName() + ": ";
  }

  public String fieldName() {
    return fieldName;
  }

  public String getName() {
    return name;
  }
}
