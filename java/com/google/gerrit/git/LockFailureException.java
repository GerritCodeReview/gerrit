// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.git;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.RefUpdate;

/** Thrown when updating a ref in Git fails with LOCK_FAILURE. */
public class LockFailureException extends GitUpdateFailureException {
  private static final long serialVersionUID = 1L;

  private static final String REF_UPDATE_RETURN_CODE_WAS_LOCK_FAILURE =
      "RefUpdate return code was: LOCK_FAILURE";

  public LockFailureException(String message, RefUpdate refUpdate) {
    super(message, refUpdate);
  }

  public LockFailureException(String message, BatchRefUpdate batchRefUpdate) {
    super(message, batchRefUpdate);
  }

  protected LockFailureException(String message, Throwable cause) {
    super(message, cause);
  }

  public static void throwIfLockFailure(ConcurrentRefUpdateException e)
      throws LockFailureException {
    if (e.getMessage().contains(REF_UPDATE_RETURN_CODE_WAS_LOCK_FAILURE)) {
      throw new LockFailureException(e.getMessage(), e);
    }
  }
}
