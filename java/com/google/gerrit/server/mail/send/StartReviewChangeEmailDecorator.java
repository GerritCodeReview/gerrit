// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.server.mail.send.ChangeEmail.ChangeEmailDecorator;
import java.util.Collection;

/** Sends an email alerting a user to a new change for them to review. */
public interface StartReviewChangeEmailDecorator extends ChangeEmailDecorator {

  /** Add initial set of reviewers. */
  void addReviewers(Collection<Account.Id> cc);

  /** Add initial set of reviewers by email (non-account). */
  void addReviewersByEmail(Collection<Address> cc);

  /** Add initial set of cc-ed accounts. */
  void addExtraCC(Collection<Account.Id> cc);

  /** Add initial set of cc-ed emails. */
  void addExtraCCByEmail(Collection<Address> cc);

  /** Set of reviewers that are removed when sending for review. */
  void addRemovedReviewers(Collection<Account.Id> removed);

  /** Set of reviewers by email (non-account) that are removed when sending for review. */
  void addRemovedByEmailReviewers(Collection<Address> removed);

  /** Mark the email as the first in the thread of emails about this change. */
  void markAsCreateChange();
}
