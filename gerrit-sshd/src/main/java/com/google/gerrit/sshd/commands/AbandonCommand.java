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

import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.changedetail.AbandonChange;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbandonCommand extends SshCommand {
  private static final Logger log =
      LoggerFactory.getLogger(AbandonCommand.class);

  @Argument(index = 0, required = true, multiValued = false, metaVar = "PROJECT,BRANCH,CHANGEID", usage = "change to abandon")
  private Change.Id changeId;

  @Option(name = "--message", aliases = "-m", usage = "cover message to publish on change", metaVar = "MESSAGE")
  private String changeComment;

  @Inject
  private AbandonChange.Factory abandonChangeFactory;

  @Override
  public void run() throws Failure {
    try {
      final ReviewResult result = abandonChangeFactory.create(
          changeId, changeComment).call();
      for (ReviewResult.Error resultError : result.getErrors()) {
        switch (resultError.getType()) {
          case ABANDON_NOT_PERMITTED:
            throw new UnloggedFailure("not permitted to abandon change");
          default:
            throw new UnloggedFailure("failure in abandon");
        }
      }
    } catch (InvalidChangeOperationException e) {
      throw new UnloggedFailure(e.getMessage());
    } catch (Exception e) {
      log.error("internal error while abandoning " + changeId, e);
      throw new UnloggedFailure("fatal: internal server error while "
                                + "abandoning " + changeId);
    }
  }

}
