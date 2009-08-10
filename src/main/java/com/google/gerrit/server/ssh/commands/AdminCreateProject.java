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

package com.google.gerrit.server.ssh.commands;

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.Project.SubmitType;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.git.ReplicationQueue;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.ssh.AdminCommand;
import com.google.gerrit.server.ssh.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;
import org.spearce.jgit.lib.Repository;

import java.io.PrintWriter;
import java.util.Collections;

/** Create a new project. **/
@AdminCommand
final class AdminCreateProject extends BaseCommand {
  @Option(name = "--name", required = true, aliases = { "-n" }, usage = "name of project to be created")
  private String projectName;

  @Option(name = "--owner", aliases = { "-o" }, usage = "name of group that will own the project (defaults to: Administrators)")
  private String ownerName;

  @Option(name = "--description", aliases = { "-d" }, usage = "description of the project")
  private String projectDescription;

  @Option(name = "--submit-type", aliases = { "-t" }, usage = "project submit type (F)ast forward only, (M)erge if necessary, merge (A)lways or (C)herry pick (defaults to: F)")
  private String submitTypeStr;

  @Option(name = "--use-contributor-agreements", aliases = { "--ca" }, usage = "set this to true if project should make the user sign a contributor agreement   (defaults to: N)")
  private String useContributorAgreements;

  @Option(name = "--use-signed-off-by", aliases = { "--so" }, usage = "set this to true if the project should mandate signed-off-by (defaults to: N)")
  private String useSignedOffBy;

  @Inject
  private ReviewDb db;

  @Inject
  private GerritServer gs;

  @Inject
  private GroupCache groupCache;

  @Inject
  private ReplicationQueue rq;

  private AccountGroup.Id ownerId = null;
  private boolean contributorAgreements = false;
  private boolean signedOffBy = false;
  private SubmitType submitType = null;

  @Override
  public void start() {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        PrintWriter p = toPrintWriter(out);

        parseCommandLine();

        try {
          validateParameters();

          Transaction txn = db.beginTransaction();

          createProject(txn);

          Repository repo  = gs.createRepository(projectName);
          repo.create(true);

          txn.commit();

          rq.replicateNewProject(new Project.NameKey(projectName));
        } catch (Exception e) {
          p.print("Error when trying to create project: "
              + e.getMessage() + "\n");
          p.flush();
        }

      }
    });
  }


  private void createProject(Transaction txn) throws OrmException,
  NoSuchEntityException {
    final Project.NameKey newProjectNameKey =
      new Project.NameKey(projectName);

    final Project newProject =
      new Project(newProjectNameKey,
      new Project.Id(db.nextProjectId()));

    newProject.setDescription(projectDescription);
    newProject.setSubmitType(submitType);
    newProject.setUseContributorAgreements(contributorAgreements);
    newProject.setUseSignedOffBy(signedOffBy);

    db.projects().insert(Collections.singleton(newProject), txn);

    final ProjectRight.Key prk =
      new ProjectRight.Key(newProjectNameKey,
          ApprovalCategory.OWN, ownerId);
    final ProjectRight pr = new ProjectRight(prk);
    pr.setMaxValue((short) 1);
    pr.setMinValue((short) 1);
    db.projectRights().insert(Collections.singleton(pr), txn);

    final Branch newBranch =
      new Branch(
          new Branch.NameKey(newProjectNameKey, Branch.R_HEADS + "master"));

    db.branches().insert(Collections.singleton(newBranch), txn);
  }

  private boolean stringToBoolean(final String boolStr,
      final boolean defaultValue) throws Failure {
    if (boolStr == null) {
      return defaultValue;
    }

    if (boolStr.equalsIgnoreCase("FALSE")
        || boolStr.equalsIgnoreCase("F")
        || boolStr.equalsIgnoreCase("NO")
        || boolStr.equalsIgnoreCase("N")) {
      return false;
    }

    if (boolStr.equalsIgnoreCase("TRUE")
        || boolStr.equalsIgnoreCase("T")
        || boolStr.equalsIgnoreCase("YES")
        || boolStr.equalsIgnoreCase("Y")) {
       return true;
     }

    throw new Failure(1, "Parameter must have boolean value (true, false)");
  }

  private void validateParameters() throws Failure, OrmException {
    if (projectName.endsWith(".git")) {
      projectName = projectName.substring(0,
          projectName.length() - ".git".length());
    }

    if (ownerName == null) {
      ownerId = groupCache.getAdministrators();
    } else {
      AccountGroup ownerGroup = groupCache.lookup(ownerName);
      if (ownerGroup == null)  {
        throw new Failure(1, "Specified group does not exist");
      }
      ownerId = ownerGroup.getId();
    }

    if (projectDescription == null) {
      projectDescription = "";
    }

    contributorAgreements = stringToBoolean(useContributorAgreements, false);
    signedOffBy = stringToBoolean(useSignedOffBy, false);

    if (submitTypeStr == null) {
      submitTypeStr = "fast-forward-only";
    }

    if (submitTypeStr.toLowerCase().equalsIgnoreCase("fast-forward-only")) {
      submitType = SubmitType.FAST_FORWARD_ONLY;
    } else if (submitTypeStr.toLowerCase().equalsIgnoreCase("merge-if-necessary")) {
      submitType = SubmitType.MERGE_IF_NECESSARY;
    } else if (submitTypeStr.toLowerCase().equalsIgnoreCase("merge-always")) {
      submitType = SubmitType.MERGE_ALWAYS;
    } else if (submitTypeStr.toLowerCase().equalsIgnoreCase("cherry-pick")) {
      submitType = SubmitType.CHERRY_PICK;
    }

    if (submitType == null) {
      throw new Failure(1, "Submit type must be either: fast-forward-only, "
          + "merge-if-necessary, merge-always or cherry-pick");
    }
  }
}

