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

package com.google.gerrit.server.mail.send;

import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import java.util.Collection;

/**
 * Interface used by {@link ChangeEmail} subclasses that may be sent in response to adding new
 * reviewers to a change.
 */
public interface ReviewerSender {
  void addReviewers(Collection<Account.Id> cc);

  void addReviewersByEmail(Collection<Address> cc);

  void addExtraCC(Collection<Account.Id> cc);

  void addExtraCCByEmail(Collection<Address> cc);
}
