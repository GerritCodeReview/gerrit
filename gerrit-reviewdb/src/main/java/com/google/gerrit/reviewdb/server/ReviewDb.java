// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.reviewdb.server;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.Relation;
import com.google.gwtorm.server.Schema;
import com.google.gwtorm.server.Sequence;

/**
 * The review service database schema.
 *
 * <p>Root entities that are at the top level of some important data graph:
 *
 * <ul>
 *   <li>{@link Account}: Per-user account registration, preferences, identity.
 *   <li>{@link Change}: All review information about a single proposed change.
 *   <li>{@link SystemConfig}: Server-wide settings, managed by administrator.
 * </ul>
 */
public interface ReviewDb extends Schema {
  /* If you change anything, update SchemaVersion.C to use a new version. */

  @Relation(id = 1)
  SchemaVersionAccess schemaVersion();

  @Relation(id = 2)
  SystemConfigAccess systemConfig();

  // Deleted @Relation(id = 3)

  // Deleted @Relation(id = 4)

  @Relation(id = 6)
  AccountAccess accounts();

  @Relation(id = 7)
  AccountExternalIdAccess accountExternalIds();

  // Deleted @Relation(id = 8)

  @Relation(id = 10)
  AccountGroupAccess accountGroups();

  @Relation(id = 11)
  AccountGroupNameAccess accountGroupNames();

  @Relation(id = 12)
  AccountGroupMemberAccess accountGroupMembers();

  @Relation(id = 13)
  AccountGroupMemberAuditAccess accountGroupMembersAudit();

  // Deleted @Relation(id = 17)

  // Deleted @Relation(id = 18)

  @Relation(id = 19)
  AccountProjectWatchAccess accountProjectWatches();

  // Deleted @Relation(id = 20)

  @Relation(id = 21)
  ChangeAccess changes();

  @Relation(id = 22)
  PatchSetApprovalAccess patchSetApprovals();

  @Relation(id = 23)
  ChangeMessageAccess changeMessages();

  @Relation(id = 24)
  PatchSetAccess patchSets();

  // Deleted @Relation(id = 25)

  @Relation(id = 26)
  PatchLineCommentAccess patchComments();

  // Deleted @Relation(id = 28)

  @Relation(id = 29)
  AccountGroupByIdAccess accountGroupById();

  @Relation(id = 30)
  AccountGroupByIdAudAccess accountGroupByIdAud();

  /** Create the next unique id for an {@link Account}. */
  @Sequence(startWith = 1000000)
  int nextAccountId() throws OrmException;

  /** Next unique id for a {@link AccountGroup}. */
  @Sequence
  int nextAccountGroupId() throws OrmException;

  int FIRST_CHANGE_ID = 1;

  /**
   * Next unique id for a {@link Change}.
   *
   * @deprecated use {@link com.google.gerrit.server.Sequences#nextChangeId()}.
   */
  @Sequence(startWith = FIRST_CHANGE_ID)
  @Deprecated
  int nextChangeId() throws OrmException;

  /**
   * Next id for a block of {@link ChangeMessage} records.
   *
   * @see com.google.gerrit.server.ChangeUtil#messageUUID(ReviewDb)
   */
  @Sequence
  int nextChangeMessageId() throws OrmException;
}
