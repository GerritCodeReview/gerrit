// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.entities.RefNames.REFS_SEQUENCES;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.PROJECT_OWNERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.schema.AclUtil.grant;
import static com.google.gerrit.server.schema.AclUtil.rule;

import com.google.gerrit.common.Version;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.notedb.RepoSequence;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Creates the {@code All-Projects} repository and initial ACLs. */
public class AllProjectsCreator {
  private final GitRepositoryManager repositoryManager;
  private final AllProjectsName allProjectsName;
  private final PersonIdent serverUser;
  private final NoteDbSchemaVersionManager versionManager;
  private final ProjectConfig.Factory projectConfigFactory;
  private final GroupReference anonymous;
  private final GroupReference registered;
  private final GroupReference owners;

  @Inject
  AllProjectsCreator(
      GitRepositoryManager repositoryManager,
      AllProjectsName allProjectsName,
      @GerritPersonIdent PersonIdent serverUser,
      NoteDbSchemaVersionManager versionManager,
      SystemGroupBackend systemGroupBackend,
      ProjectConfig.Factory projectConfigFactory) {
    this.repositoryManager = repositoryManager;
    this.allProjectsName = allProjectsName;
    this.serverUser = serverUser;
    this.versionManager = versionManager;
    this.projectConfigFactory = projectConfigFactory;

    this.anonymous = systemGroupBackend.getGroup(ANONYMOUS_USERS);
    this.registered = systemGroupBackend.getGroup(REGISTERED_USERS);
    this.owners = systemGroupBackend.getGroup(PROJECT_OWNERS);
  }

  public void create(AllProjectsInput input) throws IOException, ConfigInvalidException {
    try (Repository git = repositoryManager.openRepository(allProjectsName)) {
      initAllProjects(git, input);
    } catch (RepositoryNotFoundException notFound) {
      // A repository may be missing if this project existed only to store
      // inheritable permissions. For example 'All-Projects'.
      try (Repository git = repositoryManager.createRepository(allProjectsName)) {
        initAllProjects(git, input);
        RefUpdate u = git.updateRef(Constants.HEAD);
        u.link(RefNames.REFS_CONFIG);
      } catch (RepositoryNotFoundException err) {
        String name = allProjectsName.get();
        throw new IOException("Cannot create repository " + name, err);
      }
    }
  }

