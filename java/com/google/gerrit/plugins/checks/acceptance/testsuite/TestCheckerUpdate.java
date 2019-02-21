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

package com.google.gerrit.plugins.checks.acceptance.testsuite;

import com.google.auto.value.AutoValue;
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Optional;

@AutoValue
public abstract class TestCheckerUpdate {
  public abstract Optional<String> name();

  public abstract Optional<String> description();

  public abstract Optional<String> url();

  public abstract Optional<Project.NameKey> repository();

  public abstract Optional<CheckerStatus> status();

  abstract ThrowingConsumer<TestCheckerUpdate> checkerUpdater();

  public static Builder builder(ThrowingConsumer<TestCheckerUpdate> checkerUpdater) {
    return new AutoValue_TestCheckerUpdate.Builder().checkerUpdater(checkerUpdater);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder name(String name);

    public abstract Builder description(String description);

    public Builder clearDescription() {
      return description("");
    }

    public abstract Builder url(String url);

    public Builder clearUrl() {
      return url("");
    }

    public abstract Builder repository(Project.NameKey repository);

    abstract Builder status(CheckerStatus status);

    public Builder enable() {
      return status(CheckerStatus.ENABLED);
    }

    public Builder disable() {
      return status(CheckerStatus.DISABLED);
    }

    abstract Builder checkerUpdater(ThrowingConsumer<TestCheckerUpdate> checkerUpdater);

    abstract TestCheckerUpdate autoBuild();

    /** Executes the checker update as specified. */
    public void update() {
      TestCheckerUpdate checkerUpdater = autoBuild();
      checkerUpdater.checkerUpdater().acceptAndThrowSilently(checkerUpdater);
    }
  }
}
