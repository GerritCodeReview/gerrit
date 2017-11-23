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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.NameKey;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.notedb.GroupsMigration;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

/** Checks internal consistency of NoteDb storage of groups. */
@Singleton
public class GroupsNoteDbConsistencyChecker {
  private final AllUsersName allUsersName;
  private final GitRepositoryManager repoManager;
  private final GroupsMigration groupsMigration;

  @Inject
  GroupsNoteDbConsistencyChecker(
      AllUsersName allUsersName,
      GitRepositoryManager repositoryManager,
      GroupsMigration groupsMigration) {
    this.allUsersName = allUsersName;
    this.repoManager = repositoryManager;
    this.groupsMigration = groupsMigration;
  }

  /** Checks that all internal group references exist, and that no groups have cycles. */
  public List<ConsistencyProblemInfo> check() throws OrmException, IOException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      Map<AccountGroup.UUID, InternalGroup> groups = new HashMap<>();
      return check(repo, groups);
    }
  }

  /**
   * Performs a consistency check. Collects the internal groups in the byUUID map. If problems were
   * found, the contents of the byUUID map are undefined. If we are not writing to NoteDb, the
   * byUUID map is left unchanged.
   */
  public List<ConsistencyProblemInfo> check(
      Repository repo, Map<AccountGroup.UUID, InternalGroup> byUUID)
      throws OrmException, IOException {
    if (!groupsMigration.writeToNoteDb()) {
      return new ArrayList<>();
    }

    // Get all refs in an attempt to avoid seeing half committed group updates.
    Map<String, Ref> refs = repo.getAllRefs();

    List<ConsistencyProblemInfo> problems = new ArrayList<>();
    BiMap<AccountGroup.UUID, String> nameMap = HashBiMap.create();

    readGroups(repo, refs, problems, byUUID);
    readGroupNames(repo, refs, problems, nameMap);

    // The sequential IDs are not keys in NoteDb, so no need to check them.

    // No use continuing if we couldn't read the data.
    if (!problems.isEmpty()) {
      return problems;
    }

    problems = checkGlobalConsistency(byUUID, nameMap);
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
              groupsMigration.readFromNoteDb()
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

        // We trust SHA1 to have no collisions, so no need to check uniqueness of name.
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

  private ConsistencyProblemInfo warning(String fmt, Object... args) {
    return new ConsistencyProblemInfo(
        ConsistencyProblemInfo.Status.WARNING, String.format(fmt, args));
  }

  private ConsistencyProblemInfo error(String fmt, Object... args) {
    return new ConsistencyProblemInfo(
        ConsistencyProblemInfo.Status.ERROR, String.format(fmt, args));
  }
}
