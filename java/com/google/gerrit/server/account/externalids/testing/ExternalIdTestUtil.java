// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids.testing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdReader;
import java.io.IOException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Common methods for dealing with external IDs in tests. */
public class ExternalIdTestUtil {

  public static String insertExternalIdWithoutAccountId(
      Repository repo, RevWalk rw, PersonIdent ident, Account.Id accountId, String externalId)
      throws IOException {
    return insertExternalId(
        repo,
        rw,
        ident,
        (ins, noteMap) -> {
          ExternalId extId = ExternalId.create(ExternalId.Key.parse(externalId), accountId);
          ObjectId noteId = extId.key().sha1();
          Config c = new Config();
          extId.writeToConfig(c);
          c.unset("externalId", extId.key().get(), "accountId");
          byte[] raw = c.toText().getBytes(UTF_8);
          ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
          noteMap.set(noteId, dataBlob);
          return noteId;
        });
  }

  public static String insertExternalIdWithKeyThatDoesntMatchNoteId(
      Repository repo, RevWalk rw, PersonIdent ident, Account.Id accountId, String externalId)
      throws IOException {
    return insertExternalId(
        repo,
        rw,
        ident,
        (ins, noteMap) -> {
          ExternalId extId = ExternalId.create(ExternalId.Key.parse(externalId), accountId);
          ObjectId noteId = ExternalId.Key.parse(externalId + "x").sha1();
          Config c = new Config();
          extId.writeToConfig(c);
          byte[] raw = c.toText().getBytes(UTF_8);
          ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
          noteMap.set(noteId, dataBlob);
          return noteId;
        });
  }

  public static String insertExternalIdWithInvalidConfig(
      Repository repo, RevWalk rw, PersonIdent ident, String externalId) throws IOException {
    return insertExternalId(
        repo,
        rw,
        ident,
        (ins, noteMap) -> {
          ObjectId noteId = ExternalId.Key.parse(externalId).sha1();
          byte[] raw = "bad-config".getBytes(UTF_8);
          ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
          noteMap.set(noteId, dataBlob);
          return noteId;
        });
  }

  public static String insertExternalIdWithEmptyNote(
      Repository repo, RevWalk rw, PersonIdent ident, String externalId) throws IOException {
    return insertExternalId(
        repo,
        rw,
        ident,
        (ins, noteMap) -> {
          ObjectId noteId = ExternalId.Key.parse(externalId).sha1();
          byte[] raw = "".getBytes(UTF_8);
          ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
          noteMap.set(noteId, dataBlob);
          return noteId;
        });
  }

  private static String insertExternalId(
      Repository repo, RevWalk rw, PersonIdent ident, ExternalIdInserter extIdInserter)
      throws IOException {
    ObjectId rev = ExternalIdReader.readRevision(repo);
    NoteMap noteMap = ExternalIdReader.readNoteMap(rw, rev);

    try (ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId noteId = extIdInserter.addNote(ins, noteMap);

      CommitBuilder cb = new CommitBuilder();
      cb.setMessage("Update external IDs");
      cb.setTreeId(noteMap.writeTree(ins));
      cb.setAuthor(ident);
      cb.setCommitter(ident);
      if (!rev.equals(ObjectId.zeroId())) {
        cb.setParentId(rev);
      } else {
        cb.setParentIds(); // Ref is currently nonexistent, commit has no parents.
      }
      if (cb.getTreeId() == null) {
        if (rev.equals(ObjectId.zeroId())) {
          cb.setTreeId(ins.insert(OBJ_TREE, new byte[] {})); // No parent, assume empty tree.
        } else {
          RevCommit p = rw.parseCommit(rev);
          cb.setTreeId(p.getTree()); // Copy tree from parent.
        }
      }
      ObjectId commitId = ins.insert(cb);
      ins.flush();

      RefUpdate u = repo.updateRef(RefNames.REFS_EXTERNAL_IDS);
      u.setExpectedOldObjectId(rev);
      u.setNewObjectId(commitId);
      RefUpdate.Result res = u.update();
      switch (res) {
        case NEW:
        case FAST_FORWARD:
        case NO_CHANGE:
        case RENAMED:
        case FORCED:
          break;
        case LOCK_FAILURE:
        case IO_FAILURE:
        case NOT_ATTEMPTED:
        case REJECTED:
        case REJECTED_CURRENT_BRANCH:
        case REJECTED_MISSING_OBJECT:
        case REJECTED_OTHER_REASON:
        default:
          throw new IOException("Updating external IDs failed with " + res);
      }
      return noteId.getName();
    }
  }
}
