// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.common.flogger.FluentLogger;
import com.google.inject.Singleton;
import java.io.IOException;
import org.apache.sshd.common.Service;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionDisconnectHandler;

@Singleton
public class LogMaxConnectionsPerUserExceeded implements SessionDisconnectHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public boolean handleSessionsCountDisconnectReason(
      Session session,
      Service service,
      String username,
      int currentSessionCount,
      int maxSessionCount)
      throws IOException {
    logger.atWarning().log(
        "Max connection count for user %s exceeded, rejecting new connection."
            + " currentSessionCount = %d, maxSessionCount = %d",
        username, currentSessionCount, maxSessionCount);
    return false;
  }
}
