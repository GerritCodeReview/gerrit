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
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.ProjectCreatorGroups;
import com.google.gerrit.server.config.ProjectOwnerGroups;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Create a new project. **/
final class CreateProject extends BaseCommand {
  @Option(name = "--name", required = true, aliases = {"-n"}, metaVar = "NAME", usage = "name of project to be created")
  private String projectName;

  @Option(name = "--owner", aliases = {"-o"}, usage = "owner(s) of project")
  private List<AccountGroup.Id> ownerIds;

  @Option(name = "--parent", aliases = {"-p"}, metaVar = "NAME", usage = "parent project")
  private ProjectControl newParent;

  @Option(name = "--description", aliases = {"-d"}, metaVar = "DESC", usage = "description of project")
  private String projectDescription = "";

  @Option(name = "--submit-type", aliases = {"-t"}, usage = "project submit type\n"
      + "(default: MERGE_IF_NECESSARY)")
  private SubmitType submitType = SubmitType.MERGE_IF_NECESSARY;

  @Option(name = "--use-contributor-agreements", aliases = {"--ca"}, usage = "if contributor agreement is required")
  private boolean contributorAgreements;

  @Option(name = "--use-signed-off-by", aliases = {"--so"}, usage = "if signed-off-by is required")
  private boolean signedOffBy;

  @Option(name = "--branch", aliases = {"-b"}, metaVar = "BRANCH", usage = "initial branch name\n"
      + "(default: master)")
  private String branch = Constants.MASTER;

  @Inject
  private ReviewDb db;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  @ProjectCreatorGroups
  private Set<AccountGroup.Id> projectCreatorGroups;

  @Inject
  @ProjectOwnerGroups
  private Set<AccountGroup.Id> projectOwnerGroups;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ReplicationQueue rq;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        PrintWriter p = toPrintWriter(out);

        parseCommandLine();

        try {
          validateParameters();

          Repository repo = repoManager.createRepository(projectName);
          repo.create(true);

          RefUpdate u = repo.updateRef(Constants.HEAD);
          u.disableRefLog();
          u.link(branch);

          repoManager.setProjectDescription(projectName, projectDescription);

          createProject();

          rq.replicateNewProject(new Project.NameKey(projectName), branch);
        } catch (Exception e) {
          p.print("Error when trying to create project: " + e.getMessage()
              + "\n");
          p.flush();
        }

      }
    });
  }

  /**
   * Checks if any of the elements in the first collection can be found in the
   * second collection.
   *
   * @param findAnyOfThese which elements to look for.
   * @param inThisCollection where to look for them.
   * @param <E> type of the elements in question.
   * @return {@code true} if any of the elements in {@code findAnyOfThese} can
   *         be found in {@code inThisCollection}, {@code false} otherwise.
   */
  private static <E> boolean isAnyIncludedIn(Collection<E> findAnyOfThese,
      Collection<E> inThisCollection) {
    for (E findThisItem : findAnyOfThese) {
      if (inThisCollection.contains(findThisItem)) {
        return true;
      }
    }
    return false;
  }

  private void createProject() throws OrmException {
    final Project.NameKey newProjectNameKey = new Project.NameKey(projectName);

    List<RefRight> access = new ArrayList<RefRight>();
    for (AccountGroup.Id ownerId : ownerIds) {
      final RefRight.Key prk =
          new RefRight.Key(newProjectNameKey, new RefRight.RefPattern(
              RefRight.ALL), ApprovalCategory.OWN, ownerId);
      final RefRight pr = new RefRight(prk);
      pr.setMaxValue((short) 1);
      pr.setMinValue((short) 1);
      access.add(pr);
    }
    db.refRights().insert(access);

    final Project newProject = new Project(newProjectNameKey, db.nextProjectId());
    newProject.setDescription(projectDescription);
    newProject.setSubmitType(submitType);
    newProject.setUseContributorAgreements(contributorAgreements);
    newProject.setUseSignedOffBy(signedOffBy);
    if (newParent != null) {
      newProject.setParent(newParent.getProject().getNameKey());
    }

    db.projects().insert(Collections.singleton(newProject));
  }

  private void validateParameters() throws Failure {
    if (projectName.endsWith(".git")) {
      projectName =
          projectName.substring(0, projectName.length() - ".git".length());
    }

    if (!isAnyIncludedIn(currentUser.getEffectiveGroups(), projectCreatorGroups)) {
      throw new Failure(1, "fatal: Not permitted to create " + projectName);
    }

    if (ownerIds != null && !ownerIds.isEmpty()) {
      ownerIds =
          new ArrayList<AccountGroup.Id>(new HashSet<AccountGroup.Id>(ownerIds));
    } else {
      ownerIds = new ArrayList<AccountGroup.Id>(projectOwnerGroups);
    }

    while (branch.startsWith("/")) {
      branch = branch.substring(1);
    }
    if (!branch.startsWith(Constants.R_HEADS)) {
      branch = Constants.R_HEADS + branch;
    }
    if (!Repository.isValidRefName(branch)) {
      throw new Failure(1, "--branch \"" + branch + "\" is not a valid name");
    }
  }
}
