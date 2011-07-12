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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.ProjectOwnerGroups;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Create a new project. **/
final class CreateProject extends BaseCommand {
  private static final Logger log = LoggerFactory.getLogger(CreateProject.class);

  @Option(name = "--name", aliases = {"-n"}, metaVar = "NAME", usage = "name of project to be created (deprecated option)")
  void setProjectNameFromOption(String name) {
    if (projectName != null) {
      throw new IllegalArgumentException("NAME already supplied");
    } else {
      projectName = name;
    }
  }

  @Option(name = "--owner", aliases = {"-o"}, usage = "owner(s) of project")
  private List<AccountGroup.UUID> ownerIds;

  @Option(name = "--parent", aliases = {"-p"}, metaVar = "NAME", usage = "parent project")
  private ProjectControl newParent;

  @Option(name = "--permissions-only", usage = "create project for use only as parent")
  private boolean permissionsOnly;

  @Option(name = "--description", aliases = {"-d"}, metaVar = "DESCRIPTION", usage = "description of project")
  private String projectDescription = "";

  @Option(name = "--submit-type", aliases = {"-t"}, usage = "project submit type\n"
      + "(default: MERGE_IF_NECESSARY)")
  private SubmitType submitType = SubmitType.MERGE_IF_NECESSARY;

  @Option(name = "--use-contributor-agreements", aliases = {"--ca"}, usage = "if contributor agreement is required")
  private boolean contributorAgreements;

  @Option(name = "--use-signed-off-by", aliases = {"--so"}, usage = "if signed-off-by is required")
  private boolean signedOffBy;

  @Option(name = "--use-content-merge", usage = "allow automatic conflict resolving within files")
  private boolean contentMerge;

  @Option(name = "--require-change-id", aliases = {"--id"}, usage = "if change-id is required")
  private boolean requireChangeID;

  @Option(name = "--branch", aliases = {"-b"}, metaVar = "BRANCH", usage = "initial branch name\n"
      + "(default: master)")
  private String branch = Constants.MASTER;

  @Option(name = "--empty-commit", usage = "to create initial empty commit")
  private boolean createEmptyCommit;

  private String projectName;
  @Argument(index = 0, metaVar="NAME", usage="name of project to be created")
  void setProjectNameFromArgument(String name) {
    if (projectName != null) {
      throw new IllegalArgumentException("--name already supplied");
    } else {
      projectName = name;
    }
  }

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GroupCache groupCache;

  @Inject
  @ProjectOwnerGroups
  private Set<AccountGroup.UUID> projectOwnerGroups;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ReplicationQueue rq;

  @Inject
  @GerritPersonIdent
  private PersonIdent serverIdent;

  @Inject
  MetaDataUpdate.User metaDataUpdateFactory;

  private Project.NameKey nameKey;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();

        if ((newParent != null && !newParent.canCreateChildProject())
            || (newParent == null && !currentUser.getCapabilityByProject(null)
                .canCreateProject())) {
          String msg = String.format(
              "fatal: %s does not have \"Create Project\" capability.",
              currentUser.getUserName());
            throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, msg);
        }

        validateParameters();

        try {
          nameKey = new Project.NameKey(projectName);

          String head = permissionsOnly ? GitRepositoryManager.REF_CONFIG : branch;
          final Repository repo = repoManager.createRepository(nameKey);
          try {
            rq.replicateNewProject(nameKey, head);

            RefUpdate u = repo.updateRef(Constants.HEAD);
            u.disableRefLog();
            u.link(head);

            createProjectConfig();

            if (!permissionsOnly && createEmptyCommit) {
              createEmptyCommit(repo, nameKey, branch);
            }
          } finally {
            repo.close();
          }
        } catch (IllegalStateException err) {
          try {
            Repository repo = repoManager.openRepository(nameKey);
            try {
              if (repo.getObjectDatabase().exists()) {
                throw new UnloggedFailure(1, "fatal: project \"" + projectName + "\" exists");
              }
            } finally {
              repo.close();
            }
          } catch (RepositoryNotFoundException doesNotExist) {
            throw new Failure(1, "fatal: Cannot create " + projectName, err);
          }
        } catch (RepositoryNotFoundException badName) {
          throw new UnloggedFailure(1, "fatal: " + badName.getMessage());
        } catch (Exception err) {
          throw new Failure(1, "fatal: Cannot create " + projectName, err);
        }
      }
    });
  }

  private void createEmptyCommit(final Repository repo,
      final Project.NameKey project, final String ref) throws IOException {
    ObjectInserter oi = repo.newObjectInserter();
    try {
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(oi.insert(Constants.OBJ_TREE, new byte[] {}));
      cb.setAuthor(metaDataUpdateFactory.getUserPersonIdent());
      cb.setCommitter(serverIdent);
      cb.setMessage("Initial empty repository\n");

      ObjectId id = oi.insert(cb);
      oi.flush();

      RefUpdate ru = repo.updateRef(Constants.HEAD);
      ru.setNewObjectId(id);
      final Result result = ru.update();
      switch (result) {
        case NEW:
          rq.scheduleUpdate(project, ref);
          break;
        default: {
          throw new IOException(result.name());
        }
      }
    } catch (IOException e) {
      log.error("Cannot create empty commit for " + projectName, e);
      throw e;
    } finally {
      oi.release();
    }
  }

  private void createProjectConfig() throws IOException, ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(nameKey);
    try {
      ProjectConfig config = ProjectConfig.read(md);
      config.load(md);

      Project newProject = config.getProject();
      newProject.setDescription(projectDescription);
      newProject.setSubmitType(submitType);
      newProject.setUseContributorAgreements(contributorAgreements);
      newProject.setUseSignedOffBy(signedOffBy);
      newProject.setUseContentMerge(contentMerge);
      newProject.setRequireChangeID(requireChangeID);
      if (newParent != null) {
        newProject.setParentName(newParent.getProject().getName());
      }

      if (!ownerIds.isEmpty()) {
        AccessSection all = config.getAccessSection(AccessSection.ALL, true);
        for (AccountGroup.UUID ownerId : ownerIds) {
          AccountGroup accountGroup = groupCache.get(ownerId);
          GroupReference group = config.resolve(accountGroup);
          all.getPermission(Permission.OWNER, true).add(
              new PermissionRule(group));
        }
      }

      md.setMessage("Created project\n");
      if (!config.commit(md)) {
        throw new IOException("Cannot create " + projectName);
      }
    } finally {
      md.close();
    }
    projectCache.onCreateProject(nameKey);
    repoManager.setProjectDescription(nameKey, projectDescription);
    rq.scheduleUpdate(nameKey, GitRepositoryManager.REF_CONFIG);
  }

  private void validateParameters() throws Failure {
    if (projectName == null || projectName.isEmpty()) {
      throw new Failure(1, "fatal: Argument NAME is required");
    }

    if (projectName.endsWith(Constants.DOT_GIT_EXT)) {
      projectName = projectName.substring(0, //
          projectName.length() - Constants.DOT_GIT_EXT.length());
    }

    if (ownerIds != null && !ownerIds.isEmpty()) {
      ownerIds =
          new ArrayList<AccountGroup.UUID>(new HashSet<AccountGroup.UUID>(ownerIds));
    } else {
      ownerIds = new ArrayList<AccountGroup.UUID>(projectOwnerGroups);
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
