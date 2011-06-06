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

/** Defines a value cached in the Prolog instance. */
public class StoredValue<T> {
  public static <T> StoredValue<T> create() {
    return new StoredValue<T>(new JavaObjectTerm(new Object()));
  }

  public static <T> StoredValue<T> create(Class<T> clazz) {
    return new StoredValue<T>(new JavaObjectTerm(clazz));
  }

  private final Term key;

  public StoredValue(Term key) {
    this.key = key;
  }

  @SuppressWarnings("unchecked")
  public T getOrNull(Prolog engine) {
    Term r = engine.getHashManager().get(key);
    return r != null && r.isJavaObject() ? (T) r.toJava() : null;
  }

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
    engine.getHashManager().put(key, new JavaObjectTerm(obj));
  }

  public T get(PrologEnvironment env) {
    return env.get(this);
  }

  public void set(PrologEnvironment env, T obj) {
    env.set(this, obj);
  }
}
