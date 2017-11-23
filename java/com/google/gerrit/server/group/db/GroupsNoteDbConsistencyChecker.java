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

import static com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo.error;
import static com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo.warning;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.NameKey;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.notedb.GroupsMigration;
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

/** Check the referential integrity of NoteDb group storage. */
@Singleton
public class GroupsNoteDbConsistencyChecker {
  private final GroupsMigration groupsMigration;

  /**
   * The result of a consistency check. The UUID map is only non-null if no problems were detected.
   */
  public static class Result {
    public List<ConsistencyProblemInfo> problems;

    @Nullable public Map<AccountGroup.UUID, InternalGroup> uuidToGroupMap;
  }

  @Inject
  GroupsNoteDbConsistencyChecker(GroupsMigration groupsMigration) {
    this.groupsMigration = groupsMigration;
  }

  /**
   * Checks for problems with the given All-Users repo. Returns null if we are not writing to
   * NoteDb.
   */
  @Nullable
  public Result check(Repository repo) throws IOException {
    if (!groupsMigration.writeToNoteDb()) {
      return null;
    }

    Result r = doCheck(repo);
    if (!r.problems.isEmpty()) {
      r.uuidToGroupMap = null;
    }
    return r;
  }

  @Nullable
  private Result doCheck(Repository repo) throws IOException {
    Result result = new Result();
    result.problems = new ArrayList<>();
    result.uuidToGroupMap = new HashMap<>();

    BiMap<AccountGroup.UUID, String> uuidNameBiMap = HashBiMap.create();

    // Get all refs in an attempt to avoid seeing half committed group updates.
    Map<String, Ref> refs = repo.getAllRefs();
    readGroups(repo, refs, result);
    readGroupNames(repo, refs, result, uuidNameBiMap);
    // The sequential IDs are not keys in NoteDb, so no need to check them.

    if (!result.problems.isEmpty()) {
      return result;
    }

    // Continue checking if we could read data without problems.
    result.problems.addAll(checkGlobalConsistency(result.uuidToGroupMap, uuidNameBiMap));

    return result;
  }

  private void readGroups(Repository repo, Map<String, Ref> refs, Result result)
      throws IOException {
    for (Map.Entry<String, Ref> entry : refs.entrySet()) {
      if (!entry.getKey().startsWith(RefNames.REFS_GROUPS)) {
        continue;
      }

      AccountGroup.UUID uuid = AccountGroup.UUID.fromRef(entry.getKey());
      if (uuid == null) {
        result.problems.add(error("null UUID from %s", entry.getKey()));
        continue;
      }
      try {
        GroupConfig cfg =
            GroupConfig.loadForGroupSnapshot(repo, uuid, entry.getValue().getObjectId());
        result.uuidToGroupMap.put(uuid, cfg.getLoadedGroup().get());
      } catch (ConfigInvalidException e) {
        result.problems.add(error("group %s does not parse: %s", uuid, e.getMessage()));
      }
    }
  }

  private void readGroupNames(
      Repository repo, Map<String, Ref> refs, Result result, BiMap<UUID, String> uuidNameBiMap)
      throws IOException {
    Ref ref = refs.get(RefNames.REFS_GROUPNAMES);
    if (ref == null) {
      String msg = String.format("ref %s does not exist", RefNames.REFS_GROUPNAMES);
      result.problems.add(groupsMigration.readFromNoteDb() ? error(msg) : warning(msg));
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
          result.problems.add(
              error(
                  "notename entry %s: %s does not parse: %s",
                  note, new String(data, StandardCharsets.UTF_8), e.getMessage()));
          continue;
        }

        ObjectId nameKey = GroupNameNotes.getNoteKey(new NameKey(gRef.getName()));
        if (!Objects.equals(nameKey, note)) {
          result.problems.add(
              error("notename entry %s does not match name %s", note, gRef.getName()));
        }

        // We trust SHA1 to have no collisions, so no need to check uniqueness of name.
        uuidNameBiMap.put(gRef.getUUID(), gRef.getName());
      }
    }
  }

  /** Check invariants of the group refs with the group name refs. */
  private List<ConsistencyProblemInfo> checkGlobalConsistency(
      Map<AccountGroup.UUID, InternalGroup> uuidToGroupMap,
      BiMap<AccountGroup.UUID, String> uuidNameBiMap) {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();

    // Check consistency between the data coming from different refs.
    for (AccountGroup.UUID uuid : uuidToGroupMap.keySet()) {
      if (!uuidNameBiMap.containsKey(uuid)) {
        problems.add(error("group %s has no entry in name map", uuid));
        continue;
      }

      String noteName = uuidNameBiMap.get(uuid);
      String groupRefName = uuidToGroupMap.get(uuid).getName();
      if (!Objects.equals(noteName, groupRefName)) {
        problems.add(
            error(
                "inconsistent name for group %s (name map %s vs. group ref %s)",
                uuid, noteName, groupRefName));
      }
    }

    for (AccountGroup.UUID uuid : uuidNameBiMap.keySet()) {
      if (!uuidToGroupMap.containsKey(uuid)) {
        problems.add(
            error(
                "name map has entry (%s, %s), entry missing as group ref",
                uuid, uuidNameBiMap.get(uuid)));
      }
    }

    if (problems.isEmpty()) {
      // Check ids.
      Map<AccountGroup.Id, InternalGroup> groupById = new HashMap<>();
      for (InternalGroup g : uuidToGroupMap.values()) {
        InternalGroup before = groupById.get(g.getId());
        if (before != null) {
          problems.add(
              error(
                  "shared group id %s for %s (%s) and %s (%s)",
                  g.getId(),
                  before.getName(),
                  before.getGroupUUID(),
                  g.getName(),
                  g.getGroupUUID()));
        }
        groupById.put(g.getId(), g);
      }
    }

    return problems;
  }
}
