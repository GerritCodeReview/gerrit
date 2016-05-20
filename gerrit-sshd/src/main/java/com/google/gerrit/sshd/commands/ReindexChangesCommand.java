package com.google.gerrit.sshd.commands;

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;

@CommandMetaData(name = "reindex-changes", runsAt = MASTER,
    description = "Reindex one or more changes")
final class ReindexChangesCommand extends SshCommand {
  @Inject
  private GerritApi api;

  @Option(name = "--change", metaVar = "CHANGE", usage = "Changes to reindex")
  private List<String> changes = new ArrayList<>();

  @Override
  protected void run() throws UnloggedFailure {
    if (changes.isEmpty()) {
      throw die("Must provide at least 1 change");
    }
    for (String change : changes) {
      try {
        api.changes().id(change).index();
      } catch (RestApiException e) {
        throw die(String.format("Failed to index change %s: %s",
            change, e.getMessage()));
      }
    }
  }

}
