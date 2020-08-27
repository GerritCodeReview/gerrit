// Copyright (C) 2020 The Android Open Source Project
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
package com.google.gerrit.extensions.restapi.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;

/** {@link Subject} for doing assertions on {@link AttentionSetUpdate}s. */
public class AttentionSetUpdateSubject extends Subject {

  /**
   * Starts fluent chain to do assertions on a {@link AttentionSetUpdate}.
   *
   * @param attentionSetUpdate the {@link AttentionSetUpdate} on which assertions should be done
   * @return the created {@link AttentionSetUpdateSubject}
   */
  public static AttentionSetUpdateSubject assertThat(AttentionSetUpdate attentionSetUpdate) {
    return assertAbout(attentionSetUpdates()).that(attentionSetUpdate);
  }

  private static Factory<AttentionSetUpdateSubject, AttentionSetUpdate> attentionSetUpdates() {
    return AttentionSetUpdateSubject::new;
  }

  private final AttentionSetUpdate attentionSetUpdate;

  private AttentionSetUpdateSubject(
      FailureMetadata metadata, AttentionSetUpdate attentionSetUpdate) {
    super(metadata, attentionSetUpdate);
    this.attentionSetUpdate = attentionSetUpdate;
  }

  /**
   * Returns a {@link ComparableSubject} for the account ID of attention set update.
   *
   * @return {@link ComparableSubject} for the account ID of attention set update
   */
  public ComparableSubject<Account.Id> hasAccountIdThat() {
    return check("account()").that(attentionSetUpdate().account());
  }

  /**
   * Returns a {@link StringSubject} for the reason of attention set update.
   *
   * @return {@link StringSubject} for the reason of attention set update
   */
  public StringSubject hasReasonThat() {
    return check("reason()").that(attentionSetUpdate().reason());
  }

  /**
   * Returns a {@link ComparableSubject} for the {@link AttentionSetUpdate.Operation} of attention
   * set update.
   *
   * @return {@link ComparableSubject} for the {@link AttentionSetUpdate.Operation} of attention set
   *     update
   */
  public ComparableSubject<AttentionSetUpdate.Operation> hasOperationThat() {
    return check("operation()").that(attentionSetUpdate().operation());
  }

  private AttentionSetUpdate attentionSetUpdate() {
    isNotNull();
    return attentionSetUpdate;
  }
}
