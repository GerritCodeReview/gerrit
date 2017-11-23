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

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.NameKey;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.notedb.GroupsMigration;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

/** Check the referential integrity of NoteDb group storage */
public class GroupsNoteDbConsistencyChecker {
  private final GroupsMigration groupsMigration;

  private ImmutableMap<AccountGroup.UUID, InternalGroup> uuidToGroupMap = ImmutableMap.of();
  private ImmutableBiMap<AccountGroup.UUID, String> uuidNameBiMap = ImmutableBiMap.of();

  @Inject
  GroupsNoteDbConsistencyChecker(GroupsMigration groupsMigration) {
    this.groupsMigration = groupsMigration;
  }

  public ImmutableMap<AccountGroup.UUID, InternalGroup> getUuidToGroupMap() {
    return uuidToGroupMap;
  }

  public List<ConsistencyProblemInfo> check(Repository repo) throws OrmException, IOException {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();
    if (!groupsMigration.writeToNoteDb()) {
      return problems;
    }

    // Get all refs in an attempt to avoid seeing half committed group updates.
    Map<String, Ref> refs = repo.getAllRefs();
    problems.addAll(readGroups(repo, refs));
    problems.addAll(readGroupNames(repo, refs));

    // The sequential IDs are not keys in NoteDb, so no need to check them.

    if (problems.isEmpty()) {
      // Continue checking if we could read data without problems.
      checkGlobalConsistency();
    }
    return problems;
  }

  private List<ConsistencyProblemInfo> readGroups(Repository repo, Map<String, Ref> refs)
      throws IOException {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();
    ImmutableBiMap.Builder<AccountGroup.UUID, InternalGroup> builder = ImmutableBiMap.builder();

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
        builder.put(uuid, cfg.getLoadedGroup().get());
      } catch (ConfigInvalidException e) {
        problems.add(error("group %s does not parse: %s", uuid, e.getMessage()));
      }
    }

    uuidToGroupMap = builder.build();
    return problems;
  }

  private List<ConsistencyProblemInfo> readGroupNames(Repository repo, Map<String, Ref> refs)
      throws IOException {
    Ref ref = refs.get(RefNames.REFS_GROUPNAMES);
    if (ref == null) {
      String msg = String.format("ref %s does not exist", RefNames.REFS_GROUPNAMES);
      return Arrays.asList(groupsMigration.readFromNoteDb() ? error(msg) : warning(msg));
    }

    List<ConsistencyProblemInfo> problems = new ArrayList<>();
    ImmutableBiMap.Builder<AccountGroup.UUID, String> builder = ImmutableBiMap.builder();

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
        builder.put(gRef.getUUID(), gRef.getName());
      }
    }

    uuidNameBiMap = builder.build();
    return problems;
  }

  /** Check invariants of the group refs with the groupname refs. */
  private List<ConsistencyProblemInfo> checkGlobalConsistency() {
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
