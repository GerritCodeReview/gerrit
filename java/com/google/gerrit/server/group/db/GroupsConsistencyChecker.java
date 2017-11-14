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

import static com.google.gerrit.server.notedb.NoteDbTable.GROUPS;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;

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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

/** Checks internal consistency of NoteDb storage of groups. */
@Singleton
public class GroupsConsistencyChecker {
  private final GroupBackend groupBackend;
  private final Accounts accounts;
  private final AllUsersName allUsersName;
  private final GitRepositoryManager repoManager;
  private final boolean writeGroups;
  private final boolean readGroups;

  @Inject
  GroupsConsistencyChecker(
      Accounts accounts,
      GroupBackend groupBackend,
      AllUsersName allUsersName,
      GitRepositoryManager repositoryManager,
      @GerritServerConfig Config config) {
    this.accounts = accounts;
    this.groupBackend = groupBackend;
    this.allUsersName = allUsersName;
    this.repoManager = repositoryManager;
    this.writeGroups =
        config.getBoolean(SECTION_NOTE_DB, GROUPS.key(), NotesMigration.WRITE, false);
    this.readGroups = config.getBoolean(SECTION_NOTE_DB, GROUPS.key(), NotesMigration.READ, false);
  }

  /** Checks that all internal group references exist, and that no groups have cycles. */
  public List<ConsistencyProblemInfo> check() throws OrmException, IOException {
    if (!writeGroups) {
      return new ArrayList<>();
    }

    try (Repository repo = repoManager.openRepository(allUsersName)) {
      return check(repo);
    }
  }

  public List<ConsistencyProblemInfo> check(Repository repo) throws OrmException, IOException {
    // Get all refs in attempt to avoid seeing half committed group updates.8
    Map<String, Ref> refs = repo.getAllRefs();

    List<ConsistencyProblemInfo> problems = new ArrayList<>();
    Map<AccountGroup.UUID, InternalGroup> byUUID = new HashMap<>();
    BiMap<AccountGroup.UUID, String> nameMap = HashBiMap.create();

    readGroups(repo, refs, problems, byUUID);
    readGroupNames(repo, refs, problems, nameMap);

    // The sequential IDs are not keys in NoteDb, so no need to check them.

    // No use continuing if we couldn't read the data.
    if (!problems.isEmpty()) {
      return problems;
    }

    problems = checkGlobalConsistency(byUUID, nameMap);
    if (!problems.isEmpty()) {
      return problems;
    }

    // Check subgroups and members.
    for (InternalGroup g : byUUID.values()) {
      problems.addAll(checkGroup(g, byUUID));
    }
    return problems;
  }

  /** Check invariants of the group refs with the groupname refs. */
  private List<ConsistencyProblemInfo> checkGlobalConsistency(
      Map<AccountGroup.UUID, InternalGroup> byUUID, BiMap<AccountGroup.UUID, String> nameMap) {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();

    // Check consistency between the data coming from different refs.
    for (AccountGroup.UUID uuid : byUUID.keySet()) {
      if (!nameMap.containsKey(uuid)) {
        problems.add(error("group %s has no entry in name map", uuid));
        continue;
      }

      String noteName = nameMap.get(uuid);
      String groupRefName = byUUID.get(uuid).getName();
      if (!Objects.equals(noteName, groupRefName)) {
        problems.add(
            error(
                "inconsistent name for group %s (name map %s vs. group ref %s)",
                uuid, noteName, groupRefName));
      }
    }

    for (AccountGroup.UUID uuid : nameMap.keySet()) {
      if (!byUUID.containsKey(uuid)) {
        problems.add(
            error(
                "name map has entry (%s, %s), entry missing as group ref",
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
      InternalGroup before = groupById.get(g.getId());
      if (before != null) {
        problems.add(
            error(
                "shared group id %s for %s (%s) and %s (%s)",
                g.getId(), before.getName(), before.getGroupUUID(), g.getName(), g.getGroupUUID()));
      }
      groupById.put(g.getId(), g);
    }
    return problems;
  }

  /** Checks the metadata for a single group for problems. */
  private List<ConsistencyProblemInfo> checkGroup(
      InternalGroup g, Map<AccountGroup.UUID, InternalGroup> byUUID) throws IOException {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();

    problems.addAll(checkCycle(g, byUUID));

    if (groupBackend.get(g.getOwnerGroupUUID()) == null) {
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

  private void readGroupNames(
      Repository repo,
      Map<String, Ref> refs,
      List<ConsistencyProblemInfo> problems,
      BiMap<AccountGroup.UUID, String> result)
      throws IOException {
    Ref ref = refs.get(RefNames.REFS_GROUPNAMES);
    if (ref == null) {
      problems.add(
          new ConsistencyProblemInfo(
              readGroups
                  ? ConsistencyProblemInfo.Status.ERROR
                  : ConsistencyProblemInfo.Status.WARNING,
              String.format("ref %s does not exist", RefNames.REFS_GROUPNAMES)));
      return;
    }

    try (RevWalk rw = new RevWalk(repo)) {
      NoteMap nm = NoteMap.read(rw.getObjectReader(), rw.parseCommit(ref.getObjectId()));
      for (Note note : nm) {
        ObjectLoader ld = rw.getObjectReader().open(note.getData());
        byte[] data = ld.getCachedBytes();

        GroupReference gRef;
        try {
          gRef = GroupNameNotes.getFromNoteData(data);
        } catch (ConfigInvalidException e) {
          problems.add(
              error(
                  "notename entry %s: %s does not parse: %s",
                  note, new String(data, StandardCharsets.UTF_8), e.getMessage()));
          continue;
        }

        ObjectId nameKey = GroupNameNotes.getNoteKey(new NameKey(gRef.getName()));
        if (!Objects.equals(nameKey, note)) {
          problems.add(error("notename entry %s does not match name %s", note, gRef.getName()));
        }

        // We trust SHA1 to have no collisions, so need to check uniqueness of name.
        result.put(gRef.getUUID(), gRef.getName());
      }
    }
  }

  private void readGroups(
      Repository repo,
      Map<String, Ref> refs,
      List<ConsistencyProblemInfo> problems,
      Map<AccountGroup.UUID, InternalGroup> byUUID)
      throws IOException {
    for (Map.Entry<String, Ref> entry : refs.entrySet()) {
      if (!entry.getKey().startsWith(RefNames.REFS_GROUPS)) {
        continue;
      }

      AccountGroup.UUID uuid = AccountGroup.UUID.fromRef(entry.getKey());
      if (uuid == null) {
        problems.add(error("null UUID from %s", entry.getKey()));
        continue;
      }
      try {
        GroupConfig cfg =
            GroupConfig.loadForGroupSnapshot(repo, uuid, entry.getValue().getObjectId());

        byUUID.put(uuid, cfg.getLoadedGroup().get());
      } catch (ConfigInvalidException e) {
        problems.add(error("group %s does not parse: %s", uuid, e.getMessage()));
      }
    }
  }

  /** checkCycle walks through root's subgroups recursively, and checks for cycles. */
  private List<ConsistencyProblemInfo> checkCycle(
      InternalGroup root, Map<AccountGroup.UUID, InternalGroup> byUUID) {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();
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

  private ConsistencyProblemInfo warning(String fmt, Object... args) {
    return new ConsistencyProblemInfo(
        ConsistencyProblemInfo.Status.WARNING, String.format(fmt, args));
  }

  private ConsistencyProblemInfo error(String fmt, Object... args) {
    return new ConsistencyProblemInfo(
        ConsistencyProblemInfo.Status.ERROR, String.format(fmt, args));
  }
}
