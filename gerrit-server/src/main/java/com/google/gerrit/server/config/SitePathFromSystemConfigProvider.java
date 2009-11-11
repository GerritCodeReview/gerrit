// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.reviewdb.SystemConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.File;

/** Provides {@link java.io.File} annotated with {@link SitePath}. */
public class SitePathFromSystemConfigProvider implements Provider<File> {
  private final File path;

  @Inject
  SitePathFromSystemConfigProvider(final SystemConfig config) {
    final String p = config.sitePath;
    path = new File(p != null && p.length() > 0 ? p : ".").getAbsoluteFile();
  }

  @Override
  public File get() {
    return path;
  }
}
