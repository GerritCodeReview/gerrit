package com.google.gerrit.server;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable map that holds a collection of random objects allowing for a type-safe retrieval.
 *
 * <p>Intended to be used in {@link CurrentUser} when the object is constructed during login and
 * holds per-request state. This functionality allows plugins/extensions to contribute specific data
 * to {@link CurrentUser} that is unknown to Gerrit core.
 */
public class PropertyMap {
  /** Empty instance to be referenced once per JVM. */
  public static final PropertyMap EMPTY = builder().build();

  /**
   * Typed key for {@link PropertyMap}. This class intentionally does not implement {@link
   * Object#equals(Object)} and {@link Object#hashCode()} so that the very same key has to be used
   * to retrieve an instance.
   */
  public static class Key<T> {}

  public static <T> Key<T> key() {
    return new Key<>();
  }

  public static class Builder {
    private Map<Object, Object> mutableMap;

    private Builder() {
      this.mutableMap = new HashMap<>();
    }

    /** Adds the provided {@code value} to the {@link PropertyMap} that is being built. */
    public <T> Builder put(Key<T> key, T value) {
      mutableMap.put(key, value);
      return this;
    }

    /** Builds and returns an immutable {@link PropertyMap}. */
    public PropertyMap build() {
      return new PropertyMap(mutableMap);
    }
  }

  private final ImmutableMap<Object, Object> map;

  private PropertyMap(Map<Object, Object> map) {
    this.map = ImmutableMap.copyOf(map);
  }

  /** Returns a new {@link Builder} instance. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns the requested value wrapped as {@link Optional}. */
  @SuppressWarnings("unchecked")
  public <T> Optional<T> get(Key<T> key) {
    return Optional.ofNullable((T) map.get(key));
  }
}
