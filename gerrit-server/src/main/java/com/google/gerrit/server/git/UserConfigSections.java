// Copyright (C) 2015 The Android Open Source Project
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

public class UserConfigSections {

  /** The general user preferences. */
  public static final String GENERAL = "general";

  /** The my menu user preferences. */
  public static final String MY = "my";

  public static final String KEY_URL = "url";
  public static final String KEY_TARGET = "target";
  public static final String KEY_ID = "id";
  public static final String URL_ALIAS = "urlAlias";
  public static final String KEY_MATCH = "match";
  public static final String KEY_TOKEN = "token";

  /** The edit user preferences. */
  public static final String EDIT = "edit";

  /** The diff user preferences. */
  public static final String DIFF = "diff";

  private UserConfigSections() {}
}
