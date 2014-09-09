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

package com.google.gerrit.server.securestore;

import java.util.List;

public interface SecureStore {
  public static class EntryKey {
    public final String name;
    public final String section;
    public final String subsection;

    public EntryKey(String section, String subsection, String name) {
      this.name = name;
      this.section = section;
      this.subsection = subsection;
    }
  }

  String get(String section, String subsection, String name);

  String[] getList(String section, String subsection, String name);

  void set(String section, String subsection, String name, String value);

  void setList(String section, String subsection, String name, List<String> values);

  void unset(String section, String subsection, String name);

  Iterable<EntryKey> list();
}
