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
import com.google.gerrit.server.verifier.Verifier;
import java.sql.Timestamp;
import java.util.Optional;

@AutoValue
public abstract class VerifierUpdate {
  /** Defines the new name of the verifier. If not specified, the name remains unchanged. */
  public abstract Optional<String> getName();

  /**
   * Defines the new description of the verifier. If not specified, the description remains
   * unchanged.
   *
   * <p><strong>Note: </strong>Passing the empty string unsets the description.
   */
  public abstract Optional<String> getDescription();

  /**
   * Defines the {@code Timestamp} to be used for the NoteDb commits of the update. If not
   * specified, the current {@code Timestamp} when creating the commit will be used.
   *
   * <p>If this {@code VerifierUpdate} is passed next to an {@link VerifierCreation} during a
   * verifier creation, this {@code Timestamp} is used for the NoteDb commits of the new verifier.
   * Hence, the {@link Verifier#getCreatedOn()} field will match this {@code Timestamp}.
   */
  public abstract Optional<Timestamp> getUpdatedOn();

  public static Builder builder() {
    return new AutoValue_VerifierUpdate.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** @see #getName() */
    public abstract Builder setName(String name);

    /** @see #getDescription() */
    public abstract Builder setDescription(String description);

    /** @see #getUpdatedOn() */
    public abstract Builder setUpdatedOn(Timestamp timestamp);

    public abstract VerifierUpdate build();
  }
}
