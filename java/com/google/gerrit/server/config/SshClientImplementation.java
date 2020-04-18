// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Enums;
import com.google.common.base.Strings;

/* SSH implementation to use by JGit SSH client transport protocol. */
public enum SshClientImplementation {
  /** JCraft JSch implementation. */
  JSCH,

  /** Apache MINA implementation. */
  APACHE;

  private static final String ENV_VAR = "SSH_CLIENT_IMPLEMENTATION";
  private static final String SYS_PROP = "gerrit.sshClientImplementation";

  @VisibleForTesting
  public static SshClientImplementation getFromEnvironment() {
    String value = System.getenv(ENV_VAR);
    if (Strings.isNullOrEmpty(value)) {
      value = System.getProperty(SYS_PROP);
    }
    if (Strings.isNullOrEmpty(value)) {
      return JSCH;
    }
    SshClientImplementation client =
        Enums.getIfPresent(SshClientImplementation.class, value).orNull();
    if (!Strings.isNullOrEmpty(System.getenv(ENV_VAR))) {
      checkArgument(
          client != null, "Invalid value for env variable %s: %s", ENV_VAR, System.getenv(ENV_VAR));
    } else {
      checkArgument(
          client != null,
          "Invalid value for system property %s: %s",
          SYS_PROP,
          System.getProperty(SYS_PROP));
    }
    return client;
  }
}
