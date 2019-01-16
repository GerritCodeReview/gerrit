// Copyright (C) 2011 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.change.DeleteReviewer;
import com.google.gerrit.server.restapi.change.PostReviewers;
import com.google.gerrit.sshd.ChangeArgumentParser;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(name = "set-reviewers", description = "Add or remove reviewers on a change")
public class SetReviewersCommand extends SshCommand {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Option(name = "--project", aliases = "-p", usage = "project containing the change")
  private ProjectState projectState;

  @Option(
      name = "--add",
      aliases = {"-a"},
      metaVar = "REVIEWER",
      usage = "user or group that should be added as reviewer")
  private List<String> toAdd = new ArrayList<>();

  @Option(
      name = "--remove",
      aliases = {"-r"},
      metaVar = "REVIEWER",
      usage = "user that should be removed from the reviewer list")
  void optionRemove(Account.Id who) {
    toRemove.add(who);
  }

  @Argument(
      index = 0,
      required = true,
      multiValued = true,
      metaVar = "CHANGE",
      usage = "changes to modify")
  void addChange(String token) {
    try {
      changeArgumentParser.addChange(token, changes, projectState);
    } catch (IOException | UnloggedFailure e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    } catch (StorageException e) {
      throw new IllegalArgumentException("database is down", e);
    } catch (PermissionBackendException e) {
      throw new IllegalArgumentException("can't check permissions", e);
    }
  }

  @Inject private ReviewerResource.Factory reviewerFactory;

  @Inject private PostReviewers postReviewers;

  @Inject private DeleteReviewer deleteReviewer;

  @Inject private ChangeArgumentParser changeArgumentParser;

  private Set<Account.Id> toRemove = new HashSet<>();

  private Map<Change.Id, ChangeResource> changes = new LinkedHashMap<>();

  @Override
  protected void run() throws UnloggedFailure {
    boolean ok = true;
    for (ChangeResource rsrc : changes.values()) {
      try {
        ok &= modifyOne(rsrc);
      } catch (Exception err) {
        ok = false;
        logger.atSevere().withCause(err).log("Error updating reviewers on change %s", rsrc.getId());
        writeError("fatal", "internal error while updating " + rsrc.getId());
      }
    }

    if (!ok) {
      throw die("one or more updates failed; review output above");
    }
  }

  private boolean modifyOne(ChangeResource changeRsrc) throws Exception {
    boolean ok = true;

    // Remove reviewers
    //
    for (Account.Id reviewer : toRemove) {
      ReviewerResource rsrc = reviewerFactory.create(changeRsrc, reviewer);
      String error = null;
      try {
        deleteReviewer.apply(rsrc, new DeleteReviewerInput());
      } catch (ResourceNotFoundException e) {
        error = String.format("could not remove %s: not found", reviewer);
      } catch (Exception e) {
        error = String.format("could not remove %s: %s", reviewer, e.getMessage());
      }
      if (error != null) {
        ok = false;
        writeError("error", error);
      }
    }

    // Add reviewers
    //
    for (String reviewer : toAdd) {
      AddReviewerInput input = new AddReviewerInput();
      input.reviewer = reviewer;
      input.confirmed = true;
      String error;
      try {
        error = postReviewers.apply(changeRsrc, input).error;
      } catch (Exception e) {
        error = String.format("could not add %s: %s", reviewer, e.getMessage());
      }
      if (error != null) {
        ok = false;
        writeError("error", error);
      }
    }

    return ok;
  }
}
