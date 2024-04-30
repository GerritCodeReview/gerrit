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

package com.google.gerrit.entities.converter;

import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.Entities.HumanComment.InFilePosition;
import com.google.gerrit.proto.Entities.HumanComment.InFilePosition.Side;
import com.google.protobuf.Parser;
import java.time.Instant;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Proto converter between {@link HumanComment} and {@link
 * com.google.gerrit.proto.Entities.HumanComment}.
 */
@Immutable
public enum HumanCommentProtoConverter
    implements SafeProtoConverter<Entities.HumanComment, HumanComment> {
  INSTANCE;

  private final ProtoConverter<Entities.Account_Id, Account.Id> accountIdConverter =
      AccountIdProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.ObjectId, ObjectId> objectIdConverter =
      ObjectIdProtoConverter.INSTANCE;

  @Override
  public Entities.HumanComment toProto(HumanComment val) {

    Entities.HumanComment.Builder res =
        Entities.HumanComment.newBuilder()
            .setPatchsetId(val.key.patchSetId)
            .setAccountId(accountIdConverter.toProto(val.author.getId()))
            .setCommentUuid(val.key.uuid)
            .setCommentText(val.message)
            .setUnresolved(val.unresolved)
            .setWrittenOnMillis(val.writtenOn.toInstant().toEpochMilli())
            .setServerId(val.serverId);
    if (!val.key.filename.equals(PATCHSET_LEVEL)) {
      InFilePosition.Builder inFilePos =
          InFilePosition.newBuilder()
              .setFilePath(val.key.filename)
              .setSide(val.side <= 0 ? Side.PARENT : Side.REVISION);
      if (val.range != null) {
        inFilePos.setPositionRange(
            InFilePosition.Range.newBuilder()
                .setStartLine(val.range.startLine)
                .setStartChar(val.range.startChar)
                .setEndLine(val.range.endLine)
                .setEndChar(val.range.endChar));
      }
      if (val.lineNbr != 0) {
        inFilePos.setLineNumber(val.lineNbr);
      }
      res.setInFilePosition(inFilePos);
    }

    if (val.parentUuid != null) {
      res.setParentCommentUuid(val.parentUuid);
    }
    if (val.tag != null) {
      res.setTag(val.tag);
    }
    if (val.realAuthor != null) {
      res.setRealAuthor(accountIdConverter.toProto(val.realAuthor.getId()));
    }
    if (val.getCommitId() != null) {
      res.setDestCommitId(objectIdConverter.toProto(val.getCommitId()));
    }

    return res.build();
  }

  @Override
  public HumanComment fromProto(Entities.HumanComment proto) {
    Optional<InFilePosition> optInFilePosition =
        proto.hasInFilePosition() ? Optional.of(proto.getInFilePosition()) : Optional.empty();
    Comment.Key key =
        new Comment.Key(
            proto.getCommentUuid(),
            optInFilePosition.isPresent() ? optInFilePosition.get().getFilePath() : PATCHSET_LEVEL,
            proto.getPatchsetId());
    HumanComment res =
        new HumanComment(
            key,
            accountIdConverter.fromProto(proto.getAccountId()),
            Instant.ofEpochMilli(proto.getWrittenOnMillis()),
            optInFilePosition.isPresent()
                ? (short) optInFilePosition.get().getSide().getNumber()
                : Side.REVISION_VALUE,
            proto.getCommentText(),
            proto.getServerId(),
            proto.getUnresolved());

    res.parentUuid = proto.hasParentCommentUuid() ? proto.getParentCommentUuid() : null;
    res.tag = proto.hasTag() ? proto.getTag() : null;
    if (proto.hasRealAuthor()) {
      res.realAuthor = new Comment.Identity(accountIdConverter.fromProto(proto.getRealAuthor()));
    }
    if (proto.hasDestCommitId()) {
      res.setCommitId(objectIdConverter.fromProto(proto.getDestCommitId()));
    }

    optInFilePosition.ifPresent(
        inFilePosition -> {
          if (inFilePosition.hasPositionRange()) {
            var range = inFilePosition.getPositionRange();
            res.range =
                new Range(
                    range.getStartLine(),
                    range.getStartChar(),
                    range.getEndLine(),
                    range.getEndChar());
          }
          if (inFilePosition.hasLineNumber()) {
            res.lineNbr = inFilePosition.getLineNumber();
          }
        });
    return res;
  }

  @Override
  public Parser<Entities.HumanComment> getParser() {
    return Entities.HumanComment.parser();
  }

  @Override
  public Class<Entities.HumanComment> getProtoClass() {
    return Entities.HumanComment.class;
  }

  @Override
  public Class<HumanComment> getEntityClass() {
    return HumanComment.class;
  }
}
