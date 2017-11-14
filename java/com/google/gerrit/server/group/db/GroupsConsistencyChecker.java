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


import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.NameKey;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.DepthWalk.RevWalk;

/** Checks internal consistency of NoteDb storage of groups. */
@Singleton
public class GroupsConsistencyChecker {
  private final GroupBackend groupBackend;
  private final Accounts accounts;
  private final AllUsersName allUsersName;

  private final GitRepositoryManager gitMgr;

  @Inject
  GroupsConsistencyChecker(
      Accounts accounts,
      GroupBackend groupBackend,
      AllUsersName allUsersName,
      GitRepositoryManager gitMgr) {
    this.accounts = accounts;
    this.groupBackend = groupBackend;
    this.allUsersName = allUsersName;
    this.gitMgr = gitMgr;
  }

  /** Checks that all internal group references exist, and that no groups have cycles. */
  public List<ConsistencyProblemInfo> check()
      throws OrmException, IOException, ConfigInvalidException {
    // TODO(hanwen): short-circuit this code if we are not writing notedb data.

    List<ConsistencyProblemInfo> problems = new ArrayList<>();

    Map<AccountGroup.UUID, InternalGroup> byUUID = new HashMap<>();
    BiMap<AccountGroup.UUID, String> nameMap = new HashBiMap<>();

    try (Repository repo = gitMgr.openRepository(allUsersName)) {
      RefDatabase db = repo.getRefDatabase();

      readGroups(repo, db, problems, byUUID);
      readGroupNames(repo, db, problems, nameMap);

      // The sequential IDs are not keys in NoteDb, so no need to check them.
    }

    // No use continuing if we couldn't read the data.
    if (!problems.isEmpty()) {
      return problems;
    }

    // Check consistency between the data coming from different refs.
    for (AccountGroup.UUID uuid : byUUID.keySet()) {
      if (!nameMap.containsKey(uuid)) {
        problems.add(error("group %s has no entry in name map", uuid));
      }

      String noteName = nameMap.get(uuid);
      String groupRefName = byUUID.get(uuid).getName();
      if (!noteName.equals(groupRefName)) {
        problems.add(
            error("name for group %s are inconsistent (%s vs. %s)", uuid, noteName, groupRefName));
      }
    }

    for (AccountGroup.UUID uuid : nameMap.keySet()) {
      if (!byUUID.containsKey(uuid)) {
        problems.add(
            error(
                "name map has entry (%s, %s), entry missing in group ref",
                uuid, nameMap.get(uuid)));
      }
    }

    // No use delving further into inconsistent data.
    if (!problems.isEmpty()) {
      return problems;
    }

    // Check ids.
    Map<AccountGroup.Id, InternalGroup> groupById = new HashMap<>();
    for (InternalGroup g : byUUID.values()) {
      InternalGroup before = groupById.replace(g.getId(), g);
      if (before != null) {
        problems.add(
            error(
                "group id %s is shared between %s (%s) and %s (%s)",
                g.getId(), before.getName(), before.getGroupUUID(), g.getGroupUUID(), g.getName()));
      }
    }

    // Check subgroups and members.
    for (InternalGroup g : byUUID.values()) {
      checkCycle(g, byUUID, problems);

      if (groupBackend.get(g.getOwnerGroupUUID()) == null) {
        problems.add(
            error(
                "group %s (%s) has nonexistent owner group %s",
                g.getName(), g.getGroupUUID(), g.getOwnerGroupUUID()));
      }

      for (AccountGroup.UUID subUuid : g.getSubgroups()) {
        if (byUUID.get(subUuid) == null) {
          if (groupBackend.get(subUuid) == null) {
            problems.add(
                error(
                    "group %s (%s) has nonexistent subgroup %s",
                    g.getName(), g.getGroupUUID(), subUuid));
          }
        }
      }

      List<Account.Id> ids = g.getMembers().asList();
      List<Account> members = accounts.get(ids);
      for (int i = 0; i < ids.size(); i++) {
        if (members.get(i) == null) {
          problems.add(
              error(
                  "group %s (%s) has non-existent member %s",
                  g.getName(), g.getGroupUUID(), ids.get(i)));
        }
      }
    }

    return problems;
  }

  private void readGroupNames(
      Repository repo,
      RefDatabase db,
      List<ConsistencyProblemInfo> problems,
      BiMap<AccountGroup.UUID, String> result)
      throws IOException {
    ObjectId nameId = db.exactRef(RefNames.REFS_GROUPNAMES).getObjectId();

    try (ObjectReader rd = repo.getObjectDatabase().newReader();
        RevWalk rw = new RevWalk(repo, 1)) {
      NoteMap nm = NoteMap.read(repo.getObjectDatabase().newReader(), rw.parseCommit(nameId));

      Iterator<Note> noteIter = nm.iterator();
      while (noteIter.hasNext()) {
        Note nt = noteIter.next();
        ObjectId key = nt;
        ObjectLoader ld = rd.open(nt.getData());
        byte[] data = ld.getCachedBytes();
        try {
          GroupReference gref = GroupNameNotes.getFromNoteData(data);
          result.put(gref.getUUID(), gref.getName());
          ObjectId nameKey = GroupNameNotes.getNoteKey(new NameKey(gref.getName()));
          if (!nameKey.equals(key)) {
            problems.add(error("notename entry %s does not match name %s", key, gref.getName()));
          }
        } catch (ConfigInvalidException e) {
          problems.add(
              error(
                  "notename entry %s: %s does not parse",
                  key, new String(data, StandardCharsets.UTF_8)));
        }
      }
    }
  }

  private void readGroups(
      Repository repo,
      RefDatabase db,
      List<ConsistencyProblemInfo> problems,
      Map<AccountGroup.UUID, InternalGroup> byUUID)
      throws IOException {
    for (Map.Entry<String, Ref> entry : db.getRefs(RefNames.REFS_GROUPS).entrySet()) {
      AccountGroup.UUID uuid = AccountGroup.UUID.fromRef(entry.getKey());
      Preconditions.checkNotNull(uuid);

      try {
        GroupConfig cfg =
            GroupConfig.loadForGroupSnapshot(repo, uuid, entry.getValue().getObjectId());
        byUUID.put(uuid, cfg.getLoadedGroup().get());
      } catch (ConfigInvalidException e) {
        problems.add(error("group %s does not parse", uuid));
      }
    }
  }

  /**
   * checkCycle walks through root's subgroups recursively, and checks for cycles and that internal
   * group references exist.
   */
  private void checkCycle(
      InternalGroup root,
      Map<AccountGroup.UUID, InternalGroup> byUUID,
      List<ConsistencyProblemInfo> problems) {
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

      // We don't check for owner cycles, since those are normal in self-administered groups.
      for (AccountGroup.UUID subUuid : t.getSubgroups()) {
        InternalGroup g = byUUID.get(subUuid);
        if (g == null) {
          continue;
        }

        if (g == root) {
          problems.add(
              warning(
                  "group %s (%s) contains a cycle: %s (%s) points to it as subgroup.",
                  root.getName(), root.getGroupUUID(), t.getName(), t.getGroupUUID()));
          continue;
        }

        todo.add(g);
      }
    }
  }

  private ConsistencyProblemInfo warning(String fmt, Object... args) {
    return new ConsistencyProblemInfo(
        ConsistencyProblemInfo.Status.WARNING, String.format(fmt, args));
  }

  private ConsistencyProblemInfo error(String fmt, Object... args) {
    return new ConsistencyProblemInfo(
        ConsistencyProblemInfo.Status.ERROR, String.format(fmt, args));
  }
}
