// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.notedb.ChangeNotes;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DraftCommentsReader {
  Optional<HumanComment> getDraftComment(ChangeNotes notes, IdentifiedUser user, Comment.Key key);

  List<HumanComment> getDraftComments(Change.Id changeId, Account.Id accountId);

  List<HumanComment> getDraftsByChange(ChangeNotes notes);

  Set<Account.Id> getUsersWithDrafts(ChangeNotes changeNotes) throws IOException;

  Set<Change.Id> getChangesWithDrafts(Account.Id accountId) throws IOException;

  List<HumanComment> getDraftsByPatchSetAuthor(
      PatchSet.Id psId, Account.Id author, ChangeNotes notes);

  List<HumanComment> getDraftsByChangeAuthor(ChangeNotes notes, Account.Id author);
}
