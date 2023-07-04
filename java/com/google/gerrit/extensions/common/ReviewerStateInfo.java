package com.google.gerrit.extensions.common;

import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.proto.CustomPojo;
import com.google.gerrit.proto.Rest;
import com.google.gerrit.proto.RestProtoConverter;
import com.google.protobuf.Message;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ReviewerStateInfo extends HashMap<ReviewerState, Collection<AccountInfo>>
    implements CustomPojo {

  @Override
  @SuppressWarnings("LiteEnumValueOf")
  public Message toProto() {
    Rest.ReviewerStateInfo.Builder b = Rest.ReviewerStateInfo.newBuilder();
    for (Map.Entry<ReviewerState, Collection<AccountInfo>> entry : entrySet()) {
      Rest.ReviewerStateInfo.Entry.Builder eb = b.addEntriesBuilder();

      // errorprone complains, but since POJO enums are serialized to string for JSON, using the
      // name
      // is more robust.
      eb.setKey(Rest.ReviewerState.valueOf(entry.getKey().name()));
      entry
          .getValue()
          .forEach(ai -> eb.addValues((Rest.AccountInfo) RestProtoConverter.toProtoMessage(ai)));
    }
    return b.build();
  }

  @Override
  public void fromProto(Message msg) {
    Rest.ReviewerStateInfo rsi = (Rest.ReviewerStateInfo) msg;
    for (Rest.ReviewerStateInfo.Entry e : rsi.getEntriesList()) {
      put(
          ReviewerState.valueOf(e.getKey().name()),
          e.getValuesList().stream()
              .map(ai -> (AccountInfo) RestProtoConverter.fromProtoMessage(ai))
              .collect(Collectors.toList()));
    }
  }
}
