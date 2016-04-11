// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GroupList extends TabFile {
  public static final String FILE_NAME = "groups";
  private final Map<AccountGroup.UUID, GroupReference> byUUID;

  private GroupList(Map<AccountGroup.UUID, GroupReference> byUUID) {
        this.byUUID = byUUID;
  }

  public static GroupList parse(String text, ValidationError.Sink errors)
      throws IOException {
    List<Row> rows = parse(text, FILE_NAME, TRIM, TRIM, errors);
    Map<AccountGroup.UUID, GroupReference> groupsByUUID =
        new HashMap<>(rows.size());
    for (Row row : rows) {
      AccountGroup.UUID uuid = new AccountGroup.UUID(row.left);
      String name = row.right;
      GroupReference ref = new GroupReference(uuid, name);

      groupsByUUID.put(uuid, ref);
    }

    return new GroupList(groupsByUUID);
  }

  public GroupReference byUUID(AccountGroup.UUID uuid) {
    return byUUID.get(uuid);
  }

  public GroupReference resolve(GroupReference group) {
    if (group != null) {
      GroupReference ref = byUUID.get(group.getUUID());
      if (ref != null) {
        return ref;
      }
      byUUID.put(group.getUUID(), group);
    }
    return group;
  }

  public Collection<GroupReference> references() {
    return byUUID.values();
  }

  public Set<AccountGroup.UUID> uuids() {
    return byUUID.keySet();
  }

  public void put(UUID uuid, GroupReference reference) {
    byUUID.put(uuid, reference);
  }

  public String asText() {
    if (byUUID.isEmpty()) {
      return null;
    }

    List<Row> rows = new ArrayList<>(byUUID.size());
    for (GroupReference g : sort(byUUID.values())) {
      if (g.getUUID() != null && g.getName() != null) {
        rows.add(new Row(g.getUUID().get(), g.getName()));
      }
    }

    return asText("UUID", "Group Name", rows);
  }

  public void retainUUIDs(Collection<AccountGroup.UUID> toBeRetained) {
    byUUID.keySet().retainAll(toBeRetained);
  }

}
