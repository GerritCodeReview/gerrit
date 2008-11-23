// Copyright 2008 Google Inc.
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

/** Global configuration needed to serve web requests. */
public final class SystemConfig {
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
  }

  public static SystemConfig create() {
    final SystemConfig r = new SystemConfig();
    r.singleton = new SystemConfig.Key();
    r.maxSessionAge = 12 * 60 * 60 /* seconds */;
    return r;
  }

  @Column
  protected Key singleton;

  /** Private key to sign XSRF protection tokens. */
  @Column(length = 36)
  public String xsrfPrivateKey;

  /** Private key to sign account identification cookies. */
  @Column(length = 36)
  public String accountPrivateKey;

  /** Maximum web session age, in seconds. */
  @Column
  public int maxSessionAge;

  protected SystemConfig() {
  }
}
