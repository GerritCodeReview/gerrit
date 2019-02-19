// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.cache.serialize;

import com.google.gerrit.proto.Protos;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

/** A CacheSerializer for Protobuf messages. */
public class ProtobufSerializer<T extends MessageLite> implements CacheSerializer<T> {
  private final Parser<T> parser;

  public ProtobufSerializer(Parser<T> parser) {
    this.parser = parser;
  }

  @Override
  public byte[] serialize(T object) {
    return Protos.toByteArray(object);
  }

  @Override
  public T deserialize(byte[] in) {
    return Protos.parseUnchecked(parser, in);
  }
}
