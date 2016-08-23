package com.google.gerrit.server.notedb;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gwtorm.client.Column;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the raw data of a RevisionNote.
 * It is intended for (de)serialization to JSON only.
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
    PatchKey patchKey;

    CommentKey(PatchLineComment.Key k) {
      uuid = k.get();
      patchKey = new PatchKey(k.getParentKey());
    }
    PatchLineComment.Key export() {
      return new PatchLineComment.Key(patchKey.export(), uuid);
    }
  }

  static class PatchSetId {
    int id;
    int changeId;

    PatchSetId(PatchSet.Id id) {
      this.id = id.get();
      changeId = id.getParentKey().get();
    }
    
    PatchSet.Id export() {
      return new PatchSet.Id(new Change.Id(changeId), id);
    }
  }
  
  static class PatchKey {
    String filename;
    PatchSetId patchSetId;

    PatchKey(Patch.Key k) {
      filename = k.getFileName();
      patchSetId = new PatchSetId(k.getParentKey());
    }
    Patch.Key export() { 
      return new Patch.Key(patchSetId.export(), filename);
    }

  }

  static class CommentRange {
    int startLine;
    int startCharacter;
    int endLine;
    int endCharacter;

    CommentRange(com.google.gerrit.reviewdb.client.CommentRange cr) {
      startLine = cr.getStartLine();
      startCharacter = cr.getStartCharacter();
      endLine = cr.getEndLine();
      endCharacter = cr.getEndCharacter();
    }

    com.google.gerrit.reviewdb.client.CommentRange export() {
      return new com.google.gerrit.reviewdb.client.CommentRange(
              startLine, startCharacter, endLine, endCharacter);
    }
  }

  static class Comment {
    CommentKey key;
    int lineNbr;
    Identity author;
    Timestamp writtenOn;
    // TODO(hanwen): use string instead?
    char status;
    short side;
    String message;
    String parentUuid;
    CommentRange range;
    String tag;
    String revId;

    public Comment(PatchLineComment plc) {
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
    }

    PatchLineComment export() {
      PatchLineComment plc = new PatchLineComment(
              key.export(), lineNbr,
              author.export(), parentUuid, writtenOn);
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
  String serverId;

  ImmutableList<PatchLineComment> exportComments() {
    ImmutableList.Builder builder = ImmutableList.<PatchLineComment>builder();
    for (Comment c : comments) {
      builder.add(c.export());
    }
    return builder.build();
  }
}
