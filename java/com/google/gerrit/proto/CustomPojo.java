package com.google.gerrit.proto;

import com.google.protobuf.Message;

public interface CustomPojo {
  Message toProto();

  void fromProto(Message msg);
}
