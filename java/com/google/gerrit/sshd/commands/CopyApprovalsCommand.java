// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.approval.RecursiveApprovalCopier;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(
    name = "copy-approvals",
    description = "Copy inferred approvals labels to the latest patch-set")
@RequiresCapability(GlobalCapability.MAINTAIN_SERVER)
public class CopyApprovalsCommand extends SshCommand {

  private final Set<Project.NameKey> projects = new HashSet<>();
  private final RecursiveApprovalCopier recursiveApprovalCopier;
  private final GitRepositoryManager repositoryManager;

  @Argument(
      index = 0,
      required = false,
      multiValued = true,
      metaVar = "PROJECT",
      usage = "list of projects to scan for approvals (default: all projects)")
  void addProject(String project) {
    projects.add(Project.nameKey(project));
  }

  @Option(
      name = "--verbose",
      aliases = "-v",
      usage = "display projects/changes impacted by the label copy operation",
      metaVar = "VERBOSE")
  private boolean verbose;

  @Inject
  public CopyApprovalsCommand(
      RecursiveApprovalCopier recursiveApprovalCopier, GitRepositoryManager repositoryManager) {
    this.recursiveApprovalCopier = recursiveApprovalCopier;
    this.repositoryManager = repositoryManager;
  }

  @Override
  protected void run() throws Exception {
    AtomicInteger changesCounter = new AtomicInteger();
    stdout.println(
        "Copying inferred approvals labels on " + (projects.isEmpty() ? "all projects" : projects));

    Set<Project.NameKey> projectsList = projects.isEmpty() ? repositoryManager.list() : projects;

    for (Project.NameKey project : projectsList) {
      stdout.print("> " + project + " : ");
      recursiveApprovalCopier.persist(
          project,
          c -> {
            if (verbose) {
              stdout.println("  [" + c.getProject() + "," + c.getChangeId() + "] updated");
            }
            changesCounter.incrementAndGet();
          });
      stdout.println("DONE");
    }

    stdout.println(
        "Labels copied for "
            + projectsList.size()
            + " project(s) have impacted "
            + changesCounter.get()
            + " change(s)");
  }
}
