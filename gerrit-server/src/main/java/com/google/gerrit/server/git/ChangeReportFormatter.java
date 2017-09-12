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

package com.google.gerrit.server.git;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;

public interface ChangeReportFormatter {
  @AutoValue
  abstract class Input {
    public abstract Change change();

    @Nullable
    public abstract String subject();

    @Nullable
    public abstract Boolean draft();

    @Nullable
    public abstract Boolean edit();

    @Nullable
    public abstract Boolean _private();

    @Nullable
    public abstract Boolean wip();

    public static Builder builder() {
      return new AutoValue_ChangeReportFormatter_Input.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setChange(Change val);

      public abstract Builder setSubject(String val);

      public abstract Builder setDraft(Boolean val);

      public abstract Builder setEdit(Boolean val);

      public abstract Builder set_private(Boolean val);

      public abstract Builder setWip(Boolean val);

      abstract Input autoBuild();

      public Input build() {
        Input input = autoBuild();
        setSubject(input.subject() == null ? input.change().getSubject() : input.subject());
        setDraft(
            input.draft() == null
                ? Change.Status.DRAFT == input.change().getStatus()
                : input.draft());
        setEdit(input.edit() == null ? false : input.edit());
        set_private(input._private() == null ? input.change().isPrivate() : input._private());
        setWip(input.wip() == null ? input.change().isWorkInProgress() : input.wip());
        return autoBuild();
      }
    }
  }

  String newChange(Input input);

  String changeUpdated(Input input);

  String changeClosed(Input input);
}
