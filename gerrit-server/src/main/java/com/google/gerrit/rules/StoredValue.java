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

import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SystemException;
import com.googlecode.prolog_cafe.lang.Term;

/**
 * Defines a value cached in a {@link PrologEnvironment}.
 *
 * @see StoredValues
 */
public class StoredValue<T> {
  /** Construct a new unique key that does not match any other key. */
  public static <T> StoredValue<T> create() {
    return new StoredValue<T>(new JavaObjectTerm(new Object()));
  }

  /** Construct a key based on a Java Class object, useful for singletons. */
  public static <T> StoredValue<T> create(Class<T> clazz) {
    return new StoredValue<T>(new JavaObjectTerm(clazz));
  }

  private final Term key;

  /**
   * Initialize a stored value key using a Prolog term.
   *
   * @param key unique identity of the stored value. This will be the hash key
   *        in the interpreter's hash manager.
   */
  public StoredValue(Term key) {
    this.key = key;
  }

  /** Look up the value in the engine, or return null. */
  public T getOrNull(Prolog engine) {
    return get((PrologEnvironment) engine.control);
  }
  /** Get the value from the engine, or throw SystemException. */
  public T get(Prolog engine) {
    T r = getOrNull(engine);
    if (r == null) {
      String msg;
      if (key.isJavaObject() && key.toJava() instanceof Class<?>) {
        msg = "No " + ((Class<?>) key.toJava()).getName() + " avaliable";
      } else {
        msg = key.toString();
      }
      throw new SystemException(msg);
    }
    return r;
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
}