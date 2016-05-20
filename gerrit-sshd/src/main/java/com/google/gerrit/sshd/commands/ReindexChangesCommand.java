package com.google.gerrit.sshd.commands;

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;

import java.util.ArrayList;
import java.util.List;

@CommandMetaData(name = "reindex-changes", runsAt = MASTER,
    description = "Reindex one or more changes")
final class ReindexChangesCommand extends SshCommand {
  @Inject
  private GerritApi api;

  @Argument(index = 0, required = true, multiValued = true, metaVar = "CHANGE",
      usage = "changes to reindex")
  void addChange(String change) {
    //TODO(dpursehouse): Reuse the implementation from SetReviewersCommand
    changes.add(change);
  }

  private List<String> changes = new ArrayList<>();

  @Override
  protected void run() throws UnloggedFailure {
    if (changes.isEmpty()) {
      throw die("must provide at least 1 change");
    }
    boolean ok = true;
    for (String change : changes) {
      try {
        api.changes().id(change).index();
      } catch (RestApiException e) {
        ok = false;
        writeError("error", String.format(
            "failed to index change %s: %s", change, e.getMessage()));
      }
    }
    if (!ok) {
      throw die("failed to index one or more changes");
    }
  }

}
