// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.common;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Spawns local executables when a hook action occurs. */
@Singleton
public class ChangeHookRunner implements ChangeHooks, LifecycleListener {
    /** A logger for this class. */
    private static final Logger log = LoggerFactory.getLogger(ChangeHookRunner.class);

    public static class Module extends LifecycleModule {
      @Override
      protected void configure() {
        bind(ChangeHookRunner.class);
        bind(ChangeHooks.class).to(ChangeHookRunner.class);
        listener().to(ChangeHookRunner.class);
      }
    }

    /** Container class used to hold the return code and output of script hook execution */
    public static class HookResult {
      private int exitValue = -1;
      private String output;
      private String executionError;

      private HookResult(int exitValue, String output) {
        this.exitValue = exitValue;
        this.output = output;
      }

      private HookResult(String output, String executionError) {
        this.output = output;
        this.executionError = executionError;
      }

      public int getExitValue() {
        return exitValue;
      }

      public void setExitValue(int exitValue) {
        this.exitValue = exitValue;
      }

      public String getOutput() {
        return output;
      }

      @Override
      public String toString() {
        StringBuilder sb = new StringBuilder();

        if (output != null && output.length() != 0) {
          sb.append(output);

          if (executionError != null) {
            sb.append(" - ");
          }
        }

        if (executionError != null ) {
          sb.append(executionError);
        }

        return sb.toString();
      }
    }

    /** Path of the new patchset hook. */
    private final Optional<Path> patchsetCreatedHook;

    /** Path of the draft published hook. */
    private final Optional<Path> draftPublishedHook;

    /** Path of the new comments hook. */
    private final Optional<Path> commentAddedHook;

    /** Path of the change merged hook. */
    private final Optional<Path> changeMergedHook;

    /** Path of the change abandoned hook. */
    private final Optional<Path> changeAbandonedHook;

    /** Path of the change restored hook. */
    private final Optional<Path> changeRestoredHook;

    /** Path of the ref updated hook. */
    private final Optional<Path> refUpdatedHook;

    /** Path of the reviewer added hook. */
    private final Optional<Path> reviewerAddedHook;

    /** Path of the reviewer deleted hook. */
    private final Optional<Path> reviewerDeletedHook;

    /** Path of the topic changed hook. */
    private final Optional<Path> topicChangedHook;

    /** Path of the cla signed hook. */
    private final Optional<Path> claSignedHook;

    /** Path of the update hook. */
    private final Optional<Path> refUpdateHook;

    /** Path of the hashtags changed hook */
    private final Optional<Path> hashtagsChangedHook;

    /** Path of the project created hook. */
    private final Optional<Path> projectCreatedHook;

    private final String anonymousCowardName;

    /** Repository Manager. */
    private final GitRepositoryManager repoManager;

    /** Queue of hooks that need to run. */
    private final WorkQueue.Executor hookQueue;

    private final ProjectCache projectCache;

    private final AccountCache accountCache;

    private final EventFactory eventFactory;

    private final SitePaths sitePaths;

    /** Thread pool used to monitor sync hooks */
    private final ExecutorService syncHookThreadPool;

    /** Timeout value for synchronous hooks */
    private final int syncHookTimeout;

