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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.entities.Change.CHANGE_ID_PATTERN;
import static com.google.gerrit.entities.RefNames.REFS_CHANGES;
import static com.google.gerrit.entities.RefNames.REFS_CONFIG;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalIdsConsistencyChecker;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.git.validators.ValidationMessage.Type;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.ssh.HostKey;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
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
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Represents a list of {@link CommitValidationListener}s to run for a push to one branch of one
 * project.
 */
public class CommitValidators {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final Pattern NEW_PATCHSET_PATTERN =
      Pattern.compile("^" + REFS_CHANGES + "(?:[0-9][0-9]/)?([1-9][0-9]*)(?:/[1-9][0-9]*)?$");

  @Singleton
  public static class Factory {
    private final PersonIdent gerritIdent;
    private final DynamicItem<UrlFormatter> urlFormatter;
    private final PluginSetContext<CommitValidationListener> pluginValidators;
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsers;
    private final AllProjectsName allProjects;
    private final ExternalIdsConsistencyChecker externalIdsConsistencyChecker;
    private final AccountValidator accountValidator;
    private final ProjectCache projectCache;
    private final ProjectConfig.Factory projectConfigFactory;
    private final Config config;

    @Inject
    Factory(
        @GerritPersonIdent PersonIdent gerritIdent,
        DynamicItem<UrlFormatter> urlFormatter,
        @GerritServerConfig Config config,
        PluginSetContext<CommitValidationListener> pluginValidators,
        GitRepositoryManager repoManager,
        AllUsersName allUsers,
        AllProjectsName allProjects,
        ExternalIdsConsistencyChecker externalIdsConsistencyChecker,
        AccountValidator accountValidator,
        ProjectCache projectCache,
        ProjectConfig.Factory projectConfigFactory) {
      this.gerritIdent = gerritIdent;
      this.urlFormatter = urlFormatter;
      this.config = config;
      this.pluginValidators = pluginValidators;
      this.repoManager = repoManager;
      this.allUsers = allUsers;
      this.allProjects = allProjects;
      this.externalIdsConsistencyChecker = externalIdsConsistencyChecker;
      this.accountValidator = accountValidator;
      this.projectCache = projectCache;
      this.projectConfigFactory = projectConfigFactory;
    }

    public CommitValidators forReceiveCommits(
        PermissionBackend.ForProject forProject,
        BranchNameKey branch,
        IdentifiedUser user,
        SshInfo sshInfo,
        NoteMap rejectCommits,
        RevWalk rw,
        @Nullable Change change,
        boolean skipValidation) {
      PermissionBackend.ForRef perm = forProject.ref(branch.branch());
      ProjectState projectState =
          projectCache.get(branch.project()).orElseThrow(illegalState(branch.project()));
      ImmutableList.Builder<CommitValidationListener> validators = ImmutableList.builder();
      validators
          .add(new UploadMergesPermissionValidator(perm))
          .add(new ProjectStateValidationListener(projectState))
          .add(new AmendedGerritMergeCommitValidationListener(perm, gerritIdent))
          .add(new AuthorUploaderValidator(user, perm, urlFormatter.get()))
          .add(new FileCountValidator(repoManager, config))
          .add(new CommitterUploaderValidator(user, perm, urlFormatter.get()))
          .add(new SignedOffByValidator(user, perm, projectState))
          .add(
              new ChangeIdValidator(
                  projectState, user, urlFormatter.get(), config, sshInfo, change))
          .add(new ConfigValidator(projectConfigFactory, branch, user, rw, allUsers, allProjects))
          .add(new BannedCommitsValidator(rejectCommits))
          .add(new PluginCommitValidationListener(pluginValidators, skipValidation))
          .add(new ExternalIdUpdateListener(allUsers, externalIdsConsistencyChecker))
          .add(new AccountCommitValidator(repoManager, allUsers, accountValidator))
          .add(new GroupCommitValidator(allUsers));
      return new CommitValidators(validators.build());
    }

