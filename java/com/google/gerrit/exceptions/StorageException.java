// Copyright 2008 Google Inc.
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

package com.google.gerrit.exceptions;

/**
 * Any read/write error in a storage layer.
 *
 * <p>This includes but is not limited to:
 *
 * <ul>
 *   <li>NoteDb exceptions
 *   <li>Secondary index exceptions
 *   <li>{@code AccountPatchReviewStore} exceptions
 *   <li>Wrapped JGit exceptions
 *   <li>Other wrapped {@code IOException}s
 * </ul>
 */
public class StorageException extends Exception {
  private static final long serialVersionUID = 1L;

  public StorageException(String message) {
    super(message);
  }

  public StorageException(String message, Throwable why) {
    super(message, why);
  }

  public StorageException(Throwable why) {
    super(why);
  }
}
