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

import com.google.gerrit.reviewdb.client.AccountGroup;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** In-memory table of {@link GroupInfo}, indexed by {@code AccountGroup.Id}. */
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

  protected Map<AccountGroup.UUID, GroupInfo> groups;

  protected GroupInfoCache() {
  }

  public GroupInfoCache(final Iterable<GroupInfo> list) {
    groups = new HashMap<AccountGroup.UUID, GroupInfo>();
    for (final GroupInfo gi : list) {
      groups.put(gi.getId(), gi);
    }
  }

  /**
   * Lookup the group summary
   * <p>
   * The return value can take on one of three forms:
   * <ul>
   * <li>{@code null}, if {@code id == null}.</li>
   * <li>a valid info block, if {@code id} was loaded.</li>
   * <li>an anonymous info block, if {@code id} was not loaded.</li>
   * </ul>
   *
   * @param uuid the id desired.
   * @return info block for the group.
   */
  public GroupInfo get(final AccountGroup.UUID uuid) {
    if (uuid == null) {
      return null;
    }

    GroupInfo r = groups.get(uuid);
    if (r == null) {
      r = new GroupInfo(uuid);
      groups.put(uuid, r);
    }
    return r;
  }

  /** Merge the information from another cache into this one. */
  public void merge(final GroupInfoCache other) {
    assert this != EMPTY;
    groups.putAll(other.groups);
  }
}