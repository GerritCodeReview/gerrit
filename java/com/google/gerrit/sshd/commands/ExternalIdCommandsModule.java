// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.server.account.externalids.OnlineExternalIdCaseSensivityMigratiorExecutor;
import com.google.gerrit.server.account.externalids.storage.notedb.OnlineExternalIdCaseSensivityMigrator;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.sshd.CommandModule;
import com.google.gerrit.sshd.Commands;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutorService;

public class ExternalIdCommandsModule extends CommandModule {
  public ExternalIdCommandsModule() {
    super(/* slaveMode= */ false);
  }

  @Override
  protected void configure() {
    bind(OnlineExternalIdCaseSensivityMigrator.class);
    command(Commands.named("gerrit"), ExternalIdCaseSensitivityMigrationCommand.class);
  }

  @Provides
  @Singleton
  @OnlineExternalIdCaseSensivityMigratiorExecutor
  public ExecutorService OnlineExternalIdCaseSensivityMigratiorExecutor(WorkQueue queues) {
    return queues.createQueue(1, "MigrateExternalIdCase", true);
  }
}
