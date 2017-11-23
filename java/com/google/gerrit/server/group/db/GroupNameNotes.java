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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableBiMap.toImmutableBiMap;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds code for reading and writing group names for a single group as NoteDb data. The data is
 * stored in a refs/meta/group-names branch. The data is stored as SHA1(name) => config file with
 * the config file holding UUID and Name.
 *
 * <p>TODO(aliceks): more javadoc.
 */
public class GroupNameNotes extends VersionedMetaData {
  private static final Logger log = LoggerFactory.getLogger(GroupNameNotes.class);

  private static final String SECTION_NAME = "group";
  private static final String UUID_PARAM = "uuid";
  private static final String NAME_PARAM = "name";

  @VisibleForTesting
  static final String UNIQUE_REF_ERROR = "GroupReference collection must contain unique references";

  public static void updateGroupNames(
      Repository allUsersRepo,
      ObjectInserter inserter,
      BatchRefUpdate bru,
      Collection<GroupReference> groupReferences,
      PersonIdent ident)
      throws IOException {
    // Not strictly necessary for iteration; throws IAE if it encounters duplicates, which is nice.
    ImmutableBiMap<AccountGroup.UUID, String> biMap = toBiMap(groupReferences);

    try (ObjectReader reader = inserter.newReader();
        RevWalk rw = new RevWalk(reader)) {
      // Always start from an empty map, discarding old notes.
      NoteMap noteMap = NoteMap.newEmptyMap();
      Ref ref = allUsersRepo.exactRef(RefNames.REFS_GROUPNAMES);
      RevCommit oldCommit = ref != null ? rw.parseCommit(ref.getObjectId()) : null;

      for (Map.Entry<AccountGroup.UUID, String> e : biMap.entrySet()) {
        AccountGroup.NameKey nameKey = new AccountGroup.NameKey(e.getValue());
        ObjectId noteKey = getNoteKey(nameKey);
        noteMap.set(noteKey, getAsNoteData(e.getKey(), nameKey), inserter);
      }

      ObjectId newTreeId = noteMap.writeTree(inserter);
      if (oldCommit != null && newTreeId.equals(oldCommit.getTree())) {
        return;
      }
      CommitBuilder cb = new CommitBuilder();
      if (oldCommit != null) {
        cb.addParentId(oldCommit);
      }
      cb.setTreeId(newTreeId);
      cb.setAuthor(ident);
      cb.setCommitter(ident);
      int n = groupReferences.size();
      cb.setMessage("Store " + n + " group name" + (n != 1 ? "s" : ""));
      ObjectId newId = inserter.insert(cb).copy();

      ObjectId oldId = oldCommit != null ? oldCommit.copy() : ObjectId.zeroId();
      bru.addCommand(new ReceiveCommand(oldId, newId, RefNames.REFS_GROUPNAMES));
    }
  }

  // Returns UUID <=> Name bimap.
  private static ImmutableBiMap<AccountGroup.UUID, String> toBiMap(
      Collection<GroupReference> groupReferences) {
    try {
      return groupReferences
          .stream()
          .collect(toImmutableBiMap(gr -> gr.getUUID(), gr -> gr.getName()));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(UNIQUE_REF_ERROR, e);
    }
  }

  private final AccountGroup.UUID groupUuid;
  private final Optional<AccountGroup.NameKey> oldGroupName;
  private final Optional<AccountGroup.NameKey> newGroupName;

  private boolean nameConflicting;

  private GroupNameNotes(
      AccountGroup.UUID groupUuid,
      @Nullable AccountGroup.NameKey oldGroupName,
      @Nullable AccountGroup.NameKey newGroupName) {
    this.groupUuid = checkNotNull(groupUuid);

    if (Objects.equals(oldGroupName, newGroupName)) {
      this.oldGroupName = Optional.empty();
      this.newGroupName = Optional.empty();
    } else {
      this.oldGroupName = Optional.ofNullable(oldGroupName);
      this.newGroupName = Optional.ofNullable(newGroupName);
    }
  }