  private void initAllProjects(Repository git, AllProjectsInput input)
      throws ConfigInvalidException, IOException {
    BatchRefUpdate bru = git.getRefDatabase().newBatchUpdate();
    try (MetaDataUpdate md =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjectsName, git, bru)) {
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage(
          input.commitMessage().isPresent()
              ? input.commitMessage().get()
              : "Initialized Gerrit Code Review " + Version.getVersion());

      // init basic project configs.
      ProjectConfig config = projectConfigFactory.read(md);
      Project p = config.getProject();
      p.setDescription(
          input.projectDescription().orElse("Access inherited by all other projects."));

      // init boolean project configs.
      input.booleanProjectConfigs().forEach(p::setBooleanConfig);

      // init labels.
      input
          .codeReviewLabel()
          .ifPresent(
              codeReviewLabel ->
                  config.getLabelSections().put(codeReviewLabel.getName(), codeReviewLabel));

      if (input.initDefaultAcls()) {
        // init access sections.
        initDefaultAcls(config, input);
      }

      // commit all the above configs as a commit in "refs/meta/config" branch of the All-Projects.
      config.commitToNewRef(md, RefNames.REFS_CONFIG);

      // init sequence number.
      initSequences(git, bru, input.firstChangeIdForNoteDb());

      // init schema
      versionManager.init();

      execute(git, bru);
    }
  }

  private void initDefaultAcls(ProjectConfig config, AllProjectsInput input) {
    AccessSection capabilities = config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true);
    AccessSection heads = config.getAccessSection(AccessSection.HEADS, true);

    checkArgument(input.codeReviewLabel().isPresent());
    LabelType codeReviewLabel = input.codeReviewLabel().get();

    initDefaultAclsForRegisteredUsers(heads, codeReviewLabel, config);

    input
        .batchUsersGroup()
        .ifPresent(
            batchUsersGroup -> initDefaultAclsForBatchUsers(capabilities, config, batchUsersGroup));

    input
        .administratorsGroup()
        .ifPresent(
            adminsGroup ->
                initDefaultAclsForAdmins(
                    capabilities, config, heads, codeReviewLabel, adminsGroup));
  }

  private void initDefaultAclsForRegisteredUsers(
      AccessSection heads, LabelType codeReviewLabel, ProjectConfig config) {
    AccessSection refsFor = config.getAccessSection("refs/for/*", true);
    AccessSection magic = config.getAccessSection("refs/for/" + AccessSection.ALL, true);

    grant(config, refsFor, Permission.ADD_PATCH_SET, registered);
    grant(config, heads, codeReviewLabel, -1, 1, registered);
    grant(config, heads, Permission.FORGE_AUTHOR, registered);
    grant(config, magic, Permission.PUSH, registered);
    grant(config, magic, Permission.PUSH_MERGE, registered);
    grant(config, magic, Permission.REVERT, registered);
  }

  private void initDefaultAclsForBatchUsers(
      AccessSection capabilities, ProjectConfig config, GroupReference batchUsersGroup) {
    Permission priority = capabilities.getPermission(GlobalCapability.PRIORITY, true);
    PermissionRule r = rule(config, batchUsersGroup);
    r.setAction(Action.BATCH);
    priority.add(r);

    Permission stream = capabilities.getPermission(GlobalCapability.STREAM_EVENTS, true);
    stream.add(rule(config, batchUsersGroup));
  }

  private void initDefaultAclsForAdmins(
      AccessSection capabilities,
      ProjectConfig config,
      AccessSection heads,
      LabelType codeReviewLabel,
      GroupReference adminsGroup) {
    AccessSection all = config.getAccessSection(AccessSection.ALL, true);
    AccessSection tags = config.getAccessSection("refs/tags/*", true);
    AccessSection meta = config.getAccessSection(RefNames.REFS_CONFIG, true);

    grant(config, capabilities, GlobalCapability.ADMINISTRATE_SERVER, adminsGroup);
    grant(config, all, Permission.READ, adminsGroup, anonymous);
    grant(config, heads, codeReviewLabel, -2, 2, adminsGroup, owners);
    grant(config, heads, Permission.CREATE, adminsGroup, owners);
    grant(config, heads, Permission.PUSH, adminsGroup, owners);
    grant(config, heads, Permission.REVERT, adminsGroup, owners);
    grant(config, heads, Permission.SUBMIT, adminsGroup, owners);
    grant(config, heads, Permission.FORGE_COMMITTER, adminsGroup, owners);
    grant(config, heads, Permission.EDIT_TOPIC_NAME, true, adminsGroup, owners);

    grant(config, tags, Permission.CREATE, adminsGroup, owners);
    grant(config, tags, Permission.CREATE_TAG, adminsGroup, owners);
    grant(config, tags, Permission.CREATE_SIGNED_TAG, adminsGroup, owners);

    meta.getPermission(Permission.READ, true).setExclusiveGroup(true);
    grant(config, meta, Permission.READ, adminsGroup, owners);
    grant(config, meta, codeReviewLabel, -2, 2, adminsGroup, owners);
    grant(config, meta, Permission.CREATE, adminsGroup, owners);
    grant(config, meta, Permission.PUSH, adminsGroup, owners);
    grant(config, meta, Permission.SUBMIT, adminsGroup, owners);
  }

  private void initSequences(Repository git, BatchRefUpdate bru, int firstChangeId)
      throws IOException {
    if (git.exactRef(REFS_SEQUENCES + Sequences.NAME_CHANGES) == null) {
      // Can't easily reuse the inserter from MetaDataUpdate, but this shouldn't slow down site
      // initialization unduly.
      try (ObjectInserter ins = git.newObjectInserter()) {
        bru.addCommand(RepoSequence.storeNew(ins, Sequences.NAME_CHANGES, firstChangeId));
        ins.flush();
      }
    }
  }

  private void execute(Repository git, BatchRefUpdate bru) throws IOException {
    try (RevWalk rw = new RevWalk(git)) {
      bru.execute(rw, NullProgressMonitor.INSTANCE);
    }
    for (ReceiveCommand cmd : bru.getCommands()) {
      if (cmd.getResult() != ReceiveCommand.Result.OK) {
        throw new IOException("Failed to initialize " + allProjectsName + " refs:\n" + bru);
      }
    }
  }
}
