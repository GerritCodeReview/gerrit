// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

import java.util.EnumSet;
import java.util.Set;

/** Output options available for retrieval of account details. */
public enum ListAccountsOption {
  /** Return detailed account properties. */
  DETAILS(0),

  /** Return all secondary emails. */
  ALL_EMAILS(1);

  private final int value;

  ListAccountsOption(int v) {
    this.value = v;
  }

  public int getValue() {
    return value;
  }

  public static EnumSet<ListAccountsOption> fromBits(int v) {
    EnumSet<ListAccountsOption> r = EnumSet.noneOf(ListAccountsOption.class);
    for (ListAccountsOption o : ListAccountsOption.values()) {
      if ((v & (1 << o.value)) != 0) {
        r.add(o);
        v &= ~(1 << o.value);
      }
      if (v == 0) {
        return r;
      }
    }
    if (v != 0) {
      throw new IllegalArgumentException("unknown " + Integer.toHexString(v));
    }
    return r;
  }

  public static int toBits(Set<ListAccountsOption> set) {
    int r = 0;
    for (ListAccountsOption o : set) {
      r |= 1 << o.value;
    }
    return r;
  }
}
