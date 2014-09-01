package com.audiocodes.gwapp.plugins.acGerrit;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidationListener;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;

@Singleton
public class MergeUDRValidator implements MergeValidationListener {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private HashMap<String, AcBranch> gwappBranches;
  private HashMap<String, AcBranch> tpBranches;
  private final String ScriptPath;

  private class AcBranch {
    private String project;
    private String name;
    private String lastUser;
    private Date retrievedOn;

    AcBranch(String project, String name) {
      this.project = project;
      this.name = name;
    }

    public boolean isAllowed(String userName) {
      // Use a 5 minute cache for successful attempts only. If the user is not found, try cleartool
      if (lastUser != null) {
        Date now = new Date();
        if ((now.getTime() - retrievedOn.getTime()) < 300000) {
          if (lastUser.equals(userName)) return true;
        } else {
          lastUser = null;
          retrievedOn = null;
        }
      }

      try {
        logger.atFine().log(
            "running ruby " + ScriptPath + " " + project + " " + name + " " + userName);
        Process process = new ProcessBuilder("ruby", ScriptPath, project, name, userName).start();
        int result = process.waitFor();
        if (result == 0) {
          lastUser = userName;
          retrievedOn = new Date();
          return true;
        }
        logger.atWarning().log("process failed for " + userName);
        return false;
      } catch (Exception e) {
      }
      return true;
    }
  }

  private static String findScriptPath() {
    String[] path = System.getenv("PATH").split(System.getProperty("path.separator"));
    for (String p : path) {
      if (p.endsWith("Utils")) return p.replace('\\', '/') + "/ct-isallowed.rb";
    }
    return null;
  }

  MergeUDRValidator() {
    gwappBranches = new HashMap<>();
    tpBranches = new HashMap<>();
    ScriptPath = findScriptPath();
  }

  private AcBranch getBranch(String project, String branch) {
    HashMap<String, AcBranch> branches = null;
    if (project.equals("GWApp")) branches = gwappBranches;
    else if (project.equals("TrunkPackRam")) branches = tpBranches;
    else return null;

    branch = branch.replace("refs/heads/", "");
    AcBranch b = branches.get(branch);
    if (b != null) return b;
    b = new AcBranch(project, branch);
    branches.put(branch, b);
    return b;
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

    if (ScriptPath == null) return;
    AcBranch TPRBranch = null;
    if (destBranch.branch().compareTo("7") >= 0) {
      try {
        RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
          df.setRepository(repo);
          df.setDiffComparator(RawTextComparator.DEFAULT);
          df.setDetectRenames(true);
          List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
          for (DiffEntry diff : diffs) {
            if (diff.getNewPath().startsWith("TrunkPackRam/")) {
              TPRBranch = getBranch("TrunkPackRam", destBranch.branch());
              break;
            }
          }
        }
      } catch (IOException e) {
      }
    }
    final String projectName = destProject.getProject().getNameKey().get();
    final AcBranch branch = getBranch(projectName, destBranch.branch());
    if (branch == null) return;

    final String userName = caller.getUserName().get().toLowerCase();
    String streams = "";
    if (!branch.isAllowed(userName)) streams = projectName;
    if (TPRBranch != null && !TPRBranch.isAllowed(userName)) {
      if (streams != "") streams += " and ";
      streams += "TrunkPackRam";
    }
    if (streams != "")
      throw new MergeValidationException(
          "User must be active in " + streams + " UDR in order to submit");
  }
}
