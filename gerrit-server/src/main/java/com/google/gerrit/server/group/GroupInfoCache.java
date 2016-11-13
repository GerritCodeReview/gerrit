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

package com.google.gerrit.server.group;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupBackend;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/** Efficiently builds a {@link GroupInfoCache}. */
public class GroupInfoCache {
  public interface Factory {
    GroupInfoCache create();
  }

  private final GroupBackend groupBackend;
  private final Map<AccountGroup.UUID, GroupDescription.Basic> out;

  @Inject
  GroupInfoCache(GroupBackend groupBackend) {
    this.groupBackend = groupBackend;
    this.out = new HashMap<>();
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

  public GroupDescription.Basic get(final AccountGroup.UUID uuid) {
    want(uuid);
    return out.get(uuid);
  }
}