  public static GroupNameNotes loadForRename(
      Repository repository,
      AccountGroup.UUID groupUuid,
      AccountGroup.NameKey oldName,
      AccountGroup.NameKey newName)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    checkNotNull(oldName);
    checkNotNull(newName);

    GroupNameNotes groupNameNotes = new GroupNameNotes(groupUuid, oldName, newName);
    groupNameNotes.load(repository);
    groupNameNotes.ensureNewNameIsNotUsed();
    return groupNameNotes;
  }

  public static GroupNameNotes loadForNewGroup(
      Repository repository, AccountGroup.UUID groupUuid, AccountGroup.NameKey groupName)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    checkNotNull(groupName);

    GroupNameNotes groupNameNotes = new GroupNameNotes(groupUuid, null, groupName);
    groupNameNotes.load(repository);
    groupNameNotes.ensureNewNameIsNotUsed();
    return groupNameNotes;
  }

  public static ImmutableSet<GroupReference> loadAllGroupReferences(Repository repository)
      throws IOException, ConfigInvalidException {
    Ref ref = repository.exactRef(RefNames.REFS_GROUPNAMES);
    if (ref == null) {
      return ImmutableSet.of();
    }
    try (RevWalk revWalk = new RevWalk(repository);
        ObjectReader reader = revWalk.getObjectReader()) {
      RevCommit notesCommit = revWalk.parseCommit(ref.getObjectId());
      NoteMap noteMap = NoteMap.read(reader, notesCommit);
      ImmutableSet.Builder<GroupReference> groupReferences = ImmutableSet.builder();
      Map<AccountGroup.UUID, String> uuidMap = new HashMap<>();
      Map<String, AccountGroup.UUID> nameMap = new HashMap<>();
      for (Note note : noteMap) {
        GroupReference groupReference = getGroupReference(reader, note.getData());
        groupReferences.add(groupReference);
        checkConsistency(groupReference, uuidMap, nameMap);
      }
      return groupReferences.build();
    }
  }

  @Override
  protected String getRefName() {
    return RefNames.REFS_GROUPNAMES;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    nameConflicting = false;

    if (revision != null) {
      NoteMap noteMap = NoteMap.read(reader, revision);
      if (newGroupName.isPresent()) {
        ObjectId newNameId = getNoteKey(newGroupName.get());
        nameConflicting = noteMap.contains(newNameId);
      }
      ensureOldNameIsPresent(noteMap);
    }
  }

  private void ensureOldNameIsPresent(NoteMap noteMap) throws IOException, ConfigInvalidException {
    if (oldGroupName.isPresent()) {
      AccountGroup.NameKey oldName = oldGroupName.get();
      ObjectId noteKey = getNoteKey(oldName);
      ObjectId noteDataBlobId = noteMap.get(noteKey);
      if (noteDataBlobId == null) {
        throw new ConfigInvalidException(
            String.format("Group name '%s' doesn't exist in the list of all names", oldName));
      }
      GroupReference group = getGroupReference(reader, noteDataBlobId);
      AccountGroup.UUID foundUuid = group.getUUID();
      if (!Objects.equals(groupUuid, foundUuid)) {
        throw new ConfigInvalidException(
            String.format(
                "Name '%s' points to UUID '%s' and not to '%s'", oldName, foundUuid, groupUuid));
      }
    }
  }

  private void ensureNewNameIsNotUsed() throws OrmDuplicateKeyException {
    if (newGroupName.isPresent() && nameConflicting) {
      throw new OrmDuplicateKeyException(
          String.format("Name '%s' is already used", newGroupName.get().get()));
    }
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    if (!oldGroupName.isPresent() && !newGroupName.isPresent()) {
      return false;
    }

    NoteMap noteMap = revision == null ? NoteMap.newEmptyMap() : NoteMap.read(reader, revision);
    if (oldGroupName.isPresent()) {
      removeNote(noteMap, oldGroupName.get(), inserter);
    }

    if (newGroupName.isPresent()) {
      addNote(noteMap, newGroupName.get(), groupUuid, inserter);
    }

    commit.setTreeId(noteMap.writeTree(inserter));
    commit.setMessage(getCommitMessage());

    return true;
  }

  private static void removeNote(
      NoteMap noteMap, AccountGroup.NameKey groupName, ObjectInserter inserter) throws IOException {
    ObjectId noteKey = getNoteKey(groupName);
    noteMap.set(noteKey, null, inserter);
  }

  private static void addNote(
      NoteMap noteMap,
      AccountGroup.NameKey groupName,
      AccountGroup.UUID groupUuid,
      ObjectInserter inserter)
      throws IOException {
    ObjectId noteKey = getNoteKey(groupName);
    noteMap.set(noteKey, getAsNoteData(groupUuid, groupName), inserter);
  }

  // Use the same approach as ExternalId.Key.sha1().
  @SuppressWarnings("deprecation")
  @VisibleForTesting
  public static ObjectId getNoteKey(AccountGroup.NameKey groupName) {
    return ObjectId.fromRaw(Hashing.sha1().hashString(groupName.get(), UTF_8).asBytes());
  }

  private static String getAsNoteData(AccountGroup.UUID uuid, AccountGroup.NameKey groupName) {
    Config config = new Config();
    config.setString(SECTION_NAME, null, UUID_PARAM, uuid.get());
    config.setString(SECTION_NAME, null, NAME_PARAM, groupName.get());
    return config.toText();
  }

  @VisibleForTesting
  public static GroupReference getGroupReference(ObjectReader reader, ObjectId noteDataBlobId)
      throws IOException, ConfigInvalidException {
    byte[] noteData = reader.open(noteDataBlobId, OBJ_BLOB).getCachedBytes();
    return getFromNoteData(noteData);
  }

  static GroupReference getFromNoteData(byte[] noteData) throws ConfigInvalidException {
    Config config = new Config();
    config.fromText(new String(noteData, UTF_8));

    String uuid = config.getString(SECTION_NAME, null, UUID_PARAM);
    String name = config.getString(SECTION_NAME, null, NAME_PARAM);
    if (uuid == null || name == null) {
      throw new ConfigInvalidException(
          String.format("UUID '%s' and name '%s' must be defined", uuid, name));
    }

    return new GroupReference(new AccountGroup.UUID(uuid), name);
  }

  /**
   * Check whether there are duplicate group UUIDs or names. Use two {@code Map} rather than one
   * {@code BiMap} so that more possible inconsistencies could be found.
   */
  private static void checkConsistency(
      GroupReference groupReference,
      Map<AccountGroup.UUID, String> uuidMap,
      Map<String, AccountGroup.UUID> nameMap) {
    AccountGroup.UUID uuid = groupReference.getUUID();
    String name = groupReference.getName();

    if (uuidMap.containsKey(uuid)) {
      log.warn(
          "shared group UUID between group %s (%s) and %s (%s)",
          uuidMap.get(uuid), uuid, name, uuid);
    } else {
      uuidMap.put(uuid, name);
    }

    if (nameMap.containsKey(name)) {
      log.warn(
          "shared group name between group %s (%s) and %s (%s)",
          name, nameMap.get(name), name, uuid);
    } else {
      nameMap.put(name, uuid);
    }
  }

  private String getCommitMessage() {
    if (oldGroupName.isPresent() && newGroupName.isPresent()) {
      return String.format(
          "Rename group from '%s' to '%s'", oldGroupName.get(), newGroupName.get());
    }
    if (newGroupName.isPresent()) {
      return String.format("Create group '%s'", newGroupName.get());
    }
    if (oldGroupName.isPresent()) {
      return String.format("Delete group '%s'", oldGroupName.get());
    }
    return "No-op";
  }
}
