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

package com.google.gerrit.plugins.checkers.acceptance.testsuite;

import com.google.auto.value.AutoValue;
import java.sql.Timestamp;
import java.util.Optional;

@AutoValue
public abstract class TestChecker {
  public abstract String uuid();

  public abstract String name();

  public abstract Optional<String> description();

  public abstract Timestamp createdOn();

  static Builder builder() {
    return new AutoValue_TestChecker.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {

    public abstract Builder uuid(String checkerUuid);

    public abstract Builder name(String name);

    public abstract Builder description(String description);

    public abstract Builder description(Optional<String> description);

    public abstract Builder createdOn(Timestamp createdOn);

    abstract TestChecker build();
  }
}
