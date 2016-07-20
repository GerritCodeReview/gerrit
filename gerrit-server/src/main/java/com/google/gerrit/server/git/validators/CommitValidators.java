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

import static com.google.gerrit.reviewdb.client.Change.CHANGE_ID_PATTERN;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CONFIG;

import com.google.common.base.CharMatcher;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.WatchConfig;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.ReceiveCommits;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import com.jcraft.jsch.HostKey;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class CommitValidators {
  private static final Logger log = LoggerFactory
      .getLogger(CommitValidators.class);

  public enum Policy {
    /** Use {@link #validateForGerritCommits}. */
    GERRIT,

    /** Use {@link #validateForReceiveCommits}. */
    RECEIVE_COMMITS,

    /** Do not validate commits. */
    NONE
  }

  public interface Factory {
    CommitValidators create(RefControl refControl, SshInfo sshInfo,
        Repository repo);
  }

  private final PersonIdent gerritIdent;
  private final RefControl refControl;
  private final String canonicalWebUrl;
  private final String installCommitMsgHookCommand;
  private final SshInfo sshInfo;
  private final Repository repo;
  private final DynamicSet<CommitValidationListener> commitValidationListeners;
  private final AllUsersName allUsers;

  @Inject
  CommitValidators(@GerritPersonIdent PersonIdent gerritIdent,
      @CanonicalWebUrl @Nullable String canonicalWebUrl,
      @GerritServerConfig Config config,
      DynamicSet<CommitValidationListener> commitValidationListeners,
      AllUsersName allUsers,
      @Assisted SshInfo sshInfo,
      @Assisted Repository repo,
      @Assisted RefControl refControl) {
    this.gerritIdent = gerritIdent;
    this.canonicalWebUrl = canonicalWebUrl;
    this.installCommitMsgHookCommand =
        config.getString("gerrit", null, "installCommitMsgHookCommand");
    this.commitValidationListeners = commitValidationListeners;
    this.allUsers = allUsers;
    this.sshInfo = sshInfo;
    this.repo = repo;
    this.refControl = refControl;
  }

  public List<CommitValidationMessage> validateForReceiveCommits(
      CommitReceivedEvent receiveEvent, NoteMap rejectCommits)
      throws CommitValidationException {

    List<CommitValidationListener> validators = new LinkedList<>();

    validators.add(new UploadMergesPermissionValidator(refControl));
    validators.add(new AmendedGerritMergeCommitValidationListener(
        refControl, gerritIdent));
    validators.add(new AuthorUploaderValidator(refControl, canonicalWebUrl));
    validators.add(new CommitterUploaderValidator(refControl, canonicalWebUrl));
    validators.add(new SignedOffByValidator(refControl));
    if (MagicBranch.isMagicBranch(receiveEvent.command.getRefName())
        || ReceiveCommits.NEW_PATCHSET.matcher(
            receiveEvent.command.getRefName()).matches()) {
      validators.add(new ChangeIdValidator(refControl, canonicalWebUrl,
          installCommitMsgHookCommand, sshInfo));
    }
    validators.add(new ConfigValidator(refControl, repo, allUsers));
    validators.add(new BannedCommitsValidator(rejectCommits));
    validators.add(new PluginCommitValidationListener(commitValidationListeners));

    List<CommitValidationMessage> messages = new LinkedList<>();

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

  public List<CommitValidationMessage> validateForGerritCommits(
      CommitReceivedEvent receiveEvent) throws CommitValidationException {

    List<CommitValidationListener> validators = new LinkedList<>();

    validators.add(new UploadMergesPermissionValidator(refControl));
    validators.add(new AmendedGerritMergeCommitValidationListener(
        refControl, gerritIdent));
    validators.add(new AuthorUploaderValidator(refControl, canonicalWebUrl));
    validators.add(new SignedOffByValidator(refControl));
    if (MagicBranch.isMagicBranch(receiveEvent.command.getRefName())
        || ReceiveCommits.NEW_PATCHSET.matcher(
            receiveEvent.command.getRefName()).matches()) {
      validators.add(new ChangeIdValidator(refControl, canonicalWebUrl,
          installCommitMsgHookCommand, sshInfo));
    }
    validators.add(new ConfigValidator(refControl, repo, allUsers));
    validators.add(new PluginCommitValidationListener(commitValidationListeners));

    List<CommitValidationMessage> messages = new LinkedList<>();

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
    private static final int SHA1_LENGTH = 7;
    private static final String CHANGE_ID_PREFIX =
        FooterConstants.CHANGE_ID.getName() + ":";
    private static final String MISSING_CHANGE_ID_MSG =
        "[%s] missing "
        + FooterConstants.CHANGE_ID.getName()
        + " in commit message footer";
    private static final String MISSING_SUBJECT_MSG =
        "[%s] missing subject; "
        + FooterConstants.CHANGE_ID.getName()
        + " must be in commit message footer";
    private static final String MULTIPLE_CHANGE_ID_MSG =
        "[%s] multiple "
        + FooterConstants.CHANGE_ID.getName()
        + " lines in commit message footer";
    private static final String INVALID_CHANGE_ID_MSG =
        "[%s] invalid "
        + FooterConstants.CHANGE_ID.getName() +
        " line format in commit message footer";
    private static final Pattern CHANGE_ID = Pattern.compile(CHANGE_ID_PATTERN);

    private final ProjectControl projectControl;
    private final String canonicalWebUrl;
    private final String installCommitMsgHookCommand;
    private final SshInfo sshInfo;
    private final IdentifiedUser user;

    public ChangeIdValidator(RefControl refControl, String canonicalWebUrl,
        String installCommitMsgHookCommand, SshInfo sshInfo) {
      this.projectControl = refControl.getProjectControl();
      this.canonicalWebUrl = canonicalWebUrl;
      this.installCommitMsgHookCommand = installCommitMsgHookCommand;
      this.sshInfo = sshInfo;
      this.user = projectControl.getUser().asIdentifiedUser();
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      RevCommit commit = receiveEvent.commit;
      List<CommitValidationMessage> messages = new LinkedList<>();
      List<String> idList = commit.getFooterLines(FooterConstants.CHANGE_ID);
      String sha1 = commit.abbreviate(SHA1_LENGTH).name();

      if (idList.isEmpty()) {
        if (projectControl.getProjectState().isRequireChangeID()) {
          String shortMsg = commit.getShortMessage();
          if (shortMsg.startsWith(CHANGE_ID_PREFIX)
              && CHANGE_ID.matcher(shortMsg.substring(
                  CHANGE_ID_PREFIX.length()).trim()).matches()) {
            String errMsg = String.format(MISSING_SUBJECT_MSG, sha1);
            throw new CommitValidationException(errMsg);
          }
          String errMsg = String.format(MISSING_CHANGE_ID_MSG, sha1);
          messages.add(getMissingChangeIdErrorMsg(errMsg, commit));
          throw new CommitValidationException(errMsg, messages);
        }
      } else if (idList.size() > 1) {
        String errMsg = String.format(
            MULTIPLE_CHANGE_ID_MSG, sha1);
        throw new CommitValidationException(errMsg, messages);
      } else {
        String v = idList.get(idList.size() - 1).trim();
        if (!CHANGE_ID.matcher(v).matches()) {
          String errMsg = String.format(INVALID_CHANGE_ID_MSG, sha1);
          messages.add(
            getMissingChangeIdErrorMsg(errMsg, receiveEvent.commit));
          throw new CommitValidationException(errMsg, messages);
        }
      }
      return Collections.emptyList();
    }

    private CommitValidationMessage getMissingChangeIdErrorMsg(
        final String errMsg, final RevCommit c) {
      StringBuilder sb = new StringBuilder();
      sb.append("ERROR: ").append(errMsg);

      if (c.getFullMessage().indexOf(CHANGE_ID_PREFIX) >= 0) {
        String[] lines = c.getFullMessage().trim().split("\n");
        String lastLine = lines.length > 0 ? lines[lines.length - 1] : "";

        if (lastLine.indexOf(CHANGE_ID_PREFIX) == -1) {
          sb.append('\n');
          sb.append('\n');
          sb.append("Hint: A potential ");
          sb.append(FooterConstants.CHANGE_ID.getName());
          sb.append("Change-Id was found, but it was not in the ");
          sb.append("footer (last paragraph) of the commit message.");
        }
      }
      sb.append('\n');
      sb.append('\n');
      sb.append("Hint: To automatically insert ");
      sb.append(FooterConstants.CHANGE_ID.getName());
      sb.append(", install the hook:\n");
      sb.append(getCommitMessageHookInstallationHint());
      sb.append('\n');
      sb.append("And then amend the commit:\n");
      sb.append("  git commit --amend\n");

      return new CommitValidationMessage(sb.toString(), false);
    }

    private String getCommitMessageHookInstallationHint() {
      if (installCommitMsgHookCommand != null) {
        return installCommitMsgHookCommand;
      }
      final List<HostKey> hostKeys = sshInfo.getHostKeys();

      // If there are no SSH keys, the commit-msg hook must be installed via
      // HTTP(S)
      if (hostKeys.isEmpty()) {
        String p = "${gitdir}/hooks/commit-msg";
        return String.format(
          "  gitdir=$(git rev-parse --git-dir); curl -o %s %s/tools/hooks/commit-msg ; chmod +x %s", p,
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

      return String.format("  gitdir=$(git rev-parse --git-dir); scp -p -P %d %s@%s:hooks/commit-msg ${gitdir}/hooks/",
          sshPort, user.getUserName(), sshHost);
    }
  }

  /**
   * If this is the special project configuration branch, validate the config.
   */
  public static class ConfigValidator implements CommitValidationListener {
    private final RefControl refControl;
    private final Repository repo;
    private final AllUsersName allUsers;

    public ConfigValidator(RefControl refControl, Repository repo,
        AllUsersName allUsers) {
      this.refControl = refControl;
      this.repo = repo;
      this.allUsers = allUsers;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      IdentifiedUser currentUser = refControl.getUser().asIdentifiedUser();

      if (REFS_CONFIG.equals(refControl.getRefName())) {
        List<CommitValidationMessage> messages = new LinkedList<>();

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
        } catch (ConfigInvalidException | IOException e) {
          log.error("User " + currentUser.getUserName()
              + " tried to push an invalid project configuration "
              + receiveEvent.command.getNewId().name() + " for project "
              + receiveEvent.project.getName(), e);
          throw new CommitValidationException("invalid project configuration",
              messages);
        }
      }

      if (allUsers.equals(
              refControl.getProjectControl().getProject().getNameKey())
          && RefNames.isRefsUsers(refControl.getRefName())) {
        List<CommitValidationMessage> messages = new LinkedList<>();
        Account.Id accountId = Account.Id.fromRef(refControl.getRefName());
        if (accountId != null) {
          try {
            @SuppressWarnings("resource")
            WatchConfig wc = new WatchConfig(accountId);
            wc.load(repo, receiveEvent.command.getNewId());
            if (!wc.getValidationErrors().isEmpty()) {
              addError("Invalid project configuration:", messages);
              for (ValidationError err : wc.getValidationErrors()) {
                addError("  " + err.getMessage(), messages);
              }
              throw new ConfigInvalidException("invalid watch configuration");
            }
          } catch (IOException | ConfigInvalidException e) {
            log.error("User " + currentUser.getUserName()
                + " tried to push an invalid watch configuration "
                + receiveEvent.command.getNewId().name() + " for account "
                + accountId.get(), e);
            throw new CommitValidationException("invalid watch configuration",
                messages);
          }
        }
      }

      return Collections.emptyList();
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
      return Collections.emptyList();
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
      List<CommitValidationMessage> messages = new LinkedList<>();

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

    public SignedOffByValidator(RefControl refControl) {
      this.refControl = refControl;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      IdentifiedUser currentUser = refControl.getUser().asIdentifiedUser();
      final PersonIdent committer = receiveEvent.commit.getCommitterIdent();
      final PersonIdent author = receiveEvent.commit.getAuthorIdent();
      final ProjectControl projectControl = refControl.getProjectControl();

      if (projectControl.getProjectState().isUseSignedOffBy()) {
        boolean sboAuthor = false;
        boolean sboCommitter = false;
        boolean sboMe = false;
        for (final FooterLine footer : receiveEvent.commit.getFooterLines()) {
          if (footer.matches(FooterKey.SIGNED_OFF_BY)) {
            final String e = footer.getEmailAddress();
            if (e != null) {
              sboAuthor |= author.getEmailAddress().equals(e);
              sboCommitter |= committer.getEmailAddress().equals(e);
              sboMe |= currentUser.hasEmailAddress(e);
            }
          }
        }
        if (!sboAuthor && !sboCommitter && !sboMe
            && !refControl.canForgeCommitter()) {
          throw new CommitValidationException(
              "not Signed-off-by author/committer/uploader in commit message footer");
        }
      }
      return Collections.emptyList();
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
      IdentifiedUser currentUser = refControl.getUser().asIdentifiedUser();
      final PersonIdent author = receiveEvent.commit.getAuthorIdent();

      if (!currentUser.hasEmailAddress(author.getEmailAddress())
          && !refControl.canForgeAuthor()) {
        List<CommitValidationMessage> messages = new LinkedList<>();

        messages.add(getInvalidEmailError(receiveEvent.commit, "author", author,
            currentUser, canonicalWebUrl));
        throw new CommitValidationException("invalid author", messages);
      }
      return Collections.emptyList();
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
      IdentifiedUser currentUser = refControl.getUser().asIdentifiedUser();
      final PersonIdent committer = receiveEvent.commit.getCommitterIdent();
      if (!currentUser.hasEmailAddress(committer.getEmailAddress())
          && !refControl.canForgeCommitter()) {
        List<CommitValidationMessage> messages = new LinkedList<>();
        messages.add(getInvalidEmailError(receiveEvent.commit, "committer", committer,
            currentUser, canonicalWebUrl));
        throw new CommitValidationException("invalid committer", messages);
      }
      return Collections.emptyList();
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
      return Collections.emptyList();
    }
  }

  /** Reject banned commits. */
  public static class BannedCommitsValidator implements
      CommitValidationListener {
    private final NoteMap rejectCommits;

    public BannedCommitsValidator(NoteMap rejectCommits) {
      this.rejectCommits = rejectCommits;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(
        CommitReceivedEvent receiveEvent) throws CommitValidationException {
      try {
        if (rejectCommits.contains(receiveEvent.commit)) {
          throw new CommitValidationException("contains banned commit "
              + receiveEvent.commit.getName());
        }
        return Collections.emptyList();
      } catch (IOException e) {
        String m = "error checking banned commits";
        log.warn(m, e);
        throw new CommitValidationException(m, e);
      }
    }
  }

  private static CommitValidationMessage getInvalidEmailError(RevCommit c, String type,
      PersonIdent who, IdentifiedUser currentUser, String canonicalWebUrl) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    sb.append("ERROR:  In commit ").append(c.name()).append("\n");
    sb.append("ERROR:  ").append(type).append(" email address ")
      .append(who.getEmailAddress()).append("\n");
    sb.append("ERROR:  does not match your user account.\n");
    sb.append("ERROR:\n");
    if (currentUser.getEmailAddresses().isEmpty()) {
      sb.append("ERROR:  You have not registered any email addresses.\n");
    } else {
      sb.append("ERROR:  The following addresses are currently registered:\n");
      for (String address : currentUser.getEmailAddresses()) {
        sb.append("ERROR:    ").append(address).append("\n");
      }
    }
    sb.append("ERROR:\n");
    if (canonicalWebUrl != null) {
      sb.append("ERROR:  To register an email address, please visit:\n");
      sb.append("ERROR:  ").append(canonicalWebUrl).append("#")
        .append(PageLinks.SETTINGS_CONTACT).append("\n");
    }
    sb.append("\n");
    return new CommitValidationMessage(sb.toString(), false);
  }

  /**
   * Get the Gerrit URL.
   *
   * @return the canonical URL (with any trailing slash removed) if it is
   *         configured, otherwise fall back to "http://hostname" where hostname
   *         is the value returned by {@link #getGerritHost(String)}.
   */
  private static String getGerritUrl(String canonicalWebUrl) {
    if (canonicalWebUrl != null) {
      return CharMatcher.is('/').trimTrailingFrom(canonicalWebUrl);
    }
    return "http://" + getGerritHost(canonicalWebUrl);
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
