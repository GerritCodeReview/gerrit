// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.BanCommit;
import com.google.gerrit.server.project.BanCommit.BanResultInfo;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(
  name = "ban-commit",
  description = "Ban a commit from a project's repository",
  runsAt = MASTER_OR_SLAVE
)
public class BanCommitCommand extends SshCommand {
  @Option(
    name = "--reason",
    aliases = {"-r"},
    metaVar = "REASON",
    usage = "reason for banning the commit"
  )
  private String reason;

  @Argument(
    index = 0,
    required = true,
    metaVar = "PROJECT",
    usage = "name of the project for which the commit should be banned"
  )
  private ProjectControl projectControl;

  @Argument(
    index = 1,
    required = true,
    multiValued = true,
    metaVar = "COMMIT",
    usage = "commit(s) that should be banned"
  )
  private List<ObjectId> commitsToBan = new ArrayList<>();

  @Inject private BanCommit banCommit;

  @Override
  protected void run() throws Failure {
    try {
      BanCommit.Input input =
          BanCommit.Input.fromCommits(Lists.transform(commitsToBan, ObjectId::getName));
      input.reason = reason;

      BanResultInfo r = banCommit.apply(new ProjectResource(projectControl), input);
      printCommits(r.newlyBanned, "The following commits were banned");
      printCommits(r.alreadyBanned, "The following commits were already banned");
      printCommits(r.ignored, "The following ids do not represent commits and were ignored");
    } catch (RestApiException | IOException e) {
      throw die(e);
    }
  }

  private void printCommits(List<String> commits, String message) {
    if (commits != null && !commits.isEmpty()) {
      stdout.print(message + ":\n");
      stdout.print(Joiner.on(",\n").join(commits));
      stdout.print("\n\n");
    }
  }
}
