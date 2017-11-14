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

import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo.Status;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.group.db.Groups;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class GroupsConsistencyChecker {
  private final Groups groups;
  private final Accounts accounts;
  private final Provider<ReviewDb> db;

  @Inject
  GroupsConsistencyChecker(Groups groups, Provider<ReviewDb> db, Accounts accounts) {
    this.groups = groups;
    this.db = db;
    this.accounts = accounts;
  }

  /**
   * checkCycle walks through root's subgroups recursively, and checks for cycles and
   * that internal group references exist.
   */
  private void checkCycle(
      InternalGroup root, Map<UUID, InternalGroup> byUUID, List<ConsistencyProblemInfo> problems) {
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
   **/
  public List<ConsistencyProblemInfo> check() throws OrmException, IOException, ConfigInvalidException {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();
    Map<UUID, InternalGroup> byUUID = new HashMap<>();
    Map<String, InternalGroup> byName = new HashMap<>();
    Map<AccountGroup.Id, InternalGroup> byId = new HashMap<>();
    for (GroupReference gr : groups.getAllGroupReferences(db.get()).collect(toList())) {
      Optional<InternalGroup> optionalGroup = groups.getGroup(db.get(), gr.getUUID());
      if (!optionalGroup.isPresent()) {
        problems.add(new ConsistencyProblemInfo(Status.ERROR,
           String.format("No internal group found for %s (%s)", gr.getName(), gr.getUUID())));
        continue;
      }

      InternalGroup g = optionalGroup.get();

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

      List<Account.Id> ids = accounts.get(g.getMembers().asList();
      List<Account> members = accounts.get(ids);
      for (int i = 0; i < ids.size(); i++) {
        if (members.get(i) == null) {
          problems.add(
            new ConsistencyProblemInfo(
                Status.ERROR,
                String.format("group %s (%s) has non-existent member %s",
                 g.getName(), g.getGroupUUID(), ids.get(i)));
        }
      }
    }

    // TODO(hanwen): check that the list of IDs and list of names is exactly equal to the byName
    // and byId keys. When we do so, we must be transactional, ie. we must dereference all relevant
    // notedb refs atomically together with the getAll call.


    return problems;
  }
}
