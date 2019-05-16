// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import java.util.Collection;

/** Raised when one or more mandatory plugins are missing. */
public class MissingMandatoryPluginsException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public MissingMandatoryPluginsException(Collection<String> pluginNames) {
    super(getMessage(pluginNames));
  }

  private static String getMessage(Collection<String> pluginNames) {
    return String.format("Cannot find or load the following mandatory plugins: %s", pluginNames);
  }
}
