// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.MetaDataUpdate.User;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Updates repo refs/meta/config content. */
@Singleton
public class RepoMetaDataUpdater {
  private final Provider<User> metaDataUpdateFactory;
  private final Provider<CurrentUser> user;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;
  private final ChangeInserter.Factory changeInserterFactory;
  private final Sequences seq;

  private final BatchUpdate.Factory updateFactory;

  private final PermissionBackend permissionBackend;
  private final ChangeJson.Factory jsonFactory;

  @Inject
  RepoMetaDataUpdater(
      Provider<User> metaDataUpdateFactory,
      Provider<CurrentUser> user,
      ProjectConfig.Factory projectConfigFactory,
      ProjectCache projectCache,
      ChangeInserter.Factory changeInserterFactory,
      Sequences seq,
      BatchUpdate.Factory updateFactory,
      PermissionBackend permissionBackend,
      ChangeJson.Factory jsonFactory) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.user = user;
    this.projectConfigFactory = projectConfigFactory;
    this.projectCache = projectCache;
    this.changeInserterFactory = changeInserterFactory;
    this.seq = seq;
    this.updateFactory = updateFactory;
    this.permissionBackend = permissionBackend;
    this.jsonFactory = jsonFactory;
  }

  /**
   * Returns a creator for creating project config changes.
   *
   * <p>The method checks that user has required permissions.
   *
   * <p>Usage:
   *
   * <pre>{@code
   * try(var changeCreator =
   *  repoMetaDataUpdater.configChangeCreator(projectName, message, defaultMessage)) {
   *    ProjectConfig config = changeCreator.getConfig();
   *    // ... update project config
   *    // Create change - if the createChange method is not called, all updates are ignored and no
   *    // change is created.
   *    Response<ChangeInfo> result = changeCreator.createChange();
   *  }
   * }</pre>
   *
   * @param projectName the name of the project whose config should be updated
   * @param message the user-provided commit message. If it is not provided (i.e. it is null or
   *     empty) - the {@code defaultMessage} is used.
   * @param defaultMessage the default commit message if the user doesn't provide one.
   */
  @MustBeClosed
  public ConfigChangeCreator configChangeCreator(
      Project.NameKey projectName, @Nullable String message, String defaultMessage)
      throws PermissionBackendException, AuthException, ResourceConflictException, IOException,
          ConfigInvalidException {
    message = validateMessage(message, defaultMessage);
    PermissionBackend.ForProject forProject =
        permissionBackend.user(user.get()).project(projectName);
    if (!check(forProject, ProjectPermission.READ_CONFIG)) {
      throw new AuthException(RefNames.REFS_CONFIG + " not visible");
    }
    if (!check(forProject, ProjectPermission.WRITE_CONFIG)) {
      try {
        forProject.ref(RefNames.REFS_CONFIG).check(RefPermission.CREATE_CHANGE);
      } catch (AuthException denied) {
        throw new AuthException("cannot create change for " + RefNames.REFS_CONFIG, denied);
      }
    }
    projectCache.get(projectName).orElseThrow(illegalState(projectName)).checkStatePermitsWrite();
    // The MetaDataUpdate instance gets closed in the ConfigChangeCreator.close() method.
    MetaDataUpdate md = metaDataUpdateFactory.get().create(projectName);
    try {
      md.setInsertChangeId(true);
      md.setMessage(message);
      ProjectConfig config = projectConfigFactory.read(md);
      return new ConfigChangeCreator(md, projectName, user.get(), config);
    } catch (Throwable t) {
      try (md) {
        throw t;
      }
    }
  }

  /**
   * Returns an updater for updating project config without review.
   *
   * <p>The method checks that user has required permissions.
   *
   * <p>When the update is saved (using the {@link ConfigUpdater#commitConfigUpdate} method), the
   * project cache is updated automatically.
   *
   * <p>Usage:
   *
   * <pre>{@code
   * try(var configUpdater =
   *  repoMetaDataUpdater.configUpdater(projectName, message, defaultMessage)) {
   *    ProjectConfig config = changeCreator.getConfig();
   *    // ... update project config
   *    // Save updated config - if the commitConfigUpdate method is not called, all updates are ignored.
   *    configUpdater.commitConfigUpdate();
   *  }
   * }</pre>
   *
   * @param projectName the name of the project whose config should be updated
   * @param message the user-provided commit message. If it is not provided (i.e. it is null or
   *     empty) - the {@code defaultMessage} is used.
   * @param defaultMessage the default commit message if the user doesn't provide one.
   */
  @MustBeClosed
  public ConfigUpdater configUpdater(
      Project.NameKey projectName, @Nullable String message, String defaultMessage)
      throws AuthException, PermissionBackendException, ConfigInvalidException, IOException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    permissionBackend.user(user.get()).project(projectName).check(ProjectPermission.WRITE_CONFIG);
    return configUpdaterWithoutPermissionsCheck(projectName, message, defaultMessage);
  }

  /**
   * Returns an updater for updating project config without review and skips some permissions
   * checks.
   *
   * <p>The method doesn't do any permissions checks. It should be used only when standard
   * permissions checks from {@link #configUpdater} can't be used.
   *
   * <p>See {@link #configUpdater} for details.
   */
  @MustBeClosed
  public ConfigUpdater configUpdaterWithoutPermissionsCheck(
      Project.NameKey projectName, @Nullable String message, String defaultMessage)
      throws IOException, ConfigInvalidException {
    message = validateMessage(message, defaultMessage);
    // The MetaDataUpdate instance gets closed in the ConfigUpdater.close() method.
    MetaDataUpdate md = metaDataUpdateFactory.get().create(projectName);
    try {
      ProjectConfig config = projectConfigFactory.read(md);
      md.setMessage(message);
      return new ConfigUpdater(md, config);
    } catch (Throwable t) {
      try (md) {
        throw t;
      }
    }
  }

  /**
   * Updater for a project config without review.
   *
   * <p>See {@link #configUpdater} and {@link #configUpdaterWithoutPermissionsCheck} for details and
   * usages.
   */
  public class ConfigUpdater implements AutoCloseable {
    private final MetaDataUpdate md;
    private final ProjectConfig config;

    private ConfigUpdater(MetaDataUpdate md, ProjectConfig config) {
      this.md = md;
      this.config = config;
    }

    public ProjectConfig getConfig() {
      return config;
    }

    public void commitConfigUpdate() throws IOException {
      config.commit(md);
      projectCache.evictAndReindex(config.getProject());
    }

    public Repository getRepository() {
      return md.getRepository();
    }

    @Override
    public void close() {
      md.close();
    }
  }

  /**
   * Creates a change for a project config update.
   *
   * <p>See {@link #createChange} for details and usages.
   */
  public class ConfigChangeCreator implements AutoCloseable {
    private final MetaDataUpdate md;
    private final String oldCommitSha1;
    private final Project.NameKey projectName;
    private final CurrentUser user;
    private final ProjectConfig config;
    private boolean changeCreated;

    private ConfigChangeCreator(
        MetaDataUpdate md, Project.NameKey projectName, CurrentUser user, ProjectConfig config) {
      this.md = md;
      this.config = config;
      this.projectName = projectName;
      this.user = user;
      ObjectId oldCommit = config.getRevision();
      oldCommitSha1 = oldCommit == null ? null : oldCommit.getName();
    }

    @Override
    public void close() {
      md.close();
    }

    public ProjectConfig getConfig() {
      return config;
    }

    public Response<ChangeInfo> createChange()
        throws IOException, UpdateException, RestApiException {
      checkState(!changeCreated, "Change has been already created");
      changeCreated = true;

      Change.Id changeId = Change.id(seq.nextChangeId());
      try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
        RevCommit commit =
            config.commitToNewRef(
                md, PatchSet.id(changeId, Change.INITIAL_PATCH_SET_ID).toRefName());

        if (commit.name().equals(oldCommitSha1)) {
          throw new BadRequestException("no change");
        }

        try (ObjectInserter objInserter = md.getRepository().newObjectInserter();
            ObjectReader objReader = objInserter.newReader();
            RevWalk rw = new RevWalk(objReader);
            BatchUpdate bu = updateFactory.create(projectName, user, TimeUtil.now())) {
          bu.setRepository(md.getRepository(), rw, objInserter);
          ChangeInserter ins = newInserter(changeId, commit);
          bu.insertChange(ins);
          bu.execute();
          Change change = ins.getChange();
          return Response.created(jsonFactory.noOptions().format(change));
        }
      }
    }

    // ProjectConfig doesn't currently support fusing into a BatchUpdate.
    @SuppressWarnings("deprecation")
    private ChangeInserter newInserter(Change.Id changeId, RevCommit commit) {
      return changeInserterFactory
          .create(changeId, commit, RefNames.REFS_CONFIG)
          .setMessage(
              // Same message as in ReceiveCommits.CreateRequest.
              ApprovalsUtil.renderMessageWithApprovals(1, ImmutableMap.of(), ImmutableMap.of()))
          .setValidate(false)
          .setUpdateRef(false);
    }
  }

  private String validateMessage(@Nullable String message, String defaultMessage) {
    if (Strings.isNullOrEmpty(message)) {
      message = defaultMessage;
    } else {
      message = message.trim();
    }
    checkArgument(!message.isBlank(), "The message must not be empty");
    if (!message.endsWith("\n")) {
      return message + "\n";
    }
    return message;
  }

  private boolean check(PermissionBackend.ForProject perm, ProjectPermission p)
      throws PermissionBackendException {
    try {
      perm.check(p);
      return true;
    } catch (AuthException denied) {
      return false;
    }
  }
}
