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

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.assistedinject.Assisted;

import com.googlecode.prolog_cafe.lang.BufferingPrologControl;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologClassLoader;
import com.googlecode.prolog_cafe.lang.SystemException;
import com.googlecode.prolog_cafe.lang.Term;

public class PrologEnvironment extends BufferingPrologControl {
  private static final String[] PACKAGE_LIST = {
      Prolog.BUILTIN,
      "com.google.gerrit.rules.common",
    };

  public static interface Factory {
    PrologEnvironment create(ClassLoader cl);
  }

  private final Injector injector;
  private boolean intialized;

  @Inject
  PrologEnvironment(Injector i, @Assisted ClassLoader newCL) {
    injector = i;
    setPrologClassLoader(new PrologClassLoader(newCL));
    setMaxArity(8);
    setMaxDatabaseSize(64);
    setEnableReflection(false);
  }

  /** Get the global Guice Injector that configured the environment. */
  public Injector getInjector() {
    return injector;
  }

  public <T> T get(StoredValue<T> sv) {
    return sv.get(engine);
  }

  public <T> void set(StoredValue<T> sv, T obj) {
    sv.set(engine, obj);
  }

  @Override
  public void setPredicate(String pkg, String functor, Term... args) {
    init();
    super.setPredicate(pkg, functor, args);
  }

  private void init() {
    if (!intialized) {
      intialized = true;
      if (!initialize(PACKAGE_LIST)) {
        throw new SystemException("Prolog initialization failed");
      }
    }
  }
}
