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
package com.google.gerrit.sshd.commands;

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER;

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "activate-index",
                 description = "Activate the latest index version available",
                 runsAt = MASTER)
public class ActivateIndex extends SshCommand {
  private static final Logger log = LoggerFactory.getLogger(ActivateIndex.class);

  @Inject
  private IndexCollection indexes;

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    ChangeIndex indexToActivate = findIndexToActivate();
    if (indexToActivate == null) {
      stdout.println("Not activating index, already using latest version");
      return;
    }

    indexes.setSearchIndex(indexToActivate);
    log.info("Using index schema version {}", version(indexToActivate));
    try {
      indexToActivate.markReady(true);
    } catch (IOException e) {
      log.warn("Error activating latest index schema version {}",
          version(indexToActivate), e);
      stderr.println("Error activating latest index: " + e.getMessage());
    }

    List<ChangeIndex> toRemove = Lists.newArrayListWithExpectedSize(1);
    for (ChangeIndex i : indexes.getWriteIndexes()) {
      if (version(i) != version(indexToActivate)) {
        toRemove.add(i);
      }
    }
    for (ChangeIndex i : toRemove) {
      try {
        i.markReady(false);
        indexes.removeWriteIndex(version(i));
      } catch (IOException e) {
        log.warn("Error deactivating old index schema version {}", version(i),
            e);
        stderr.println("Error deactivating old index: " + e.getMessage());
      }
    }
  }

  private ChangeIndex findIndexToActivate() {
    int versionToActivate = -1;
    int currentIndexVersion = version(indexes.getSearchIndex());
    for (ChangeIndex i : indexes.getWriteIndexes()) {
      int version = version(i);
      if (version > currentIndexVersion && version > versionToActivate) {
        versionToActivate = version;
      }
    }
    return indexes.getWriteIndex(versionToActivate);
  }

  private int version(ChangeIndex index) {
    return index.getSchema().getVersion();
  }
}
