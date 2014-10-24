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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GroupList {
  public static final String FILE_NAME = "groups";
  private final Map<AccountGroup.UUID, GroupReference> byUUID;

  private GroupList(Map<AccountGroup.UUID, GroupReference> byUUID) {
        this.byUUID = byUUID;
  }

  public static GroupList parse(String text, ValidationError.Sink errors) throws IOException {
    Map<AccountGroup.UUID, GroupReference> groupsByUUID = new HashMap<>();

    BufferedReader br = new BufferedReader(new StringReader(text));
    String s;
    for (int lineNumber = 1; (s = br.readLine()) != null; lineNumber++) {
      if (s.isEmpty() || s.startsWith("#")) {
        continue;
      }

      int tab = s.indexOf('\t');
      if (tab < 0) {
        errors.error(new ValidationError(FILE_NAME, lineNumber, "missing tab delimiter"));
        continue;
      }

      AccountGroup.UUID uuid = new AccountGroup.UUID(s.substring(0, tab).trim());
      String name = s.substring(tab + 1).trim();
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

  private static String pad(int len, String src) {
    if (len <= src.length()) {
      return src;
    }

    StringBuilder r = new StringBuilder(len);
    r.append(src);
    while (r.length() < len) {
      r.append(' ');
    }
    return r.toString();
  }

  private static <T extends Comparable<? super T>> List<T> sort(Collection<T> m) {
    ArrayList<T> r = new ArrayList<>(m);
    Collections.sort(r);
    return r;
  }

  public String asText() {
    if (byUUID.isEmpty()) {
      return null;
    }

    final int uuidLen = 40;
    StringBuilder buf = new StringBuilder();
    buf.append(pad(uuidLen, "# UUID"));
    buf.append('\t');
    buf.append("Group Name");
    buf.append('\n');

    buf.append('#');
    buf.append('\n');

    for (GroupReference g : sort(byUUID.values())) {
      if (g.getUUID() != null && g.getName() != null) {
        buf.append(pad(uuidLen, g.getUUID().get()));
        buf.append('\t');
        buf.append(g.getName());
        buf.append('\n');
      }
    }
    return buf.toString();
  }

  public void retainUUIDs(Collection<AccountGroup.UUID> toBeRetained) {
    byUUID.keySet().retainAll(toBeRetained);
  }

}
