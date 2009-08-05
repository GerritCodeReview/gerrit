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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

public final class TrustedExternalId {
  public static class Key extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column
    protected String pattern;

    protected Key() {
    }

    public Key(final String re) {
      pattern = re;
    }

    @Override
    public String get() {
      return pattern;
    }

    @Override
    protected void set(String newValue) {
      pattern = newValue;
    }
  }

  @Column
  protected Key idPattern;

  protected TrustedExternalId() {
  }

  public TrustedExternalId(final TrustedExternalId.Key k) {
    idPattern = k;
  }

  public TrustedExternalId.Key getKey() {
    return idPattern;
  }

  public String getIdPattern() {
    return idPattern.pattern;
  }

  public boolean matches(final AccountExternalId id) {
    final String p = getIdPattern();
    if (p.startsWith("^") && p.endsWith("$")) {
      return id.getExternalId().matches(p);
    }
    return id.getExternalId().startsWith(p);
  }
}
