// Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;

/**
 * An incrementing sequence that's used to assign new unique numbers for change, account and group
 * IDs.
 */
public interface IncrementingSequence {
  enum SequenceType {
    ACCOUNTS,
    CHANGES,
    GROUPS;
  }

  /** An interface for creating instances of {@link IncrementingSequence} */
  interface Factory {
    /** Creates an in-memory representation of the sequence. */
    IncrementingSequence create(SequenceType sequenceType);
  }

  /** Returns the next available sequence value and increments the sequence for the next call. */
  int next();

  /** Similar to {@link #next()} but returns a {@code count} of next available values. */
  ImmutableList<Integer> next(int count);

  /** Returns the next available sequence value. */
  int current();

  /** Returns the last sequence number that was assigned. */
  int last();

  /**
   * Stores a new {@code value} to be returned on the next calls for {@link #next()} or {@link
   * #current()}.
   */
  void storeNew(int value);

  /** Returns the batch size that was used to initialize the sequence. */
  int getBatchSize();
}
