// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import com.google.auto.value.AutoValue;

/** The difference between two {@link ChangeInfo}s returned by {@link ChangeInfoDiffer}. */
@AutoValue
public abstract class ChangeInfoDifference {

  public abstract ChangeInfo oldChangeInfo();

  public abstract ChangeInfo newChangeInfo();

  public abstract ChangeInfo added();

  public abstract ChangeInfo removed();

  public static Builder builder() {
    return new AutoValue_ChangeInfoDifference.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setOldChangeInfo(ChangeInfo oldChangeInfo);

    public abstract Builder setNewChangeInfo(ChangeInfo newChangeInfo);

    public abstract Builder setAdded(ChangeInfo added);

    public abstract Builder setRemoved(ChangeInfo removed);

    public abstract ChangeInfoDifference build();
  }
}
