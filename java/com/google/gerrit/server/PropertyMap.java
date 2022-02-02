// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.collect.ImmutableMap;
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
   * Object#equals(Object)} and {@link Object#hashCode()} so that the same instance has to be used
   * to retrieve a stored value.
   *
   * <p>We require the exact same key instance because {@link PropertyMap} is implemented in a
   * type-safe fashion by using Java generics to guarantee the return type. The generic type can't
   * be recovered at runtime, so there is no way to just use the type's full name as key - we'd have
   * to pass additional arguments. At the same time, this is in-line with how we'd want callers to
   * use {@link PropertyMap}: Instantiate a static, per-JVM key that is reused when setting and
   * getting values.
   */
  public static class Key<T> {}

  public static <T> Key<T> key() {
    return new Key<>();
  }

  public static class Builder {
    private ImmutableMap.Builder<Object, Object> mutableMap;

    private Builder() {
      this.mutableMap = ImmutableMap.builder();
    }

    /** Adds the provided {@code value} to the {@link PropertyMap} that is being built. */
    public <T> Builder put(Key<T> key, T value) {
      mutableMap.put(key, value);
      return this;
    }

    /** Builds and returns an immutable {@link PropertyMap}. */
    public PropertyMap build() {
      return new PropertyMap(mutableMap.build());
    }
  }

  private final ImmutableMap<Object, Object> map;

  private PropertyMap(ImmutableMap<Object, Object> map) {
    this.map = map;
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
