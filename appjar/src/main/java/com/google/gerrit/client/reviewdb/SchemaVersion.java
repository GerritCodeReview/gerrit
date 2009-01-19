// Copyright 2009 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

/** Current version of the database schema, to facilitate live upgrades. */
public final class SchemaVersion {
  public static final class Key extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final String VALUE = "X";

    @Column(length = 1)
    protected String one = VALUE;

    public Key() {
    }

    @Override
    public String get() {
      return VALUE;
    }

    @Override
    protected void set(final String newValue) {
      assert get().equals(newValue);
    }
  }

  /** Construct a new, unconfigured instance. */
  public static SchemaVersion create() {
    final SchemaVersion r = new SchemaVersion();
    r.singleton = new SchemaVersion.Key();
    return r;
  }

  @Column
  protected Key singleton;

  /** Current version number of the schema. */
  @Column
  public transient int versionNbr;

  protected SchemaVersion() {
  }
}