    public CommitValidators forGerritCommits(
        PermissionBackend.ForProject forProject,
        BranchNameKey branch,
        IdentifiedUser user,
        SshInfo sshInfo,
        RevWalk rw,
        @Nullable Change change) {
      PermissionBackend.ForRef perm = forProject.ref(branch.branch());
      ProjectState projectState =
          projectCache.get(branch.project()).orElseThrow(illegalState(branch.project()));
      ImmutableList.Builder<CommitValidationListener> validators = ImmutableList.builder();
      validators
          .add(new UploadMergesPermissionValidator(perm))
          .add(new ProjectStateValidationListener(projectState))
          .add(new AmendedGerritMergeCommitValidationListener(perm, gerritIdent))
          .add(new AuthorUploaderValidator(user, perm, urlFormatter.get()))
          .add(new FileCountValidator(repoManager, config))
          .add(new SignedOffByValidator(user, perm, projectState))
          .add(
              new ChangeIdValidator(
                  projectState, user, urlFormatter.get(), config, sshInfo, change))
          .add(new ConfigValidator(projectConfigFactory, branch, user, rw, allUsers, allProjects))
          .add(new PluginCommitValidationListener(pluginValidators))
          .add(new ExternalIdUpdateListener(allUsers, externalIdsConsistencyChecker))
          .add(new AccountCommitValidator(repoManager, allUsers, accountValidator))
          .add(new GroupCommitValidator(allUsers));
      return new CommitValidators(validators.build());
    }

