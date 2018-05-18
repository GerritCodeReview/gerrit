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

package com.google.gerrit.server.cache;

import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.io.IOException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

/** Static utilities for writing protobuf-based {@link CacheSerializer} implementations. */
public class ProtoCacheSerializers {
  /**
   * Serializes a proto to a byte array.
   *
   * <p>Guarantees deterministic serialization and thus is suitable for use in persistent caches.
   * Should be used in preference to {@link MessageLite#toByteArray()}, which is not guaranteed
   * deterministic.
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
   * Serializes an object to a {@link ByteString} using a protobuf codec.
   *
   * <p>Guarantees deterministic serialization and thus is suitable for use in persistent caches.
   * Should be used in preference to {@link ProtobufCodec#encodeToByteString(Object)}, which is not
   * guaranteed deterministic.
   *
   * @param object the object to serialize.
   * @param codec codec for serializing.
   * @return a {@code ByteString} with the message contents.
   */
  public static <T> ByteString toByteString(T object, ProtobufCodec<T> codec) {
    try (ByteString.Output bout = ByteString.newOutput()) {
      CodedOutputStream cout = CodedOutputStream.newInstance(bout);
      cout.useDeterministicSerialization();
      codec.encode(object, cout);
      cout.flush();
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
   * Helper for serializing {@link ObjectId} instances to/from protobuf fields.
   *
   * <p>For serializing, reuse a single instance's {@link #toByteString(ObjectId)} to minimize the
   * number of temporary buffers. For deserializing, simply use the static {@link
   * #fromByteString(ByteString)} method.
   */
  public static class ObjectIdHelper {
    public static ObjectId fromByteString(ByteString in) {
      return ObjectId.fromRaw(in.toByteArray());
    }

    private final byte[] buf = new byte[Constants.OBJECT_ID_LENGTH];

    public ByteString toByteString(ObjectId id) {
      id.copyRawTo(buf, 0);
      return ByteString.copyFrom(buf);
    }
  }

  private ProtoCacheSerializers() {}
}
