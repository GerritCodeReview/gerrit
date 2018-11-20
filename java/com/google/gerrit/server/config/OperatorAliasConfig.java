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

package com.google.gerrit.server.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.Config;

@Singleton
public class OperatorAliasConfig {
  private static final String SECTION = "operator-alias";
  private static final String SUBSECTION_CHANGE = "change";
  private final Config cfg;
  private final Map<String, String> changeQueryOperatorAliases;

  @Inject
  OperatorAliasConfig(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
    changeQueryOperatorAliases = new HashMap<>();
    loadChangeOperatorAliases();
  }

  public Map<String, String> getChangeQueryOperatorAliases() {
    return changeQueryOperatorAliases;
  }

  private void loadChangeOperatorAliases() {
    for (String name : cfg.getNames(SECTION, SUBSECTION_CHANGE)) {
      changeQueryOperatorAliases.put(name, cfg.getString(SECTION, SUBSECTION_CHANGE, name));
    }
  }
}
