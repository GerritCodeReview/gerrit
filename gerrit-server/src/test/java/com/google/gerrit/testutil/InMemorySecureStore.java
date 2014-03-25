// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.testutil;

import com.google.gerrit.server.securestore.SecureStore;

import org.eclipse.jgit.lib.Config;

public class InMemorySecureStore implements SecureStore {
  private final Config cfg = new Config();

  @Override
  public String get(String section, String subsection, String name) {
    return cfg.getString(section, subsection, name);
  }

  @Override
  public void set(String section, String subsection, String name,
      String value) {
    cfg.setString(section, subsection, name, value);
  }

  @Override
  public void unset(String section, String subsection, String name) {
    cfg.unset(section, subsection, name);
  }
}
