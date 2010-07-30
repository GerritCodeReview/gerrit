package com.google.gerrit.ehcache;

import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.inject.Provider;

import net.sf.ehcache.Ehcache;

import java.util.concurrent.TimeUnit;

public class PopulatingProtobufCache<K, V> implements Cache<K, V> {
  private static class ProtobufEntryCreator<K, V> extends
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

  private final PopulatingCache<SerializableProtobuf<K>, SerializableProtobuf<V>> cache;
  private final ProtobufCodec<K> keyCodec;
  private final ProtobufCodec<V> valueCodec;
  private final Provider<V> valueProvider;

  PopulatingProtobufCache(Ehcache s, EntryCreator<K, V> entryCreator,
      Class<K> keyClass, Class<V> valueClass, Provider<V> valueProvider) {
    keyCodec = CodecFactory.encoder(keyClass);
    valueCodec = CodecFactory.encoder(valueClass);
    this.valueProvider = valueProvider;
    cache =
        new PopulatingCache<SerializableProtobuf<K>, SerializableProtobuf<V>>(
            s, new ProtobufEntryCreator<K, V>(entryCreator, keyClass,
                valueClass));
  }

  @Override
  public V get(K key) {
    return cache.get(new SerializableProtobuf<K>(key, keyCodec))
        .toObject(valueCodec, valueProvider);
  }

  @Override
  public long getTimeToLive(TimeUnit unit) {
    return cache.getTimeToLive(unit);
  }

  @Override
  public void put(K key, V value) {
    cache.put(new SerializableProtobuf<K>(key, keyCodec),
        new SerializableProtobuf<V>(value, valueCodec));
  }

  @Override
  public void remove(K key) {
    cache.remove(new SerializableProtobuf<K>(key, keyCodec));
  }

  @Override
  public void removeAll() {
    cache.removeAll();
  }

  @Override
  public String toString() {
    return cache.toString();
  }
}
