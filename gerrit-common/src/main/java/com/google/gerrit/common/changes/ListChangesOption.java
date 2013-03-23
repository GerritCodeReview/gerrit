// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.common.changes;

import java.util.EnumSet;

/** Output options available when using {@code /changes/} RPCs. */
public enum ListChangesOption {
  LABELS(0),
  DETAILED_LABELS(8),

  /** Return information on the current patch set of the change. */
  CURRENT_REVISION(1),
  ALL_REVISIONS(2),

  /** If revisions are included, parse the commit object. */
  CURRENT_COMMIT(3),
  ALL_COMMITS(4),

  /** If a patch set is included, include the files of the patch set. */
  CURRENT_FILES(5),
  ALL_FILES(6),

  /** If accounts are included, include detailed account info. */
  DETAILED_ACCOUNTS(7),

  /** Include messages associated with the change. */
  MESSAGES(9);

  private final int value;

  private ListChangesOption(int v) {
    this.value = v;
  }

  public int getValue() {
    return value;
  }

  public static EnumSet<ListChangesOption> fromBits(int v) {
    EnumSet<ListChangesOption> r = EnumSet.noneOf(ListChangesOption.class);
    for (ListChangesOption o : ListChangesOption.values()) {
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

  public static int toBits(EnumSet<ListChangesOption> set) {
    int r = 0;
    for (ListChangesOption o : set) {
      r |= 1 << o.value;
    }
    return r;
  }
}
