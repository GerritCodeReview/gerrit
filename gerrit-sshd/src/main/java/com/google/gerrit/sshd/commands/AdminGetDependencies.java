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
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** Create a new dependency. **/
@AdminCommand
final class AdminGetDependencies extends BaseCommand {
  @Option(name = "--change", required = true, aliases = {"-ch"}, metaVar = "CHANGE", usage = "change_id which will get the dependencies")
  private String change;

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
        final PrintWriter p = toPrintWriter(out);

        ownerId = authConfig.getAdministratorsGroup();
        parseCommandLine();


        final Change ch = db.changes().get(new Change.Id(Integer.parseInt(change)));

        List <Change> deps = this.getDependsOn(ch);


        List <Change> neededs = this.getNeededBy(ch);

        StringBuffer strdeps = new StringBuffer();
        StringBuffer strnds = new StringBuffer();
        strdeps.append("Deps: ");
        strnds.append("Needed: ");

        for (Change c :deps)
          strdeps.append(c.getId().get()+"; ");


        for (Change c :neededs)
          strnds.append(c.getId().get()+"; ");


        p.print(strdeps);
        p.println();
        p.print(strnds);
        p.println();
        p.flush();

      }


      private List<Change> getDependsOn(Change change) throws OrmException{
        List<Change> dependsOn = new ArrayList<Change>();

        PatchSet pset = db.patchSets().get(change.currPatchSetId());
        List <PatchSetAncestor> psetAncList = db.patchSetAncestors().ancestorsOf(pset.getId()).toList();

        for (PatchSetAncestor psetAnc : psetAncList){
          String revId = psetAnc.getAncestorRevision().get();
          List<Change> changesPerRevIdList = db.changes().byKey(new Change.Key("I"+revId)).toList();

          for (Change c : changesPerRevIdList){
            if (c.getStatus() != Change.Status.MERGED)
              dependsOn.add(c);
          }
        }
        return dependsOn;
      }

      private List<Change> getNeededBy(Change change) throws OrmException{
        List<Change> neededBy = new ArrayList<Change>();

        String revId = change.getKey().get().substring(1);

        List <PatchSetAncestor> psetAncList = db.patchSetAncestors().descendantsOf(new RevId(revId)).toList();

        for (PatchSetAncestor psetAnc : psetAncList){
          Change.Id chId = psetAnc.getPatchSet().getParentKey();
          Change c = db.changes().get(chId);
          neededBy.add(c);
        }

        return neededBy;
      }


    });


  }




}
