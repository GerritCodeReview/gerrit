package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.FanOutExecutor;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.inject.Inject;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.PushCertificate;

/**
 * Performs an update on {@code All-Users} asynchronously if required. No-op in case no updates were
 * scheduled for asynchronous execution.
 */
public class AllUsersAsyncUpdate {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<PersonIdent> serverIdent;
  private final ExecutorService executor;
  private final AllUsersName allUsersName;
  private final GitRepositoryManager repoManager;

  private OpenRepo allUsersRepo;
  private boolean canCloseEarly = true;

  @Inject
  AllUsersAsyncUpdate(
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      @FanOutExecutor ExecutorService executor,
      AllUsersName allUsersName,
      GitRepositoryManager repoManager) {
    this.serverIdent = serverIdent;
    this.executor = executor;
    this.allUsersName = allUsersName;
    this.repoManager = repoManager;
  }

  /** Returns an OpenRepo handle for All-Users. */
  OpenRepo repo() throws IOException {
    allUsersRepo = firstNonNull(allUsersRepo, OpenRepo.open(repoManager, allUsersName));
    return allUsersRepo;
  }

  /** Returns true if no operations should be performed on the repo. */
  boolean isEmpty() {
    return allUsersRepo == null || allUsersRepo.cmds.isEmpty();
  }

  /** Closes the All-Users repository if there are no asynchronous operations pending. */
  void maybeClose() {
    if (allUsersRepo == null || !canCloseEarly) {
      return;
    }

    try {
      allUsersRepo.close();
    } catch (RuntimeException e) {
      logger.atSevere().withCause(e).log("Unable to close repo");
    }
  }

  /** Executes repository update asynchronously. No-op in case no updates were scheduled. */
  void execute(PersonIdent refLogIdent, String refLogMessage, PushCertificate pushCert) {
    if (allUsersRepo == null || allUsersRepo.cmds.isEmpty()) {
      return;
    }

    // There are operations to be performed asynchronously, so we can't close this early. The async
    // operation will close the repo.
    canCloseEarly = false;
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        executor.submit(
            () -> {
              try {
                allUsersRepo.flush();
                BatchRefUpdate bru = allUsersRepo.repo.getRefDatabase().newBatchUpdate();
                bru.setPushCertificate(pushCert);
                if (refLogMessage != null) {
                  bru.setRefLogMessage(refLogMessage, false);
                } else {
                  bru.setRefLogMessage(
                      firstNonNull(NoteDbUtil.guessRestApiHandler(), "Update NoteDb refs"), false);
                }
                bru.setRefLogIdent(refLogIdent != null ? refLogIdent : serverIdent.get());
                bru.setAtomic(true);
                allUsersRepo.cmds.addTo(bru);
                bru.setAllowNonFastForwards(true);
                RefUpdateUtil.executeChecked(bru, allUsersRepo.rw);
              } catch (IOException e) {
                logger.atSevere().withCause(e).log(
                    "Failed to delete draft comments asynchronously after publishing them");
              } finally {
                allUsersRepo.close();
              }
            });
  }
}
