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

package com.google.gerrit.server.git;

import com.google.common.base.Strings;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.CommitValidationResult;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.MagicBranch;

import com.jcraft.jsch.HostKey;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class CommitUtil {
  private static final Logger log = LoggerFactory.getLogger(CommitUtil.class);

  private static final Pattern NEW_PATCHSET = Pattern
      .compile("^refs/changes/(?:[0-9][0-9]/)?([1-9][0-9]*)(?:/new)?$");

  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  public static boolean validateCommitSettings(final RefControl ctl,
      final RevCommit c, final RevWalk rw, boolean checkChangeId,
      final String canonicalWebUrl, final SshInfo sshInfo,
      final PersonIdent gerritIdent, final IdentifiedUser currentUser,
      List<CommitValidationMessage> messages, CommitValidationCallback callback)
      throws MissingObjectException, IOException {
    rw.parseBody(c);

    final PersonIdent committer = c.getCommitterIdent();
    final PersonIdent author = c.getAuthorIdent();
    final ProjectControl projectControl = ctl.getProjectControl();

    // Require permission to upload merges.
    if (c.getParentCount() > 1 && !ctl.canUploadMerges()) {
      callback.onRejected("you are not allowed to upload merges", messages);
      return false;
    }

    // Don't allow the user to amend a merge created by Gerrit Code Review.
    // This seems to happen all too often, due to users not paying any
    // attention to what they are doing.
    //
    if (c.getParentCount() > 1
        && author.getName().equals(gerritIdent.getName())
        && author.getEmailAddress().equals(gerritIdent.getEmailAddress())
        && !ctl.canForgeGerritServerIdentity()) {
      callback.onRejected("do not amend merges not made by you", messages);
      return false;
    }

    // Require that author matches the uploader.
    //
    if (!currentUser.getEmailAddresses().contains(author.getEmailAddress())
        && !ctl.canForgeAuthor()) {
      addInvalidEmailError(c, "author", author, currentUser, canonicalWebUrl,
          messages);
      callback.onRejected("invalid author", messages);
      return false;
    }

    // Require that committer matches the uploader.
    //
    if (!currentUser.getEmailAddresses().contains(committer.getEmailAddress())
        && !ctl.canForgeCommitter()) {
      addInvalidEmailError(c, "committer", committer, currentUser,
          canonicalWebUrl, messages);
      callback.onRejected("invalid committer", messages);
      return false;
    }

    if (projectControl.getProjectState().isUseSignedOffBy()) {
      // If the project wants Signed-off-by / Acked-by lines, verify we
      // have them for the blamable parties involved on this change.
      //
      boolean sboAuthor = false, sboCommitter = false, sboMe = false;
      for (final FooterLine footer : c.getFooterLines()) {
        if (footer.matches(FooterKey.SIGNED_OFF_BY)) {
          final String e = footer.getEmailAddress();
          if (e != null) {
            sboAuthor |= author.getEmailAddress().equals(e);
            sboCommitter |= committer.getEmailAddress().equals(e);
            sboMe |= currentUser.getEmailAddresses().contains(e);
          }
        }
      }
      if (!sboAuthor && !sboCommitter && !sboMe && !ctl.canForgeCommitter()) {
        callback
            .onRejected(
                "not Signed-off-by author/committer/uploader in commit message footer",
                messages);
        return false;
      }
    }

    final List<String> idList = c.getFooterLines(CHANGE_ID);
    if (checkChangeId) {
      if (idList.isEmpty()) {
        if (projectControl.getProjectState().isRequireChangeID()) {
          String errMsg = "missing Change-Id in commit message footer";
          addFixedCommitMsgWithChangeId(errMsg, c, currentUser,
              canonicalWebUrl, sshInfo, messages);
          callback.onRejected(errMsg, messages);
          return false;
        }
      } else if (idList.size() > 1) {
        callback.onRejected(
            "multiple Change-Id lines in commit message footer", messages);
        return false;
      } else {
        final String v = idList.get(idList.size() - 1).trim();
        if (!v.matches("^I[0-9a-f]{8,}.*$")) {
          final String errMsg =
              "missing or invalid Change-Id line format in commit message footer";
          addFixedCommitMsgWithChangeId(errMsg, c, currentUser,
              canonicalWebUrl, sshInfo, messages);
          callback.onRejected(errMsg, messages);
          return false;
        }
      }
    }

    return true;
  }

  public static boolean validateCommit(final RefControl ctl,
      final ReceiveCommand cmd, final RevCommit c, final RevWalk rw,
      final PersonIdent gerritIdent, final IdentifiedUser currentUser,
      final String canonicalWebUrl, final NoteMap rejectCommits,
      final Repository repo,
      final DynamicSet<CommitValidationListener> commitValidators,
      final SshInfo sshInfo, CommitValidationCallback callback)
      throws MissingObjectException, IOException {

    List<CommitValidationMessage> messages =
        new LinkedList<CommitValidationMessage>();
    final ProjectControl projectControl = ctl.getProjectControl();
    final Project project = projectControl.getProject();

    boolean isMagicBranch =
        MagicBranch.isMagicBranch(cmd.getRefName())
            || NEW_PATCHSET.matcher(cmd.getRefName()).matches();

    if (!validateCommitSettings(ctl, c, rw, isMagicBranch, canonicalWebUrl,
        sshInfo, gerritIdent, currentUser, messages, callback)) {
      return false;
    }

    // Check for banned commits to prevent them from entering the tree again.
    if (rejectCommits.contains(c)) {
      callback.onRejected("contains banned commit " + c.getName(), messages);
      return false;
    }

    // If this is the special project configuration branch, validate the config.
    if (GitRepositoryManager.REF_CONFIG.equals(ctl.getRefName())) {
      try {
        ProjectConfig cfg = new ProjectConfig(project.getNameKey());
        cfg.load(repo, cmd.getNewId());
        if (!cfg.getValidationErrors().isEmpty()) {
          addError("Invalid project configuration:", messages);
          for (ValidationError err : cfg.getValidationErrors()) {
            addError("  " + err.getMessage(), messages);
          }
          callback.onRejected("invalid project configuration", messages);
          log.error("User " + currentUser.getUserName()
              + " tried to push invalid project configuration "
              + cmd.getNewId().name() + " for " + project.getName());
          return false;
        }
      } catch (Exception e) {
        callback.onRejected("invalid project configuration", messages);
        log.error("User " + currentUser.getUserName()
            + " tried to push invalid project configuration "
            + cmd.getNewId().name() + " for " + project.getName(), e);
        return false;
      }
    }

    for (CommitValidationListener validator : commitValidators) {
      CommitValidationResult validationResult =
          validator.onCommitReceived(new CommitReceivedEvent(cmd, project, ctl
              .getRefName(), c, currentUser));
      final String message = validationResult.getValidationReason();
      if (!validationResult.isValidated()) {
        callback.onRejected(message, messages);
        return false;
      } else if (!Strings.isNullOrEmpty(message)) {
        addMessage(String.format("(W) %s", message), messages);
      }
    }

    callback.onAccepted(messages);
    return true;
  }

  private static void addInvalidEmailError(RevCommit c, String type,
      PersonIdent who, IdentifiedUser currentUser, String canonicalWebUrl,
      List<CommitValidationMessage> messages) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    sb.append("ERROR:  In commit " + c.name() + "\n");
    sb.append("ERROR:  " + type + " email address " + who.getEmailAddress()
        + "\n");
    sb.append("ERROR:  does not match your user account.\n");
    sb.append("ERROR:\n");
    if (currentUser.getEmailAddresses().isEmpty()) {
      sb.append("ERROR:  You have not registered any email addresses.\n");
    } else {
      sb.append("ERROR:  The following addresses are currently registered:\n");
      for (String address : currentUser.getEmailAddresses()) {
        sb.append("ERROR:    " + address + "\n");
      }
    }
    sb.append("ERROR:\n");
    if (canonicalWebUrl != null) {
      sb.append("ERROR:  To register an email address, please visit:\n");
      sb.append("ERROR:  " + canonicalWebUrl + "#" + PageLinks.SETTINGS_CONTACT
          + "\n");
    }
    sb.append("\n");
    addMessage(sb.toString(), messages);
  }

  private static void addFixedCommitMsgWithChangeId(final String errMsg,
      final RevCommit c, final IdentifiedUser currentUser,
      String canonicalWebUrl, final SshInfo sshInfo,
      List<CommitValidationMessage> messages) {
    // We handle 3 cases:
    // 1. No change id in the commit message at all.
    // 2. change id last in the commit message but missing empty line to create
    // the footer.
    // 3. there is a change-id somewhere in the commit message, but we ignore
    // it.
    final String changeId = "Change-Id:";
    StringBuilder sb = new StringBuilder();
    sb.append("ERROR: ").append(errMsg);
    sb.append("\n");
    sb.append("Suggestion for commit message:\n");

    if (c.getFullMessage().indexOf(changeId) == -1) {
      sb.append(c.getFullMessage());
      sb.append("\n");
      sb.append(changeId).append(" I").append(c.name());
    } else {
      String lines[] = c.getFullMessage().trim().split("\n");
      String lastLine = lines.length > 0 ? lines[lines.length - 1] : "";

      if (lastLine.indexOf(changeId) == 0) {
        for (int i = 0; i < lines.length - 1; i++) {
          sb.append(lines[i]);
          sb.append("\n");
        }

        sb.append("\n");
        sb.append(lastLine);
      } else {
        sb.append(c.getFullMessage());
        sb.append("\n");
        sb.append(changeId).append(" I").append(c.name());
        sb.append("\nHint: A potential Change-Id was found, but it was not in the footer of the commit message.");
      }
    }
    sb.append("\n");
    sb.append("Hint: To automatically add a Change-Id to commit messages, install the commit-msg hook:\n");
    sb.append(getCommitMessageHookInstallationHint(currentUser,
        canonicalWebUrl, sshInfo));

    addMessage(sb.toString(), messages);
  }

  private static String getCommitMessageHookInstallationHint(
      final IdentifiedUser currentUser, String canonicalWebUrl,
      final SshInfo sshInfo) {
    final List<HostKey> hostKeys = sshInfo.getHostKeys();

    // If there are no SSH keys, the commit-msg hook must be installed via
    // HTTP(S)
    if (hostKeys.isEmpty()) {
      return "$ curl -o .git/hooks/commit-msg " + getGerritUrl(canonicalWebUrl)
          + "/tools/hooks/commit-msg\n" + "$ chmod +x .git/hooks/commit-msg";
    }

    // SSH keys exist, so the hook can be installed with scp.
    String sshHost;
    int sshPort;
    String host = hostKeys.get(0).getHost();
    int c = host.lastIndexOf(':');
    if (0 <= c) {
      if (host.startsWith("*:")) {
        sshHost = getGerritHost(canonicalWebUrl);
      } else {
        sshHost = host.substring(0, c);
      }
      sshPort = Integer.parseInt(host.substring(c + 1));
    } else {
      sshHost = host;
      sshPort = 22;
    }

    return "$ scp -p -P " + sshPort + " " + currentUser.getUserName() + "@"
        + sshHost + ":hooks/commit-msg .git/hooks/";
  }

  private static void addMessage(String message,
      List<CommitValidationMessage> messages) {
    messages.add(new CommitValidationMessage(message, false));
  }

  private static void addError(String error,
      List<CommitValidationMessage> messages) {
    messages.add(new CommitValidationMessage(error, true));
  }

  /**
   * Get the Gerrit URL.
   *
   * @return the canonical URL (with any trailing slash removed) if it is
   *         configured, otherwise fall back to "http://hostname" where hostname
   *         is the value returned by {@link #getGerritHost()}.
   */
  private static String getGerritUrl(String canonicalWebUrl) {
    if (canonicalWebUrl != null) {
      if (canonicalWebUrl.endsWith("/")) {
        return canonicalWebUrl.substring(0, canonicalWebUrl.lastIndexOf("/"));
      }
      return canonicalWebUrl;
    } else {
      return "http://" + getGerritHost(canonicalWebUrl);
    }
  }

  /**
   * Get the Gerrit hostname.
   *
   * @return the hostname from the canonical URL if it is configured, otherwise
   *         whatever the OS says the hostname is.
   */
  private static String getGerritHost(String canonicalWebUrl) {
    String host;
    if (canonicalWebUrl != null) {
      try {
        host = new URL(canonicalWebUrl).getHost();
      } catch (MalformedURLException e) {
        host = SystemReader.getInstance().getHostname();
      }
    } else {
      host = SystemReader.getInstance().getHostname();
    }
    return host;
  }

  static public abstract class CommitValidationCallback {
    public abstract void onRejected(String rejectReason,
        List<CommitValidationMessage> messages);

    public abstract void onAccepted(List<CommitValidationMessage> messages);
  }
}
