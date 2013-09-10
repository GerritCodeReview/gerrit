// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.index;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Definition of a field stored in the secondary index.
 *
 * @param I input type from which documents are created and search results are
 *     returned.
 * @param T type that should be extracted from the input object when converting
 *     to an index document.
 */
public abstract class FieldDef<I, T> {
  /** Definition of a single (non-repeatable) field. */
  public static abstract class Single<I, T> extends FieldDef<I, T> {
    Single(String name, FieldType<T> type, boolean stored) {
      super(name, type, stored);
    }

    @Override
    public final boolean isRepeatable() {
      return false;
    }
  }

  /** Definition of a repeatable field. */
  public static abstract class Repeatable<I, T>
      extends FieldDef<I, Iterable<T>> {
    Repeatable(String name, FieldType<T> type, boolean stored) {
      super(name, type, stored);
    }

    @Override
    public final boolean isRepeatable() {
      return true;
    }
  }

  /** Arguments needed to fill in missing data in the input object. */
  public static class FillArgs {
    final Provider<ReviewDb> db;
    final GitRepositoryManager repoManager;
    final PatchListCache patchListCache;
    final ChangeControl.GenericFactory changeControlFactory;

    private final InternalUser internalUser;

    @Inject
    FillArgs(Provider<ReviewDb> db, GitRepositoryManager repoManager,
        PatchListCache patchListCache,
        ChangeControl.GenericFactory changeControlFactory,
        InternalUser internalUser) {
      this.db = db;
      this.repoManager = repoManager;
      this.patchListCache = patchListCache;
      this.changeControlFactory = changeControlFactory;
      this.internalUser = internalUser;
    }

    /**
     * Create a change control.
     *
     * @param change change object.
     * @return change control as seen by Gerrit's internal user; index fields
     *     may not depend on any per-user state.
     */
    ChangeControl changeControlFor(Change change) throws NoSuchChangeException {
      return changeControlFactory.controlFor(change, internalUser);
    }
  }

  private final String name;
  private final FieldType<?> type;
  private final boolean stored;

  private FieldDef(String name, FieldType<?> type, boolean stored) {
    this.name = name;
    this.type = type;
    this.stored = stored;
  }

  /** @return name of the field. */
  public final String getName() {
    return name;
  }

  /**
   * @return type of the field; for repeatable fields, the inner type, not the
   *     iterable type.
   */
  public final FieldType<?> getType() {
    return type;
  }

  /** @return whether the field should be stored in the index. */
  public final boolean isStored() {
    return stored;
  }

  /**
   * Get the field contents from the input object.
   *
   * @param input input object.
   * @param args arbitrary arguments needed to fill in indexable fields of the
   *     input object.
   * @return the field value(s) to index.
   *
   * @throws OrmException
   */
  public abstract T get(I input, FillArgs args) throws OrmException;

  /** @return whether the field is repeatable. */
  public abstract boolean isRepeatable();
}
