// Copyright (C) 2024 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/** Account related settings from {@code gerrit.config}. */
@Singleton
public class AccountConfig {
  private final boolean enableDelete;
  private final String[] caseInsensitiveLocalParts;

  @Inject
  AccountConfig(@GerritServerConfig Config cfg) {
    enableDelete = cfg.getBoolean("accounts", "enableDelete", true);
    caseInsensitiveLocalParts = cfg.getStringList("accounts", null, "caseInsensitiveLocalPart");
  }

  public String[] getCaseInsensitiveLocalParts() {
    return caseInsensitiveLocalParts;
  }

  public boolean isDeleteEnabled() {
    return enableDelete;
  }
}
