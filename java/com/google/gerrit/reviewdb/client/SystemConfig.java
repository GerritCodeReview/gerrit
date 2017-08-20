// Copyright (C) 2008 The Android Open Source Project
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

/** Global configuration needed to serve web requests. */
public final class SystemConfig {
  public static final class Key extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    private static final String VALUE = "X";

    @Column(id = 1, length = 1)
    protected String one = VALUE;

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
  public static SystemConfig create() {
    final SystemConfig r = new SystemConfig();
    r.singleton = new SystemConfig.Key();
    return r;
  }

  @Column(id = 1)
  protected Key singleton;

  /** Local filesystem location of header/footer/CSS configuration files */
  @Column(id = 3, notNull = false, length = Integer.MAX_VALUE)
  public transient String sitePath;

  // DO NOT LOOK BELOW THIS LINE. These fields have all been deleted,
  // but survive to support schema upgrade code.

  /** DEPRECATED DO NOT USE */
  @Column(id = 2, length = 36, notNull = false)
  public transient String registerEmailPrivateKey;
  /** DEPRECATED DO NOT USE */
  @Column(id = 4, notNull = false)
  public AccountGroup.Id adminGroupId;
  /** DEPRECATED DO NOT USE */
  @Column(id = 10, notNull = false)
  public AccountGroup.UUID adminGroupUUID;
  /** DEPRECATED DO NOT USE */
  @Column(id = 5, notNull = false)
  public AccountGroup.Id anonymousGroupId;
  /** DEPRECATED DO NOT USE */
  @Column(id = 6, notNull = false)
  public AccountGroup.Id registeredGroupId;
  /** DEPRECATED DO NOT USE */
  @Column(id = 7, notNull = false)
  public Project.NameKey wildProjectName;
  /** DEPRECATED DO NOT USE */
  @Column(id = 9, notNull = false)
  public AccountGroup.Id ownerGroupId;
  /** DEPRECATED DO NOT USE */
  @Column(id = 8, notNull = false)
  public AccountGroup.Id batchUsersGroupId;
  /** DEPRECATED DO NOT USE */
  @Column(id = 11, notNull = false)
  public AccountGroup.UUID batchUsersGroupUUID;

  protected SystemConfig() {}
}
