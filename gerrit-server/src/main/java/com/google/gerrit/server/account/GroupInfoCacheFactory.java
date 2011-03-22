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

package com.google.gerrit.server.account;

import com.google.gerrit.common.data.GroupInfo;
import com.google.gerrit.common.data.GroupInfoCache;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Efficiently builds a {@link GroupInfoCache}. */
public class GroupInfoCacheFactory {
  public interface Factory {
    GroupInfoCacheFactory create();
  }

  private final GroupCache groupCache;
  private final Map<AccountGroup.Id, AccountGroup> out;

  @Inject
  GroupInfoCacheFactory(final GroupCache groupCache) {
    this.groupCache = groupCache;
    this.out = new HashMap<AccountGroup.Id, AccountGroup>();
  }

  /**
   * Indicate a group will be needed later on.
   *
   * @param id identity that will be needed in the future; may be null.
   */
  public void want(final AccountGroup.Id id) {
    if (id != null && !out.containsKey(id)) {
      out.put(id, groupCache.get(id));
    }
  }

  /** Indicate one or more groups will be needed later on. */
  public void want(final Iterable<AccountGroup.Id> ids) {
    for (final AccountGroup.Id id : ids) {
      want(id);
    }
  }

  public AccountGroup get(final AccountGroup.Id id) {
    want(id);
    return out.get(id);
  }

  /**
   * Create an GroupInfoCache with the currently loaded AccountGroup entities.
   * */
  public GroupInfoCache create() {
    final List<GroupInfo> r = new ArrayList<GroupInfo>(out.size());
    for (final AccountGroup a : out.values()) {
      r.add(new GroupInfo(a));
    }
    return new GroupInfoCache(r);
  }
}