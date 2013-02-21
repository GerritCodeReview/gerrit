// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.group;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;

import java.util.Set;

public class GroupAssert {

  public static void assertGroups(Iterable<String> expected, Set<String> actual) {
    for (String g : expected) {
      assertTrue("missing group " + g, actual.remove(g));
    }
    assertTrue("unexpected groups: " + actual, actual.isEmpty());
  }

  public static void assertGroupInfo(AccountGroup group, GroupInfo info) {
    if (info.name != null) {
      // 'name' is not set if returned in a map
      assertEquals(group.getName(), info.name);
    }
    assertEquals(group.getGroupUUID().get(), Url.decode(info.id));
    assertEquals(Integer.valueOf(group.getId().get()), info.group_id);
    assertEquals("#/admin/groups/uuid-" + Url.encode(group.getGroupUUID().get()), info.url);
    assertEquals(group.isVisibleToAll(), toBoolean(info.options.visible_to_all));
    assertEquals(group.getDescription(), info.description);
    assertEquals(group.getOwnerGroupUUID().get(), Url.decode(info.owner_id));
  }

  public static boolean toBoolean(Boolean b) {
    if (b == null) {
      return false;
    }
    return b.booleanValue();
  }
}
