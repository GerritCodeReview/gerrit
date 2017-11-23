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

package com.google.gerrit.server.group.db;

import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

/**
 * Checks individual groups for oddities, such as cycles, non-existent subgroups, etc. Only works if
 * we are writing to NoteDb.
 */
public class GroupsConsistencyChecker {
  private final AllUsersName allUsersName;
  private final GroupBackend groupBackend;
  private final Accounts accounts;
  private final GitRepositoryManager repoManager;
  private final GroupsNoteDbConsistencyChecker globalChecker;

  @Inject
  GroupsConsistencyChecker(
      AllUsersName allUsersName,
      Accounts accounts,
      GroupBackend groupBackend,
      GitRepositoryManager repositoryManager,
      GroupsNoteDbConsistencyChecker globalChecker) {
    this.accounts = accounts;
    this.groupBackend = groupBackend;
    this.globalChecker = globalChecker;
    this.repoManager = repositoryManager;
    this.allUsersName = allUsersName;
  }

  public List<ConsistencyCheckInfo.ConsistencyProblemInfo> check()
      throws OrmException, IOException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      Map<AccountGroup.UUID, InternalGroup> groups = new HashMap<>();
      List<ConsistencyCheckInfo.ConsistencyProblemInfo> problems =
          globalChecker.check(repo, groups);
      if (!problems.isEmpty()) {
        return problems;
      }

      for (InternalGroup g : groups.values()) {
        problems.addAll(checkGroup(g, groups));
      }
      return problems;
    }
  }

  /** checkCycle walks through root's subgroups recursively, and checks for cycles. */
  private List<ConsistencyCheckInfo.ConsistencyProblemInfo> checkCycle(
      InternalGroup root, Map<AccountGroup.UUID, InternalGroup> byUUID) {
    List<ConsistencyCheckInfo.ConsistencyProblemInfo> problems = new ArrayList<>();
    Set<InternalGroup> todo = new LinkedHashSet<>();
    Set<InternalGroup> seen = new HashSet<>();

    todo.add(root);
    while (!todo.isEmpty()) {
      InternalGroup t = todo.iterator().next();
      todo.remove(t);

      if (seen.contains(t)) {
        continue;
      }
      seen.add(t);

      // We don't check for owner cycles, since those are normal in self-administered groups.
      for (AccountGroup.UUID subUuid : t.getSubgroups()) {
        InternalGroup g = byUUID.get(subUuid);
        if (g == null) {
          continue;
        }

        if (Objects.equals(g, root)) {
          problems.add(
              warning(
                  "group %s (%s) contains a cycle: %s (%s) points to it as subgroup.",
                  root.getName(), root.getGroupUUID(), t.getName(), t.getGroupUUID()));
        }

        todo.add(g);
      }
    }
    return problems;
  }

  /** Checks the metadata for a single group for problems. */
  private List<ConsistencyCheckInfo.ConsistencyProblemInfo> checkGroup(
      InternalGroup g, Map<AccountGroup.UUID, InternalGroup> byUUID) throws IOException {
    List<ConsistencyCheckInfo.ConsistencyProblemInfo> problems = new ArrayList<>();

    problems.addAll(checkCycle(g, byUUID));

    if (byUUID.get(g.getOwnerGroupUUID()) == null
        && groupBackend.get(g.getOwnerGroupUUID()) == null) {
      problems.add(
          error(
              "group %s (%s) has nonexistent owner group %s",
              g.getName(), g.getGroupUUID(), g.getOwnerGroupUUID()));
    }

    for (AccountGroup.UUID subUuid : g.getSubgroups()) {
      if (byUUID.get(subUuid) == null && groupBackend.get(subUuid) == null) {
        problems.add(
            error(
                "group %s (%s) has nonexistent subgroup %s",
                g.getName(), g.getGroupUUID(), subUuid));
      }
    }

    for (Account.Id id : g.getMembers().asList()) {
      Account account;
      try {
        account = accounts.get(id);
      } catch (ConfigInvalidException e) {
        problems.add(
            error(
                "group %s (%s) has member %s with invalid configuration: %s",
                g.getName(), g.getGroupUUID(), id, e.getMessage()));
        continue;
      }
      if (account == null) {
        problems.add(
            error("group %s (%s) has nonexistent member %s", g.getName(), g.getGroupUUID(), id));
      }
    }
    return problems;
  }

  private ConsistencyCheckInfo.ConsistencyProblemInfo warning(String fmt, Object... args) {
    return new ConsistencyCheckInfo.ConsistencyProblemInfo(
        ConsistencyCheckInfo.ConsistencyProblemInfo.Status.WARNING, String.format(fmt, args));
  }

  private ConsistencyCheckInfo.ConsistencyProblemInfo error(String fmt, Object... args) {
    return new ConsistencyCheckInfo.ConsistencyProblemInfo(
        ConsistencyCheckInfo.ConsistencyProblemInfo.Status.ERROR, String.format(fmt, args));
  }
}
