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
import static com.google.gerrit.reviewdb.client.RefNames.REFS_SEQUENCES;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.PROJECT_OWNERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.schema.AclUtil.grant;
import static com.google.gerrit.server.schema.AclUtil.rule;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.Version;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.RepoSequence;
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
  private final GitRepositoryManager mgr;
  private final AllProjectsName allProjectsName;
  private final PersonIdent serverUser;
  private final NotesMigration notesMigration;
  private String message;
  private int firstChangeId = ReviewDb.FIRST_CHANGE_ID;

  @Nullable private GroupReference admin;

  @Nullable private GroupReference batch;
  private final GroupReference anonymous;
  private final GroupReference registered;
  private final GroupReference owners;

  @Inject
  AllProjectsCreator(
      GitRepositoryManager mgr,
      AllProjectsName allProjectsName,
      SystemGroupBackend systemGroupBackend,
      @GerritPersonIdent PersonIdent serverUser,
      NotesMigration notesMigration) {
    this.mgr = mgr;
    this.allProjectsName = allProjectsName;
    this.serverUser = serverUser;
    this.notesMigration = notesMigration;

    this.anonymous = systemGroupBackend.getGroup(ANONYMOUS_USERS);
    this.registered = systemGroupBackend.getGroup(REGISTERED_USERS);
    this.owners = systemGroupBackend.getGroup(PROJECT_OWNERS);
  }

  /** If called, grant default permissions to this admin group */
  public AllProjectsCreator setAdministrators(GroupReference admin) {
    this.admin = admin;
    return this;
  }

  /** If called, grant stream-events permission and set appropriate priority for this group */
  public AllProjectsCreator setBatchUsers(GroupReference batch) {
    this.batch = batch;
    return this;
  }

  public AllProjectsCreator setCommitMessage(String message) {
    this.message = message;
    return this;
  }

  public AllProjectsCreator setFirstChangeIdForNoteDb(int id) {
    checkArgument(id > 0, "id must be positive: %s", id);
    firstChangeId = id;
    return this;
  }

  public void create() throws IOException, ConfigInvalidException {
    try (Repository git = mgr.openRepository(allProjectsName)) {
      initAllProjects(git);
    } catch (RepositoryNotFoundException notFound) {
      // A repository may be missing if this project existed only to store
      // inheritable permissions. For example 'All-Projects'.
      try (Repository git = mgr.createRepository(allProjectsName)) {
        initAllProjects(git);
        RefUpdate u = git.updateRef(Constants.HEAD);
        u.link(RefNames.REFS_CONFIG);
      } catch (RepositoryNotFoundException err) {
        String name = allProjectsName.get();
        throw new IOException("Cannot create repository " + name, err);
      }
    }
  }

  private void initAllProjects(Repository git) throws IOException, ConfigInvalidException {
    BatchRefUpdate bru = git.getRefDatabase().newBatchUpdate();
    try (MetaDataUpdate md =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjectsName, git, bru)) {
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage(
          MoreObjects.firstNonNull(
              Strings.emptyToNull(message),
              "Initialized Gerrit Code Review " + Version.getVersion()));

      ProjectConfig config = ProjectConfig.read(md);
      Project p = config.getProject();
      p.setDescription("Access inherited by all other projects.");
      p.setBooleanConfig(BooleanProjectConfig.REQUIRE_CHANGE_ID, InheritableBoolean.TRUE);
      p.setBooleanConfig(BooleanProjectConfig.USE_CONTENT_MERGE, InheritableBoolean.TRUE);
      p.setBooleanConfig(BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS, InheritableBoolean.FALSE);
      p.setBooleanConfig(BooleanProjectConfig.USE_SIGNED_OFF_BY, InheritableBoolean.FALSE);
      p.setBooleanConfig(BooleanProjectConfig.ENABLE_SIGNED_PUSH, InheritableBoolean.FALSE);

      AccessSection cap = config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true);
      AccessSection all = config.getAccessSection(AccessSection.ALL, true);
      AccessSection heads = config.getAccessSection(AccessSection.HEADS, true);
      AccessSection tags = config.getAccessSection("refs/tags/*", true);
      AccessSection meta = config.getAccessSection(RefNames.REFS_CONFIG, true);
      AccessSection refsFor = config.getAccessSection("refs/for/*", true);
      AccessSection magic = config.getAccessSection("refs/for/" + AccessSection.ALL, true);

      grant(config, cap, GlobalCapability.ADMINISTRATE_SERVER, admin);
      grant(config, all, Permission.READ, admin, anonymous);
      grant(config, refsFor, Permission.ADD_PATCH_SET, registered);

      if (batch != null) {
        Permission priority = cap.getPermission(GlobalCapability.PRIORITY, true);
        PermissionRule r = rule(config, batch);
        r.setAction(Action.BATCH);
        priority.add(r);

        Permission stream = cap.getPermission(GlobalCapability.STREAM_EVENTS, true);
        stream.add(rule(config, batch));
      }

      LabelType cr = initCodeReviewLabel(config);
      grant(config, heads, cr, -1, 1, registered);

      grant(config, heads, cr, -2, 2, admin, owners);
      grant(config, heads, Permission.CREATE, admin, owners);
      grant(config, heads, Permission.PUSH, admin, owners);
      grant(config, heads, Permission.SUBMIT, admin, owners);
      grant(config, heads, Permission.FORGE_AUTHOR, registered);
      grant(config, heads, Permission.FORGE_COMMITTER, admin, owners);
      grant(config, heads, Permission.EDIT_TOPIC_NAME, true, admin, owners);

      grant(config, tags, Permission.CREATE, admin, owners);
      grant(config, tags, Permission.CREATE_TAG, admin, owners);
      grant(config, tags, Permission.CREATE_SIGNED_TAG, admin, owners);

      grant(config, magic, Permission.PUSH, registered);
      grant(config, magic, Permission.PUSH_MERGE, registered);

      meta.getPermission(Permission.READ, true).setExclusiveGroup(true);
      grant(config, meta, Permission.READ, admin, owners);
      grant(config, meta, cr, -2, 2, admin, owners);
      grant(config, meta, Permission.CREATE, admin, owners);
      grant(config, meta, Permission.PUSH, admin, owners);
      grant(config, meta, Permission.SUBMIT, admin, owners);

      config.commitToNewRef(md, RefNames.REFS_CONFIG);
      initSequences(git, bru);
      execute(git, bru);
    }
  }

  public static LabelType initCodeReviewLabel(ProjectConfig c) {
    LabelType type =
        new LabelType(
            "Code-Review",
            ImmutableList.of(
                new LabelValue((short) 2, "Looks good to me, approved"),
                new LabelValue((short) 1, "Looks good to me, but someone else must approve"),
                new LabelValue((short) 0, "No score"),
                new LabelValue((short) -1, "I would prefer this is not merged as is"),
                new LabelValue((short) -2, "This shall not be merged")));
    type.setCopyMinScore(true);
    type.setCopyAllScoresOnTrivialRebase(true);
    c.getLabelSections().put(type.getName(), type);
    return type;
  }

  private void initSequences(Repository git, BatchRefUpdate bru) throws IOException {
    if (notesMigration.readChangeSequence()
        && git.exactRef(REFS_SEQUENCES + Sequences.NAME_CHANGES) == null) {
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
