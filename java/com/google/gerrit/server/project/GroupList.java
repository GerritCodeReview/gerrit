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

package com.google.gerrit.server.project;

import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.git.ValidationError;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.meta.TabFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupList extends TabFile {
  private static final Logger log = LoggerFactory.getLogger(GroupList.class);

  public static final String FILE_NAME = "groups";

  private final Map<AccountGroup.UUID, GroupReference> byUUID;

  private GroupList(Map<AccountGroup.UUID, GroupReference> byUUID) {
    this.byUUID = byUUID;
  }

  public static GroupList parse(Project.NameKey project, String text, ValidationError.Sink errors)
      throws IOException {
    List<Row> rows = parse(text, FILE_NAME, TRIM, TRIM, errors);
    Map<AccountGroup.UUID, GroupReference> groupsByUUID = new HashMap<>(rows.size());
    for (Row row : rows) {
      if (row.left == null) {
        log.warn("null field in group list for {}:\n{}", project, text);
        continue;
      }
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
      if (group.getUUID() == null || group.getUUID().get() == null) {
        // A GroupReference from ProjectConfig that refers to a group not found
        // in this file will have a null UUID. Since there may be multiple
        // different missing references, it's not appropriate to cache the
        // results, nor return null the set from #uuids.
        return group;
      }
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

  public void put(AccountGroup.UUID uuid, GroupReference reference) {
    if (uuid == null || uuid.get() == null) {
      return; // See note in #resolve above.
    }
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
