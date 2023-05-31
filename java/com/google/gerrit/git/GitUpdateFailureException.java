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

package com.google.gerrit.git;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Thrown when updating a ref in Git fails. */
public class GitUpdateFailureException extends IOException {
  private static final long serialVersionUID = 1L;

  private final ImmutableList<GitUpdateFailure> failures;

  public GitUpdateFailureException(String message, RefUpdate refUpdate) {
    super(message);
    this.failures = ImmutableList.of(GitUpdateFailure.create(refUpdate));
  }

  public GitUpdateFailureException(String message, BatchRefUpdate batchRefUpdate) {
    super(message);
    this.failures =
        batchRefUpdate.getCommands().stream()
            .filter(c -> c.getResult() != ReceiveCommand.Result.OK)
            .map(GitUpdateFailure::create)
            .collect(toImmutableList());
  }

  protected GitUpdateFailureException(String message, Throwable cause) {
    super(message, cause);
    this.failures = ImmutableList.of();
  }

  /** Returns the names of the refs for which the update failed. */
  public ImmutableList<String> getFailedRefs() {
    return failures.stream().map(GitUpdateFailure::ref).collect(toImmutableList());
  }

  /** Returns the failures that caused this exception. */
  @UsedAt(UsedAt.Project.GOOGLE)
  public ImmutableList<GitUpdateFailure> getFailures() {
    return failures;
  }

  @AutoValue
  public abstract static class GitUpdateFailure {
    private static GitUpdateFailure create(RefUpdate refUpdate) {
      return builder().ref(refUpdate.getName()).result(refUpdate.getResult().name()).build();
    }

    private static GitUpdateFailure create(ReceiveCommand receiveCommand) {
      return builder()
          .ref(receiveCommand.getRefName())
          .result(receiveCommand.getResult().name())
          .message(receiveCommand.getMessage())
          .build();
    }

    public abstract String ref();

    public abstract String result();

    public abstract Optional<String> message();

    public static GitUpdateFailure.Builder builder() {
      return new AutoValue_GitUpdateFailureException_GitUpdateFailure.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder ref(String ref);

      abstract Builder result(String result);

      abstract Builder message(@Nullable String message);

      abstract GitUpdateFailure build();
    }
  }
}
