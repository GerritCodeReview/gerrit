// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.change.DeleteDraft.Input;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.util.Collections;

@Singleton
class DeleteDraft implements RestModifyView<DraftResource, Input> {
  static class Input {
  }

  private final Provider<ReviewDb> db;
  private final PatchLineCommentsUtil plcUtil;
  private final ChangeUpdate.Factory updateFactory;
  private final PatchListCache patchListCache;

  @Inject
  DeleteDraft(Provider<ReviewDb> db,
      PatchLineCommentsUtil plcUtil,
      ChangeUpdate.Factory updateFactory,
      PatchListCache patchListCache) {
    this.db = db;
    this.plcUtil = plcUtil;
    this.updateFactory = updateFactory;
    this.patchListCache = patchListCache;
  }

  @Override
  public Response<CommentInfo> apply(DraftResource rsrc, Input input)
      throws OrmException, IOException {
    ChangeUpdate update = updateFactory.create(rsrc.getControl());

    PatchList patchList = null;
    try {
      patchList = patchListCache.get(rsrc.getChange(), rsrc.getPatchSet());
    } catch (PatchListNotAvailableException e) {
      throw new OrmException("could not load PatchList for this patchset", e);
    }
    RevId patchSetCommit = new RevId(ObjectId.toString(patchList.getNewId()));
    RevId baseCommit = new RevId(ObjectId.toString(patchList.getOldId()));
    PatchLineComment c = rsrc.getComment();
    if (c.getRevId() == null) {
      if (c.getRevId() == null) {
        c.setRevId((c.getSide() == (short) 0) ? baseCommit : patchSetCommit);
      }
    }
    plcUtil.deleteComments(db.get(), update, Collections.singleton(c));
    update.commit();
    return Response.none();
  }
}