    /**
     * Create a new ChangeHookRunner.
     *
     * @param queue Queue to use when processing hooks.
     * @param repoManager The repository manager.
     * @param config Config file to use.
     * @param sitePath The sitepath of this gerrit install.
     * @param projectCache the project cache instance for the server.
     */
    @Inject
    public ChangeHookRunner(WorkQueue queue,
      GitRepositoryManager repoManager,
      @GerritServerConfig Config config,
      @AnonymousCowardName String anonymousCowardName,
      SitePaths sitePath,
      ProjectCache projectCache,
      AccountCache accountCache,
      EventFactory eventFactory) {
        this.anonymousCowardName = anonymousCowardName;
        this.repoManager = repoManager;
        this.hookQueue = queue.createQueue(1, "hook");
        this.projectCache = projectCache;
        this.accountCache = accountCache;
        this.eventFactory = eventFactory;
        this.sitePaths = sitePath;

        Path hooksPath;
        String hooksPathConfig = config.getString("hooks", null, "path");
        if (hooksPathConfig != null) {
          hooksPath = Paths.get(hooksPathConfig);
        } else {
          hooksPath = sitePath.hooks_dir;
        }

        // When adding a new hook, make sure to check that the setting name
        // canonicalizes correctly in hook() below.
        patchsetCreatedHook = hook(config, hooksPath, "patchset-created");
        draftPublishedHook = hook(config, hooksPath, "draft-published");
        commentAddedHook = hook(config, hooksPath, "comment-added");
        changeMergedHook = hook(config, hooksPath, "change-merged");
        changeAbandonedHook = hook(config, hooksPath, "change-abandoned");
        changeRestoredHook = hook(config, hooksPath, "change-restored");
        refUpdatedHook = hook(config, hooksPath, "ref-updated");
        reviewerAddedHook = hook(config, hooksPath, "reviewer-added");
        reviewerDeletedHook = hook(config, hooksPath, "reviewer-deleted");
        topicChangedHook = hook(config, hooksPath, "topic-changed");
        claSignedHook = hook(config, hooksPath, "cla-signed");
        refUpdateHook = hook(config, hooksPath, "ref-update");
        hashtagsChangedHook = hook(config, hooksPath, "hashtags-changed");
        projectCreatedHook = hook(config, hooksPath, "project-created");

        syncHookTimeout = config.getInt("hooks", "syncHookTimeout", 30);
        syncHookThreadPool = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
              .setNameFormat("SyncHook-%d")
              .build());
    }

    private static Optional<Path> hook(Config config, Path path, String name) {
      String setting = name.replace("-", "") + "hook";
      String value = config.getString("hooks", null, setting);
      Path p = path.resolve(value != null ? value : name);
      return Files.exists(p) ? Optional.of(p) : Optional.<Path>absent();
    }

    /**
     * Get the Repository for the given project name, or null on error.
     *
     * @param name Project to get repo for,
     * @return Repository or null.
     */
    private Repository openRepository(Project.NameKey name) {
      try {
        return repoManager.openRepository(name);
      } catch (IOException err) {
        log.warn("Cannot open repository " + name.get(), err);
        return null;
      }
    }

    private void addArg(List<String> args, String name, String value) {
      if (value != null) {
        args.add(name);
        args.add(value);
      }
    }

    @Override
    public HookResult doRefUpdateHook(Project project, String refname,
        Account uploader, ObjectId oldId, ObjectId newId) {
      if (!refUpdateHook.isPresent()) {
        return null;
      }

      List<String> args = new ArrayList<>();
      addArg(args, "--project", project.getName());
      addArg(args, "--refname", refname);
      addArg(args, "--uploader", getDisplayName(uploader));
      addArg(args, "--oldrev", oldId.getName());
      addArg(args, "--newrev", newId.getName());

      return runSyncHook(project.getNameKey(), refUpdateHook, args);
    }

    @Override
    public void doProjectCreatedHook(Project.NameKey project, String headName) {
      if (!projectCreatedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      addArg(args, "--project", project.get());
      addArg(args, "--head", headName);

      runHook(project, projectCreatedHook, args);
    }

    @Override
    public void doPatchsetCreatedHook(Change change,
        PatchSet patchSet, ReviewDb db) throws OrmException {
      if (!patchsetCreatedHook.isPresent()) {
        return;
      }

      AccountState owner = accountCache.get(change.getOwner());
      AccountState uploader = accountCache.get(patchSet.getUploader());
      List<String> args = new ArrayList<>();
      ChangeAttribute c = eventFactory.asChangeAttribute(change);
      PatchSetAttribute ps = patchSetAttribute(change, patchSet);

      addArg(args, "--change", c.id);
      addArg(args, "--is-draft", String.valueOf(patchSet.isDraft()));
      addArg(args, "--kind", String.valueOf(ps.kind));
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--topic", c.topic);
      addArg(args, "--uploader", getDisplayName(uploader.getAccount()));
      addArg(args, "--commit", ps.revision);
      addArg(args, "--patchset", ps.number);

      runHook(change.getProject(), patchsetCreatedHook, args);
    }

    @Override
    public void doDraftPublishedHook(Change change, PatchSet patchSet,
          ReviewDb db) throws OrmException {
      if (!draftPublishedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = eventFactory.asChangeAttribute(change);
      PatchSetAttribute ps = patchSetAttribute(change, patchSet);
      AccountState owner = accountCache.get(change.getOwner());
      AccountState uploader = accountCache.get(patchSet.getUploader());

      addArg(args, "--change", c.id);
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--topic", c.topic);
      addArg(args, "--uploader", getDisplayName(uploader.getAccount()));
      addArg(args, "--commit", ps.revision);
      addArg(args, "--patchset", ps.number);

      runHook(change.getProject(), draftPublishedHook, args);
    }

    @Override
    public void doCommentAddedHook(final Change change, Account account,
          PatchSet patchSet, String comment, final Map<String, Short> approvals,
          final Map<String, Short> oldApprovals, ReviewDb db)
              throws OrmException {
      if (!commentAddedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = eventFactory.asChangeAttribute(change);
      PatchSetAttribute ps = patchSetAttribute(change, patchSet);
      AccountState owner = accountCache.get(change.getOwner());

      addArg(args, "--change", c.id);
      addArg(args, "--is-draft", patchSet.isDraft() ? "true" : "false");
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--topic", c.topic);
      addArg(args, "--author", getDisplayName(account));
      addArg(args, "--commit", ps.revision);
      addArg(args, "--comment", comment == null ? "" : comment);
      LabelTypes labelTypes = projectCache.get(
          change.getProject()).getLabelTypes();
      for (Map.Entry<String, Short> approval : approvals.entrySet()) {
        LabelType lt = labelTypes.byLabel(approval.getKey());
        if (lt != null && approval.getValue() != null) {
          addArg(args, "--" + lt.getName(), Short.toString(approval.getValue()));
          if (oldApprovals != null && !oldApprovals.isEmpty()) {
            Short oldValue = oldApprovals.get(approval.getKey());
            if (oldValue != null) {
              addArg(args, "--" + lt.getName() + "-oldValue",
                  Short.toString(oldValue));
            }
          }
        }
      }
      runHook(change.getProject(), commentAddedHook, args);
    }

    @Override
    public void doChangeMergedHook(Change change, Account account,
        PatchSet patchSet, ReviewDb db, String mergeResultRev)
        throws OrmException {
      if (!changeMergedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = eventFactory.asChangeAttribute(change);
      PatchSetAttribute ps = patchSetAttribute(change, patchSet);
      AccountState owner = accountCache.get(change.getOwner());

      addArg(args, "--change", c.id);
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--topic", c.topic);
      addArg(args, "--submitter", getDisplayName(account));
      addArg(args, "--commit", ps.revision);
      addArg(args, "--newrev", mergeResultRev);

      runHook(change.getProject(), changeMergedHook, args);
    }

    @Override
    public void doChangeAbandonedHook(Change change, Account account,
          PatchSet patchSet, String reason, ReviewDb db)
          throws OrmException {
      if (!changeAbandonedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = eventFactory.asChangeAttribute(change);
      PatchSetAttribute ps = patchSetAttribute(change, patchSet);
      AccountState owner = accountCache.get(change.getOwner());

      addArg(args, "--change", c.id);
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--topic", c.topic);
      addArg(args, "--abandoner", getDisplayName(account));
      addArg(args, "--commit", ps.revision);
      addArg(args, "--reason", reason == null ? "" : reason);

      runHook(change.getProject(), changeAbandonedHook, args);
    }

    @Override
    public void doChangeRestoredHook(Change change, Account account,
          PatchSet patchSet, String reason, ReviewDb db)
          throws OrmException {
      if (!changeRestoredHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = eventFactory.asChangeAttribute(change);
      PatchSetAttribute ps = patchSetAttribute(change, patchSet);
      AccountState owner = accountCache.get(change.getOwner());

      addArg(args, "--change", c.id);
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--topic", c.topic);
      addArg(args, "--restorer", getDisplayName(account));
      addArg(args, "--commit", ps.revision);
      addArg(args, "--reason", reason == null ? "" : reason);

      runHook(change.getProject(), changeRestoredHook, args);
    }

    @Override
    public void doRefUpdatedHook(final Branch.NameKey refName,
        final ObjectId oldId, final ObjectId newId, Account account) {
      if (!refUpdatedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      RefUpdateAttribute r =
          eventFactory.asRefUpdateAttribute(oldId, newId, refName);
      addArg(args, "--oldrev", r.oldRev);
      addArg(args, "--newrev", r.newRev);
      addArg(args, "--refname", r.refName);
      addArg(args, "--project", r.project);
      if (account != null) {
        addArg(args, "--submitter", getDisplayName(account));
      }

      runHook(refName.getParentKey(), refUpdatedHook, args);
    }

    @Override
    public void doReviewerAddedHook(Change change, Account account,
        PatchSet patchSet, ReviewDb db) throws OrmException {
      if (!reviewerAddedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = eventFactory.asChangeAttribute(change);
      AccountState owner = accountCache.get(change.getOwner());

      addArg(args, "--change", c.id);
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--reviewer", getDisplayName(account));

      runHook(change.getProject(), reviewerAddedHook, args);
    }

    @Override
    public void doReviewerDeletedHook(final Change change, Account account,
      PatchSet patchSet, String comment, final Map<String, Short> approvals,
      final Map<String, Short> oldApprovals, ReviewDb db) throws OrmException {
      if (!reviewerDeletedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = eventFactory.asChangeAttribute(change);
      AccountState owner = accountCache.get(change.getOwner());

      addArg(args, "--change", c.id);
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--reviewer", getDisplayName(account));
      LabelTypes labelTypes = projectCache.get(
          change.getProject()).getLabelTypes();
      // append votes that were removed
      for (Map.Entry<String, Short> approval : approvals.entrySet()) {
        LabelType lt = labelTypes.byLabel(approval.getKey());
        if (lt != null && approval.getValue() != null) {
          addArg(args, "--" + lt.getName(), Short.toString(approval.getValue()));
          if (oldApprovals != null && !oldApprovals.isEmpty()) {
            Short oldValue = oldApprovals.get(approval.getKey());
            if (oldValue != null) {
              addArg(args, "--" + lt.getName() + "-oldValue",
                  Short.toString(oldValue));
            }
          }
        }
      }
      runHook(change.getProject(), reviewerDeletedHook, args);
    }

    @Override
    public void doTopicChangedHook(Change change, Account account,
        String oldTopic, ReviewDb db)
            throws OrmException {
      if (!topicChangedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = eventFactory.asChangeAttribute(change);
      AccountState owner = accountCache.get(change.getOwner());

      addArg(args, "--change", c.id);
      addArg(args, "--change-owner", getDisplayName(owner.getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--changer", getDisplayName(account));
      addArg(args, "--old-topic", oldTopic);
      addArg(args, "--new-topic", c.topic);

      runHook(change.getProject(), topicChangedHook, args);
    }

    String[] hashtagArray(Set<String> hashtags) {
      if (hashtags != null && hashtags.size() > 0) {
        return Sets.newHashSet(hashtags).toArray(
            new String[hashtags.size()]);
      }
      return null;
    }

    @Override
    public void doHashtagsChangedHook(Change change, Account account,
        Set<String> added, Set<String> removed, Set<String> hashtags, ReviewDb db)
            throws OrmException {
      if (!hashtagsChangedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = eventFactory.asChangeAttribute(change);
      AccountState owner = accountCache.get(change.getOwner());

      addArg(args, "--change", c.id);
      addArg(args, "--change-owner", getDisplayName(owner.getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--editor", getDisplayName(account));
      if (hashtags != null) {
        for (String hashtag : hashtags) {
          addArg(args, "--hashtag", hashtag);
        }
      }
      if (added != null) {
        for (String hashtag : added) {
          addArg(args, "--added", hashtag);
        }
      }
      if (removed != null) {
        for (String hashtag : removed) {
          addArg(args, "--removed", hashtag);
        }
      }
      runHook(change.getProject(), hashtagsChangedHook, args);
    }

    @Override
    public void doClaSignupHook(Account account, String claName) {
      if (!claSignedHook.isPresent()) {
        return;
      }

      if (account != null) {
        List<String> args = new ArrayList<>();
        addArg(args, "--submitter", getDisplayName(account));
        addArg(args, "--user-id", account.getId().toString());
        addArg(args, "--cla-name", claName);

        runHook(claSignedHook, args);
      }
    }

    private PatchSetAttribute patchSetAttribute(Change change,
        PatchSet patchSet) {
      try (Repository repo =
            repoManager.openRepository(change.getProject());
          RevWalk revWalk = new RevWalk(repo)) {
        return eventFactory.asPatchSetAttribute(
            revWalk, change, patchSet);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Get the display name for the given account.
     *
     * @param account Account to get name for.
     * @return Name for this account.
     */
    private String getDisplayName(Account account) {
      if (account != null) {
        String result = (account.getFullName() == null)
            ? anonymousCowardName
            : account.getFullName();
        if (account.getPreferredEmail() != null) {
          result += " (" + account.getPreferredEmail() + ")";
        }
        return result;
      }

      return anonymousCowardName;
    }

  /**
   * Run a hook.
   *
   * @param project used to open repository to run the hook for.
   * @param hook the hook to execute.
   * @param args Arguments to use to run the hook.
   */
  private synchronized void runHook(Project.NameKey project, Optional<Path> hook,
      List<String> args) {
    if (project != null && hook.isPresent()) {
      hookQueue.execute(new AsyncHookTask(project, hook.get(), args));
    }
  }

  private synchronized void runHook(Optional<Path> hook, List<String> args) {
    if (hook.isPresent()) {
      hookQueue.execute(new AsyncHookTask(null, hook.get(), args));
    }
  }

  private HookResult runSyncHook(Project.NameKey project,
      Optional<Path> hook, List<String> args) {

    if (!hook.isPresent()) {
      return null;
    }

    SyncHookTask syncHook = new SyncHookTask(project, hook.get(), args);
    FutureTask<HookResult> task = new FutureTask<>(syncHook);

    syncHookThreadPool.execute(task);

    String message;

    try {
      return task.get(syncHookTimeout, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      message = "Synchronous hook timed out "  + hook.get().toAbsolutePath();
      log.error(message);
    } catch (Exception e) {
      message = "Error running hook " + hook.get().toAbsolutePath();
      log.error(message, e);
    }

    task.cancel(true);
    syncHook.cancel();
    return  new HookResult(syncHook.getOutput(), message);
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
    syncHookThreadPool.shutdown();
    boolean isTerminated;
    do {
      try {
        isTerminated = syncHookThreadPool.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException ie) {
        isTerminated = false;
      }
    } while (!isTerminated);
  }

  private class HookTask {
    private final Project.NameKey project;
    private final Path hook;
    private final List<String> args;
    private StringWriter output;
    private Process ps;

    protected HookTask(Project.NameKey project, Path hook, List<String> args) {
      this.project = project;
      this.hook = hook;
      this.args = args;
    }

    public String getOutput() {
      return output != null ? output.toString() : null;
    }

    protected HookResult runHook() {
      Repository repo = null;
      HookResult result = null;
      try {

        List<String> argv = new ArrayList<>(1 + args.size());
        argv.add(hook.toAbsolutePath().toString());
        argv.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(true);

        if (project != null) {
          repo = openRepository(project);
        }

        Map<String, String> env = pb.environment();
        env.put("GERRIT_SITE", sitePaths.site_path.toAbsolutePath().toString());

        if (repo != null) {
          pb.directory(repo.getDirectory());

          env.put("GIT_DIR", repo.getDirectory().getAbsolutePath());
        }

        ps = pb.start();
        ps.getOutputStream().close();
        String output = null;
        try (InputStream is = ps.getInputStream()) {
          output = readOutput(is);
        } finally {
          ps.waitFor();
          result = new HookResult(ps.exitValue(), output);
        }
      } catch (InterruptedException iex) {
        // InterruptedExeception - timeout or cancel
      } catch (Throwable err) {
        log.error("Error running hook " + hook.toAbsolutePath(), err);
      } finally {
        if (repo != null) {
          repo.close();
        }
      }

      if (result != null) {
        int exitValue = result.getExitValue();
        if (exitValue == 0) {
          log.debug("hook[" + getName() + "] exitValue:" + exitValue);
        } else {
          log.info("hook[" + getName() + "] exitValue:" + exitValue);
        }

        BufferedReader br =
            new BufferedReader(new StringReader(result.getOutput()));
        try {
          String line;
          while ((line = br.readLine()) != null) {
            log.info("hook[" + getName() + "] output: " + line);
          }
        } catch (IOException iox) {
          log.error("Error writing hook output", iox);
        }
      }

      return result;
    }

    private String readOutput(InputStream is) throws IOException {
      output = new StringWriter();
      InputStreamReader input = new InputStreamReader(is);
      char[] buffer = new char[4096];
      int n;
      while ((n = input.read(buffer)) != -1) {
        output.write(buffer, 0, n);
      }

      return output.toString();
    }

    protected String getName() {
      return hook.getFileName().toString();
    }

    @Override
    public String toString() {
      return "hook " + hook.getFileName();
    }

    public void cancel() {
      ps.destroy();
    }
  }

  /** Callable type used to run synchronous hooks */
  private final class SyncHookTask extends HookTask
      implements Callable<HookResult> {

    private SyncHookTask(Project.NameKey project, Path hook, List<String> args) {
      super(project, hook, args);
    }

    @Override
    public HookResult call() throws Exception {
      return super.runHook();
    }
  }

  /** Runnable type used to run asynchronous hooks */
  private final class AsyncHookTask extends HookTask implements Runnable {

    private AsyncHookTask(Project.NameKey project, Path hook, List<String> args) {
      super(project, hook, args);
    }

    @Override
    public void run() {
      super.runHook();
    }
  }
}
