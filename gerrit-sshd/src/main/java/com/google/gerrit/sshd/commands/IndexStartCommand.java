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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.lucene.OnlineReindexer;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "start", description = "Start the online indexer",
  runsAt = MASTER)
public class IndexStartCommand extends SshCommand {

  @Inject
  private IndexCollection indexes;

  @Inject
  private OnlineReindexer.Factory reindexerFactory;

  @Override
  protected void run() {
    int versionToReindex = -1;
    int currentIndexVersion = indexes.getSearchIndex().getSchema().getVersion();
    for (ChangeIndex i : indexes.getWriteIndexes()) {
      int version = i.getSchema().getVersion();
      if (version > currentIndexVersion && version > versionToReindex) {
        versionToReindex = version;
      }
    }
    if (versionToReindex != -1) {
      reindexerFactory.create(versionToReindex).start();
      stdout.println("Starting online reindex from schema version "
          + currentIndexVersion + " to " + versionToReindex);
    } else {
      stdout.println("Nothing to reindex: version " + currentIndexVersion
          + " is the latest");
    }
  }
}