    public CommitValidators forMergedCommits(
        PermissionBackend.ForProject forProject, BranchNameKey branch, IdentifiedUser user) {
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
      PermissionBackend.ForRef perm = forProject.ref(branch.branch());
      ProjectState projectState =
          projectCache.get(branch.project()).orElseThrow(illegalState(branch.project()));
      ImmutableList.Builder<CommitValidationListener> validators = ImmutableList.builder();
      validators
          .add(new UploadMergesPermissionValidator(perm))
          .add(new ProjectStateValidationListener(projectState))
          .add(new AuthorUploaderValidator(user, perm, urlFormatter.get()))
          .add(new CommitterUploaderValidator(user, perm, urlFormatter.get()));
      return new CommitValidators(validators.build());
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
        try (TraceTimer traceTimer =
            TraceContext.newTimer(
                "Running CommitValidationListener",
                Metadata.builder()
                    .className(commitValidator.getClass().getSimpleName())
                    .projectName(receiveEvent.getProjectNameKey().get())
                    .branchName(receiveEvent.getBranchNameKey().branch())
                    .commit(receiveEvent.commit.name())
                    .build())) {
          messages.addAll(commitValidator.onCommitReceived(receiveEvent));
        }
      }
    } catch (CommitValidationException e) {
      logger.atFine().withCause(e).log(
          "CommitValidationException occurred: %s", e.getFullMessage());
      // Keep the old messages (and their order) in case of an exception
      messages.addAll(e.getMessages());
      throw new CommitValidationException(e.getMessage(), messages);
    }
    return messages;
  }

  public static class ChangeIdValidator implements CommitValidationListener {
    private static final String CHANGE_ID_PREFIX = FooterConstants.CHANGE_ID.getName() + ":";
    private static final String MISSING_CHANGE_ID_MSG = "missing Change-Id in message footer";
    private static final String MISSING_SUBJECT_MSG =
        "missing subject; Change-Id must be in message footer";
    private static final String CHANGE_ID_ABOVE_FOOTER_MSG = "Change-Id must be in message footer";
    private static final String MULTIPLE_CHANGE_ID_MSG =
        "multiple Change-Id lines in message footer";
    private static final String INVALID_CHANGE_ID_MSG =
        "invalid Change-Id line format in message footer";

    @VisibleForTesting
    public static final String CHANGE_ID_MISMATCH_MSG =
        "Change-Id in message footer does not match Change-Id of target change";

    private static final Pattern CHANGE_ID = Pattern.compile(CHANGE_ID_PATTERN);

    private final ProjectState projectState;
    private final UrlFormatter urlFormatter;
    private final String installCommitMsgHookCommand;
    private final SshInfo sshInfo;
    private final IdentifiedUser user;
    private final Change change;

    public ChangeIdValidator(
        ProjectState projectState,
        IdentifiedUser user,
        UrlFormatter urlFormatter,
        Config config,
        SshInfo sshInfo,
        Change change) {
      this.projectState = projectState;
      this.user = user;
      this.urlFormatter = urlFormatter;
      installCommitMsgHookCommand = config.getString("gerrit", null, "installCommitMsgHookCommand");
      this.sshInfo = sshInfo;
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

      if (idList.isEmpty()) {
        String shortMsg = commit.getShortMessage();
        if (shortMsg.startsWith(CHANGE_ID_PREFIX)
            && CHANGE_ID.matcher(shortMsg.substring(CHANGE_ID_PREFIX.length()).trim()).matches()) {
          throw new CommitValidationException(MISSING_SUBJECT_MSG);
        }
        if (commit.getFullMessage().contains("\n" + CHANGE_ID_PREFIX)) {
          messages.add(
              new CommitValidationMessage(
                  CHANGE_ID_ABOVE_FOOTER_MSG
                      + "\n"
                      + "\n"
                      + "Hint: run\n"
                      + "  git commit --amend\n"
                      + "and move 'Change-Id: Ixxx..' to the bottom on a separate line\n",
                  Type.ERROR));
          throw new CommitValidationException(CHANGE_ID_ABOVE_FOOTER_MSG, messages);
        }
        if (projectState.is(BooleanProjectConfig.REQUIRE_CHANGE_ID)) {
          messages.add(getMissingChangeIdErrorMsg(MISSING_CHANGE_ID_MSG));
          throw new CommitValidationException(MISSING_CHANGE_ID_MSG, messages);
        }
      } else if (idList.size() > 1) {
        throw new CommitValidationException(MULTIPLE_CHANGE_ID_MSG, messages);
      } else {
        String v = idList.get(idList.size() - 1).trim();
        // Reject Change-Ids with wrong format and invalid placeholder ID from
        // Egit (I0000000000000000000000000000000000000000).
        if (!CHANGE_ID.matcher(v).matches() || v.matches("^I00*$")) {
          messages.add(getMissingChangeIdErrorMsg(INVALID_CHANGE_ID_MSG));
          throw new CommitValidationException(INVALID_CHANGE_ID_MSG, messages);
        }
        if (change != null && !v.equals(change.getKey().get())) {
          throw new CommitValidationException(CHANGE_ID_MISMATCH_MSG);
        }
      }

      return Collections.emptyList();
    }

    private static boolean shouldValidateChangeId(CommitReceivedEvent event) {
      return MagicBranch.isMagicBranch(event.command.getRefName())
          || NEW_PATCHSET_PATTERN.matcher(event.command.getRefName()).matches();
    }

    private CommitValidationMessage getMissingChangeIdErrorMsg(String errMsg) {
      return new CommitValidationMessage(
          errMsg
              + "\n"
              + "\nHint: to automatically insert a Change-Id, install the hook:\n"
              + getCommitMessageHookInstallationHint()
              + "\n"
              + "and then amend the commit:\n"
              + "  git commit --amend --no-edit\n"
              + "Finally, push your changes again\n",
          Type.ERROR);
    }

    private String getCommitMessageHookInstallationHint() {
      if (installCommitMsgHookCommand != null) {
        return installCommitMsgHookCommand;
      }
      final List<HostKey> hostKeys = sshInfo.getHostKeys();

      // If there are no SSH keys, the commit-msg hook must be installed via
      // HTTP(S)
      Optional<String> webUrl = urlFormatter.getWebUrl();
      if (hostKeys.isEmpty()) {
        checkState(webUrl.isPresent());
        return String.format(
            "  f=\"$(git rev-parse --git-dir)/hooks/commit-msg\"; curl -o \"$f\" %stools/hooks/commit-msg ; chmod +x \"$f\"",
            webUrl.get());
      }

      // SSH keys exist, so the hook can be installed with scp.
      String sshHost;
      int sshPort;
      String host = hostKeys.get(0).getHost();
      int c = host.lastIndexOf(':');
      if (0 <= c) {
        if (host.startsWith("*:")) {
          checkState(webUrl.isPresent());
          sshHost = getGerritHost(webUrl.get());
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

  /** Limits the number of files per change. */
  private static class FileCountValidator implements CommitValidationListener {

    private final GitRepositoryManager repoManager;
    private final int maxFileCount;

    FileCountValidator(GitRepositoryManager repoManager, Config config) {
      this.repoManager = repoManager;
      maxFileCount = config.getInt("change", null, "maxFiles", 100_000);
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      // TODO(zieren): Refactor interface to signal the intent of the event instead of hard-coding
      // it here. Due to interface limitations, this method is called from both receive commits
      // and from main Gerrit (e.g. when publishing a change edit). This is why we need to gate the
      // early return on REFS_CHANGES (though pushes to refs/changes are not possible).
      String refName = receiveEvent.command.getRefName();
      if (!refName.startsWith("refs/for/") && !refName.startsWith(RefNames.REFS_CHANGES)) {
        // This is a direct push bypassing review. We don't need to enforce any file-count limits
        // here.
        return Collections.emptyList();
      }

      // Use DiffFormatter to compute the number of files in the change. This should be faster than
      // the previous approach of using the PatchListCache.
      try {
        long changedFiles = countChangedFiles(receiveEvent);
        if (changedFiles > maxFileCount) {
          throw new CommitValidationException(
              String.format(
                  "Exceeding maximum number of files per change (%d > %d)",
                  changedFiles, maxFileCount));
        }
      } catch (IOException e) {
        // This happens e.g. for cherrypicks.
        if (!receiveEvent.command.getRefName().startsWith(REFS_CHANGES)) {
          logger.atWarning().withCause(e).log(
              "Failed to validate file count for commit: %s", receiveEvent.commit.toString());
        }
      }
      return Collections.emptyList();
    }

    private long countChangedFiles(CommitReceivedEvent receiveEvent) throws IOException {
      try (Repository repository = repoManager.openRepository(receiveEvent.project.getNameKey());
          RevWalk revWalk = new RevWalk(repository);
          DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        diffFormatter.setReader(revWalk.getObjectReader(), repository.getConfig());
        diffFormatter.setDetectRenames(true);
        // For merge commits, i.e. >1 parents, we use parent #0 by convention.
        List<DiffEntry> diffEntries =
            diffFormatter.scan(
                receiveEvent.commit.getParentCount() > 0 ? receiveEvent.commit.getParent(0) : null,
                receiveEvent.commit);
        return diffEntries.stream().map(DiffEntry::getNewPath).distinct().count();
      }
    }
  }

  /** If this is the special project configuration branch, validate the config. */
  public static class ConfigValidator implements CommitValidationListener {
    private final ProjectConfig.Factory projectConfigFactory;
    private final BranchNameKey branch;
    private final IdentifiedUser user;
    private final RevWalk rw;
    private final AllUsersName allUsers;
    private final AllProjectsName allProjects;

    public ConfigValidator(
        ProjectConfig.Factory projectConfigFactory,
        BranchNameKey branch,
        IdentifiedUser user,
        RevWalk rw,
        AllUsersName allUsers,
        AllProjectsName allProjects) {
      this.projectConfigFactory = projectConfigFactory;
      this.branch = branch;
      this.user = user;
      this.rw = rw;
      this.allProjects = allProjects;
      this.allUsers = allUsers;
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      if (REFS_CONFIG.equals(branch.branch())) {
        List<CommitValidationMessage> messages = new ArrayList<>();

        try {
          ProjectConfig cfg = projectConfigFactory.create(receiveEvent.project.getNameKey());
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
          logger.atSevere().withCause(e).log(
              "User %s tried to push an invalid project configuration %s for project %s",
              user.getLoggableName(),
              receiveEvent.command.getNewId().name(),
              receiveEvent.project.getName());
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
        throw new CommitValidationException("you are not allowed to upload merges", e);
      } catch (PermissionBackendException e) {
        logger.atSevere().withCause(e).log("cannot check MERGE");
        throw new CommitValidationException("internal auth error");
      }
    }
  }

  /** Execute commit validation plug-ins */
  public static class PluginCommitValidationListener implements CommitValidationListener {
    private boolean skipValidation;
    private final PluginSetContext<CommitValidationListener> commitValidationListeners;

    public PluginCommitValidationListener(
        final PluginSetContext<CommitValidationListener> commitValidationListeners) {
      this(commitValidationListeners, false);
    }

    public PluginCommitValidationListener(
        final PluginSetContext<CommitValidationListener> commitValidationListeners,
        boolean skipValidation) {
      this.skipValidation = skipValidation;
      this.commitValidationListeners = commitValidationListeners;
    }

    private void runValidator(
        CommitValidationListener validator,
        List<CommitValidationMessage> messages,
        CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      if (skipValidation && !validator.shouldValidateAllCommits()) {
        return;
      }
      messages.addAll(validator.onCommitReceived(receiveEvent));
    }

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      List<CommitValidationMessage> messages = new ArrayList<>();
      try {
        commitValidationListeners.runEach(
            l -> runValidator(l, messages, receiveEvent), CommitValidationException.class);
      } catch (CommitValidationException e) {
        messages.addAll(e.getMessages());
        throw new CommitValidationException(e.getMessage(), messages);
      }
      return messages;
    }

    @Override
    public boolean shouldValidateAllCommits() {
      return commitValidationListeners.stream().anyMatch(v -> v.shouldValidateAllCommits());
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
              "not Signed-off-by author/committer/uploader in message footer", denied);
        } catch (PermissionBackendException e) {
          logger.atSevere().withCause(e).log("cannot check FORGE_COMMITTER");
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
    private final UrlFormatter urlFormatter;

    public AuthorUploaderValidator(
        IdentifiedUser user, PermissionBackend.ForRef perm, UrlFormatter urlFormatter) {
      this.user = user;
      this.perm = perm;
      this.urlFormatter = urlFormatter;
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
            "invalid author", invalidEmail("author", author, user, urlFormatter), e);
      } catch (PermissionBackendException e) {
        logger.atSevere().withCause(e).log("cannot check FORGE_AUTHOR");
        throw new CommitValidationException("internal auth error");
      }
    }
  }

  /** Require that committer matches the uploader. */
  public static class CommitterUploaderValidator implements CommitValidationListener {
    private final IdentifiedUser user;
    private final PermissionBackend.ForRef perm;
    private final UrlFormatter urlFormatter;

    public CommitterUploaderValidator(
        IdentifiedUser user, PermissionBackend.ForRef perm, UrlFormatter urlFormatter) {
      this.user = user;
      this.perm = perm;
      this.urlFormatter = urlFormatter;
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
            "invalid committer", invalidEmail("committer", committer, user, urlFormatter), e);
      } catch (PermissionBackendException e) {
        logger.atSevere().withCause(e).log("cannot check FORGE_COMMITTER");
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
                  RefPermission.FORGE_SERVER.name()),
              denied);
        } catch (PermissionBackendException e) {
          logger.atSevere().withCause(e).log("cannot check FORGE_SERVER");
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
        logger.atWarning().withCause(e).log(m);
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
              problems.stream()
                  .map(
                      p ->
                          new CommitValidationMessage(
                              p.message,
                              p.status == ConsistencyProblemInfo.Status.ERROR
                                  ? ValidationMessage.Type.ERROR
                                  : ValidationMessage.Type.OTHER))
                  .collect(toList());
          if (msgs.stream().anyMatch(ValidationMessage::isError)) {
            throw new CommitValidationException("invalid external IDs", msgs);
          }
          return msgs;
        } catch (IOException | ConfigInvalidException e) {
          String m = "error validating external IDs";
          logger.atWarning().withCause(e).log(m);
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
              errorMessages.stream()
                  .map(m -> new CommitValidationMessage(m, Type.ERROR))
                  .collect(toList()));
        }
      } catch (IOException e) {
        String m = String.format("Validating update for account %s failed", accountId.get());
        logger.atSevere().withCause(e).log(m);
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
      String type, PersonIdent who, IdentifiedUser currentUser, UrlFormatter urlFormatter) {
    StringBuilder sb = new StringBuilder();

    sb.append("email address ")
        .append(who.getEmailAddress())
        .append(" is not registered in your account, and you lack 'forge ")
        .append(type)
        .append("' permission.\n");

    if (currentUser.getEmailAddresses().isEmpty()) {
      sb.append("You have not registered any email addresses.\n");
    } else {
      sb.append("The following addresses are currently registered:\n");
      for (String address : currentUser.getEmailAddresses()) {
        sb.append("   ").append(address).append("\n");
      }
    }

    if (urlFormatter.getSettingsUrl("").isPresent()) {
      sb.append("To register an email address, visit:\n")
          .append(urlFormatter.getSettingsUrl("EmailAddresses").get())
          .append("\n\n");
    }
    return new CommitValidationMessage(sb.toString(), Type.ERROR);
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
    messages.add(new CommitValidationMessage(error, Type.ERROR));
  }
}
