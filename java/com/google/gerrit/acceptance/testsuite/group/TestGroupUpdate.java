// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.group;

import com.google.auto.value.AutoValue;
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import java.util.Optional;

@AutoValue
public abstract class TestGroupUpdate {

  public abstract Optional<String> description();

  abstract ThrowingConsumer<TestGroupUpdate> groupUpdater();

  public static Builder builder(ThrowingConsumer<TestGroupUpdate> groupUpdater) {
    return new AutoValue_TestGroupUpdate.Builder().groupUpdater(groupUpdater);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder description(String description);

    public Builder clearDescription() {
      return description("");
    }

    abstract Builder groupUpdater(ThrowingConsumer<TestGroupUpdate> groupUpdater);

    abstract TestGroupUpdate autoBuild();

    /** Executes the group update as specified. */
    public void update() throws Exception {
      TestGroupUpdate groupUpdater = autoBuild();
      groupUpdater.groupUpdater().accept(groupUpdater);
    }
  }
}
