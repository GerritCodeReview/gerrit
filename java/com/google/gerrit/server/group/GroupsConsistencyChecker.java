// Copyright (C) 2017 The Android Open Source Project
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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo.Status;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.group.db.Groups;
import com.google.gwtorm.server.OrmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class GroupsConsistencyChecker {
  private final Groups groups;
  private final Provider<ReviewDb> db;

  @Inject
  GroupsConsistencyChecker(Groups groups, Provider<ReviewDb> db) {
    this.groups = groups;
    this.db = db;
  }

  /**
   * checkCycle walks through root's subgroups recursively, and checks for cycles and
   * that internal group references exist.
   */
  private void checkCycle(
      InternalGroup root, Map<UUID, InternalGroup> byUUID, List<ConsistencyProblemInfo> problems) {
    // todo => source
    Set<InternalGroup> todo = new HashSet<>();
    todo.add(root);
    Set<InternalGroup> seen = new HashSet<>();

    while (!todo.isEmpty()) {
      InternalGroup t = todo.iterator().next();
      todo.remove(t);

      if (seen.contains(t)) {
        continue;
      }
      seen.add(t);

      InternalGroup owner = byUUID.get(t.getOwnerGroupUUID());

      if (owner == null) {
        problems.add(
            new ConsistencyProblemInfo(
                Status.WARNING,
                String.format(
                    "group %s (%s) refers to nonexistent group %s as subgroup",
                    t.getName(), t.getGroupUUID(), t.getOwnerGroupUUID())));
      }
      for (UUID subUuid : t.getSubgroups()) {
        InternalGroup g = byUUID.get(subUuid);

        if (g == root) {
          problems.add(
              new ConsistencyProblemInfo(
                  Status.WARNING,
                  String.format(
                      "group %s (%s) contains a cycle: %s (%s) points to it as subgroup.",
                      root.getName(),
                      root.getGroupUUID(),
                      t.getName(),
                      t.getGroupUUID())));
        } else if (g == null) {
          problems.add(
              new ConsistencyProblemInfo(
                  Status.ERROR,
                  String.format(
                      "group %s (%s) refers to nonexistent group %s as subgroup",
                      t.getName(), t.getGroupUUID(), subUuid)));
        } else {
          todo.add(g);
        }
      }
    }
  }

  /**
   * Checks that all internal group references exist, and that no groups have cycles.
   *
   * @throws OrmException if there was a ReviewDb failure.
   */
  public List<ConsistencyProblemInfo> check() throws OrmException {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();
    Map<UUID, InternalGroup> byUUID = new HashMap<>();
    Map<String, InternalGroup> byName = new HashMap<>();
    Map<AccountGroup.Id, InternalGroup> byId = new HashMap<>();
    for (InternalGroup g : groups.getAll(db.get()).collect(toList())) {
      InternalGroup before = byUUID.putIfAbsent(g.getGroupUUID(), g);
      if (before != null) {
        problems.add(
            new ConsistencyProblemInfo(
                Status.ERROR,
                String.format(
                    "already have group with UUID %s, new %s, old %s",
                    g.getGroupUUID(), g.getName(), before.getName())));
        return problems;
      }

      before = byName.putIfAbsent(g.getName(), g);
      if (before != null) {
        problems.add(
            new ConsistencyProblemInfo(
                Status.ERROR,
                String.format(
                    "already have group with name %s, new %s, old %s",
                    g.getName(), g.getGroupUUID(), before.getGroupUUID())));
        return problems;
      }

      before = byId.get(g.getId());
      if (before != null) {
        problems.add(
            new ConsistencyProblemInfo(
                Status.ERROR,
                String.format(
                    "already have group with id %s, new %s, old %s",
                    g.getId(), g.getGroupUUID(), before.getGroupUUID())));
        return problems;
      }
    }

    for (InternalGroup g : byUUID.values()) {
      checkCycle(g, byUUID, problems);
    }

    // TODO(hanwen): check that the list of IDs and list of names is exactly equal to the byName
    // and byId keys. When we do so, we must be transactional, ie. we must dereference all relevant
    // notedb refs atomically together with the getAll call.

    // TODO(hanwen): should we check that group members exist?

    return problems;
  }
}
