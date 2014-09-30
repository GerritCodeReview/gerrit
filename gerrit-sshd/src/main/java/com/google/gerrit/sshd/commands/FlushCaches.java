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

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.server.config.PostCaches.Operation.FLUSH;
import static com.google.gerrit.server.config.PostCaches.Operation.FLUSH_ALL;
import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.ListCaches;
import com.google.gerrit.server.config.ListCaches.OutputFormat;
import com.google.gerrit.server.config.PostCaches;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;

/** Causes the caches to purge all entries and reload. */
@RequiresCapability(GlobalCapability.FLUSH_CACHES)
@CommandMetaData(name = "flush-caches", description = "Flush some/all server caches from memory",
  runsAt = MASTER_OR_SLAVE)
final class FlushCaches extends SshCommand {
  @Option(name = "--cache", usage = "flush named cache", metaVar = "NAME")
  private List<String> caches = new ArrayList<>();

  @Option(name = "--all", usage = "flush all caches")
  private boolean all;

  @Option(name = "--list", usage = "list available caches")
  private boolean list;

  @Inject
  private Provider<ListCaches> listCaches;

  @Inject
  private PostCaches postCaches;

  @Override
  protected void run() throws Failure {
    try {
      if (list) {
        if (all || caches.size() > 0) {
          throw error("error: cannot use --list with --all or --cache");
        }
        doList();
        return;
      }

      if (all && caches.size() > 0) {
        throw error("error: cannot combine --all and --cache");
      } else if (!all && caches.size() == 1 && caches.contains("all")) {
        caches.clear();
        all = true;
      } else if (!all && caches.isEmpty()) {
        all = true;
      }

      if (all) {
        postCaches.apply(new ConfigResource(),
            new PostCaches.Input(FLUSH_ALL));
      } else {
        postCaches.apply(new ConfigResource(),
            new PostCaches.Input(FLUSH, caches));
      }
    } catch (RestApiException e) {
      throw die(e.getMessage());
    }
  }

  private static UnloggedFailure error(String msg) {
    return new UnloggedFailure(1, msg);
  }

  @SuppressWarnings("unchecked")
  private void doList() {
    for (String name : (List<String>) listCaches.get()
        .setFormat(OutputFormat.LIST).apply(new ConfigResource())) {
      stderr.print(name);
      stderr.print('\n');
    }
    stderr.flush();
  }
}
