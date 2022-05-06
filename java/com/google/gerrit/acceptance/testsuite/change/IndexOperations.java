// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

import com.google.gerrit.acceptance.DisabledAccountIndex;
import com.google.gerrit.acceptance.DisabledChangeIndex;
import com.google.gerrit.acceptance.DisabledProjectIndex;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.inject.Inject;

/** Helpers to enable and disable reads/writes to secondary indices during testing. */
public interface IndexOperations {
  /**
   * Disables reads from the secondary index that this instance is scoped to. Reads fail with {@code
   * UnsupportedOperationException}.
   */
  AutoCloseable disableReads();

  /**
   * Disables writes to the secondary index that this instance is scoped to. Writes fail with {@code
   * UnsupportedOperationException}.
   */
  AutoCloseable disableWrites();

  /** Disables reads from and writes to the secondary index that this instance is scoped to. */
  default AutoCloseable disableReadsAndWrites() {
    AutoCloseable reads = disableReads();
    AutoCloseable writes = disableWrites();
    return () -> {
      reads.close();
      writes.close();
    };
  }

  class Change implements IndexOperations {
    @Inject private ChangeIndexCollection indices;

    @Override
    public AutoCloseable disableReads() {
      ChangeIndex maybeDisabledSearchIndex = indices.getSearchIndex();
      if (!(maybeDisabledSearchIndex instanceof DisabledChangeIndex)) {
        indices.setSearchIndex(
            new DisabledChangeIndex(maybeDisabledSearchIndex), /* closeOld */ false);
      }

      return () -> {
        ChangeIndex maybeEnabledSearchIndex = indices.getSearchIndex();
        if (maybeEnabledSearchIndex instanceof DisabledChangeIndex) {
          indices.setSearchIndex(
              ((DisabledChangeIndex) maybeEnabledSearchIndex).unwrap(), /* closeOld */ false);
        }
      };
    }

    @Override
    public AutoCloseable disableWrites() {
      for (ChangeIndex i : indices.getWriteIndexes()) {
        if (!(i instanceof DisabledChangeIndex)) {
          indices.addWriteIndex(new DisabledChangeIndex(i));
        }
      }
      return () -> {
        for (ChangeIndex i : indices.getWriteIndexes()) {
          if (i instanceof DisabledChangeIndex) {
            indices.addWriteIndex(((DisabledChangeIndex) i).unwrap());
          }
        }
      };
    }
  }

  class Account implements IndexOperations {
    @Inject private AccountIndexCollection indices;

    @Override
    public AutoCloseable disableReads() {
      AccountIndex maybeDisabledSearchIndex = indices.getSearchIndex();
      if (!(maybeDisabledSearchIndex instanceof DisabledAccountIndex)) {
        indices.setSearchIndex(
            new DisabledAccountIndex(maybeDisabledSearchIndex), /* closeOld */ false);
      }

      return () -> {
        AccountIndex maybeEnabledSearchIndex = indices.getSearchIndex();
        if (maybeEnabledSearchIndex instanceof DisabledAccountIndex) {
          indices.setSearchIndex(
              ((DisabledAccountIndex) maybeEnabledSearchIndex).unwrap(), /* closeOld */ false);
        }
      };
    }

    @Override
    public AutoCloseable disableWrites() {
      for (AccountIndex i : indices.getWriteIndexes()) {
        if (!(i instanceof DisabledAccountIndex)) {
          indices.addWriteIndex(new DisabledAccountIndex(i));
        }
      }
      return () -> {
        for (AccountIndex i : indices.getWriteIndexes()) {
          if (i instanceof DisabledAccountIndex) {
            indices.addWriteIndex(((DisabledAccountIndex) i).unwrap());
          }
        }
      };
    }
  }

  class Project implements IndexOperations {
    @Inject private ProjectIndexCollection indices;

    @Override
    public AutoCloseable disableReads() {
      ProjectIndex maybeDisabledSearchIndex = indices.getSearchIndex();
      if (!(maybeDisabledSearchIndex instanceof DisabledProjectIndex)) {
        indices.setSearchIndex(
            new DisabledProjectIndex(maybeDisabledSearchIndex), /* closeOld */ false);
      }

      return () -> {
        ProjectIndex maybeEnabledSearchIndex = indices.getSearchIndex();
        if (maybeEnabledSearchIndex instanceof DisabledProjectIndex) {
          indices.setSearchIndex(
              ((DisabledProjectIndex) maybeEnabledSearchIndex).unwrap(), /* closeOld */ false);
        }
      };
    }

    @Override
    public AutoCloseable disableWrites() {
      for (ProjectIndex i : indices.getWriteIndexes()) {
        if (!(i instanceof DisabledProjectIndex)) {
          indices.addWriteIndex(new DisabledProjectIndex(i));
        }
      }
      return () -> {
        for (ProjectIndex i : indices.getWriteIndexes()) {
          if (i instanceof DisabledProjectIndex) {
            indices.addWriteIndex(((DisabledProjectIndex) i).unwrap());
          }
        }
      };
    }
  }
}
