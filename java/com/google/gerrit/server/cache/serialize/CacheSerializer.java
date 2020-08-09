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

/**
 * Interface for serializing/deserializing a type to/from a persistent cache.
 *
 * <p>Implementations are null-hostile and will throw exceptions from {@link #serialize} when passed
 * null values, unless otherwise specified.
 */
public interface CacheSerializer<T> {
  /**
   * Serializes the object to a new byte array.
   *
   * @param object object to serialize.
   * @return serialized byte array representation.
   * @throws RuntimeException for malformed input, for example null or an otherwise unsupported
   *     value.
   */
  byte[] serialize(T object);

  /**
   * Deserializes a single object from the given byte array.
   *
   * @param in serialized byte array representation.
   * @throws RuntimeException for malformed input, for example null or an otherwise corrupt
   *     serialized representation.
   */
  T deserialize(byte[] in);
}
