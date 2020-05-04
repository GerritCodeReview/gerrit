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

package com.google.gerrit.testing;

import com.google.gerrit.server.logging.LoggingContext;

public class FloggerInitializer {
  private static final String FLOGGER_BACKEND_PROPERTY = "flogger.backend_factory";
  private static final String FLOGGER_LOGGING_CONTEXT = "flogger.logging_context";

  private FloggerInitializer() {}

  public static void initBackend() {
    System.setProperty(
        FLOGGER_BACKEND_PROPERTY,
        "com.google.common.flogger.backend.log4j.Log4jBackendFactory#getInstance");
    System.setProperty(FLOGGER_LOGGING_CONTEXT, LoggingContext.class.getName() + "#getInstance");
  }
}
