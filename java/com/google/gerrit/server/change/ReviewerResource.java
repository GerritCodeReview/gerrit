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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.mail.Address;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

public class ReviewerResource implements RestResource {
  public static final TypeLiteral<RestView<ReviewerResource>> REVIEWER_KIND =
      new TypeLiteral<RestView<ReviewerResource>>() {};

  public interface Factory {
    ReviewerResource create(ChangeResource change, Account.Id id);

    ReviewerResource create(RevisionResource revision, Account.Id id);
  }

  private final ChangeResource change;
  private final RevisionResource revision;
  @Nullable private final IdentifiedUser user;
  @Nullable private final Address address;

  @AssistedInject
  ReviewerResource(
      IdentifiedUser.GenericFactory userFactory,
      @Assisted ChangeResource change,
      @Assisted Account.Id id) {
    this.change = change;
    this.user = userFactory.create(id);
    this.revision = null;
    this.address = null;
  }

  @AssistedInject
  ReviewerResource(
      IdentifiedUser.GenericFactory userFactory,
      @Assisted RevisionResource revision,
      @Assisted Account.Id id) {
    this.revision = revision;
    this.change = revision.getChangeResource();
    this.user = userFactory.create(id);
    this.address = null;
  }

  public ReviewerResource(ChangeResource change, Address address) {
    this.change = change;
    this.address = address;
    this.revision = null;
    this.user = null;
  }

  public ReviewerResource(RevisionResource revision, Address address) {
    this.revision = revision;
    this.change = revision.getChangeResource();
    this.address = address;
    this.user = null;
  }

  public ChangeResource getChangeResource() {
    return change;
  }

  public RevisionResource getRevisionResource() {
    return revision;
  }

  public Change.Id getChangeId() {
    return change.getId();
  }

  public Change getChange() {
    return change.getChange();
  }

  public IdentifiedUser getReviewerUser() {
    checkArgument(user != null, "no user provided");
    return user;
  }

  public Address getReviewerByEmail() {
    checkArgument(address != null, "no address provided");
    return address;
  }

  /**
   * Check if this resource was constructed by email or by {@code Account.Id}.
   *
   * @return true if the resource was constructed by providing an {@code Address}; false if the
   *     resource was constructed by providing an {@code Account.Id}.
   */
  public boolean isByEmail() {
    return user == null;
  }
}
