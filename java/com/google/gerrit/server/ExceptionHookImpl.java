// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.update.UpdateException;
import java.util.Optional;
import org.eclipse.jgit.lib.RefUpdate;

/**
 * Class to detect and handle exceptions that are caused by temporary errors, and hence should cause
 * a retry of the failed operation.
 */
public class ExceptionHookImpl implements ExceptionHook {
  @Override
  public boolean shouldRetry(Throwable throwable) {
    return isLockFailure(throwable);
  }

  @Override
  public Optional<String> formatCause(Throwable throwable) {
    if (isLockFailure(throwable)) {
      return Optional.of(RefUpdate.Result.LOCK_FAILURE.name());
    }
    return Optional.empty();
  }

  private static boolean isLockFailure(Throwable throwable) {
    if (throwable instanceof UpdateException || throwable instanceof StorageException) {
      throwable = throwable.getCause();
    }
    return throwable instanceof LockFailureException;
  }
}
