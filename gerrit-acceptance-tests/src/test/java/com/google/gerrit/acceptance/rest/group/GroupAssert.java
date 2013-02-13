package com.google.gerrit.acceptance.rest.group;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    assertEquals(group.getGroupUUID().get(), info.id);
    assertEquals(Integer.valueOf(group.getId().get()), info.group_id);
    assertEquals("#/admin/groups/uuid-" + group.getGroupUUID().get(), info.url);
    assertEquals(group.isVisibleToAll(), toBoolean(info.options.visible_to_all));
    assertEquals(group.getDescription(), info.description);
    assertEquals(group.getOwnerGroupUUID().get(), info.owner_id);
  }

  private static boolean toBoolean(Boolean b) {
    if (b == null) {
      return false;
    }
    return b.booleanValue();
  }
}
