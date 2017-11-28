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
import static com.google.gerrit.server.group.db.GroupNameNotes.getGroupReference;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.InternalGroup;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Check the referential integrity of NoteDb group storage. */
@Singleton
public class GroupsNoteDbConsistencyChecker {
  private static final Logger log = LoggerFactory.getLogger(GroupsNoteDbConsistencyChecker.class);

  private static final String GROUP_CONSISTENCY_CHECK_LOG_PREFIX = "[GroupConsistencyCheck]: ";

  /**
   * The result of a consistency check. The UUID map is only non-null if no problems were detected.
   */
  public static class Result {
    public List<ConsistencyProblemInfo> problems;

    @Nullable public Map<AccountGroup.UUID, InternalGroup> uuidToGroupMap;
  }

  @Inject
  GroupsNoteDbConsistencyChecker() {}

  /** Checks for problems with the given All-Users repo. */
  public Result check(Repository repo) throws IOException {
    Result r = doCheck(repo);
    if (!r.problems.isEmpty()) {
      r.uuidToGroupMap = null;
    }
    return r;
  }

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
      Repository repo,
      Map<String, Ref> refs,
      Result result,
      BiMap<AccountGroup.UUID, String> uuidNameBiMap)
      throws IOException {
    Ref ref = refs.get(RefNames.REFS_GROUPNAMES);
    if (ref == null) {
      String msg = String.format("ref %s does not exist", RefNames.REFS_GROUPNAMES);
      result.problems.add(error(msg));
      return;
    }

    try (RevWalk rw = new RevWalk(repo)) {
      RevCommit c = rw.parseCommit(ref.getObjectId());
      NoteMap nm = NoteMap.read(rw.getObjectReader(), c);

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

        ObjectId nameKey = GroupNameNotes.getNoteKey(new AccountGroup.NameKey(gRef.getName()));
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

  /**
   * Check group 'uuid' and 'name' read from 'group.config' with group name notes.
   *
   * @param allUsersRepo 'AllUsersName' repository.
   * @param name the name of the group to be checked.
   * @param uuid the {@code AccountGroup.UUID} of the group to be checked.
   * @return a list of {@code ConsistencyProblemInfo} containing the problem details.
   */
  public static List<ConsistencyProblemInfo> checkWithGroupNameNotes(
      Repository allUsersRepo, String name, AccountGroup.UUID uuid) {
    try {
      Ref ref = allUsersRepo.exactRef(RefNames.REFS_GROUPNAMES);
      if (ref == null) {
        return Arrays.asList(warning("ref %s does not exist", RefNames.REFS_GROUPNAMES));
      }

      try (RevWalk revWalk = new RevWalk(allUsersRepo);
          ObjectReader reader = revWalk.getObjectReader()) {
        RevCommit notesCommit = revWalk.parseCommit(ref.getObjectId());
        NoteMap noteMap = NoteMap.read(reader, notesCommit);
        ObjectId noteDataBlobId =
            noteMap.get(GroupNameNotes.getNoteKey(new AccountGroup.NameKey(name)));

        if (noteDataBlobId == null) {
          return Arrays.asList(
              warning("Group with name '%s' doesn't exist in the list of all names", name));
        }

        List<ConsistencyProblemInfo> problems = new ArrayList<>();
        GroupReference groupRef = getGroupReference(reader, noteDataBlobId);
        if (!Objects.equals(uuid, groupRef.getUUID())) {
          problems.add(
              warning(
                  "group with name '%s' has UUID '%s' in 'group.config' while '%s' in group name notes",
                  name, uuid, groupRef.getUUID()));
        }

        if (!Objects.equals(name, groupRef.getName())) {
          problems.add(
              warning(
                  "group with UUID '%s' has name '%s' in 'group.config' while '%s' in group name notes",
                  uuid, name, groupRef.getName()));
        }

        return problems;
      }
    } catch (IOException | ConfigInvalidException e) {
      return Arrays.asList(warning("fail to check consistency with group name notes"));
    }
  }

  public static void logConsistencyProblem(ConsistencyProblemInfo p) {
    if (p.status == ConsistencyProblemInfo.Status.WARNING) {
      log.warn(GROUP_CONSISTENCY_CHECK_LOG_PREFIX + p.message);
    } else {
      log.error(GROUP_CONSISTENCY_CHECK_LOG_PREFIX + p.message);
    }
  }

  public static void logConsistencyProblemAsWarning(String fmt, Object... args) {
    logConsistencyProblem(warning(fmt, args));
  }

  /** Check whether there are duplicate group UUIDs. */
  public static List<ConsistencyProblemInfo> checkDuplicateUUIDs(
      ImmutableListMultimap<AccountGroup.UUID, String> byUUID) {
    return byUUID
        .keySet()
        .stream()
        .filter(t -> byUUID.get(t).size() > 1)
        .map(t -> toProblemInfo(t, byUUID.get(t)))
        .collect(Collectors.toList());
  }

  private static ConsistencyProblemInfo toProblemInfo(
      AccountGroup.UUID uuid, ImmutableList<String> names) {
    StringJoiner stringJoiner =
        new StringJoiner(", ", String.format("shared group UUID '%s' between groups: ", uuid), "");
    stringJoiner.setEmptyValue("");
    names.stream().forEachOrdered(stringJoiner::add);
    return warning(stringJoiner.toString());
  }
}
