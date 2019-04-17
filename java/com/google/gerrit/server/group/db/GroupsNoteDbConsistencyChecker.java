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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.group.InternalGroup;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Check the referential integrity of NoteDb group storage. */
@Singleton
public class GroupsNoteDbConsistencyChecker {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AllUsersName allUsersName;

  @Inject
  GroupsNoteDbConsistencyChecker(AllUsersName allUsersName) {
    this.allUsersName = allUsersName;
  }

  /**
   * The result of a consistency check. The UUID map is only non-null if no problems were detected.
   */
  public static class Result {
    public List<ConsistencyProblemInfo> problems;

    @Nullable public Map<AccountGroup.UUID, InternalGroup> uuidToGroupMap;
  }

  /** Checks for problems with the given All-Users repo. */
  public Result check(Repository allUsersRepo) throws IOException {
    Result r = doCheck(allUsersRepo);
    if (!r.problems.isEmpty()) {
      r.uuidToGroupMap = null;
    }
    return r;
  }

  private Result doCheck(Repository allUsersRepo) throws IOException {
    Result result = new Result();
    result.problems = new ArrayList<>();
    result.uuidToGroupMap = new HashMap<>();

    BiMap<AccountGroup.UUID, String> uuidNameBiMap = HashBiMap.create();

    // Get group refs and group names ref using the most atomic API available, in an attempt to
    // avoid seeing half-committed group updates.
    List<Ref> refs =
        allUsersRepo
            .getRefDatabase()
            .getRefsByPrefix(RefNames.REFS_GROUPS, RefNames.REFS_GROUPNAMES);
    readGroups(allUsersRepo, refs, result);
    readGroupNames(allUsersRepo, refs, result, uuidNameBiMap);
    // The sequential IDs are not keys in NoteDb, so no need to check them.

    if (!result.problems.isEmpty()) {
      return result;
    }

    // Continue checking if we could read data without problems.
    result.problems.addAll(checkGlobalConsistency(result.uuidToGroupMap, uuidNameBiMap));

    return result;
  }

  private void readGroups(Repository allUsersRepo, List<Ref> refs, Result result)
      throws IOException {
    for (Ref ref : refs) {
      if (!ref.getName().startsWith(RefNames.REFS_GROUPS)) {
        continue;
      }

      AccountGroup.UUID uuid = AccountGroup.UUID.fromRef(ref.getName());
      if (uuid == null) {
        result.problems.add(error("null UUID from %s", ref.getName()));
        continue;
      }
      try {
        GroupConfig cfg =
            GroupConfig.loadForGroupSnapshot(allUsersName, allUsersRepo, uuid, ref.getObjectId());
        result.uuidToGroupMap.put(uuid, cfg.getLoadedGroup().get());
      } catch (ConfigInvalidException e) {
        result.problems.add(error("group %s does not parse: %s", uuid, e.getMessage()));
      }
    }
  }

  private void readGroupNames(
      Repository repo,
      List<Ref> refs,
      Result result,
      BiMap<AccountGroup.UUID, String> uuidNameBiMap)
      throws IOException {
    Optional<Ref> maybeRef =
        refs.stream().filter(r -> r.getName().equals(RefNames.REFS_GROUPNAMES)).findFirst();
    if (!maybeRef.isPresent()) {
      String msg = String.format("ref %s does not exist", RefNames.REFS_GROUPNAMES);
      result.problems.add(error(msg));
      return;
    }
    Ref ref = maybeRef.get();

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

        ObjectId nameKey = GroupNameNotes.getNoteKey(AccountGroup.nameKey(gRef.getName()));
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

  public static void ensureConsistentWithGroupNameNotes(
      Repository allUsersRepo, InternalGroup group) throws IOException {
    List<ConsistencyCheckInfo.ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkWithGroupNameNotes(
            allUsersRepo, group.getNameKey(), group.getGroupUUID());
    problems.forEach(GroupsNoteDbConsistencyChecker::logConsistencyProblem);
  }

  /**
   * Check group 'uuid' and 'name' read from 'group.config' with group name notes.
   *
   * @param allUsersRepo 'All-Users' repository.
   * @param groupName the name of the group to be checked.
   * @param groupUUID the {@code AccountGroup.UUID} of the group to be checked.
   * @return a list of {@code ConsistencyProblemInfo} containing the problem details.
   */
  @VisibleForTesting
  static List<ConsistencyProblemInfo> checkWithGroupNameNotes(
      Repository allUsersRepo, AccountGroup.NameKey groupName, AccountGroup.UUID groupUUID)
      throws IOException {
    try {
      Optional<GroupReference> groupRef = GroupNameNotes.loadGroup(allUsersRepo, groupName);

      if (!groupRef.isPresent()) {
        return ImmutableList.of(
            warning("Group with name '%s' doesn't exist in the list of all names", groupName));
      }

      AccountGroup.UUID uuid = groupRef.get().getUUID();

      List<ConsistencyProblemInfo> problems = new ArrayList<>();
      if (!Objects.equals(groupUUID, uuid)) {
        problems.add(
            warning(
                "group with name '%s' has UUID '%s' in 'group.config' but '%s' in group name notes",
                groupName, groupUUID, uuid));
      }

      String name = groupName.get();
      String actualName = groupRef.get().getName();
      if (!Objects.equals(name, actualName)) {
        problems.add(
            warning("group note of name '%s' claims to represent name of '%s'", name, actualName));
      }
      return problems;
    } catch (ConfigInvalidException e) {
      return ImmutableList.of(
          warning("fail to check consistency with group name notes: %s", e.getMessage()));
    }
  }

  public static void logConsistencyProblemAsWarning(String fmt, Object... args) {
    logConsistencyProblem(warning(fmt, args));
  }

  public static void logConsistencyProblem(ConsistencyProblemInfo p) {
    if (p.status == ConsistencyProblemInfo.Status.WARNING) {
      logger.atWarning().log(p.message);
    } else {
      logger.atSevere().log(p.message);
    }
  }

  public static void logFailToLoadFromGroupRefAsWarning(AccountGroup.UUID uuid) {
    logConsistencyProblem(
        warning("Group with UUID %s from group name notes failed to load from group ref", uuid));
  }
}
