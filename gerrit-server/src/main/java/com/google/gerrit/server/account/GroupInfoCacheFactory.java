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

import com.google.common.collect.Maps;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupInfoCache;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.inject.Inject;

import java.util.Map;

/** Efficiently builds a {@link GroupInfoCache}. */
public class GroupInfoCacheFactory {
  public interface Factory {
    GroupInfoCacheFactory create();
  }

  private final GroupBackend groupBackend;
  private final Map<AccountGroup.UUID, GroupDescription.Basic> out;

  @Inject
  GroupInfoCacheFactory(GroupBackend groupBackend) {
    this.groupBackend = groupBackend;
    this.out = Maps.newHashMap();
  }

  /**
   * Indicate a group will be needed later on.
   *
   * @param uuid identity that will be needed in the future; may be null.
   */
  public void want(final AccountGroup.UUID uuid) {
    if (uuid != null && !out.containsKey(uuid)) {
      out.put(uuid, groupBackend.get(uuid));
    }
  }

  /** Indicate one or more groups will be needed later on. */
  public void want(final Iterable<AccountGroup.UUID> uuids) {
    for (final AccountGroup.UUID uuid : uuids) {
      want(uuid);
    }
  }

  public GroupDescription.Basic get(final AccountGroup.UUID uuid) {
    want(uuid);
    return out.get(uuid);
  }
}