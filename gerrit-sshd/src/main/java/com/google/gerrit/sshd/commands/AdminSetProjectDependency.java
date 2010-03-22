// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetAncestor;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;

import java.io.PrintWriter;
import java.util.Collections;

/** Create a new dependency. **/
@AdminCommand
final class AdminSetProjectDependency extends BaseCommand {
  @Option(name = "--from", required = true, aliases = {"-f"}, metaVar = "FROM", usage = "change_id which will depends on TO change_id")
  private String changeIdFrom;

  @Option(name = "--to", aliases = {"-t"}, usage = "change_id which will be dependent on FROM change_id")
  private String changeIdTo;

  @Option(name = "--owner", aliases = {"-o"}, usage = "owner of project\n"
    + "(default: Administrators)")
  private AccountGroup.Id ownerId;


  @Inject
  private ReviewDb db;


  @Inject
  private AuthConfig authConfig;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        PrintWriter p = toPrintWriter(out);

        ownerId = authConfig.getAdministratorsGroup();
        parseCommandLine();


        final Change changeFrom = db.changes().get(new Change.Id(Integer.parseInt(changeIdFrom)));
        final Change changeTo = db.changes().get(new Change.Id(Integer.parseInt(changeIdTo)));

        final PatchSet psetFrom = db.patchSets().get(changeFrom.currentPatchSetId());
        final PatchSet psetTo = db.patchSets().get(changeTo.currentPatchSetId());

        final int pos = db.patchSetAncestors().ancestorsOf(psetFrom.getId()).toList().size();

        final PatchSetAncestor.Id psaId = new PatchSetAncestor.Id(psetFrom.getId(), pos + 1);

        final PatchSetAncestor psa = new PatchSetAncestor(psaId);

        psa.setAncestorRevision(psetTo.getRevision());

        db.patchSetAncestors().insert(Collections.singleton(psa));

      }
    });


  }




}
