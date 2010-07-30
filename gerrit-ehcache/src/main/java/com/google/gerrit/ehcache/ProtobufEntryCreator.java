// Copyright 2010 Google Inc. All Rights Reserved.

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