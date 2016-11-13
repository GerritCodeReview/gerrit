// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.rules;

import com.googlecode.prolog_cafe.exceptions.SystemException;
import com.googlecode.prolog_cafe.lang.Prolog;

/**
 * Defines a value cached in a {@link PrologEnvironment}.
 *
 * @see StoredValues
 */
public class StoredValue<T> {
  /** Construct a new unique key that does not match any other key. */
  public static <T> StoredValue<T> create() {
    return new StoredValue<>();
  }

  /** Construct a key based on a Java Class object, useful for singletons. */
  public static <T> StoredValue<T> create(Class<T> clazz) {
    return new StoredValue<>(clazz);
  }

  private final Object key;

  /**
   * Initialize a stored value key using any Java Object.
   *
   * @param key unique identity of the stored value. This will be the hash key in the Prolog
   *     Environments's hash map.
   */
  public StoredValue(Object key) {
    this.key = key;
  }

  /** Initializes a stored value key with a new unique key. */
  public StoredValue() {
    key = this;
  }

  /** Look up the value in the engine, or return null. */
  public T getOrNull(Prolog engine) {
    return get((PrologEnvironment) engine.control);
  }
  /** Get the value from the engine, or throw SystemException. */
  public T get(Prolog engine) {
    T obj = getOrNull(engine);
    if (obj == null) {
      //unless createValue() is overridden, will return null
      obj = createValue(engine);
      if (obj == null) {
        throw new SystemException("No " + key + " available");
      }
      set(engine, obj);
    }
    return obj;
  }

  public void set(Prolog engine, T obj) {
    set((PrologEnvironment) engine.control, obj);
  }

  /** Perform {@link #getOrNull(Prolog)} on the environment's interpreter. */
  public T get(PrologEnvironment env) {
    return env.get(this);
  }

  /** Set the value into the environment's interpreter. */
  public void set(PrologEnvironment env, T obj) {
    env.set(this, obj);
  }

  /**
   * Creates a value to store, returns null by default.
   *
   * @param engine Prolog engine.
   * @return new value.
   */
  protected T createValue(Prolog engine) {
    return null;
  }
}
