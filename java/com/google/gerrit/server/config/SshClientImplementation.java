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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

/* SSH implementation to use by JGit SSH client transport protocol. */
public enum SshClientImplementation {
  /** JCraft JSch implementation. */
  JSCH,

  /** MINA SSHD implementation. */
  MINA;

  @VisibleForTesting
  public static SshClientImplementation getFromEnvironment() {
    String value = Strings.emptyToNull(System.getenv("SSH_CLIENT_IMPLEMENTATION"));
    if (value == null) {
      value = Strings.emptyToNull(System.getProperty("gerrit.sshClientImplementation"));
    }
    return value == null ? JSCH : SshClientImplementation.valueOf(value);
  }
}
