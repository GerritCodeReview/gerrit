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

package com.google.gerrit.server.cache.serialize;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

public enum StringCacheSerializer implements CacheSerializer<String> {
  INSTANCE;

  @Override
  public byte[] serialize(String object) {
    return serialize(UTF_8, object);
  }

  @VisibleForTesting
  static byte[] serialize(Charset charset, String s) {
    if (s.isEmpty()) {
      return new byte[0];
    }
    try {
      ByteBuffer buf =
          charset
              .newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .encode(CharBuffer.wrap(s));
      byte[] result = new byte[buf.remaining()];
      buf.get(result);
      return result;
    } catch (CharacterCodingException e) {
      throw new IllegalStateException("Failed to serialize string", e);
    }
  }

  @Override
  public String deserialize(byte[] in) {
    if (in.length == 0) {
      return "";
    }
    try {
      return UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(in))
          .toString();
    } catch (CharacterCodingException e) {
      throw new IllegalStateException("Failed to deserialize string", e);
    }
  }
}
