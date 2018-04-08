// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.config;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class AllUsersNameProvider implements Provider<AllUsersName> {
  public static final String DEFAULT = "All-Users";

  private final AllUsersName name;

  @Inject
  AllUsersNameProvider(@GerritServerConfig Config cfg) {
    String n = cfg.getString("gerrit", null, "allUsers");
    if (n == null || n.isEmpty()) {
      n = DEFAULT;
    }
    name = new AllUsersName(n);
  }

  @Override
  public AllUsersName get() {
    return name;
  }
}
