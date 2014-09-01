package com.audiocodes.gwapp.plugins.acGerrit;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidationListener;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

@Singleton
public class MergeSubmoduleValidator implements MergeValidationListener {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalDiskRepositoryManager repoManager;

  @Inject
  MergeSubmoduleValidator(LocalDiskRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  private boolean testBranch(RevWalk subRevwalk, RevCommit subCommit, ObjectId branchCommit) {
    try {
      RevCommit subBranch = subRevwalk.parseCommit(branchCommit);
      return subRevwalk.isMergedInto(subCommit, subBranch);
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public void onPreMerge(
      Repository repo,
      CodeReviewRevWalk rw,
      CodeReviewCommit commit,
      ProjectState destProject,
      BranchNameKey destBranch,
      PatchSet.Id patchSetId,
      IdentifiedUser caller)
      throws MergeValidationException {

    final String projectName = destProject.getProject().getNameKey().get();
    if (!projectName.equals("TP/GWApp")) return;
    String message = new String();
    try {
      RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
      try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        df.setRepository(repo);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setDetectRenames(true);
        List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
        for (DiffEntry diff : diffs) {
          if (diff.getNewMode() != FileMode.GITLINK) continue;
          String path = diff.getNewPath();
          ObjectId newIdObj = diff.getNewId().toObjectId();
          String newId = newIdObj.name();
          try (SubmoduleWalk subWalk =
              SubmoduleWalk.forPath(repo, commit.getTree().getId(), path)) {
            final String subUrl = subWalk.getModulesUrl();
            // If the url is not relative, there's not much we can do.
            if (!subUrl.startsWith(".")) continue;
            final String subProject =
                (destProject.getProject().getName() + "/" + subUrl)
                    .replaceAll("[^/]*/../", "")
                    .replaceFirst("\\.git$", "");
            logger.atFine().log("Testing submodule " + subProject + ". parent: " + repo.getDirectory());
            Repository subRepo = repoManager.openRepository(Project.nameKey(subProject));
            try (RevWalk subRevwalk = new RevWalk(subRepo)) {
              logger.atFine().log("Opened repo. destBranch: " + destBranch.branch() + ". new: " + newId);
              RevCommit subCommit;
              try {
                subCommit = subRevwalk.parseCommit(newIdObj);
              } catch (MissingObjectException e) {
                message += "Submodule " + path + " references an invalid commit. " + newId + "\n";
                continue;
              }
              try {
                RevCommit subBranch = subRevwalk.parseCommit(subRepo.resolve(destBranch.branch()));
                if (!testBranch(subRevwalk, subCommit, subBranch))
                  message +=
                      "Submodule " + path + " references an unsubmitted commit: " + newId + "\n";
              } catch (MissingObjectException | NullPointerException e) {
                try (Git git = new Git(subRepo)) {
                  boolean skip = false;
                  for (Ref ref : git.branchList().call()) {
                    RevCommit subBranch = subRevwalk.parseCommit(ref.getObjectId());
                    if (testBranch(subRevwalk, subCommit, subBranch)) {
                      skip = true;
                      break;
                    }
                  }
                  if (skip) continue;
                } catch (Exception ex) {
                  logger.atSevere().withCause(ex).log("Error while resolving branches");
                }
                message +=
                    "Submodule " + path + " references an unsubmitted commit: " + newId + "\n";
              }
            }
          } catch (ConfigInvalidException e) {
            logger.atSevere().withCause(e).log("Config Error");
          }
        }
      }
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Error on submodule validation");
      message += "Unknown error";
    }
    if (!message.isEmpty()) throw new MergeValidationException(message);
  }
}
