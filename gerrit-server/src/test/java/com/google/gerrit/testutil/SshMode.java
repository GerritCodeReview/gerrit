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

/**
 * Whether to enable/disable tests using SSH by inspecting the global environment.
 *
 * <p>Acceptance tests should generally not inspect this directly, since SSH may also be disabled on
 * a per-class or per-method basis. Inject {@code @SshEnabled boolean} instead.
 */
public enum SshMode {
  /** Tests annotated with UseSsh will be disabled. */
  NO,

  /** Tests annotated with UseSsh will be enabled. */
  YES;

  private static final String ENV_VAR = "GERRIT_USE_SSH";
  private static final String SYS_PROP = "gerrit.use.ssh";

  public static SshMode get() {
    String value = System.getenv(ENV_VAR);
    if (Strings.isNullOrEmpty(value)) {
      value = System.getProperty(SYS_PROP);
    }
    if (Strings.isNullOrEmpty(value)) {
      return YES;
    }
    value = value.toUpperCase();
    SshMode mode = Enums.getIfPresent(SshMode.class, value).orNull();
    if (!Strings.isNullOrEmpty(System.getenv(ENV_VAR))) {
      checkArgument(
          mode != null, "Invalid value for env variable %s: %s", ENV_VAR, System.getenv(ENV_VAR));
    } else {
      checkArgument(
          mode != null,
          "Invalid value for system property %s: %s",
          SYS_PROP,
          System.getProperty(SYS_PROP));
    }
    return mode;
  }

  public static boolean useSsh() {
    return get() == YES;
  }
}
