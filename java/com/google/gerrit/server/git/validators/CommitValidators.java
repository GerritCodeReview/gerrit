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
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CONFIG;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.CanonicalWebUrl;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Branch.NameKey;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalIdsConsistencyChecker;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.BanCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jcraft.jsch.HostKey;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitValidators {
  private static final Logger log = LoggerFactory.getLogger(CommitValidators.class);

  public static final Pattern NEW_PATCHSET_PATTERN =
      Pattern.compile("^" + REFS_CHANGES + "(?:[0-9][0-9]/)?([1-9][0-9]*)(?:/[1-9][0-9]*)?$");

  @Singleton
  public static class Factory {
    private final PersonIdent gerritIdent;
    private final String canonicalWebUrl;
    private final DynamicSet<CommitValidationListener> pluginValidators;
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsers;
    private final AllProjectsName allProjects;
    private final ExternalIdsConsistencyChecker externalIdsConsistencyChecker;
    private final AccountValidator accountValidator;
    private final String installCommitMsgHookCommand;
    private final ProjectCache projectCache;

    @Inject
    Factory(
        @GerritPersonIdent PersonIdent gerritIdent,
        @CanonicalWebUrl @Nullable String canonicalWebUrl,
        @GerritServerConfig Config cfg,
        DynamicSet<CommitValidationListener> pluginValidators,
        GitRepositoryManager repoManager,
        AllUsersName allUsers,
        AllProjectsName allProjects,
        ExternalIdsConsistencyChecker externalIdsConsistencyChecker,
        AccountValidator accountValidator,
        ProjectCache projectCache) {
      this.gerritIdent = gerritIdent;
      this.canonicalWebUrl = canonicalWebUrl;
      this.pluginValidators = pluginValidators;
      this.repoManager = repoManager;
      this.allUsers = allUsers;
      this.allProjects = allProjects;
      this.externalIdsConsistencyChecker = externalIdsConsistencyChecker;
      this.accountValidator = accountValidator;
      this.installCommitMsgHookCommand =
          cfg != null ? cfg.getString("gerrit", null, "installCommitMsgHookCommand") : null;
      this.projectCache = projectCache;
    }

    public CommitValidators forReceiveCommits(
        PermissionBackend.ForRef perm,
        Branch.NameKey branch,
        IdentifiedUser user,
        SshInfo sshInfo,
        Repository repo,
        RevWalk rw,
        @Nullable Change change)
        throws IOException {
      NoteMap rejectCommits = BanCommit.loadRejectCommitsMap(repo, rw);
      ProjectState projectState = projectCache.checkedGet(branch.getParentKey());
      return new CommitValidators(
          ImmutableList.of(
              new UploadMergesPermissionValidator(perm),
              new ProjectStateValidationListener(projectState),
              new AmendedGerritMergeCommitValidationListener(perm, gerritIdent),
              new AuthorUploaderValidator(user, perm, canonicalWebUrl),
              new CommitterUploaderValidator(user, perm, canonicalWebUrl),
              new SignedOffByValidator(user, perm, projectState),
              new ChangeIdValidator(
                  projectState,
                  user,
                  canonicalWebUrl,
                  installCommitMsgHookCommand,
                  sshInfo,
                  change),
              new ConfigValidator(branch, user, rw, allUsers, allProjects),
              new BannedCommitsValidator(rejectCommits),
              new PluginCommitValidationListener(pluginValidators),
              new ExternalIdUpdateListener(allUsers, externalIdsConsistencyChecker),
              new AccountCommitValidator(repoManager, allUsers, accountValidator),
              new GroupCommitValidator(allUsers)));
    }

    public CommitValidators forGerritCommits(
        ForRef perm,
        NameKey branch,
        IdentifiedUser user,
        SshInfo sshInfo,
        RevWalk rw,
        @Nullable Change change)
        throws IOException {
      ProjectState projectState = projectCache.checkedGet(branch.getParentKey());
      return new CommitValidators(
          ImmutableList.of(
              new UploadMergesPermissionValidator(perm),
              new ProjectStateValidationListener(projectState),
              new AmendedGerritMergeCommitValidationListener(perm, gerritIdent),
              new AuthorUploaderValidator(user, perm, canonicalWebUrl),
              new SignedOffByValidator(user, perm, projectCache.checkedGet(branch.getParentKey())),
              new ChangeIdValidator(
                  projectState,
                  user,
                  canonicalWebUrl,
                  installCommitMsgHookCommand,
                  sshInfo,
                  change),
              new ConfigValidator(branch, user, rw, allUsers, allProjects),
              new PluginCommitValidationListener(pluginValidators),
              new ExternalIdUpdateListener(allUsers, externalIdsConsistencyChecker),
              new AccountCommitValidator(repoManager, allUsers, accountValidator),
              new GroupCommitValidator(allUsers)));
    }

    public CommitValidators forMergedCommits(
        Project.NameKey project, PermissionBackend.ForRef perm, IdentifiedUser user)
        throws IOException {
      // Generally only include validators that are based on permissions of the
      // user creating a change for a merged commit; generally exclude
      // validators that would require amending the change in order to correct.
      //
      // Examples:
      //  - Change-Id and Signed-off-by can't be added to an already-merged
      //    commit.
      //  - If the commit is banned, we can't ban it here. In fact, creating a
      //    review of a previously merged and recently-banned commit is a use
      //    case for post-commit code review: so reviewers have a place to
      //    discuss what to do about it.
      //  - Plugin validators may do things like require certain commit message
      //    formats, so we play it safe and exclude them.
      return new CommitValidators(
          ImmutableList.of(
              new UploadMergesPermissionValidator(perm),
              new ProjectStateValidationListener(projectCache.checkedGet(project)),
              new AuthorUploaderValidator(user, perm, canonicalWebUrl),
              new CommitterUploaderValidator(user, perm, canonicalWebUrl)));
    }
  }

  private final List<CommitValidationListener> validators;

  CommitValidators(List<CommitValidationListener> validators) {
    this.validators = validators;
  }

  public List<CommitValidationMessage> validate(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    List<CommitValidationMessage> messages = new ArrayList<>();
    try {
      for (CommitValidationListener commitValidator : validators) {
        messages.addAll(commitValidator.onCommitReceived(receiveEvent));
      }
    } catch (CommitValidationException e) {
      log.debug("CommitValidationException occurred: {}", e.getFullMessage(), e);
      // Keep the old messages (and their order) in case of an exception
      messages.addAll(e.getMessages());
      throw new CommitValidationException(e.getMessage(), messages);
    }
    return messages;
  }

  public static class ChangeIdValidator implements CommitValidationListener {
    private static final String CHANGE_ID_PREFIX = FooterConstants.CHANGE_ID.getName() + ":";
    private static final String MISSING_CHANGE_ID_MSG =
        "[%s] missing " + FooterConstants.CHANGE_ID.getName() + " in commit message footer";
    private static final String MISSING_SUBJECT_MSG =
        "[%s] missing subject; "
            + FooterConstants.CHANGE_ID.getName()
            + " must be in commit message footer";
    private static final String MULTIPLE_CHANGE_ID_MSG =
        "[%s] multiple " + FooterConstants.CHANGE_ID.getName() + " lines in commit message footer";
    private static final String INVALID_CHANGE_ID_MSG =
        "[%s] invalid "
            + FooterConstants.CHANGE_ID.getName()
            + " line format in commit message footer";

    @VisibleForTesting
    public static final String CHANGE_ID_MISMATCH_MSG =
        "[%s] "
            + FooterConstants.CHANGE_ID.getName()
            + " in commit message footer does not match"
            + FooterConstants.CHANGE_ID.getName()
            + " of target change";

    private static final Pattern CHANGE_ID = Pattern.compile(CHANGE_ID_PATTERN);

    private final ProjectState projectState;
    private final String canonicalWebUrl;
    private final String installCommitMsgHookCommand;
    private final SshInfo sshInfo;
    private final IdentifiedUser user;
    private final Change change;

    public ChangeIdValidator(
        ProjectState projectState,
        IdentifiedUser user,
        String canonicalWebUrl,
        String installCommitMsgHookCommand,
        SshInfo sshInfo,
        Change change) {
      this.projectState = projectState;
      this.canonicalWebUrl = canonicalWebUrl;
      this.installCommitMsgHookCommand = installCommitMsgHookCommand;
      this.sshInfo = sshInfo;
      this.user = user;
      this.change = change;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      if (!shouldValidateChangeId(receiveEvent)) {
        return Collections.emptyList();
      }
      RevCommit commit = receiveEvent.commit;
      List<CommitValidationMessage> messages = new ArrayList<>();
      List<String> idList = commit.getFooterLines(FooterConstants.CHANGE_ID);
      String sha1 = commit.abbreviate(RevId.ABBREV_LEN).name();

      if (idList.isEmpty()) {
        String shortMsg = commit.getShortMessage();
        if (shortMsg.startsWith(CHANGE_ID_PREFIX)
            && CHANGE_ID.matcher(shortMsg.substring(CHANGE_ID_PREFIX.length()).trim()).matches()) {
          String errMsg = String.format(MISSING_SUBJECT_MSG, sha1);
          throw new CommitValidationException(errMsg);
        }
        if (projectState.is(BooleanProjectConfig.REQUIRE_CHANGE_ID)) {
          String errMsg = String.format(MISSING_CHANGE_ID_MSG, sha1);
          messages.add(getMissingChangeIdErrorMsg(errMsg, commit));
          throw new CommitValidationException(errMsg, messages);
        }
      } else if (idList.size() > 1) {
        String errMsg = String.format(MULTIPLE_CHANGE_ID_MSG, sha1);
        throw new CommitValidationException(errMsg, messages);
      } else {
        String v = idList.get(idList.size() - 1).trim();
        // Reject Change-Ids with wrong format and invalid placeholder ID from
        // Egit (I0000000000000000000000000000000000000000).
        if (!CHANGE_ID.matcher(v).matches() || v.matches("^I00*$")) {
          String errMsg = String.format(INVALID_CHANGE_ID_MSG, sha1);
          messages.add(getMissingChangeIdErrorMsg(errMsg, receiveEvent.commit));
          throw new CommitValidationException(errMsg, messages);
        }
        if (change != null && !v.equals(change.getKey().get())) {
          String errMsg = String.format(CHANGE_ID_MISMATCH_MSG, sha1);
          throw new CommitValidationException(errMsg);
        }
      }

      return Collections.emptyList();
    }

    private static boolean shouldValidateChangeId(CommitReceivedEvent event) {
      return MagicBranch.isMagicBranch(event.command.getRefName())
          || NEW_PATCHSET_PATTERN.matcher(event.command.getRefName()).matches();
    }

    private CommitValidationMessage getMissingChangeIdErrorMsg(String errMsg, RevCommit c) {
      StringBuilder sb = new StringBuilder();
      sb.append("ERROR: ").append(errMsg);

      if (c.getFullMessage().indexOf(CHANGE_ID_PREFIX) >= 0) {
        String lastLine = Iterables.getLast(Splitter.on('\n').split(c.getFullMessage()), "");
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
            "  gitdir=$(git rev-parse --git-dir); curl -o %s %stools/hooks/commit-msg ; chmod +x %s",
            p, canonicalWebUrl, p);
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

      return String.format(
          "  gitdir=$(git rev-parse --git-dir); scp -p -P %d %s@%s:hooks/commit-msg ${gitdir}/hooks/",
          sshPort, user.getUserName().orElse("<USERNAME>"), sshHost);
    }
  }

  /** If this is the special project configuration branch, validate the config. */
  public static class ConfigValidator implements CommitValidationListener {
    private final Branch.NameKey branch;
    private final IdentifiedUser user;
    private final RevWalk rw;
    private final AllUsersName allUsers;
    private final AllProjectsName allProjects;

    public ConfigValidator(
        Branch.NameKey branch,
        IdentifiedUser user,
        RevWalk rw,
        AllUsersName allUsers,
        AllProjectsName allProjects) {
      this.branch = branch;
      this.user = user;
      this.rw = rw;
      this.allProjects = allProjects;
      this.allUsers = allUsers;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      if (REFS_CONFIG.equals(branch.get())) {
        List<CommitValidationMessage> messages = new ArrayList<>();

        try {
          ProjectConfig cfg = new ProjectConfig(receiveEvent.project.getNameKey());
          cfg.load(rw, receiveEvent.command.getNewId());
          if (!cfg.getValidationErrors().isEmpty()) {
            addError("Invalid project configuration:", messages);
            for (ValidationError err : cfg.getValidationErrors()) {
              addError("  " + err.getMessage(), messages);
            }
            throw new ConfigInvalidException("invalid project configuration");
          }
          if (allUsers.equals(receiveEvent.project.getNameKey())
              && !allProjects.equals(cfg.getProject().getParent(allProjects))) {
            addError("Invalid project configuration:", messages);
            addError(
                String.format("  %s must inherit from %s", allUsers.get(), allProjects.get()),
                messages);
            throw new ConfigInvalidException("invalid project configuration");
          }
        } catch (ConfigInvalidException | IOException e) {
          log.error(
              "User "
                  + user.getLoggableName()
                  + " tried to push an invalid project configuration "
                  + receiveEvent.command.getNewId().name()
                  + " for project "
                  + receiveEvent.project.getName(),
              e);
          throw new CommitValidationException("invalid project configuration", messages);
        }
      }

      return Collections.emptyList();
    }
  }

  /** Require permission to upload merge commits. */
  public static class UploadMergesPermissionValidator implements CommitValidationListener {
    private final PermissionBackend.ForRef perm;

    public UploadMergesPermissionValidator(PermissionBackend.ForRef perm) {
      this.perm = perm;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      if (receiveEvent.commit.getParentCount() <= 1) {
        return Collections.emptyList();
      }
      try {
        perm.check(RefPermission.MERGE);
        return Collections.emptyList();
      } catch (AuthException e) {
        throw new CommitValidationException("you are not allowed to upload merges");
      } catch (PermissionBackendException e) {
        log.error("cannot check MERGE", e);
        throw new CommitValidationException("internal auth error");
      }
    }
  }

  /** Execute commit validation plug-ins */
  public static class PluginCommitValidationListener implements CommitValidationListener {
    private final DynamicSet<CommitValidationListener> commitValidationListeners;

    public PluginCommitValidationListener(
        final DynamicSet<CommitValidationListener> commitValidationListeners) {
      this.commitValidationListeners = commitValidationListeners;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      List<CommitValidationMessage> messages = new ArrayList<>();

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
    private final IdentifiedUser user;
    private final PermissionBackend.ForRef perm;
    private final ProjectState state;

    public SignedOffByValidator(
        IdentifiedUser user, PermissionBackend.ForRef perm, ProjectState state) {
      this.user = user;
      this.perm = perm;
      this.state = state;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      if (!state.is(BooleanProjectConfig.USE_SIGNED_OFF_BY)) {
        return Collections.emptyList();
      }

      RevCommit commit = receiveEvent.commit;
      PersonIdent committer = commit.getCommitterIdent();
      PersonIdent author = commit.getAuthorIdent();

      boolean sboAuthor = false;
      boolean sboCommitter = false;
      boolean sboMe = false;
      for (FooterLine footer : commit.getFooterLines()) {
        if (footer.matches(FooterKey.SIGNED_OFF_BY)) {
          String e = footer.getEmailAddress();
          if (e != null) {
            sboAuthor |= author.getEmailAddress().equals(e);
            sboCommitter |= committer.getEmailAddress().equals(e);
            sboMe |= user.hasEmailAddress(e);
          }
        }
      }
      if (!sboAuthor && !sboCommitter && !sboMe) {
        try {
          perm.check(RefPermission.FORGE_COMMITTER);
        } catch (AuthException denied) {
          throw new CommitValidationException(
              "not Signed-off-by author/committer/uploader in commit message footer");
        } catch (PermissionBackendException e) {
          log.error("cannot check FORGE_COMMITTER", e);
          throw new CommitValidationException("internal auth error");
        }
      }
      return Collections.emptyList();
    }
  }

  /** Require that author matches the uploader. */
  public static class AuthorUploaderValidator implements CommitValidationListener {
    private final IdentifiedUser user;
    private final PermissionBackend.ForRef perm;
    private final String canonicalWebUrl;

    public AuthorUploaderValidator(
        IdentifiedUser user, PermissionBackend.ForRef perm, String canonicalWebUrl) {
      this.user = user;
      this.perm = perm;
      this.canonicalWebUrl = canonicalWebUrl;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      PersonIdent author = receiveEvent.commit.getAuthorIdent();
      if (user.hasEmailAddress(author.getEmailAddress())) {
        return Collections.emptyList();
      }
      try {
        perm.check(RefPermission.FORGE_AUTHOR);
        return Collections.emptyList();
      } catch (AuthException e) {
        throw new CommitValidationException(
            "invalid author",
            invalidEmail(receiveEvent.commit, "author", author, user, canonicalWebUrl));
      } catch (PermissionBackendException e) {
        log.error("cannot check FORGE_AUTHOR", e);
        throw new CommitValidationException("internal auth error");
      }
    }
  }

  /** Require that committer matches the uploader. */
  public static class CommitterUploaderValidator implements CommitValidationListener {
    private final IdentifiedUser user;
    private final PermissionBackend.ForRef perm;
    private final String canonicalWebUrl;

    public CommitterUploaderValidator(
        IdentifiedUser user, PermissionBackend.ForRef perm, String canonicalWebUrl) {
      this.user = user;
      this.perm = perm;
      this.canonicalWebUrl = canonicalWebUrl;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      PersonIdent committer = receiveEvent.commit.getCommitterIdent();
      if (user.hasEmailAddress(committer.getEmailAddress())) {
        return Collections.emptyList();
      }
      try {
        perm.check(RefPermission.FORGE_COMMITTER);
        return Collections.emptyList();
      } catch (AuthException e) {
        throw new CommitValidationException(
            "invalid committer",
            invalidEmail(receiveEvent.commit, "committer", committer, user, canonicalWebUrl));
      } catch (PermissionBackendException e) {
        log.error("cannot check FORGE_COMMITTER", e);
        throw new CommitValidationException("internal auth error");
      }
    }
  }

  /**
   * Don't allow the user to amend a merge created by Gerrit Code Review. This seems to happen all
   * too often, due to users not paying any attention to what they are doing.
   */
  public static class AmendedGerritMergeCommitValidationListener
      implements CommitValidationListener {
    private final PermissionBackend.ForRef perm;
    private final PersonIdent gerritIdent;

    public AmendedGerritMergeCommitValidationListener(
        PermissionBackend.ForRef perm, PersonIdent gerritIdent) {
      this.perm = perm;
      this.gerritIdent = gerritIdent;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      PersonIdent author = receiveEvent.commit.getAuthorIdent();
      if (receiveEvent.commit.getParentCount() > 1
          && author.getName().equals(gerritIdent.getName())
          && author.getEmailAddress().equals(gerritIdent.getEmailAddress())) {
        try {
          // Stop authors from amending the merge commits that Gerrit itself creates.
          perm.check(RefPermission.FORGE_SERVER);
        } catch (AuthException denied) {
          throw new CommitValidationException(
              String.format(
                  "pushing merge commit %s by %s requires '%s' permission",
                  receiveEvent.commit.getId(),
                  gerritIdent.getEmailAddress(),
                  RefPermission.FORGE_SERVER.name()));
        } catch (PermissionBackendException e) {
          log.error("cannot check FORGE_SERVER", e);
          throw new CommitValidationException("internal auth error");
        }
      }
      return Collections.emptyList();
    }
  }

  /** Reject banned commits. */
  public static class BannedCommitsValidator implements CommitValidationListener {
    private final NoteMap rejectCommits;

    public BannedCommitsValidator(NoteMap rejectCommits) {
      this.rejectCommits = rejectCommits;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      try {
        if (rejectCommits.contains(receiveEvent.commit)) {
          throw new CommitValidationException(
              "contains banned commit " + receiveEvent.commit.getName());
        }
        return Collections.emptyList();
      } catch (IOException e) {
        String m = "error checking banned commits";
        log.warn(m, e);
        throw new CommitValidationException(m, e);
      }
    }
  }

  /** Validates updates to refs/meta/external-ids. */
  public static class ExternalIdUpdateListener implements CommitValidationListener {
    private final AllUsersName allUsers;
    private final ExternalIdsConsistencyChecker externalIdsConsistencyChecker;

    public ExternalIdUpdateListener(
        AllUsersName allUsers, ExternalIdsConsistencyChecker externalIdsConsistencyChecker) {
      this.externalIdsConsistencyChecker = externalIdsConsistencyChecker;
      this.allUsers = allUsers;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      if (allUsers.equals(receiveEvent.project.getNameKey())
          && RefNames.REFS_EXTERNAL_IDS.equals(receiveEvent.refName)) {
        try {
          List<ConsistencyProblemInfo> problems =
              externalIdsConsistencyChecker.check(receiveEvent.commit);
          List<CommitValidationMessage> msgs =
              problems
                  .stream()
                  .map(
                      p ->
                          new CommitValidationMessage(
                              p.message, p.status == ConsistencyProblemInfo.Status.ERROR))
                  .collect(toList());
          if (msgs.stream().anyMatch(m -> m.isError())) {
            throw new CommitValidationException("invalid external IDs", msgs);
          }
          return msgs;
        } catch (IOException | ConfigInvalidException e) {
          String m = "error validating external IDs";
          log.warn(m, e);
          throw new CommitValidationException(m, e);
        }
      }
      return Collections.emptyList();
    }
  }

  public static class AccountCommitValidator implements CommitValidationListener {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsers;
    private final AccountValidator accountValidator;

    public AccountCommitValidator(
        GitRepositoryManager repoManager,
        AllUsersName allUsers,
        AccountValidator accountValidator) {
      this.repoManager = repoManager;
      this.allUsers = allUsers;
      this.accountValidator = accountValidator;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      if (!allUsers.equals(receiveEvent.project.getNameKey())) {
        return Collections.emptyList();
      }

      if (receiveEvent.command.getRefName().startsWith(MagicBranch.NEW_CHANGE)) {
        // no validation on push for review, will be checked on submit by
        // MergeValidators.AccountMergeValidator
        return Collections.emptyList();
      }

      Account.Id accountId = Account.Id.fromRef(receiveEvent.refName);
      if (accountId == null) {
        return Collections.emptyList();
      }

      try (Repository repo = repoManager.openRepository(allUsers)) {
        List<String> errorMessages =
            accountValidator.validate(
                accountId,
                repo,
                receiveEvent.revWalk,
                receiveEvent.command.getOldId(),
                receiveEvent.commit);
        if (!errorMessages.isEmpty()) {
          throw new CommitValidationException(
              "invalid account configuration",
              errorMessages
                  .stream()
                  .map(m -> new CommitValidationMessage(m, true))
                  .collect(toList()));
        }
      } catch (IOException e) {
        String m = String.format("Validating update for account %s failed", accountId.get());
        log.error(m, e);
        throw new CommitValidationException(m, e);
      }
      return Collections.emptyList();
    }
  }

  /** Rejects updates to group branches. */
  public static class GroupCommitValidator implements CommitValidationListener {
    private final AllUsersName allUsers;

    public GroupCommitValidator(AllUsersName allUsers) {
      this.allUsers = allUsers;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      // Groups are stored inside the 'All-Users' repository.
      if (!allUsers.equals(receiveEvent.project.getNameKey())) {
        return Collections.emptyList();
      }

      if (receiveEvent.command.getRefName().startsWith(MagicBranch.NEW_CHANGE)) {
        // no validation on push for review, will be checked on submit by
        // MergeValidators.GroupMergeValidator
        return Collections.emptyList();
      }

      if (RefNames.isGroupRef(receiveEvent.command.getRefName())) {
        throw new CommitValidationException("group update not allowed");
      }
      return Collections.emptyList();
    }
  }

  /** Rejects updates to projects that don't allow writes. */
  public static class ProjectStateValidationListener implements CommitValidationListener {
    private final ProjectState projectState;

    public ProjectStateValidationListener(ProjectState projectState) {
      this.projectState = projectState;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      if (projectState.statePermitsWrite()) {
        return Collections.emptyList();
      }
      throw new CommitValidationException("project state does not permit write");
    }
  }

  private static CommitValidationMessage invalidEmail(
      RevCommit c,
      String type,
      PersonIdent who,
      IdentifiedUser currentUser,
      String canonicalWebUrl) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    sb.append("ERROR:  In commit ").append(c.name()).append("\n");
    sb.append("ERROR:  ")
        .append(type)
        .append(" email address ")
        .append(who.getEmailAddress())
        .append("\n");
    sb.append("ERROR:  does not match your user account and you have no 'forge ")
        .append(type)
        .append("' permission.\n");
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
      sb.append("ERROR:  ")
          .append(canonicalWebUrl)
          .append("#")
          .append(PageLinks.SETTINGS_CONTACT)
          .append("\n");
    }
    sb.append("\n");
    return new CommitValidationMessage(sb.toString(), false);
  }

  /**
   * Get the Gerrit hostname.
   *
   * @return the hostname from the canonical URL if it is configured, otherwise whatever the OS says
   *     the hostname is.
   */
  private static String getGerritHost(String canonicalWebUrl) {
    if (canonicalWebUrl != null) {
      try {
        return new URL(canonicalWebUrl).getHost();
      } catch (MalformedURLException ignored) {
      }
    }

    return SystemReader.getInstance().getHostname();
  }

  private static void addError(String error, List<CommitValidationMessage> messages) {
    messages.add(new CommitValidationMessage(error, true));
  }
}
