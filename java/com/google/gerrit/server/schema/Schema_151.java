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

package com.google.gerrit.server.schema;

import com.google.common.collect.Streams;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit.Key;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** A schema which adds the 'created on' field to groups. */
public class Schema_151 extends SchemaVersion {
  @Inject
  protected Schema_151(Provider<Schema_150> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    List<AccountGroup> accountGroups = db.accountGroups().all().toList();
    for (AccountGroup accountGroup : accountGroups) {
      ResultSet<AccountGroupMemberAudit> groupMemberAudits =
          db.accountGroupMembersAudit().byGroup(accountGroup.getId());
      Optional<Timestamp> firstTimeMentioned =
          Streams.stream(groupMemberAudits)
              .map(AccountGroupMemberAudit::getKey)
              .map(Key::getAddedOn)
              .min(Comparator.naturalOrder());
      Timestamp createdOn =
          firstTimeMentioned.orElseGet(() -> AccountGroup.auditCreationInstantTs());

      accountGroup.setCreatedOn(createdOn);
    }
    db.accountGroups().update(accountGroups);
  }
}
