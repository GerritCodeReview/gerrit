// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.git;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;

public interface ChangeReportFormatter {
  @AutoValue
  public abstract static class Input {
    public abstract Change change();

    @Nullable
    public abstract String subject();

    @Nullable
    public abstract Boolean isEdit();

    @Nullable
    public abstract Boolean isPrivate();

    @Nullable
    public abstract Boolean isWorkInProgress();

    public static Builder builder() {
      return new AutoValue_ChangeReportFormatter_Input.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setChange(Change val);

      public abstract Builder setSubject(String val);

      public abstract Builder setIsEdit(Boolean val);

      public abstract Builder setIsPrivate(Boolean val);

      public abstract Builder setIsWorkInProgress(Boolean val);

      abstract Change change();

      abstract String subject();

      abstract Boolean isEdit();

      abstract Boolean isPrivate();

      abstract Boolean isWorkInProgress();

      abstract Input autoBuild();

      public Input build() {
        setChange(change());
        setSubject(subject() == null ? change().getSubject() : subject());
        setIsEdit(isEdit() == null ? false : isEdit());
        setIsPrivate(isPrivate() == null ? change().isPrivate() : isPrivate());
        setIsWorkInProgress(
            isWorkInProgress() == null ? change().isWorkInProgress() : isWorkInProgress());
        return autoBuild();
      }
    }
  }

  String newChange(Input input);

  String changeUpdated(Input input);

  String changeClosed(Input input);
}
