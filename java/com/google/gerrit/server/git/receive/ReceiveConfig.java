// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.git.receive;

import static com.google.gerrit.common.data.GlobalCapability.BATCH_CHANGES_LIMIT;

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountLimits;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
class ReceiveConfig {
  final boolean checkMagicRefs;
  final boolean checkReferencedObjectsAreReachable;
  final int maxBatchCommits;
  final boolean disablePrivateChanges;
  private final int systemMaxBatchChanges;
  private final AccountLimits.Factory limitsFactory;

  @Inject
  ReceiveConfig(@GerritServerConfig Config config, AccountLimits.Factory limitsFactory) {
    checkMagicRefs = config.getBoolean("receive", null, "checkMagicRefs", true);
    checkReferencedObjectsAreReachable =
        config.getBoolean("receive", null, "checkReferencedObjectsAreReachable", true);
    maxBatchCommits = config.getInt("receive", null, "maxBatchCommits", 10000);
    systemMaxBatchChanges = config.getInt("receive", "maxBatchChanges", 0);
    disablePrivateChanges = config.getBoolean("change", null, "disablePrivateChanges", false);
    this.limitsFactory = limitsFactory;
  }

  public int getEffectiveMaxBatchChangesLimit(CurrentUser user) {
    AccountLimits limits = limitsFactory.create(user);
    if (limits.hasExplicitRange(BATCH_CHANGES_LIMIT)) {
      return limits.getRange(BATCH_CHANGES_LIMIT).getMax();
    }
    return systemMaxBatchChanges;
  }
}
