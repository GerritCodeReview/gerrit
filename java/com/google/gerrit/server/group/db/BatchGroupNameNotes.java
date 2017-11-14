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

import static com.google.gerrit.server.group.db.GroupNameNotes.getAsNoteData;
import static com.google.gerrit.server.group.db.GroupNameNotes.getGroupReference;
import static com.google.gerrit.server.group.db.GroupNameNotes.getNoteKey;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
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

/** Helper class for editing group name notes for multiple groups in a single batch. */
public class BatchGroupNameNotes {
  public static void updateGroupNames(
      Repository allUsersRepo,
      ObjectInserter inserter,
      BatchRefUpdate bru,
      Collection<AccountGroup> groups,
      PersonIdent ident)
      throws ConfigInvalidException, IOException {
    try (ObjectReader reader = inserter.newReader();
        RevWalk rw = new RevWalk(reader)) {
      RevCommit oldCommit;
      NoteMap noteMap;
      Ref ref = allUsersRepo.exactRef(RefNames.REFS_GROUPNAMES);
      if (ref != null) {
        oldCommit = rw.parseCommit(ref.getObjectId());
        noteMap = NoteMap.read(reader, oldCommit);
      } else {
        oldCommit = null;
        noteMap = NoteMap.newEmptyMap();
      }

      ListMultimap<AccountGroup.UUID, String> existing = readNamesByUuid(reader, noteMap);
      int updated = 0;

      for (AccountGroup group : groups) {
        AccountGroup.UUID uuid = group.getGroupUUID();
        ObjectId noteKey = getNoteKey(group.getNameKey());
        ObjectId noteDataBlobId = noteMap.get(noteKey);
        if (noteDataBlobId != null) {
          checkNoteMatches(reader, noteDataBlobId, group);
        } else {
          updated++;
          noteMap.set(noteKey, getAsNoteData(uuid, group.getNameKey()), inserter);
        }

        for (String name : existing.get(group.getGroupUUID())) {
          if (!name.equals(group.getName())) {
            noteMap.remove(getNoteKey(new AccountGroup.NameKey(name)));
          }
        }
      }

      ObjectId newTreeId = noteMap.writeTree(inserter);
      if (oldCommit != null && newTreeId.equals(oldCommit.getTree())) {
        return;
      }
      CommitBuilder cb = new CommitBuilder();
      if (ref != null) {
        cb.addParentId(oldCommit);
      }
      cb.setTreeId(noteMap.writeTree(inserter));
      cb.setAuthor(ident);
      cb.setCommitter(ident);
      cb.setMessage("Update " + updated + " group name" + (updated != 1 ? "s" : ""));
      ObjectId newId = inserter.insert(cb).copy();

      ObjectId oldId = oldCommit != null ? oldCommit.copy() : ObjectId.zeroId();
      bru.addCommand(new ReceiveCommand(oldId, newId, RefNames.REFS_GROUPNAMES));
    }
  }

  @VisibleForTesting
  static ListMultimap<AccountGroup.UUID, String> readNamesByUuid(Repository allUsersRepo)
      throws ConfigInvalidException, IOException {
    try (RevWalk rw = new RevWalk(allUsersRepo)) {
      Ref ref = allUsersRepo.exactRef(RefNames.REFS_GROUPNAMES);
      return readNamesByUuid(
          rw.getObjectReader(),
          NoteMap.read(rw.getObjectReader(), rw.parseCommit(ref.getObjectId())));
    }
  }

  private static ListMultimap<AccountGroup.UUID, String> readNamesByUuid(
      ObjectReader reader, NoteMap noteMap) throws ConfigInvalidException, IOException {
    ListMultimap<AccountGroup.UUID, String> result =
        MultimapBuilder.hashKeys().arrayListValues(1).build();
    for (Note note : noteMap) {
      GroupReference groupReference = getGroupReference(reader, note.getData());
      result.put(groupReference.getUUID(), groupReference.getName());
    }
    return result;
  }

  private static void checkNoteMatches(
      ObjectReader reader, ObjectId noteDataBlobId, AccountGroup group)
      throws ConfigInvalidException, IOException {
    AccountGroup.UUID foundUuid = getGroupReference(reader, noteDataBlobId).getUUID();
    if (!foundUuid.equals(group.getGroupUUID())) {
      throw new ConfigInvalidException(
          String.format(
              "Name '%s' points to UUID '%s' and not to '%s'",
              group.getName(), foundUuid, group.getGroupUUID()));
    }
  }

  private BatchGroupNameNotes() {}
}
