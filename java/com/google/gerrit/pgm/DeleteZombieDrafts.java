// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.pgm;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdCacheImpl;
import com.google.gerrit.server.account.storage.notedb.AccountNoteDbReadStorageModule;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.WorkQueue.WorkQueueModule;
import com.google.gerrit.server.index.options.AutoFlush;
import com.google.gerrit.server.notedb.DeleteZombieCommentsRefs;
import com.google.gerrit.server.notedb.NoteDbDraftCommentsModule;
import com.google.gerrit.server.notedb.NoteDbStarredChangesModule;
import com.google.inject.Injector;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.io.IOException;
import org.kohsuke.args4j.Option;

/**
 * A pgm which can be used to clean zombie draft comments refs More context in
 * https://gerrit-review.googlesource.com/c/gerrit/+/246233
 *
 * <p>The implementation is in {@link DeleteZombieCommentsRefs}
 */
public class DeleteZombieDrafts extends SiteProgram {
  @Option(
      name = "--cleanup-percentage",
      aliases = {"-c"},
      usage = "Clean a % of zombie drafts (default is 100%)")
  private Integer cleanupPercentage = 100;

  @Override
  public int run() throws IOException {
    mustHaveValidSite();
    Injector sysInjector = createSysInjector();
    DeleteZombieCommentsRefs cleanup =
        sysInjector.getInstance(DeleteZombieCommentsRefs.Factory.class).create(cleanupPercentage);
    cleanup.execute();
    return 0;
  }

  private Injector createSysInjector() {
    return createDbInjector()
        .createChildInjector(
            new WorkQueueModule(),
            LuceneIndexModule.latestVersion(false, AutoFlush.ENABLED),
            new BatchProgramModule(createDbInjector(), ImmutableSet.of()),
            new AccountNoteDbReadStorageModule(),
            new ExternalIdCacheImpl.ExternalIdCacheModule(),
            new ExternalIdCacheImpl.ExternalIdCacheBindingModule(),
            new NoteDbDraftCommentsModule(),
            new NoteDbStarredChangesModule(),
            new FactoryModuleBuilder().build(ChangeResource.Factory.class),
            new FactoryModuleBuilder().build(DeleteZombieCommentsRefs.Factory.class));
  }
}
