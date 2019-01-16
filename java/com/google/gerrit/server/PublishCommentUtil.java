// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.reviewdb.client.PatchLineComment.Status.PUBLISHED;
import static java.util.stream.Collectors.toSet;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Map;

@Singleton
public class PublishCommentUtil {
  private final PatchListCache patchListCache;
  private final PatchSetUtil psUtil;
  private final CommentsUtil commentsUtil;

  @Inject
  PublishCommentUtil(
      CommentsUtil commentsUtil, PatchListCache patchListCache, PatchSetUtil psUtil) {
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
  }

  public void publish(
      ChangeContext ctx, PatchSet.Id psId, Collection<Comment> drafts, @Nullable String tag)
      throws StorageException {
    ChangeNotes notes = ctx.getNotes();
    checkArgument(notes != null);
    if (drafts.isEmpty()) {
      return;
    }

    Map<PatchSet.Id, PatchSet> patchSets =
        psUtil.getAsMap(notes, drafts.stream().map(d -> psId(notes, d)).collect(toSet()));
    for (Comment d : drafts) {
      PatchSet ps = patchSets.get(psId(notes, d));
      if (ps == null) {
        throw new StorageException("patch set " + ps + " not found");
      }
      d.writtenOn = ctx.getWhen();
      d.tag = tag;
      // Draft may have been created by a different real user; copy the current real user. (Only
      // applies to X-Gerrit-RunAs, since modifying drafts via on_behalf_of is not allowed.)
      ctx.getUser().updateRealAccountId(d::setRealAuthor);
      try {
        CommentsUtil.setCommentRevId(d, patchListCache, notes.getChange(), ps);
      } catch (PatchListNotAvailableException e) {
        throw new StorageException(e);
      }
    }
    commentsUtil.putComments(ctx.getUpdate(psId), PUBLISHED, drafts);
  }

  private static PatchSet.Id psId(ChangeNotes notes, Comment c) {
    return new PatchSet.Id(notes.getChangeId(), c.key.patchSetId);
  }
}
