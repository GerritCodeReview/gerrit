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
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Per-thread Prolog interpreter.
 * <p>
 * This class is not thread safe.
 * <p>
 * A single copy of the Prolog interpreter, for the current thread.
 */
public class PrologEnvironment extends BufferingPrologControl {
  static final int MAX_ARITY = 8;

  public static interface Factory {
    /**
     * Construct a new Prolog interpreter.
     *
     * @param src the machine to template the new environment from.
     * @return the new interpreter.
     */
    PrologEnvironment create(PrologMachineCopy src);
  }

  private final Injector injector;
  private final Map<StoredValue<Object>, Object> storedValues;
  private final List<Runnable> cleanup;

  @Inject
  PrologEnvironment(Injector i, @Assisted PrologMachineCopy src) {
    super(src);
    injector = i;
    setMaxArity(MAX_ARITY);
    setEnabled(EnumSet.allOf(Prolog.Feature.class), false);
    storedValues = new HashMap<StoredValue<Object>, Object>();
    cleanup = new LinkedList<Runnable>();
  }

  /** Get the global Guice Injector that configured the environment. */
  public Injector getInjector() {
    return injector;
  }

  /**
   * Lookup a stored value in the interpreter's hash manager.
   *
   * @param <T> type of stored Java object.
   * @param sv unique key.
   * @return the value; null if not stored.
   */
  public <T> T get(StoredValue<T> sv) {
    return (T) storedValues.get(sv);
  }

  /**
   * Set a stored value on the interpreter's hash manager.
   *
   * @param <T> type of stored Java object.
   * @param sv unique key.
   * @param obj the value to store under {@code sv}.
   */
  public <T> void set(StoredValue<T> sv, T obj) {
    storedValues.put((StoredValue<Object>) sv, obj);
  }

  /**
   * Copy the stored values from another interpreter to this one.
   * Also take on the cleanup duties of the child interpreter.
   */
  public void copyStoredValues(PrologEnvironment child) {
    storedValues.putAll(child.storedValues);
    child.delegateCleanup(cleanup);
  }

  /**
   * child interpreter delegates cleanup duties by copying own cleanup list
   * to parent and then clearing own list
   */
  private void delegateCleanup(List<Runnable> parentList) {
    parentList.addAll(cleanup);
    cleanup.clear();
  }

  /**
   * Adds cleanup task to run when close() is called
   * @param task is run when close() is called
   */
  public void addToCleanup(Runnable task) {
    cleanup.add(task);
  }

  /**
   * Release resources stored in interpreter's hash manager.
   */
  public void close() {
    for (Runnable task : cleanup) {
      task.run();
    }
    //ensure cleanup tasks run only once, even if close() is called again
    cleanup.clear();
  }
}