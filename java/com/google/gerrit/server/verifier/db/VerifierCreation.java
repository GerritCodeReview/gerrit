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

package com.google.gerrit.server.verifier.db;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class VerifierCreation {
  /** Defines the UUID the verifier should have. */
  public abstract String getVerifierUuid();

  /** Defines the name the verifier should have. */
  public abstract String getName();

  public static Builder builder() {
    return new AutoValue_VerifierCreation.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** @see #getVerifierUuid() */
    public abstract Builder setVerifierUuid(String verifierUuid);

    /** @see #getName() */
    public abstract Builder setName(String name);

    public abstract VerifierCreation build();
  }
}
