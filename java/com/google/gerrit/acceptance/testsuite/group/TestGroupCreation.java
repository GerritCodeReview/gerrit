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
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import java.util.Optional;

@AutoValue
public abstract class TestGroupCreation {

  public abstract Optional<String> name();

  public abstract Optional<String> description();

  abstract ThrowingFunction<TestGroupCreation, TestGroup> groupCreator();

  public static Builder builder(ThrowingFunction<TestGroupCreation, TestGroup> groupCreator) {
    return new AutoValue_TestGroupCreation.Builder().groupCreator(groupCreator);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder name(String name);

    public abstract Builder description(String description);

    public Builder clearDescription() {
      return description("");
    }

    abstract Builder groupCreator(ThrowingFunction<TestGroupCreation, TestGroup> groupCreator);

    abstract TestGroupCreation autoBuild();

    /**
     * Executes the group creation as specified.
     *
     * @return the created {@code TestGroup}
     */
    public TestGroup create() throws Exception {
      TestGroupCreation groupCreation = autoBuild();
      return groupCreation.groupCreator().apply(groupCreation);
    }
  }
}
