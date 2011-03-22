// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.AccountGroup;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** In-memory table of {@link GroupInfo}, indexed by {@link AccountGroup.Id}. */
public class GroupInfoCache {
  private static final GroupInfoCache EMPTY;
  static {
    EMPTY = new GroupInfoCache();
    EMPTY.groups = Collections.emptyMap();
  }

  /** Obtain an empty cache singleton. */
  public static GroupInfoCache empty() {
    return EMPTY;
  }

  protected Map<AccountGroup.Id, GroupInfo> groups;

  protected GroupInfoCache() {
  }

  public GroupInfoCache(final Iterable<GroupInfo> list) {
    groups = new HashMap<AccountGroup.Id, GroupInfo>();
    for (final GroupInfo gi : list) {
      groups.put(gi.getId(), gi);
    }
  }

  /**
   * Lookup the group summary
   * <p>
   * The return value can take on one of three forms:
   * <ul>
   * <li><code>null</code>, if <code>id == null</code>.</li>
   * <li>a valid info block, if <code>id</code> was loaded.</li>
   * <li>an anonymous info block, if <code>id</code> was not loaded.</li>
   * </ul>
   *
   * @param id the id desired.
   * @return info block for the group.
   */
  public GroupInfo get(final AccountGroup.Id id) {
    if (id == null) {
      return null;
    }

    GroupInfo r = groups.get(id);
    if (r == null) {
      r = new GroupInfo(id);
      groups.put(id, r);
    }
    return r;
  }

  /** Merge the information from another cache into this one. */
  public void merge(final GroupInfoCache other) {
    assert this != EMPTY;
    groups.putAll(other.groups);
  }
}