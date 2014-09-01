package com.audiocodes.gwapp.plugins.acGerrit;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidationListener;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class MergeVIValidator implements MergeValidationListener {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String baseDir = "/var/gerrit/tools/ac-plugin-files/";
  private static final String IssueExp = "((?:VI|[A-Z0-9]{2,}-)\\d+)";
  private static final Pattern VI_Pattern1 = Pattern.compile("Issue: " + IssueExp);
  private static final Pattern VI_Pattern2 = Pattern.compile("\\[" + IssueExp + "\\]");
  private HashMap<String, String> userNames;

  MergeVIValidator() {
    userNames = new HashMap<>();

    try {
      List<String> lines =
          Files.readAllLines(Paths.get(baseDir + "UserList.txt"), Charset.defaultCharset());

      for (String line : lines) {
        String[] entries = line.split(":");
        if (entries.length < 3) continue;
        // Map email to VI user name
        userNames.put(entries[2].toLowerCase(), entries[1]);
      }
    } catch (IOException e) {
      System.out.println(e);
    }
  }

  @Override
  public void onPreMerge(
      Repository repo,
      CodeReviewRevWalk rw,
      CodeReviewCommit commit,
      ProjectState projectState,
      BranchNameKey destBranch,
      PatchSet.Id patchSetId,
      IdentifiedUser caller)
      throws MergeValidationException {

    if (destBranch.branch().startsWith("refs/heads/collab/")) return;

    final String project = projectState.getProject().getNameKey().get();
    if (project.startsWith("TP/Tools/") && !project.contains("VoiceAIConnector")) return;

    final LabelTypes labelTypes = projectState.getLabelTypes(commit.getNotes());
    final LabelType viLabel = labelTypes.byLabel("VI-Check");
    final LabelType jiraLabel = labelTypes.byLabel("Jira-Check");
    String bugTracking = "Jira";
    if (jiraLabel == null) {
      if (viLabel != null) bugTracking = "VI";
      else if (!project.startsWith("TP/")) return;
    }

    String email = "";
    String fullName = "";
    String viNumber = "";

    try {
      rw.parseBody(commit);
      String commitMessage = commit.getFullMessage();
      fullName = commit.getAuthorIdent().getName().toLowerCase();
      email =
          commit.getAuthorIdent().getEmailAddress().toLowerCase().replace("@audiocodes.com", "");
      Matcher m1 = VI_Pattern1.matcher(commitMessage);
      if (m1.find()) {
        viNumber = m1.group(1);
      } else {
        Matcher m2 = VI_Pattern2.matcher(commitMessage);
        if (m2.find()) viNumber = m2.group(1);
      }
    } catch (IOException e) {
    }

    String viUserName = userNames.get(email);

    if (viUserName == null) viUserName = fullName;

    String message;
    try {
      logger.atFine().log(
          "running perl "
              + baseDir
              + "CheckIssue.pl '"
              + viNumber
              + "' "
              + viUserName
              + " \""
              + commit.getId().getName()
              + "\" "
              + project
              + ", Branch: "
              + destBranch.branch()
              + ", label: '"
              + bugTracking
              + "'");
      Process process =
          new ProcessBuilder(
                  "perl",
                  baseDir + "CheckIssue.pl",
                  viNumber,
                  viUserName,
                  commit.getId().getName(),
                  project,
                  destBranch.branch(),
                  bugTracking)
              .start();
      InputStream stream = process.getInputStream();
      try (Scanner s = new Scanner(stream)) {
        message = s.useDelimiter("\\A").hasNext() ? s.next() : "";
      }
      int result = process.waitFor();
      if (result == 0) return;
      stream.close();
    } catch (Exception e) {
      message = "CheckIssue unexpected failure";
    }
    logger.atWarning().log("process failed for " + fullName + ": " + message);
    throw new MergeValidationException(message);
  }
}
