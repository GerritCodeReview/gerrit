// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.ehcache;

import com.google.gerrit.server.cache.EntryCreator;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;

class ProtobufEntryCreator<K, V> extends
    EntryCreator<SerializableProtobuf<K>, SerializableProtobuf<V>> {
  private final ProtobufCodec<K> keyCodec;
  private final ProtobufCodec<V> valueCodec;
  private final EntryCreator<K, V> creator;

  public ProtobufEntryCreator(EntryCreator<K, V> entryCreator,
      Class<K> keyClass, Class<V> valueClass) {
    this.keyCodec = CodecFactory.encoder(keyClass);
    this.valueCodec = CodecFactory.encoder(valueClass);
    this.creator = entryCreator;
  }

  @Override
  public SerializableProtobuf<V> createEntry(SerializableProtobuf<K> key)
      throws Exception {
    return new SerializableProtobuf<V>(creator.createEntry(key.toObject(
        keyCodec, null)), valueCodec);
  }
}