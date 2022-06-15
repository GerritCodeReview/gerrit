// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.index;

import com.google.protobuf.MessageLite;
import java.sql.Timestamp;

/**
 * Representation of a field stored on the index. Used to load field values from different index
 * backends.
 */
public interface StoredValue {
  /** Returns the {@link String} value of the field. */
  String asString();

  /** Returns the {@link String} values of the field. */
  Iterable<String> asStrings();

  /** Returns the {@link Integer} value of the field. */
  Integer asInteger();

  /** Returns the {@link Integer} values of the field. */
  Iterable<Integer> asIntegers();

  /** Returns the {@link Long} value of the field. */
  Long asLong();

  /** Returns the {@link Long} values of the field. */
  Iterable<Long> asLongs();

  /** Returns the {@link Timestamp} value of the field. */
  Timestamp asTimestamp();

  /** Returns the {@code byte[]} value of the field. */
  byte[] asByteArray();

  /** Returns the {@code byte[]} values of the field. */
  Iterable<byte[]> asByteArrays();

  /** Returns the {@code MessageLite} value of the field. */
  MessageLite asProto();

  /** Returns the {@code MessageLite} values of the field. */
  Iterable<MessageLite> asProtos();
}
