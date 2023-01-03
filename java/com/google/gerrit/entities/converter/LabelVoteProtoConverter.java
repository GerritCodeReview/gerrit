package com.google.gerrit.entities.converter;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.LabelVote;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.Entities.Label_Vote;
import com.google.protobuf.Parser;

@Immutable
public enum LabelVoteProtoConverter implements ProtoConverter<Entities.Label_Vote, LabelVote> {
  INSTANCE;

  @Override
  public Label_Vote toProto(LabelVote value) {
    return Label_Vote.newBuilder().setLabelName(value.label()).setVote(value.value()).build();
  }

  @Override
  public LabelVote fromProto(Label_Vote proto) {
    return LabelVote.create(proto.getLabelName(), (short) proto.getVote());
  }

  @Override
  public Parser<Label_Vote> getParser() {
    return Label_Vote.parser();
  }
}
