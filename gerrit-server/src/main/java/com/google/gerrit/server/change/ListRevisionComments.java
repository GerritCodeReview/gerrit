// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class ListRevisionComments extends ListRevisionDrafts {
  @Inject
  ListRevisionComments(Provider<ReviewDb> db,
      Provider<CommentJson> commentJson,
      PatchLineCommentsUtil plcUtil) {
    super(db, commentJson, plcUtil);
  }

  @Override
  protected boolean includeAuthorInfo() {
    return true;
  }

  @Override
  protected Iterable<PatchLineComment> listComments(RevisionResource rsrc)
      throws OrmException {
    ChangeNotes notes = rsrc.getNotes();
    return plcUtil.publishedByPatchSet(db.get(), notes, rsrc.getPatchSet().getId());
  }
}
