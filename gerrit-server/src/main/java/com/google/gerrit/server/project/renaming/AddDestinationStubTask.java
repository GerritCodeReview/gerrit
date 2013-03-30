//Copyright (C) 2013 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.google.gerrit.server.project.renaming;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Project renaming task adding a blocking stub config at the destination.
 *
 * This tasks creates a project at the destination and applies restrictive
 * access conditions to it already before the real repository gets move to the
 * destination. Thereby we prohibit acces to the destination while renaming the
 * project.
 */
public class AddDestinationStubTask implements Task {
  private static final Logger log = LoggerFactory
      .getLogger(AddDestinationStubTask.class);

  private final GitRepositoryManager repoManager;
  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final GroupCache groupCache;
  private final ReviewDb db;
  private final ProjectCache projectCache;

  private final File gitDir;

  private final Project.NameKey source;
  private final Project.NameKey destination;

  public interface Factory extends Task.Factory {
    AddDestinationStubTask create(@Assisted("source") Project.NameKey source,
        @Assisted("destination") Project.NameKey destination);
  }

  @Inject
  public AddDestinationStubTask(GitRepositoryManager repoManager,
      MetaDataUpdate.User metaDataUpdateFactory,
      GroupCache groupCache, ReviewDb db, SitePaths site,
      @GerritServerConfig Config cfg, ProjectCache projectCache,
      @Assisted("source") Project.NameKey source,
      @Assisted("destination") Project.NameKey destination) {
    this.repoManager = repoManager;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.groupCache = groupCache;
    this.db = db;
    this.projectCache = projectCache;

    this.gitDir = site.resolve(cfg.getString("gerrit", null, "basePath"));

    this.source = source;
    this.destination = destination;
  }

  @Override
  public void carryOut() throws ProjectRenamingFailedException {
    Repository repo;
    try {
      repo = repoManager.createRepository(destination);
    } catch (Exception e) {
      throw new ProjectRenamingFailedException("Could not create stub "
          + "repository for " + destination, e);
    }
    try {

      final String head = GitRepositoryManager.REF_CONFIG;
      final RefUpdate u = repo.updateRef(Constants.HEAD);
      u.disableRefLog();
      u.link(head);

      final MetaDataUpdate md =
          metaDataUpdateFactory.create(destination);
      try {
        final ProjectConfig config = ProjectConfig.read(md);
        config.load(md);
        for (AccessSection accessSection : config.getAccessSections()) {
          config.remove(accessSection);
        }
        AccessSection allAccessSection = config.getAccessSection(
            AccessSection.ALL, true);
        GroupReference anonymousGroup =
            config.resolve(groupCache.get(AccountGroup.ANONYMOUS_USERS));
        for (Permission permission : allAccessSection.getPermissions()) {
          allAccessSection.remove(permission);
        }
        for (String permissionName : Permission.getAllNames()) {
          Permission permission = allAccessSection.getPermission(
              permissionName, true);
          PermissionRule blockRule = new PermissionRule();
          blockRule.setBlock();
          blockRule.setGroup(anonymousGroup);
          permission.add(blockRule);
        }

        Project project = config.getProject();
        project.setDescription("Temporary stub project for renaming "
            + source);
        md.setMessage("Inject blocking config\n");
        config.commit(md);
      } finally {
        md.close();
      }
    } catch (Exception e) {
      repo.close();
      rollback();
      throw new ProjectRenamingFailedException("Could not blocking stub "
          + "configuration repository for " + destination, e);
    }
    repo.close();
  }

  @Override
  public void rollback() {
    // Although the stub's config disallowed uploading changes, we cannot guard
    // against someone watching the stub. So we should scrub the database clean
    // of all watches on destination.
    //
    // Nevertheless we do not try to remove watches, since it's hard to add a
    // legitimate watch on the stub, as the stub only exists for a short period
    // of time. In case watches on the stub exist, they might also stem from a
    // failed rolling back of a RenameWatchesTask. In that case, scrubbing the
    // database clean would remove any chance of restoring the watches that
    // failed to roll back automatically. So we do not try to roll back.
    //
    // So we check, if there are any watches on the stub. If there are, we warn
    // that we do not remove them.

    try {
      if (db.accountProjectWatches().byProject(destination).iterator()
          .hasNext()) {
        log.error("Some project watches on the project " + destination
            + " still exist. We cannot decide whether they are valid watches "
            + "that were created while the project stub was in place, or some "
            + "remnants of a failed/partial rollback, so we do not remove "
            + "them. Please check them by hand.");
      }
    } catch (OrmException e) {
      log.error("Could not check whether watches on " + destination + " still "
          + "exist. Please check/remove them by hand.");
    }

    // Removing stub repository from disk
    try {
      deleteFromDisk(destination);
    } catch (RepositoryNotFoundException e) {
      // If no repository can be found at destination, the rollback was trigged
      // by a task after RenameRepositoryTask, and the rollback of
      // RenameRepositoryTask cleaned up the repository at destination, when it
      // got moved back to source again.
    } catch (Exception e) {
      log.error("Could not remove destination stub repository " + destination
          + " when rolling back project renaming. Please remove it by hand", e);
    }

    // Clearing cache (if project ever went there)
    ProjectState projectState = projectCache.get(destination);
    if (projectState != null) {
      Project project = projectState.getProject();
      projectCache.remove(project);
    }
  }

  private void deleteFromDisk(Project.NameKey project) throws IOException,
      RepositoryNotFoundException {
    // Remove from the jgit cache
    final Repository repository = repoManager.openRepository(project);
    repository.close();
    RepositoryCache.close(repository);

    // Delete the repository from disk
    File parentFile = repository.getDirectory().getParentFile();
    recursiveDelete(repository.getDirectory());

    // Delete parent folders while they are (now) empty
    recursiveDeleteParent(parentFile, gitDir);
  }

  /**
   * Recursively delete the specified file and all of its contents.
   *
   * @return true on success, false if there was an error.
   * @throws IOException thrown if deletion fails at some point.
   */
  private void recursiveDelete(File file) throws IOException {
    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        recursiveDelete(f);
      }
    }
    if (!file.delete()) {
      throw new IOException("Could not delete " + file.getAbsoluteFile());
    }
  }

  /**
   * Delete parent directories up to a given boundary.
   * <p>
   * Recursively delete the specified file and its parent files until we hit
   * the file {@code until} or the parent file is populated. This is used when we
   * have a tree structure such as a/b/c/d.git and a/b/e.git - if we delete
   * a/b/c/d.git, we no longer need a/b/c/.
   * @throws IOException if some file could not get deleted
   */
  private void recursiveDeleteParent(File file, File until) throws IOException {
    if (file.equals(until)) {
      return;
    }
    if (file.listFiles().length == 0) {
      File parent = file.getParentFile();
      if (!file.delete()) {
        throw new IOException("Could not delete " + file.getAbsolutePath());
      }
      recursiveDeleteParent(parent, until);
    }
  }

  @Override
  public int getPriority() {
    return 21;
  }
}
