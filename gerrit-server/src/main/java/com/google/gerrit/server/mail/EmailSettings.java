// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

@Singleton
public class EmailSettings {
  public final boolean includeDiff;
  public final int maximumDiffSize;

  private final Config cfg;

  @Inject
  EmailSettings(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
    includeDiff = cfg.getBoolean("sendemail", "includeDiff", false);
    maximumDiffSize = cfg.getInt("sendemail", "maximumDiffSize", 256 << 10);
  }

  boolean isEnabledForClass(String mc) {
    // Subsection is the only part of the config that's case-sensitive, and we
    // have mixed camelCase and lowercase in message class names. In the name of
    // consistency, we only use lower case names in the doc and convert them all
    // to lowercase here.
    return cfg.getBoolean("sendemail", mc.toLowerCase(), "enable", true);
  }
}
