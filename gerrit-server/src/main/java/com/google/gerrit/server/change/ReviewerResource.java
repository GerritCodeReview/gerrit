// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

public class ReviewerResource implements RestResource {
  public static final TypeLiteral<RestView<ReviewerResource>> REVIEWER_KIND =
      new TypeLiteral<RestView<ReviewerResource>>() {};

  public interface Factory {
    ReviewerResource create(ChangeResource change, Account.Id id);
  }

  private final ChangeResource change;
  private final IdentifiedUser user;

  @AssistedInject
  ReviewerResource(
      IdentifiedUser.GenericFactory userFactory,
      @Assisted ChangeResource change,
      @Assisted Account.Id id) {
    this.change = change;
    this.user = userFactory.create(id);
  }

  public ChangeResource getChangeResource() {
    return change;
  }

  public Change.Id getChangeId() {
    return change.getId();
  }

  public Change getChange() {
    return change.getChange();
  }

  public IdentifiedUser getReviewerUser() {
    return user;
  }

  /**
   * @return the control for the caller's user (as opposed to the reviewer's user as returned by
   *     {@link #getReviewerControl()}).
   */
  public ChangeControl getControl() {
    return change.getControl();
  }

  /**
   * @return the control for the reviewer's user (as opposed to the caller's user as returned by
   *     {@link #getControl()}).
   */
  public ChangeControl getReviewerControl() {
    return change.getControl().forUser(user);
  }
}
