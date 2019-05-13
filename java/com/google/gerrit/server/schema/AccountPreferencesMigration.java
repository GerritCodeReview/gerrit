// Copyright (C) 2019 The Android Open Source Project
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

import static com.google.gerrit.reviewdb.client.RefNames.REFS_USERS;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.schema.NoteDbSchemaVersion.Arguments;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public class AccountPreferencesMigration {
  @FunctionalInterface
  interface ConfigUpdate {
    void update(Config config);
  }

  public static void migrate(String message, Arguments args, ConfigUpdate update)
      throws IOException, ConfigInvalidException {
    try (Repository repo = args.repoManager.openRepository(args.allUsers);
        RevWalk rw = new RevWalk(repo)) {
      BatchRefUpdate batchUpdate = repo.getRefDatabase().newBatchUpdate();
      for (String userRef :
          repo.getRefDatabase().getRefsByPrefix(REFS_USERS).stream()
              .map(Ref::getName)
              .collect(toList())) {
        try (MetaDataUpdate md =
            new MetaDataUpdate(GitReferenceUpdated.DISABLED, args.allUsers, repo, batchUpdate)) {
          md.setMessage(message);
          md.getCommitBuilder().setAuthor(args.serverIdentProvider.get());
          md.getCommitBuilder().setCommitter(args.serverIdentProvider.get());
          VersionedAccountPreferences prefs = new VersionedAccountPreferences(userRef);
          prefs.load(md);
          Config config = prefs.getConfig();
          if (config != null) {
            update.update(config);
            prefs.commit(md);
          }
        }
      }
      batchUpdate.execute(rw, NullProgressMonitor.INSTANCE);
    }
  }
}
