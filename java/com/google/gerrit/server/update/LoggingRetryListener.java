// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.update;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.inject.Singleton;

@Singleton
public class LoggingRetryListener implements RetryListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public void onRetry(
      String actionType, @Nullable String actionName, long nextAttempt, Throwable cause) {
    logger.atInfo().withCause(cause).log(
        "Retrying %s (%s. retry)",
        actionName != null ? actionType + "." + actionName : actionType, nextAttempt - 1);
  }
}
