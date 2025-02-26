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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.FixSuggestion;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.Entities.HumanComment.InFilePosition;
import com.google.gerrit.proto.Entities.HumanComment.InFilePosition.Side;
import com.google.gerrit.proto.Entities.HumanComment.Range;
import com.google.protobuf.Parser;
import java.time.Instant;
import java.util.List;
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
        inFilePos.setPositionRange(toRangeProto(val.range));
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
    if (val.fixSuggestions != null) {
      for (FixSuggestion suggestion : val.fixSuggestions) {
        res.addFixSuggestions(
            Entities.HumanComment.FixSuggestion.newBuilder()
                .setFixId(suggestion.fixId)
                .setDescription(suggestion.description)
                .addAllReplacements(
                    suggestion.replacements.stream()
                        .map(
                            r ->
                                Entities.HumanComment.FixReplacement.newBuilder()
                                    .setPath(r.path)
                                    .setRange(toRangeProto(r.range))
                                    .setReplacement(r.replacement)
                                    .build())
                        .collect(toImmutableList())));
      }
    }
    return res.build();
  }

  private Range.Builder toRangeProto(Comment.Range range) {
    return Range.newBuilder()
        .setStartLine(range.startLine)
        .setStartChar(range.startChar)
        .setEndLine(range.endLine)
        .setEndChar(range.endChar);
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
            proto.getUnresolved(),
            proto.hasDestCommitId()
                ? objectIdConverter.fromProto(proto.getDestCommitId()).getName()
                : null,
            proto.hasParentCommentUuid() ? proto.getParentCommentUuid() : null,
            proto.hasTag() ? proto.getTag() : null,
            fromFixSuggestionsProto(proto.getFixSuggestionsList()),
            /* realAuthor= */ null);

    if (proto.hasRealAuthor()) {
      // Not setting real author from the constructor because if the proto has a value - we want to
      // set it even if it's the same as the `author`.
      res.realAuthor = new Comment.Identity(accountIdConverter.fromProto(proto.getRealAuthor()));
    }

    optInFilePosition.ifPresent(
        inFilePosition -> {
          if (inFilePosition.hasPositionRange()) {
            res.range = fromRangeProto(inFilePosition.getPositionRange());
          }
          if (inFilePosition.hasLineNumber()) {
            res.lineNbr = inFilePosition.getLineNumber();
          }
        });
    return res;
  }

  private Comment.Range fromRangeProto(Range range) {
    return new Comment.Range(
        range.getStartLine(), range.getStartChar(), range.getEndLine(), range.getEndChar());
  }

  @Nullable
  private ImmutableList<FixSuggestion> fromFixSuggestionsProto(
      List<Entities.HumanComment.FixSuggestion> suggestionsList) {
    if (suggestionsList.isEmpty()) {
      return null;
    }
    return suggestionsList.stream()
        .map(
            s ->
                new FixSuggestion(
                    s.getFixId(),
                    s.getDescription(),
                    s.getReplacementsList().stream()
                        .map(
                            r ->
                                new FixReplacement(
                                    r.getPath(), fromRangeProto(r.getRange()), r.getReplacement()))
                        .collect(toImmutableList())))
        .collect(toImmutableList());
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
