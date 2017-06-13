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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

/** Current version of the database schema, to facilitate live upgrades. */
public final class CurrentSchemaVersion {
  public static final class Key extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    private static final String VALUE = "X";

    @Column(id = 1, length = 1)
    public String one = VALUE;

    public Key() {}

    @Override
    public String get() {
      return VALUE;
    }

    @Override
    protected void set(String newValue) {
      assert get().equals(newValue);
    }
  }

  /** Construct a new, unconfigured instance. */
  public static CurrentSchemaVersion create() {
    final CurrentSchemaVersion r = new CurrentSchemaVersion();
    r.singleton = new CurrentSchemaVersion.Key();
    return r;
  }

  @Column(id = 1)
  public Key singleton;

  /** Current version number of the schema. */
  @Column(id = 2)
  public transient int versionNbr;

  public CurrentSchemaVersion() {}
}
