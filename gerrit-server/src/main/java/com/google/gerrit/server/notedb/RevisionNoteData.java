// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;

import java.sql.Timestamp;
import java.util.List;

/**
 * Holds the raw data of a RevisionNote.
 * <p>It is intended for (de)serialization to JSON only.
 */
class RevisionNoteData {
  static class Identity {
    int id;

    Identity(Account.Id id) {
      this.id = id.get();
    }

    Account.Id export() {
      return new Account.Id(id);
    }
  }

  static class CommentKey {
    String uuid;
    String filename;
    int patchSetId;
    int changeId;

    CommentKey(PatchLineComment.Key k) {
      uuid = k.get();
      filename = k.getParentKey().getFileName();
      patchSetId = k.getParentKey().getParentKey().get();
      changeId = k.getParentKey().getParentKey().getParentKey().get();
    }

    PatchLineComment.Key export() {
      return new PatchLineComment.Key(
          new Patch.Key(
              new PatchSet.Id(new Change.Id(changeId), patchSetId),
              filename),
          uuid);
    }
  }

  static class CommentRange {
    int startLine;
    int startChar;
    int endLine;
    int endChar;

    CommentRange(com.google.gerrit.reviewdb.client.CommentRange cr) {
      startLine = cr.getStartLine();
      startChar = cr.getStartCharacter();
      endLine = cr.getEndLine();
      endChar = cr.getEndCharacter();
    }

    com.google.gerrit.reviewdb.client.CommentRange export() {
      return new com.google.gerrit.reviewdb.client.CommentRange(
          startLine, startChar, endLine, endChar);
    }
  }

  static class Comment {
    CommentKey key;
    int lineNbr;
    Identity author;
    Timestamp writtenOn;
    char status;
    short side;
    String message;
    String parentUuid;
    CommentRange range;
    String tag;
    String revId;
    String serverId;

    public Comment(PatchLineComment plc, String serverId) {
      key = new CommentKey(plc.getKey());
      lineNbr = plc.getLine();
      author = new Identity(plc.getAuthor());
      writtenOn = plc.getWrittenOn();
      status = plc.getStatus().getCode();
      side = plc.getSide();
      message = plc.getMessage();
      parentUuid = plc.getParentUuid();
      range = plc.getRange() != null ? new CommentRange(plc.getRange()) : null;
      tag = plc.getTag();
      revId = plc.getRevId().get();
      this.serverId = serverId;
    }

    PatchLineComment export() {
      PatchLineComment plc = new PatchLineComment(
          key.export(), lineNbr, author.export(), parentUuid, writtenOn);
      plc.setSide(side);
      plc.setStatus(PatchLineComment.Status.forCode(status));
      plc.setMessage(message);
      if (range != null) {
        plc.setRange(range.export());
      }
      plc.setTag(tag);
      plc.setRevId(new RevId(revId));
      return plc;
    }
  }


  String pushCert;
  List<Comment> comments;

  ImmutableList<PatchLineComment> exportComments(
      final PatchLineComment.Status status) {
    return ImmutableList.copyOf(
        Lists.transform(comments, new Function<Comment, PatchLineComment>() {
          @Override
          public PatchLineComment apply(Comment c) {
            PatchLineComment plc = c.export();
            plc.setStatus(status);
            return plc;
          }
        }));
  }
}
