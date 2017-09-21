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

package com.google.gerrit.server.config;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Config;

public class AnonymousCowardNameProvider implements Provider<String> {
  public static final String DEFAULT = "Anonymous Coward";

  private final String anonymousCoward;

  @Inject
  public AnonymousCowardNameProvider(@GerritServerConfig Config cfg) {
    String anonymousCoward = cfg.getString("user", null, "anonymousCoward");
    if (anonymousCoward == null) {
      anonymousCoward = DEFAULT;
    }
    this.anonymousCoward = anonymousCoward;
  }

  @Override
  public String get() {
    return anonymousCoward;
  }
}
