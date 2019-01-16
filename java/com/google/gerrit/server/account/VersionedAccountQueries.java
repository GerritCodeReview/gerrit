// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import org.eclipse.jgit.lib.CommitBuilder;

/** Named Queries for user accounts. */
public class VersionedAccountQueries extends VersionedMetaData {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static VersionedAccountQueries forUser(Account.Id id) {
    return new VersionedAccountQueries(RefNames.refsUsers(id));
  }

  private final String ref;
  private QueryList queryList;

  private VersionedAccountQueries(String ref) {
    this.ref = ref;
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  public QueryList getQueryList() {
    return queryList;
  }

  @Override
  protected void onLoad() {
    queryList =
        QueryList.parse(
            readUTF8(QueryList.FILE_NAME),
            error ->
                logger.atSevere().log(
                    "Error parsing file %s: %s", QueryList.FILE_NAME, error.getMessage()));
  }

  @Override
  protected boolean onSave(CommitBuilder commit) {
    throw new UnsupportedOperationException("Cannot yet save named queries");
  }
}
