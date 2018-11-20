// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.securestore.testing;

import com.google.gerrit.server.securestore.SecureStore;
import java.util.List;
import org.eclipse.jgit.lib.Config;

public class InMemorySecureStore extends SecureStore {
  private final Config cfg = new Config();

  @Override
  public String[] getList(String section, String subsection, String name) {
    return cfg.getStringList(section, subsection, name);
  }

  @Override
  public String[] getListForPlugin(
      String pluginName, String section, String subsection, String name) {
    throw new UnsupportedOperationException("not used by tests");
  }

  @Override
  public void setList(String section, String subsection, String name, List<String> values) {
    cfg.setStringList(section, subsection, name, values);
  }

  @Override
  public void unset(String section, String subsection, String name) {
    cfg.unset(section, subsection, name);
  }

  @Override
  public Iterable<EntryKey> list() {
    throw new UnsupportedOperationException("not used by tests");
  }

  @Override
  public boolean isOutdated() {
    throw new UnsupportedOperationException("not used by tests");
  }

  @Override
  public void reload() {
    throw new UnsupportedOperationException("not used by tests");
  }
}
