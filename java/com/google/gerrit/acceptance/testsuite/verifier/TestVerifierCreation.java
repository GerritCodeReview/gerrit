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

package com.google.gerrit.acceptance.testsuite.verifier;

import com.google.auto.value.AutoValue;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import java.util.Optional;

@AutoValue
public abstract class TestVerifierCreation {

  public abstract Optional<String> name();

  public abstract Optional<String> description();

  abstract ThrowingFunction<TestVerifierCreation, String> verifierCreator();

  public static Builder builder(ThrowingFunction<TestVerifierCreation, String> verifierCreator) {
    return new AutoValue_TestVerifierCreation.Builder().verifierCreator(verifierCreator);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder name(String name);

    public abstract Builder description(String description);

    public Builder clearDescription() {
      return description("");
    }

    abstract Builder verifierCreator(
        ThrowingFunction<TestVerifierCreation, String> verifierCreator);

    abstract TestVerifierCreation autoBuild();

    /**
     * Executes the verifier creation as specified.
     *
     * @return the UUID of the created verifier
     */
    public String create() {
      TestVerifierCreation verifierCreation = autoBuild();
      return verifierCreation.verifierCreator().applyAndThrowSilently(verifierCreation);
    }
  }
}
