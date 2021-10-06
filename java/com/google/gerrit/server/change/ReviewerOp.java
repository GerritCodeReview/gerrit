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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import java.io.IOException;
import java.util.Optional;

public class ReviewerOp implements BatchUpdateOp {
  protected boolean sendEmail = true;
  protected boolean sendEvent = true;
  protected Runnable eventSender = () -> {};
  protected PatchSet patchSet;
  protected Result opResult;

  // TODO(dborowitz): This mutable setter is ugly, but a) it's less ugly than adding boolean args
  // all the way through the constructor stack, and b) this class is slated to be completely
  // rewritten.
  public void suppressEmail() {
    this.sendEmail = false;
  }

  public void suppressEvent() {
    this.sendEvent = false;
  }

  public void sendEvent() {
    eventSender.run();
  }

  void setPatchSet(PatchSet patchSet) {
    this.patchSet = requireNonNull(patchSet);
  }

  @AutoValue
  public abstract static class Result {
    public abstract ImmutableList<PatchSetApproval> addedReviewers();

    public abstract ImmutableList<Address> addedReviewersByEmail();

    public abstract ImmutableList<Account.Id> addedCCs();

    public abstract ImmutableList<Address> addedCCsByEmail();

    public abstract Optional<Account.Id> deletedReviewer();

    public abstract Optional<Address> deletedReviewerByEmail();

    static Builder builder() {
      return new AutoValue_ReviewerOp_Result.Builder()
          .setAddedReviewers(ImmutableList.of())
          .setAddedReviewersByEmail(ImmutableList.of())
          .setAddedCCs(ImmutableList.of())
          .setAddedCCsByEmail(ImmutableList.of());
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setAddedReviewers(Iterable<PatchSetApproval> addedReviewers);

      abstract Builder setAddedReviewersByEmail(Iterable<Address> addedReviewersByEmail);

      abstract Builder setAddedCCs(Iterable<Account.Id> addedCCs);

      abstract Builder setAddedCCsByEmail(Iterable<Address> addedCCsByEmail);

      abstract Builder setDeletedReviewerByEmail(Address deletedReviewerByEmail);

      abstract Builder setDeletedReviewer(Account.Id deletedReviewer);

      abstract Result build();
    }
  }

  public Result getResult() {
    checkState(opResult != null, "Batch update wasn't executed yet");
    return opResult;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws RestApiException, IOException, PermissionBackendException {
    return false;
  }
}
