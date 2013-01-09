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

package com.google.gerrit.server.git.validators;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import com.jcraft.jsch.HostKey;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

public class CommitValidators {
  private static final Logger log = LoggerFactory
      .getLogger(CommitValidators.class);

  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  private static final Pattern NEW_PATCHSET = Pattern
      .compile("^refs/changes/(?:[0-9][0-9])?(/[1-9][0-9]*){1,2}(?:/new)?$");

  public interface Factory {
    CommitValidators create(RefControl refControl, SshInfo sshInfo,
        Repository repo);
  }

  private final PersonIdent gerritIdent;
  private final RefControl refControl;
  private final String canonicalWebUrl;
  private final SshInfo sshInfo;
  private final Repository repo;
  private final DynamicSet<CommitValidationListener> commitValidationListeners;

  @Inject
  CommitValidators(@GerritPersonIdent final PersonIdent gerritIdent,
      @CanonicalWebUrl @Nullable final String canonicalWebUrl,
      final DynamicSet<CommitValidationListener> commitValidationListeners,
      @Assisted final SshInfo sshInfo,
      @Assisted final Repository repo, @Assisted final RefControl refControl) {
    this.gerritIdent = gerritIdent;
    this.refControl = refControl;
    this.canonicalWebUrl = canonicalWebUrl;
    this.sshInfo = sshInfo;
    this.repo = repo;
    this.commitValidationListeners = commitValidationListeners;
  }

  public List<CommitValidationMessage> validateForReceiveCommits(
      CommitReceivedEvent receiveEvent) throws CommitValidationException {

    List<CommitValidationListener> validators =
        new LinkedList<CommitValidationListener>();

    validators.add(new UploadMergesPermissionValidator(refControl));
    validators.add(new AmendedGerritMergeCommitValidationListener(
        refControl, gerritIdent));
    validators.add(new AuthorUploaderValidator(refControl, canonicalWebUrl));
    validators.add(new CommitterUploaderValidator(refControl, canonicalWebUrl));
    validators.add(new SignedOffByValidator(refControl, canonicalWebUrl));
    validators.add(new ChangeIdValidator(refControl, canonicalWebUrl, sshInfo));
    validators.add(new ConfigValidator(refControl, repo));
    validators.add(new PluginCommitValidationListener(commitValidationListeners));

    List<CommitValidationMessage> messages =
        new LinkedList<CommitValidationMessage>();

    try {
      for (CommitValidationListener commitValidator : validators) {
        messages.addAll(commitValidator.onCommitReceived(receiveEvent));
      }
    } catch (CommitValidationException e) {
      // Keep the old messages (and their order) in case of an exception
      messages.addAll(e.getMessages());
      throw new CommitValidationException(e.getMessage(), messages);
    }
    return messages;
  }

  public List<CommitValidationMessage> validateForRevertCommits(
      CommitReceivedEvent receiveEvent) throws CommitValidationException {

    List<CommitValidationListener> validators =
        new LinkedList<CommitValidationListener>();

    validators.add(new UploadMergesPermissionValidator(refControl));
    validators.add(new AmendedGerritMergeCommitValidationListener(
        refControl, gerritIdent));
    validators.add(new AuthorUploaderValidator(refControl, canonicalWebUrl));
    validators.add(new SignedOffByValidator(refControl, canonicalWebUrl));
    validators.add(new ChangeIdValidator(refControl, canonicalWebUrl, sshInfo));
    validators.add(new ConfigValidator(refControl, repo));
    validators.add(new PluginCommitValidationListener(commitValidationListeners));

    List<CommitValidationMessage> messages =
        new LinkedList<CommitValidationMessage>();

    try {
      for (CommitValidationListener commitValidator : validators) {
        messages.addAll(commitValidator.onCommitReceived(receiveEvent));
      }
    } catch (CommitValidationException e) {
      // Keep the old messages (and their order) in case of an exception
      messages.addAll(e.getMessages());
      throw new CommitValidationException(e.getMessage(), messages);
    }
    return messages;
  }

