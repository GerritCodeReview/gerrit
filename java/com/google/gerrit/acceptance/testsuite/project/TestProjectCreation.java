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

package com.google.gerrit.acceptance.testsuite.project;

import com.google.auto.value.AutoValue;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import java.util.Optional;

@AutoValue
public abstract class TestProjectCreation {

  public abstract Optional<String> nameSuffix();

  public abstract Optional<Project.NameKey> parent();

  public abstract Optional<Boolean> createEmptyCommit();

  public abstract Optional<SubmitType> submitType();

  public abstract ThrowingFunction<TestProjectCreation, NameKey> projectCreator();

  public static Builder builder(ThrowingFunction<TestProjectCreation, NameKey> projectCreator) {
    return new AutoValue_TestProjectCreation.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder nameSuffix(String n);

    public abstract Builder parent(Project.NameKey nameKey);

    public abstract Builder createEmptyCommit(Boolean b);

    public abstract Builder submitType(SubmitType submitType);

    public abstract Builder projectCreator(ThrowingFunction<TestProjectCreation, NameKey> creator);

    public abstract TestProjectCreation autoBuild();

    public Project.NameKey create() throws Exception {
      TestProjectCreation creation = autoBuild();
      return creation.projectCreator().apply(creation);
    }
  }
}
