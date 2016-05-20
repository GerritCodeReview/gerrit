package com.google.gerrit.sshd.commands;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.Index;
import com.google.gerrit.sshd.ChangeArgumentParser;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@CommandMetaData(name = "index", description = "Index changes")
final class IndexChangesCommand extends SshCommand {
  @Inject
  private Index index;

  @Inject
  private ChangeArgumentParser changeArgumentParser;

  @Argument(index = 0, required = true, multiValued = true, metaVar = "CHANGE",
      usage = "changes to index")
  void addChange(String token) {
    try {
      changeArgumentParser.addChange(token, changes, null);
    } catch (UnloggedFailure e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    } catch (OrmException e) {
      throw new IllegalArgumentException("database is down", e);
    }
  }

  private Map<Change.Id, ChangeResource> changes = new LinkedHashMap<>();

  @Override
  protected void run() throws UnloggedFailure {
    boolean ok = true;
    for (ChangeResource rsrc : changes.values()) {
      try {
        index.apply(rsrc, new Index.Input());
      } catch (IOException | RestApiException e) {
        ok = false;
        writeError("error", String.format(
            "failed to index change %s: %s", rsrc.getId(), e.getMessage()));
      }
    }
    if (!ok) {
      throw die("failed to index one or more changes");
    }
  }

}