  public static class ChangeIdValidator implements CommitValidationListener {
    private final RefControl refControl;
    private final String canonicalWebUrl;
    private final SshInfo sshInfo;

    public ChangeIdValidator(RefControl refControl, String canonicalWebUrl,
        SshInfo sshInfo) {
      this.refControl = refControl;
      this.canonicalWebUrl = canonicalWebUrl;
      this.sshInfo = sshInfo;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {

      final ProjectControl projectControl = refControl.getProjectControl();
      IdentifiedUser currentUser = (IdentifiedUser) refControl.getCurrentUser();
      final List<String> idList = receiveEvent.commit.getFooterLines(CHANGE_ID);

      if (MagicBranch.isMagicBranch(receiveEvent.command.getRefName())
          || NEW_PATCHSET.matcher(receiveEvent.command.getRefName()).matches()) {
        List<CommitValidationMessage> messages =
            new LinkedList<CommitValidationMessage>();

        if (idList.isEmpty()) {
          if (projectControl.getProjectState().isRequireChangeID()) {
            String errMsg = "missing Change-Id in commit message footer";
            messages.add(getFixedCommitMsgWithChangeId(errMsg, receiveEvent.commit,
                currentUser, canonicalWebUrl, sshInfo));
            throw new CommitValidationException(errMsg, messages);
          }
        } else if (idList.size() > 1) {
          throw new CommitValidationException(
              "multiple Change-Id lines in commit message footer", messages);
        } else {
          final String v = idList.get(idList.size() - 1).trim();
          if (!v.matches("^I[0-9a-f]{8,}.*$")) {
            final String errMsg =
                "missing or invalid Change-Id line format in commit message footer";
            messages.add(getFixedCommitMsgWithChangeId(errMsg, receiveEvent.commit,
                currentUser, canonicalWebUrl, sshInfo));
            throw new CommitValidationException(errMsg, messages);
          }
        }
      }
      return Collections.<CommitValidationMessage>emptyList();
    }
  }

  /**
   * If this is the special project configuration branch, validate the config.
   */
  public static class ConfigValidator implements CommitValidationListener {
    private final RefControl refControl;
    private final Repository repo;

    public ConfigValidator(RefControl refControl, Repository repo) {
      this.refControl = refControl;
      this.repo = repo;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      IdentifiedUser currentUser = (IdentifiedUser) refControl.getCurrentUser();

      if (GitRepositoryManager.REF_CONFIG.equals(refControl.getRefName())) {
        List<CommitValidationMessage> messages =
            new LinkedList<CommitValidationMessage>();

        try {
          ProjectConfig cfg =
              new ProjectConfig(receiveEvent.project.getNameKey());
          cfg.load(repo, receiveEvent.command.getNewId());
          if (!cfg.getValidationErrors().isEmpty()) {
            addError("Invalid project configuration:", messages);
            for (ValidationError err : cfg.getValidationErrors()) {
              addError("  " + err.getMessage(), messages);
            }
            throw new ConfigInvalidException("invalid project configuration");
          }
        } catch (Exception e) {
          log.error("User " + currentUser.getUserName()
              + " tried to push invalid project configuration "
              + receiveEvent.command.getNewId().name() + " for "
              + receiveEvent.project.getName(), e);
          throw new CommitValidationException("invalid project configuration",
              messages);
        }
      }
      return Collections.<CommitValidationMessage>emptyList();
    }
  }

  /** Require permission to upload merges. */
  public static class UploadMergesPermissionValidator implements
      CommitValidationListener {
    private final RefControl refControl;

    public UploadMergesPermissionValidator(RefControl refControl) {
      this.refControl = refControl;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      if (receiveEvent.commit.getParentCount() > 1
          && !refControl.canUploadMerges()) {
        throw new CommitValidationException("you are not allowed to upload merges");
      }
      return Collections.<CommitValidationMessage>emptyList();
    }
  }

