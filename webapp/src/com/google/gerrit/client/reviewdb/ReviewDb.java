// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Relation;
import com.google.gwtorm.client.Schema;
import com.google.gwtorm.client.Sequence;

/** The review service database schema. */
public interface ReviewDb extends Schema {
  @Relation
  SystemConfigAccess systemConfig();

  @Relation
  ContributorAgreementAccess contributorAgreements();

  @Relation
  AccountAccess accounts();

  @Relation
  AccountAgreementAccess accountAgreements();

  @Relation
  AccountGroupAccess accountGroups();

  @Relation
  AccountGroupMemberAccess accountGroupMembers();

  /** Create the next unique id for an {@link Account}. */
  @Sequence(startWith = 1000000)
  int nextAccountId();

  /** Create the next unique id for a {@link ContributorAgreement}. */
  @Sequence
  int nextContributorAgreementId();

  /** Next unique id for a {@link AccountGroup}. */
  @Sequence
  int nextAccountGroupId();
}
