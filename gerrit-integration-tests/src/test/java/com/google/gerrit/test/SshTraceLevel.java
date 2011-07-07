// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.test;

import com.jcraft.jsch.Logger;

public enum SshTraceLevel {
  DEBUG(Logger.DEBUG), INFO(Logger.INFO), WARN(Logger.WARN),
  ERROR(Logger.ERROR), FATAL(Logger.FATAL);

  private final int level;

  private SshTraceLevel(final int level) {
    this.level = level;
  }

  public int getLevel() {
    return level;
  }

  public static SshTraceLevel valueOf(final int level) {
    for (final SshTraceLevel sshTraceLevel : values()) {
      if (sshTraceLevel.getLevel() == level) {
        return sshTraceLevel;
      }
    }
    throw new IllegalArgumentException("No enum const class "
        + SshTraceLevel.class.getName() + " for level = " + level + ".");
  }
}