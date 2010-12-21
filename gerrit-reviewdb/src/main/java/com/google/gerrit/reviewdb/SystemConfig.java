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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

/** Global configuration needed to serve web requests. */
public final class SystemConfig {
  public static final class Key extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    private static final String VALUE = "X";

    @Column(id = 1, length = 1)
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
  public static SystemConfig create() {
    final SystemConfig r = new SystemConfig();
    r.singleton = new SystemConfig.Key();
    return r;
  }

  @Column(id = 1)
  protected Key singleton;

  /** Private key to sign account identification cookies. */
  @Column(id = 2, length = 36)
  public transient String registerEmailPrivateKey;

  /**
   * Local filesystem location of header/footer/CSS configuration files
   */
  @Column(id = 3, notNull = false)
  public transient String sitePath;

  /** Identity of the administration group; those with full access. */
  @Column(id = 4)
  public AccountGroup.Id adminGroupId;
  @Column(id = 10)
  public AccountGroup.UUID adminGroupUUID;

  /** Identity of the anonymous group, which permits anyone. */
  @Column(id = 5)
  public AccountGroup.Id anonymousGroupId;

  /** Identity of the registered users group, which permits anyone. */
  @Column(id = 6)
  public AccountGroup.Id registeredGroupId;

  /** Identity of the project  */
  @Column(id = 7)
  public Project.NameKey wildProjectName;

  /** Identity of the batch users group */
  @Column(id = 8)
  public AccountGroup.Id batchUsersGroupId;
  @Column(id = 11)
  public AccountGroup.UUID batchUsersGroupUUID;

  /** Identity of the owner group, which permits any project owner. */
  @Column(id = 9)
  public AccountGroup.Id ownerGroupId;

  protected SystemConfig() {
  }
}
