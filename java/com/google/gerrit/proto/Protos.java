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

package com.google.gerrit.proto;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.io.IOException;

/** Static utilities for dealing with protobuf-based objects. */
public class Protos {
  /**
   * Serializes a proto to a byte array.
   *
   * <p>Guarantees deterministic serialization. No matter whether the use case cares about
   * determinism or not, always use this method in preference to {@link MessageLite#toByteArray()},
   * which is not guaranteed deterministic.
   *
   * @param message the proto message to serialize.
   * @return a byte array with the message contents.
   */
  public static byte[] toByteArray(MessageLite message) {
    byte[] bytes = new byte[message.getSerializedSize()];
    CodedOutputStream cout = CodedOutputStream.newInstance(bytes);
    cout.useDeterministicSerialization();
    try {
      message.writeTo(cout);
      cout.checkNoSpaceLeft();
      return bytes;
    } catch (IOException e) {
      throw new IllegalStateException("exception writing to byte array", e);
    }
  }

  /**
   * Serializes a proto to a {@code ByteString}.
   *
   * <p>Guarantees deterministic serialization. No matter whether the use case cares about
   * determinism or not, always use this method in preference to {@link MessageLite#toByteString()},
   * which is not guaranteed deterministic.
   *
   * @param message the proto message to serialize
   * @return a {@code ByteString} with the message contents
   */
  public static ByteString toByteString(MessageLite message) {
    try (ByteString.Output bout = ByteString.newOutput(message.getSerializedSize())) {
      CodedOutputStream outputStream = CodedOutputStream.newInstance(bout);
      outputStream.useDeterministicSerialization();
      message.writeTo(outputStream);
      outputStream.flush();
      return bout.toByteString();
    } catch (IOException e) {
      throw new IllegalStateException("exception writing to ByteString", e);
    }
  }

  /**
   * Parses a byte array to a protobuf message.
   *
   * @param parser parser for the proto type.
   * @param in byte array with the message contents.
   * @return parsed proto.
   */
  public static <M extends MessageLite> M parseUnchecked(Parser<M> parser, byte[] in) {
    try {
      return parser.parseFrom(in);
    } catch (IOException e) {
      throw new IllegalArgumentException("exception parsing byte array to proto", e);
    }
  }

  /**
   * Parses a specific segment of a byte array to a protobuf message.
   *
   * @param parser parser for the proto type
   * @param in byte array with the message contents
   * @param offset offset in the byte array to start reading from
   * @param length amount of read bytes
   * @return parsed proto
   */
  public static <M extends MessageLite> M parseUnchecked(
      Parser<M> parser, byte[] in, int offset, int length) {
    try {
      return parser.parseFrom(in, offset, length);
    } catch (IOException e) {
      throw new IllegalArgumentException("exception parsing byte array to proto", e);
    }
  }

  /**
   * Parses a {@code ByteString} to a protobuf message.
   *
   * @param parser parser for the proto type
   * @param byteString {@code ByteString} with the message contents
   * @return parsed proto
   */
  public static <M extends MessageLite> M parseUnchecked(Parser<M> parser, ByteString byteString) {
    try {
      return parser.parseFrom(byteString);
    } catch (IOException e) {
      throw new IllegalArgumentException("exception parsing ByteString to proto", e);
    }
  }

  private Protos() {}
}