  /** Execute commit validation plug-ins */
  public static class PluginCommitValidationListener implements
      CommitValidationListener {
    private final DynamicSet<CommitValidationListener> commitValidationListeners;

    public PluginCommitValidationListener(
        final DynamicSet<CommitValidationListener> commitValidationListeners) {
      this.commitValidationListeners = commitValidationListeners;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      List<CommitValidationMessage> messages =
          new LinkedList<CommitValidationMessage>();

      for (CommitValidationListener validator : commitValidationListeners) {
        try {
          messages.addAll(validator.onCommitReceived(receiveEvent));
        } catch (CommitValidationException e) {
          messages.addAll(e.getMessages());
          throw new CommitValidationException(e.getMessage(), messages);
        }
      }
      return messages;
    }
  }

  public static class SignedOffByValidator implements CommitValidationListener {
    private final RefControl refControl;

    public SignedOffByValidator(RefControl refControl, String canonicalWebUrl) {
      this.refControl = refControl;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      IdentifiedUser currentUser = (IdentifiedUser) refControl.getCurrentUser();
      final PersonIdent committer = receiveEvent.commit.getCommitterIdent();
      final PersonIdent author = receiveEvent.commit.getAuthorIdent();
      final ProjectControl projectControl = refControl.getProjectControl();

      if (projectControl.getProjectState().isUseSignedOffBy()) {
        boolean sboAuthor = false, sboCommitter = false, sboMe = false;
        for (final FooterLine footer : receiveEvent.commit.getFooterLines()) {
          if (footer.matches(FooterKey.SIGNED_OFF_BY)) {
            final String e = footer.getEmailAddress();
            if (e != null) {
              sboAuthor |= author.getEmailAddress().equals(e);
              sboCommitter |= committer.getEmailAddress().equals(e);
              sboMe |= currentUser.getEmailAddresses().contains(e);
            }
          }
        }
        if (!sboAuthor && !sboCommitter && !sboMe
            && !refControl.canForgeCommitter()) {
          throw new CommitValidationException(
              "not Signed-off-by author/committer/uploader in commit message footer");
        }
      }
      return Collections.<CommitValidationMessage>emptyList();
    }
  }

  /** Require that author matches the uploader. */
  public static class AuthorUploaderValidator implements
      CommitValidationListener {
    private final RefControl refControl;
    private final String canonicalWebUrl;

    public AuthorUploaderValidator(RefControl refControl, String canonicalWebUrl) {
      this.refControl = refControl;
      this.canonicalWebUrl = canonicalWebUrl;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      IdentifiedUser currentUser = (IdentifiedUser) refControl.getCurrentUser();
      final PersonIdent author = receiveEvent.commit.getAuthorIdent();

      if (!currentUser.getEmailAddresses().contains(author.getEmailAddress())
          && !refControl.canForgeAuthor()) {
        List<CommitValidationMessage> messages =
            new LinkedList<CommitValidationMessage>();

        messages.add(getInvalidEmailError(receiveEvent.commit, "author", author,
            currentUser, canonicalWebUrl));
        throw new CommitValidationException("invalid author", messages);
      }
      return Collections.<CommitValidationMessage>emptyList();
    }
  }

  /** Require that committer matches the uploader. */
  public static class CommitterUploaderValidator implements
      CommitValidationListener {
    private final RefControl refControl;
    private final String canonicalWebUrl;

    public CommitterUploaderValidator(RefControl refControl,
        String canonicalWebUrl) {
      this.refControl = refControl;
      this.canonicalWebUrl = canonicalWebUrl;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      IdentifiedUser currentUser = (IdentifiedUser) refControl.getCurrentUser();
      final PersonIdent committer = receiveEvent.commit.getCommitterIdent();
      if (!currentUser.getEmailAddresses()
          .contains(committer.getEmailAddress())
          && !refControl.canForgeCommitter()) {
        List<CommitValidationMessage> messages =
            new LinkedList<CommitValidationMessage>();
        messages.add(getInvalidEmailError(receiveEvent.commit, "committer", committer,
            currentUser, canonicalWebUrl));
        throw new CommitValidationException("invalid committer", messages);
      }
      return Collections.<CommitValidationMessage>emptyList();
    }
  }

