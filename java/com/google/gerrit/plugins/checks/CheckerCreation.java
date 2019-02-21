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

package com.google.gerrit.plugins.checks;

import com.google.auto.value.AutoValue;
import com.google.gerrit.reviewdb.client.Project;

@AutoValue
public abstract class CheckerCreation {
  /**
   * Defines the UUID the checker should have.
   *
   * <p>Must be a SHA-1 that is unique across all checkers.
   */
  public abstract String getCheckerUuid();

  /** Defines the name the checker should have. */
  public abstract String getName();

  /** Defines the repository for which the checker applies. */
  public abstract Project.NameKey getRepository();

  public static Builder builder() {
    return new AutoValue_CheckerCreation.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCheckerUuid(String checkerUuid);

    public abstract Builder setName(String name);

    public abstract Builder setRepository(Project.NameKey repository);

    public abstract CheckerCreation build();
  }
}
