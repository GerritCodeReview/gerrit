// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.index.account;

import com.google.common.base.Stopwatch;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.SiteIndexer;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllAccountsIndexer
    extends SiteIndexer<Account.Id, AccountState, AccountIndex> {
  @SuppressWarnings("unused")
  private static final Logger log =
      LoggerFactory.getLogger(AllAccountsIndexer.class);

  @SuppressWarnings("unused")
  private final SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  AllAccountsIndexer(SchemaFactory<ReviewDb> schemaFactory) {
    this.schemaFactory = schemaFactory;
  }

  @Override
  public Result indexAll(AccountIndex index) {
    ProgressMonitor pm = new TextProgressMonitor();
    pm.beginTask("Reindexing accounts",
        ProgressMonitor.UNKNOWN);
    Stopwatch sw = Stopwatch.createStarted();
    // TODO(davido): provide the implementation
    return new Result(sw, true, 0, 0);
  }
}