  /**
   * Don't allow the user to amend a merge created by Gerrit Code Review. This
   * seems to happen all too often, due to users not paying any attention to
   * what they are doing.
   */
  public static class AmendedGerritMergeCommitValidationListener implements
      CommitValidationListener {
    private final PersonIdent gerritIdent;
    private final RefControl refControl;

    public AmendedGerritMergeCommitValidationListener(
        final RefControl refControl, final PersonIdent gerritIdent) {
      this.refControl = refControl;
      this.gerritIdent = gerritIdent;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      final PersonIdent author = receiveEvent.commit.getAuthorIdent();

      if (receiveEvent.commit.getParentCount() > 1
          && author.getName().equals(gerritIdent.getName())
          && author.getEmailAddress().equals(gerritIdent.getEmailAddress())
          && !refControl.canForgeGerritServerIdentity()) {
        throw new CommitValidationException("do not amend merges not made by you");
      }
      return Collections.<CommitValidationMessage>emptyList();
    }
  }

  private static CommitValidationMessage getInvalidEmailError(RevCommit c, String type,
      PersonIdent who, IdentifiedUser currentUser, String canonicalWebUrl) {
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
    return new CommitValidationMessage(sb.toString(), false);
  }

  /**
   * We handle 3 cases:
   * 1. No change id in the commit message at all.
   * 2. Change id last in the commit message but missing empty line to create the footer.
   * 3. There is a change-id somewhere in the commit message, but we ignore it.
   *
   * @return The fixed up commit message
   */
  private static CommitValidationMessage getFixedCommitMsgWithChangeId(final String errMsg,
      final RevCommit c, final IdentifiedUser currentUser,
      String canonicalWebUrl, final SshInfo sshInfo) {
    final String changeId = "Change-Id:";
    StringBuilder sb = new StringBuilder();
    sb.append("ERROR: ").append(errMsg);
    sb.append('\n');
    sb.append("Suggestion for commit message:\n");

    if (c.getFullMessage().indexOf(changeId) == -1) {
      sb.append(c.getFullMessage());
      sb.append('\n');
      sb.append(changeId).append(" I").append(c.name());
    } else {
      String lines[] = c.getFullMessage().trim().split("\n");
      String lastLine = lines.length > 0 ? lines[lines.length - 1] : "";

      if (lastLine.indexOf(changeId) == 0) {
        for (int i = 0; i < lines.length - 1; i++) {
          sb.append(lines[i]);
          sb.append('\n');
        }

        sb.append('\n');
        sb.append(lastLine);
      } else {
        sb.append(c.getFullMessage());
        sb.append('\n');
        sb.append(changeId).append(" I").append(c.name());
        sb.append('\n');
        sb.append("Hint: A potential Change-Id was found, but it was not in the footer of the commit message.");
      }
    }
    sb.append('\n');
    sb.append('\n');
    sb.append("Hint: To automatically insert Change-Id, install the hook:\n");
    sb.append(getCommitMessageHookInstallationHint(currentUser,
        canonicalWebUrl, sshInfo)).append('\n');
    sb.append('\n');

    return new CommitValidationMessage(sb.toString(), false);
  }

  private static String getCommitMessageHookInstallationHint(
      final IdentifiedUser currentUser, String canonicalWebUrl,
      final SshInfo sshInfo) {
    final List<HostKey> hostKeys = sshInfo.getHostKeys();

    // If there are no SSH keys, the commit-msg hook must be installed via
    // HTTP(S)
    if (hostKeys.isEmpty()) {
      String p = ".git/hooks/commit-msg";
      return String.format(
          "  curl -o %s %s/tools/hooks/commit-msg ; chmod +x %s", p,
          getGerritUrl(canonicalWebUrl), p);
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

    return String.format("  scp -p -P %d %s@%s:hooks/commit-msg .git/hooks/",
        sshPort, currentUser.getUserName(), sshHost);
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

  private static void addError(String error,
      List<CommitValidationMessage> messages) {
    messages.add(new CommitValidationMessage(error, true));
  }
}
