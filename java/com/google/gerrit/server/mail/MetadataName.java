// Copyright (C) 2016 The Android Open Source Project
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

public final class MetadataName {
  public static final String CHANGE_NUMBER = "Gerrit-Change-Number";
  public static final String PATCH_SET = "Gerrit-PatchSet";
  public static final String MESSAGE_TYPE = "Gerrit-MessageType";
  public static final String TIMESTAMP = "Gerrit-Comment-Date";

  public static String toHeader(String metadataName) {
    return "X-" + metadataName;
  }

  public static String toHeaderWithDelimiter(String metadataName) {
    return toHeader(metadataName) + ": ";
  }

  public static String toFooterWithDelimiter(String metadataName) {
    return metadataName + ": ";
  }
}
