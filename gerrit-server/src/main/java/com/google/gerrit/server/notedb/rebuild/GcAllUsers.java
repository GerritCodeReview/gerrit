package com.google.gerrit.server.notedb.rebuild;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.inject.Inject;
import java.io.PrintWriter;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcAllUsers {
  private static final Logger log = LoggerFactory.getLogger(GcAllUsers.class);

  private final AllUsersName allUsers;
  private final GarbageCollection.Factory gcFactory;
  private final GitRepositoryManager repoManager;

  @Inject
  GcAllUsers(
      AllUsersName allUsers,
      GarbageCollection.Factory gcFactory,
      GitRepositoryManager repoManager) {
    this.allUsers = allUsers;
    this.gcFactory = gcFactory;
    this.repoManager = repoManager;
  }

  public void runWithLogger() {
    // Print log messages using logger, and skip progress.
    run(s -> log.info(s), null);
  }

  public void run(PrintWriter writer) {
    // Print both log messages and progress to given writer.
    run(checkNotNull(writer)::println, writer);
  }

  private void run(Consumer<String> logOneLine, @Nullable PrintWriter progressWriter) {
    if (!(repoManager instanceof LocalDiskRepositoryManager)) {
      logOneLine.accept("Skipping GC of " + allUsers + "; not a local disk repo");
      return;
    }
    GarbageCollectionResult result =
        gcFactory.create().run(ImmutableList.of(allUsers), progressWriter);
    if (!result.hasErrors()) {
      return;
    }
    for (GarbageCollectionResult.Error e : result.getErrors()) {
      switch (e.getType()) {
        case GC_ALREADY_SCHEDULED:
          logOneLine.accept("GC already scheduled for " + e.getProjectName());
          break;
        case GC_FAILED:
          logOneLine.accept("GC failed for " + e.getProjectName());
          break;
        case REPOSITORY_NOT_FOUND:
          logOneLine.accept(e.getProjectName() + " repo not found");
          break;
        default:
          logOneLine.accept("GC failed for " + e.getProjectName() + ": " + e.getType());
          break;
      }
    }
  }
}
