// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Iterator over lazily-loaded {@link ChangeNotes}, which may throw checked exceptions from its
 * methods.
 */
public interface ChangeNotesIterator {
  /**
   * Exception thrown from {@link #hasNext()} or {@link #next()} when a change failed to load, and
   * the iterator has enough information to know which specific change it was trying to load at the
   * time.
   */
  public static class NextChangeNotesException extends OrmException {
    private static final long serialVersionUID = 1L;

    private static String msg(Change.Id id) {
      return "Failed to load change " + checkNotNull(id);
    }

    private final Change.Id id;

    NextChangeNotesException(Change.Id id) {
      super(msg(id));
      this.id = id;
    }

    NextChangeNotesException(Change.Id id, Throwable cause) {
      super(msg(id), cause);
      this.id = id;
    }

    public Change.Id getId() {
      return id;
    }
  }

  /**
   * Check whether there is another {@link ChangeNotes} in the iterator.
   *
   * <p>If this method throws an exception once, future calls will all return false.
   *
   * @return whether there is another notes instance left.
   * @throws NextChangeNotesException if an error occurred and the iterator had enough information
   *     to know which specific change it was trying to load at the time.
   * @throws OrmException if an error occurred but the specific change that caused the error could
   *     not be identified.
   * @throws IOException if an error occurred but the specific change that caused the error could
   *     not be identified.
   */
  boolean hasNext() throws OrmException, IOException;

  /**
   * Get the next {@link ChangeNotes} from the iterator.
   *
   * <p>If this method throws an exception once, future calls will all throw {@code
   * NoSuchElementException}.
   *
   * @return whether there is another notes instance left.
   * @throws NextChangeNotesException if an error occurred and the iterator had enough information
   *     to know which specific change it was trying to load at the time.
   * @throws OrmException if an error occurred but the specific change that caused the error could
   *     not be identified.
   * @throws IOException if an error occurred but the specific change that caused the error could
   *     not be identified.
   * @throws NoSuchElementException if no notes are left in the iterator.
   */
  ChangeNotes next() throws OrmException, IOException, NoSuchElementException;
}
