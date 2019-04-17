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

import static com.google.common.collect.ImmutableBiMap.toImmutableBiMap;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import java.io.IOException;
import java.util.Collection;
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

/**
 * An enforcer of unique names for groups in NoteDb.
 *
 * <p>The way groups are stored in NoteDb (see {@link GroupConfig}) doesn't enforce unique names,
 * even though groups in Gerrit must not have duplicate names. The storage format doesn't allow to
 * quickly look up whether a name has already been used either. That's why we additionally keep a
 * map of name/UUID pairs and manage it with this class.
 *
 * <p>To claim the name for a new group, create an instance of {@code GroupNameNotes} via {@link
 * #forNewGroup(Project.NameKey, Repository, AccountGroup.UUID, AccountGroup.NameKey)} and call
 * {@link #commit(com.google.gerrit.server.git.meta.MetaDataUpdate) commit(MetaDataUpdate)} on it.
 * For renaming, call {@link #forRename(Project.NameKey, Repository, AccountGroup.UUID,
 * AccountGroup.NameKey, AccountGroup.NameKey)} and also commit the returned {@code GroupNameNotes}.
 * Both times, the creation of the {@code GroupNameNotes} will fail if the (new) name is already
 * used. Committing the {@code GroupNameNotes} is necessary to make the adjustments for real.
 *
 * <p>The map has an additional benefit: We can quickly iterate over all group name/UUID pairs
 * without having to load all groups completely (which is costly).
 *
 * <p><em>Internal details</em>
 *
 * <p>The map of names is represented by Git {@link Note notes}. They are stored on the branch
 * {@link RefNames#REFS_GROUPNAMES}. Each commit on the branch reflects one moment in time of the
 * complete map.
 *
 * <p>As key for the notes, we use the SHA-1 of the name. As data, they contain a text version of a
 * JGit {@link Config} file. That config file has two entries:
 *
 * <ul>
 *   <li>the name of the group (as clear text)
 *   <li>the UUID of the group which currently has this name
 * </ul>
 */
public class GroupNameNotes extends VersionedMetaData {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SECTION_NAME = "group";
  private static final String UUID_PARAM = "uuid";
  private static final String NAME_PARAM = "name";

  @VisibleForTesting
  static final String UNIQUE_REF_ERROR = "GroupReference collection must contain unique references";

  /**
   * Creates an instance of {@code GroupNameNotes} for use when renaming a group.
   *
   * <p><strong>Note: </strong>The returned instance of {@code GroupNameNotes} has to be committed
   * via {@link #commit(com.google.gerrit.server.git.meta.MetaDataUpdate) commit(MetaDataUpdate)} in
   * order to claim the new name and free up the old one.
   *
   * @param projectName the name of the project which holds the commits of the notes
   * @param repository the repository which holds the commits of the notes
   * @param groupUuid the UUID of the group which is renamed
   * @param oldName the current name of the group
   * @param newName the new name of the group
   * @return an instance of {@code GroupNameNotes} configured for a specific renaming of a group
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException if the note for the specified group doesn't exist or is in an
   *     invalid state
   * @throws OrmDuplicateKeyException if a group with the new name already exists
   */
  public static GroupNameNotes forRename(
      Project.NameKey projectName,
      Repository repository,
      AccountGroup.UUID groupUuid,
      AccountGroup.NameKey oldName,
      AccountGroup.NameKey newName)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    requireNonNull(oldName);
    requireNonNull(newName);

