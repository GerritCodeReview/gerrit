// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.update.BatchUpdateListener;
import com.google.gerrit.server.update.ChainedReceiveCommands;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Utility class for executing commands on a given repository. */
class NoteDbUpdateExecutor {
  private final Provider<PersonIdent> serverIdent;

  @Inject
  NoteDbUpdateExecutor(@GerritPersonIdent Provider<PersonIdent> serverIdent) {
    this.serverIdent = serverIdent;
  }

  Optional<BatchRefUpdate> execute(
      OpenRepo or,
      boolean dryrun,
      boolean maybeAllowNonFastForwards,
      ImmutableList<BatchUpdateListener> batchUpdateListeners,
      @Nullable PushCertificate pushCert,
      @Nullable PersonIdent refLogIdent,
      @Nullable String refLogMessage)
      throws IOException {
    if (or == null || or.cmds.isEmpty()) {
      return Optional.empty();
    }
    if (!dryrun) {
      or.flush();
    } else {
      // OpenRepo buffers objects separately; caller may assume that objects are available in the
      // inserter it previously passed via setChangeRepo.
      or.flushToFinalInserter();
    }

    BatchRefUpdate bru = or.repo.getRefDatabase().newBatchUpdate();
    bru.setPushCertificate(pushCert);
    if (refLogMessage != null) {
      bru.setRefLogMessage(refLogMessage, false);
    } else {
      bru.setRefLogMessage(
          firstNonNull(NoteDbUtil.guessRestApiHandler(), "Update NoteDb refs"), false);
    }
    bru.setRefLogIdent(refLogIdent != null ? refLogIdent : serverIdent.get());
    bru.setAtomic(true);
    or.cmds.addTo(bru);
    bru.setAllowNonFastForwards(maybeAllowNonFastForwards || allowNonFastForwards(or.cmds));
    for (BatchUpdateListener listener : batchUpdateListeners) {
      bru = listener.beforeUpdateRefs(bru);
    }

    if (!dryrun) {
      RefUpdateUtil.executeChecked(bru, or.rw);
    }
    return Optional.of(bru);
  }

  private boolean allowNonFastForwards(ChainedReceiveCommands receiveCommands) {
    return receiveCommands.getCommands().values().stream()
        .anyMatch(cmd -> cmd.getType().equals(ReceiveCommand.Type.UPDATE_NONFASTFORWARD));
  }
}
