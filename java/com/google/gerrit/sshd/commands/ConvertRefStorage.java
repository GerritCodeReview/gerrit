// Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.git.DelegateRepository;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "convert-ref-storage",
    description = "Convert ref storage to reftable (experimental)",
    runsAt = MASTER_OR_SLAVE)
public class ConvertRefStorage extends SshCommand {
  @Inject private GitRepositoryManager repoManager;

  private enum StorageFormatOption {
    reftable,
    refdir,
  }

  @Option(
      name = "--format",
      usage = "storage format to convert to (reftable or refdir) (default: reftable)")
  private StorageFormatOption storageFormat = StorageFormatOption.reftable;

  @Option(
      name = "--backup",
      aliases = {"-b"},
      usage = "create backup of old ref storage format (default: true)")
  private boolean backup = true;

  @Option(
      name = "--reflogs",
      aliases = {"-r"},
      usage = "write reflogs to reftable (default: true)")
  private boolean writeLogs = true;

  @Option(
      name = "--project",
      aliases = {"-p"},
      metaVar = "PROJECT",
      required = true,
      usage = "project for which the storage format should be changed")
  private ProjectState projectState;

  @Override
  public void run() throws Exception {
    enableGracefulStop();
    Project.NameKey projectName = projectState.getNameKey();
    try (Repository repo = repoManager.openRepository(projectName)) {
      if (repo instanceof DelegateRepository) {
        ((DelegateRepository) repo).convertRefStorage(storageFormat.name(), writeLogs, backup);
      } else {
        checkState(
            repo instanceof FileRepository, "Repository is not an instance of FileRepository!");
        ((FileRepository) repo).convertRefStorage(storageFormat.name(), writeLogs, backup);
      }
    } catch (RepositoryNotFoundException e) {
      throw die("'" + projectName + "': not a git archive", e);
    } catch (IOException e) {
      throw die("Error converting: '" + projectName + "': " + e.getMessage(), e);
    }
  }
}
