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

package com.google.gerrit.testutil;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Enums;
import com.google.common.base.Strings;

public enum SshMode {
  /** Tests annotated with UseSsh will be disabled. */
  NO,

  /** Tests annotated with UseSsh will be enabled. */
  YES;

  private static final String VAR = "GERRIT_USE_SSH";

  public static SshMode get() {
    String value = System.getenv(VAR);
    if (Strings.isNullOrEmpty(value)) {
      return YES;
    }
    value = value.toUpperCase().replace("-", "_");
    SshMode mode = Enums.getIfPresent(SshMode.class, value).orNull();
    checkArgument(mode != null,
        "Invalid value for %s: %s", VAR, System.getenv(VAR));
    return mode;
  }

  public static boolean useSsh() {
    return get() == YES;
  }
}