    GroupNameNotes groupNameNotes = new GroupNameNotes(groupUuid, oldName, newName);
    groupNameNotes.load(projectName, repository);
    groupNameNotes.ensureNewNameIsNotUsed();
    return groupNameNotes;
  }

  /**
   * Creates an instance of {@code GroupNameNotes} for use when creating a new group.
   *
   * <p><strong>Note: </strong>The returned instance of {@code GroupNameNotes} has to be committed
   * via {@link #commit(com.google.gerrit.server.git.meta.MetaDataUpdate) commit(MetaDataUpdate)} in
   * order to claim the new name.
   *
   * @param projectName the name of the project which holds the commits of the notes
   * @param repository the repository which holds the commits of the notes
   * @param groupUuid the UUID of the new group
   * @param groupName the name of the new group
   * @return an instance of {@code GroupNameNotes} configured for a specific group creation
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException in no case so far
   * @throws OrmDuplicateKeyException if a group with the new name already exists
   */
  public static GroupNameNotes forNewGroup(
      Project.NameKey projectName,
      Repository repository,
      AccountGroup.UUID groupUuid,
      AccountGroup.NameKey groupName)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    requireNonNull(groupName);

    GroupNameNotes groupNameNotes = new GroupNameNotes(groupUuid, null, groupName);
    groupNameNotes.load(projectName, repository);
    groupNameNotes.ensureNewNameIsNotUsed();
    return groupNameNotes;
  }

  /**
   * Loads the {@code GroupReference} (name/UUID pair) for the group with the specified name.
   *
   * @param repository the repository which holds the commits of the notes
   * @param groupName the name of the group
   * @return the corresponding {@code GroupReference} if a group/note with the given name exists
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException if the note for the specified group is in an invalid state
   */
  public static Optional<GroupReference> loadGroup(
      Repository repository, AccountGroup.NameKey groupName)
      throws IOException, ConfigInvalidException {
    Ref ref = repository.exactRef(RefNames.REFS_GROUPNAMES);
    if (ref == null) {
      return Optional.empty();
    }

    try (RevWalk revWalk = new RevWalk(repository);
        ObjectReader reader = revWalk.getObjectReader()) {
      RevCommit notesCommit = revWalk.parseCommit(ref.getObjectId());
      NoteMap noteMap = NoteMap.read(reader, notesCommit);
      ObjectId noteDataBlobId = noteMap.get(getNoteKey(groupName));
      if (noteDataBlobId == null) {
        return Optional.empty();
      }
      return Optional.of(getGroupReference(reader, noteDataBlobId));
    }
  }

  /**
   * Loads the {@code GroupReference}s (name/UUID pairs) for all groups.
   *
   * <p>Even though group UUIDs should be unique, this class doesn't enforce it. For this reason,
   * it's technically possible that two of the {@code GroupReference}s have a duplicate UUID but a
   * different name. In practice, this shouldn't occur unless we introduce a bug in the future.
   *
   * @param repository the repository which holds the commits of the notes
   * @return the {@code GroupReference}s of all existing groups/notes
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException if one of the notes is in an invalid state
   */
  public static ImmutableList<GroupReference> loadAllGroups(Repository repository)
      throws IOException, ConfigInvalidException {
    Ref ref = repository.exactRef(RefNames.REFS_GROUPNAMES);
    if (ref == null) {
      return ImmutableList.of();
    }
    try (RevWalk revWalk = new RevWalk(repository);
        ObjectReader reader = revWalk.getObjectReader()) {
      RevCommit notesCommit = revWalk.parseCommit(ref.getObjectId());
      NoteMap noteMap = NoteMap.read(reader, notesCommit);

      Multiset<GroupReference> groupReferences = HashMultiset.create();
      for (Note note : noteMap) {
        GroupReference groupReference = getGroupReference(reader, note.getData());
        int numOfOccurrences = groupReferences.add(groupReference, 1);
        if (numOfOccurrences > 1) {
          GroupsNoteDbConsistencyChecker.logConsistencyProblemAsWarning(
              "The UUID of group %s (%s) is duplicate in group name notes",
              groupReference.getName(), groupReference.getUUID());
        }
      }

      return ImmutableList.copyOf(groupReferences);
    }
  }

  /**
   * Replaces the map of name/UUID pairs with a new version which matches exactly the passed {@code
   * GroupReference}s.
   *
   * <p>All old entries are discarded and replaced by the new ones.
   *
   * <p>This operation also works if the previous map has invalid entries or can't be read anymore.
   *
   * <p><strong>Note: </strong>This method doesn't flush the {@code ObjectInserter}. It doesn't
   * execute the {@code BatchRefUpdate} either.
   *
   * @param repository the repository which holds the commits of the notes
   * @param inserter an {@code ObjectInserter} for that repository
   * @param bru a {@code BatchRefUpdate} to which this method adds commands
   * @param groupReferences all {@code GroupReference}s (name/UUID pairs) which should be contained
   *     in the map of name/UUID pairs
   * @param ident the {@code PersonIdent} which is used as author and committer for commits
   * @throws IOException if the repository can't be accessed for some reason
   */
  public static void updateAllGroups(
      Repository repository,
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
      Ref ref = repository.exactRef(RefNames.REFS_GROUPNAMES);
      RevCommit oldCommit = ref != null ? rw.parseCommit(ref.getObjectId()) : null;

      for (Map.Entry<AccountGroup.UUID, String> e : biMap.entrySet()) {
        AccountGroup.NameKey nameKey = AccountGroup.nameKey(e.getValue());
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
      return groupReferences.stream()
          .collect(toImmutableBiMap(GroupReference::getUUID, GroupReference::getName));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(UNIQUE_REF_ERROR, e);
    }
  }

  private final AccountGroup.UUID groupUuid;
  private Optional<AccountGroup.NameKey> oldGroupName;
  private Optional<AccountGroup.NameKey> newGroupName;

  private boolean nameConflicting;

  private GroupNameNotes(
      AccountGroup.UUID groupUuid,
      @Nullable AccountGroup.NameKey oldGroupName,
      @Nullable AccountGroup.NameKey newGroupName) {
    this.groupUuid = requireNonNull(groupUuid);

    if (Objects.equals(oldGroupName, newGroupName)) {
      this.oldGroupName = Optional.empty();
      this.newGroupName = Optional.empty();
    } else {
      this.oldGroupName = Optional.ofNullable(oldGroupName);
      this.newGroupName = Optional.ofNullable(newGroupName);
    }
  }

  @Override
  protected String getRefName() {
    return RefNames.REFS_GROUPNAMES;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    nameConflicting = false;

    logger.atFine().log("Reading group notes");

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

    logger.atFine().log("Updating group notes");

    NoteMap noteMap = revision == null ? NoteMap.newEmptyMap() : NoteMap.read(reader, revision);
    if (oldGroupName.isPresent()) {
      removeNote(noteMap, oldGroupName.get(), inserter);
    }

    if (newGroupName.isPresent()) {
      addNote(noteMap, newGroupName.get(), groupUuid, inserter);
    }

    commit.setTreeId(noteMap.writeTree(inserter));
    commit.setMessage(getCommitMessage());

    oldGroupName = Optional.empty();
    newGroupName = Optional.empty();

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

  private static GroupReference getGroupReference(ObjectReader reader, ObjectId noteDataBlobId)
      throws IOException, ConfigInvalidException {
    byte[] noteData = reader.open(noteDataBlobId, OBJ_BLOB).getCachedBytes();
    return getFromNoteData(noteData);
  }

  static GroupReference getFromNoteData(byte[] noteData) throws ConfigInvalidException {
    Config config = new Config();
    config.fromText(new String(noteData, UTF_8));

    String uuid = config.getString(SECTION_NAME, null, UUID_PARAM);
    String name = Strings.nullToEmpty(config.getString(SECTION_NAME, null, NAME_PARAM));
    if (uuid == null) {
      throw new ConfigInvalidException(String.format("UUID for group '%s' must be defined", name));
    }

    return new GroupReference(AccountGroup.uuid(uuid), name);
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
