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

/**
 * @deprecated Use {@link Metadata} instead!
 */
@Deprecated
public final class MetadataName {
  @Deprecated
  public static final String CHANGE_NUMBER = "Gerrit-Change-Number";
  @Deprecated
  public static final String PATCH_SET = "Gerrit-PatchSet";
  @Deprecated
  public static final String MESSAGE_TYPE = "Gerrit-MessageType";
  @Deprecated
  public static final String TIMESTAMP = "Gerrit-Comment-Date";

  @Deprecated
  public static String toHeader(String metadataName) {
    return "X-" + metadataName;
  }

  @Deprecated
  public static String toHeaderWithDelimiter(String metadataName) {
    return toHeader(metadataName) + ": ";
  }

  @Deprecated
  public static String toFooterWithDelimiter(String metadataName) {
    return metadataName + ": ";
  }
}
