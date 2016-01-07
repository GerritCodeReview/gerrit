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
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.events.ChangeAbandonedEvent;
import com.google.gerrit.server.events.ChangeMergedEvent;
import com.google.gerrit.server.events.ChangeRestoredEvent;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.DraftPublishedEvent;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.events.HashtagsChangedEvent;
import com.google.gerrit.server.events.MergeFailedEvent;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gerrit.server.events.ProjectCreatedEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.events.ReviewerAddedEvent;
import com.google.gerrit.server.events.TopicChangedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
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
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Spawns local executables when a hook action occurs. */
@Singleton
public class ChangeHookRunner implements ChangeHooks, EventDispatcher,
  EventSource, LifecycleListener, NewProjectCreatedListener {
    /** A logger for this class. */
    private static final Logger log = LoggerFactory.getLogger(ChangeHookRunner.class);

    public static class Module extends LifecycleModule {
      @Override
      protected void configure() {
        bind(ChangeHookRunner.class);
        bind(ChangeHooks.class).to(ChangeHookRunner.class);
        bind(EventDispatcher.class).to(ChangeHookRunner.class);
        bind(EventSource.class).to(ChangeHookRunner.class);
        DynamicSet.bind(binder(), NewProjectCreatedListener.class).to(ChangeHookRunner.class);
        listener().to(ChangeHookRunner.class);
      }
    }

    private static class EventListenerHolder {
      final EventListener listener;
      final CurrentUser user;

      EventListenerHolder(EventListener l, CurrentUser u) {
        listener = l;
        user = u;
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

    /** Listeners to receive changes as they happen (limited by visibility
     *  of holder's user). */
    private final Map<EventListener, EventListenerHolder> listeners =
        new ConcurrentHashMap<>();

    /** Listeners to receive all changes as they happen. */
    private final DynamicSet<EventListener> unrestrictedListeners;

    /** Path of the new patchset hook. */
    private final Optional<Path> patchsetCreatedHook;

    /** Path of the draft published hook. */
    private final Optional<Path> draftPublishedHook;

    /** Path of the new comments hook. */
    private final Optional<Path> commentAddedHook;

    /** Path of the change merged hook. */
    private final Optional<Path> changeMergedHook;

    /** Path of the merge failed hook. */
    private final Optional<Path> mergeFailedHook;

    /** Path of the change abandoned hook. */
    private final Optional<Path> changeAbandonedHook;

    /** Path of the change restored hook. */
    private final Optional<Path> changeRestoredHook;

    /** Path of the ref updated hook. */
    private final Optional<Path> refUpdatedHook;

    /** Path of the reviewer added hook. */
    private final Optional<Path> reviewerAddedHook;

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
      EventFactory eventFactory,
      DynamicSet<EventListener> unrestrictedListeners) {
        this.anonymousCowardName = anonymousCowardName;
        this.repoManager = repoManager;
        this.hookQueue = queue.createQueue(1, "hook");
        this.projectCache = projectCache;
        this.accountCache = accountCache;
        this.eventFactory = eventFactory;
        this.sitePaths = sitePath;
        this.unrestrictedListeners = unrestrictedListeners;

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
        mergeFailedHook = hook(config, hooksPath, "merge-failed");
        changeAbandonedHook = hook(config, hooksPath, "change-abandoned");
        changeRestoredHook = hook(config, hooksPath, "change-restored");
        refUpdatedHook = hook(config, hooksPath, "ref-updated");
        reviewerAddedHook = hook(config, hooksPath, "reviewer-added");
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

    @Override
    public void addEventListener(EventListener listener, CurrentUser user) {
      listeners.put(listener, new EventListenerHolder(listener, user));
    }

    @Override
    public void removeEventListener(EventListener listener) {
      listeners.remove(listener);
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

    /**
     * Fire the update hook
     *
     */
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
      ProjectCreatedEvent event = new ProjectCreatedEvent();
      event.projectName = project.get();
      event.headName = headName;

      fireEvent(project, event);

      if (!projectCreatedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      addArg(args, "--project", project.get());
      addArg(args, "--head", headName);

      runHook(project, projectCreatedHook, args);
    }

    /**
     * Fire the Patchset Created Hook.
     *
     * @param change The change itself.
     * @param patchSet The Patchset that was created.
     * @param db Review database.
     * @throws OrmException
     */
    @Override
    public void doPatchsetCreatedHook(Change change,
        PatchSet patchSet, ReviewDb db) throws OrmException {
      PatchSetCreatedEvent event = new PatchSetCreatedEvent();
      Supplier<AccountState> uploader =
          getAccountSupplier(patchSet.getUploader());
      Supplier<AccountState> owner = getAccountSupplier(change.getOwner());

      event.change = changeAttributeSupplier(change);
      event.patchSet = patchSetAttributeSupplier(change, patchSet);
      event.uploader = accountAttributeSupplier(uploader);

      fireEvent(change, event, db);

      if (!patchsetCreatedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();

      ChangeAttribute c = event.change.get();
      PatchSetAttribute ps = event.patchSet.get();
      addArg(args, "--change", c.id);
      addArg(args, "--is-draft", String.valueOf(patchSet.isDraft()));
      addArg(args, "--kind", String.valueOf(ps.kind));
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.get().getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--topic", c.topic);
      addArg(args, "--uploader", getDisplayName(uploader.get().getAccount()));
      addArg(args, "--commit", ps.revision);
      addArg(args, "--patchset", ps.number);

      runHook(change.getProject(), patchsetCreatedHook, args);
    }

    @Override
    public void doDraftPublishedHook(Change change, PatchSet patchSet,
          ReviewDb db) throws OrmException {
      DraftPublishedEvent event = new DraftPublishedEvent();
      Supplier<AccountState> uploader =
          getAccountSupplier(patchSet.getUploader());
      Supplier<AccountState> owner = getAccountSupplier(change.getOwner());

      event.change = changeAttributeSupplier(change);
      event.patchSet = patchSetAttributeSupplier(change, patchSet);
      event.uploader = accountAttributeSupplier(uploader);

      fireEvent(change, event, db);

      if (!draftPublishedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = event.change.get();
      PatchSetAttribute ps = event.patchSet.get();

      addArg(args, "--change", c.id);
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.get().getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--topic", c.topic);
      addArg(args, "--uploader", getDisplayName(uploader.get().getAccount()));
      addArg(args, "--commit", ps.revision);
      addArg(args, "--patchset", ps.number);

      runHook(change.getProject(), draftPublishedHook, args);
    }

    @Override
    public void doCommentAddedHook(final Change change, Account account,
          PatchSet patchSet, String comment, final Map<String, Short> approvals,
          ReviewDb db) throws OrmException {
      CommentAddedEvent event = new CommentAddedEvent();
      Supplier<AccountState> owner = getAccountSupplier(change.getOwner());

      event.change = changeAttributeSupplier(change);
      event.author =  accountAttributeSupplier(account);
      event.patchSet = patchSetAttributeSupplier(change, patchSet);
      event.comment = comment;
      event.approvals = Suppliers.memoize(
          new Supplier<ApprovalAttribute[]>() {
            @Override
            public ApprovalAttribute[] get() {
              LabelTypes labelTypes = projectCache.get(
                  change.getProject()).getLabelTypes();
              if (approvals.size() > 0) {
                ApprovalAttribute[] r = new ApprovalAttribute[approvals.size()];
                int i = 0;
                for (Map.Entry<String, Short> approval : approvals.entrySet()) {
                  r[i++] = getApprovalAttribute(labelTypes, approval);
                }
                return r;
              }
              return null;
            }
          });

      fireEvent(change, event, db);

      if (!commentAddedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = event.change.get();
      PatchSetAttribute ps = event.patchSet.get();

      addArg(args, "--change", c.id);
      addArg(args, "--is-draft", patchSet.isDraft() ? "true" : "false");
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.get().getAccount()));
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
        if (lt != null) {
          addArg(args, "--" + lt.getName(), Short.toString(approval.getValue()));
        }
      }

      runHook(change.getProject(), commentAddedHook, args);
    }

    @Override
    public void doChangeMergedHook(Change change, Account account,
        PatchSet patchSet, ReviewDb db, String mergeResultRev)
        throws OrmException {
      ChangeMergedEvent event = new ChangeMergedEvent();
      Supplier<AccountState> owner = getAccountSupplier(change.getOwner());

      event.change = changeAttributeSupplier(change);
      event.submitter = accountAttributeSupplier(account);
      event.patchSet = patchSetAttributeSupplier(change, patchSet);
      event.newRev = mergeResultRev;

      fireEvent(change, event, db);

      if (!changeMergedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = event.change.get();
      PatchSetAttribute ps = event.patchSet.get();

      addArg(args, "--change", c.id);
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.get().getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--topic", c.topic);
      addArg(args, "--submitter", getDisplayName(account));
      addArg(args, "--commit", ps.revision);
      addArg(args, "--newrev", mergeResultRev);

      runHook(change.getProject(), changeMergedHook, args);
    }

    @Override
    public void doMergeFailedHook(Change change, Account account,
          PatchSet patchSet, String reason,
          ReviewDb db) throws OrmException {
      MergeFailedEvent event = new MergeFailedEvent();
      Supplier<AccountState> owner = getAccountSupplier(change.getOwner());

      event.change = changeAttributeSupplier(change);
      event.submitter = accountAttributeSupplier(account);
      event.patchSet = patchSetAttributeSupplier(change, patchSet);
      event.reason = reason;

      fireEvent(change, event, db);

      if (!mergeFailedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = event.change.get();
      PatchSetAttribute ps = event.patchSet.get();

      addArg(args, "--change", c.id);
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.get().getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--topic", c.topic);
      addArg(args, "--submitter", getDisplayName(account));
      addArg(args, "--commit", ps.revision);
      addArg(args, "--reason",  reason == null ? "" : reason);

      runHook(change.getProject(), mergeFailedHook, args);
    }

    @Override
    public void doChangeAbandonedHook(Change change, Account account,
          PatchSet patchSet, String reason, ReviewDb db)
          throws OrmException {
      ChangeAbandonedEvent event = new ChangeAbandonedEvent();
      AccountState owner = accountCache.get(change.getOwner());

      event.change = changeAttributeSupplier(change);
      event.abandoner = accountAttributeSupplier(account);
      event.patchSet = patchSetAttributeSupplier(change, patchSet);
      event.reason = reason;

      fireEvent(change, event, db);

      if (!changeAbandonedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = event.change.get();
      PatchSetAttribute ps = event.patchSet.get();

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
      ChangeRestoredEvent event = new ChangeRestoredEvent();
      AccountState owner = accountCache.get(change.getOwner());

      event.change = changeAttributeSupplier(change);
      event.restorer = accountAttributeSupplier(account);
      event.patchSet = patchSetAttributeSupplier(change, patchSet);
      event.reason = reason;

      fireEvent(change, event, db);

      if (!changeRestoredHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = event.change.get();
      PatchSetAttribute ps = event.patchSet.get();

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
    public void doRefUpdatedHook(Branch.NameKey refName, RefUpdate refUpdate,
        Account account) {
      doRefUpdatedHook(refName, refUpdate.getOldObjectId(),
          refUpdate.getNewObjectId(), account);
    }

    @Override
    public void doRefUpdatedHook(final Branch.NameKey refName,
        final ObjectId oldId, final ObjectId newId, Account account) {
      RefUpdatedEvent event = new RefUpdatedEvent();

      if (account != null) {
        event.submitter = accountAttributeSupplier(account);
      }
      event.refUpdate = Suppliers.memoize(
          new Supplier<RefUpdateAttribute>() {
            @Override
            public RefUpdateAttribute get() {
              return eventFactory.asRefUpdateAttribute(oldId, newId, refName);
            }
          });

      fireEvent(refName, event);

      if (!refUpdatedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      RefUpdateAttribute r = event.refUpdate.get();
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
      ReviewerAddedEvent event = new ReviewerAddedEvent();
      Supplier<AccountState> owner = getAccountSupplier(change.getOwner());

      event.change = changeAttributeSupplier(change);
      event.patchSet = patchSetAttributeSupplier(change, patchSet);
      event.reviewer = accountAttributeSupplier(account);

      fireEvent(change, event, db);

      if (!reviewerAddedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = event.change.get();

      addArg(args, "--change", c.id);
      addArg(args, "--change-url", c.url);
      addArg(args, "--change-owner", getDisplayName(owner.get().getAccount()));
      addArg(args, "--project", c.project);
      addArg(args, "--branch", c.branch);
      addArg(args, "--reviewer", getDisplayName(account));

      runHook(change.getProject(), reviewerAddedHook, args);
    }

    @Override
    public void doTopicChangedHook(Change change, Account account,
        String oldTopic, ReviewDb db)
            throws OrmException {
      TopicChangedEvent event = new TopicChangedEvent();
      AccountState owner = accountCache.get(change.getOwner());

      event.change = changeAttributeSupplier(change);
      event.changer = accountAttributeSupplier(account);
      event.oldTopic = oldTopic;

      fireEvent(change, event, db);

      if (!topicChangedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = event.change.get();

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
      HashtagsChangedEvent event = new HashtagsChangedEvent();
      AccountState owner = accountCache.get(change.getOwner());

      event.change = changeAttributeSupplier(change);
      event.editor = accountAttributeSupplier(account);
      event.hashtags = hashtagArray(hashtags);
      event.added = hashtagArray(added);
      event.removed = hashtagArray(removed);

      fireEvent(change, event, db);

      if (!hashtagsChangedHook.isPresent()) {
        return;
      }

      List<String> args = new ArrayList<>();
      ChangeAttribute c = event.change.get();

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

    @Override
    public void postEvent(Change change, com.google.gerrit.server.events.Event event,
        ReviewDb db) throws OrmException {
      fireEvent(change, event, db);
    }

    @Override
    public void postEvent(Branch.NameKey branchName, com.google.gerrit.server.events.Event event) {
      fireEvent(branchName, event);
    }

    private Supplier<AccountState> getAccountSupplier(
        final Account.Id account) {
      return Suppliers.memoize(
          new Supplier<AccountState>() {
            @Override
            public AccountState get() {
              return accountCache.get(account);
            }
          });
    }

    private Supplier<AccountAttribute> accountAttributeSupplier(
        final Supplier<AccountState> s) {
      return Suppliers.memoize(
          new Supplier<AccountAttribute>() {
            @Override
            public AccountAttribute get() {
              return eventFactory.asAccountAttribute(s.get().getAccount());
            }
          });
    }

    private Supplier<AccountAttribute> accountAttributeSupplier(
        final Account account) {
      return Suppliers.memoize(
          new Supplier<AccountAttribute>() {
            @Override
            public AccountAttribute get() {
              return eventFactory.asAccountAttribute(account);
            }
          });
    }

    private Supplier<PatchSetAttribute> patchSetAttributeSupplier(
        final Change change, final PatchSet patchSet) {
      return Suppliers.memoize(
          new Supplier<PatchSetAttribute>() {
            @Override
            public PatchSetAttribute get() {
              try (Repository repo
                  = repoManager.openRepository(change.getProject());
                  RevWalk revWalk = new RevWalk(repo)) {
                return eventFactory.asPatchSetAttribute(revWalk, patchSet);
              } catch (IOException e) {
                throw Throwables.propagate(e);
              }
            }
          });
    }

    private Supplier<ChangeAttribute> changeAttributeSupplier(
        final Change change) {
      return Suppliers.memoize(
          new Supplier<ChangeAttribute>() {
            @Override
            public ChangeAttribute get() {
              return eventFactory.asChangeAttribute(change);
            }
          });
    }

    private void fireEventForUnrestrictedListeners(com.google.gerrit.server.events.Event event) {
      for (EventListener listener : unrestrictedListeners) {
        listener.onEvent(event);
      }
    }

    private void fireEvent(Change change, com.google.gerrit.server.events.Event event,
        ReviewDb db) throws OrmException {
      for (EventListenerHolder holder : listeners.values()) {
        if (isVisibleTo(change, holder.user, db)) {
          holder.listener.onEvent(event);
        }
      }

      fireEventForUnrestrictedListeners( event );
    }

    private void fireEvent(Project.NameKey project, ProjectCreatedEvent event) {
      for (EventListenerHolder holder : listeners.values()) {
        if (isVisibleTo(project, event, holder.user)) {
          holder.listener.onEvent(event);
        }
      }

      fireEventForUnrestrictedListeners(event);
    }

    private void fireEventForUnrestrictedListeners(ProjectCreatedEvent event) {
      for (EventListener listener : unrestrictedListeners) {
        listener.onEvent(event);
      }
    }

    private boolean isVisibleTo(Project.NameKey project, ProjectCreatedEvent event, CurrentUser user) {
      ProjectState pe = projectCache.get(project);
      if (pe == null) {
        return false;
      }
      ProjectControl pc = pe.controlFor(user);
      return pc.controlForRef(event.getHeadName()).isVisible();
    }

    private void fireEvent(Branch.NameKey branchName, com.google.gerrit.server.events.Event event) {
      for (EventListenerHolder holder : listeners.values()) {
        if (isVisibleTo(branchName, holder.user)) {
          holder.listener.onEvent(event);
        }
      }

      fireEventForUnrestrictedListeners(event);
    }

    private boolean isVisibleTo(Change change, CurrentUser user, ReviewDb db)
        throws OrmException {
      ProjectState pe = projectCache.get(change.getProject());
      if (pe == null) {
        return false;
      }
      ProjectControl pc = pe.controlFor(user);
      return pc.controlFor(change).isVisible(db);
    }

    private boolean isVisibleTo(Branch.NameKey branchName, CurrentUser user) {
      ProjectState pe = projectCache.get(branchName.getParentKey());
      if (pe == null) {
        return false;
      }
      ProjectControl pc = pe.controlFor(user);
      return pc.controlForRef(branchName).isVisible();
    }

    /**
     * Create an ApprovalAttribute for the given approval suitable for serialization to JSON.
     * @param approval
     * @return object suitable for serialization to JSON
     */
    private ApprovalAttribute getApprovalAttribute(LabelTypes labelTypes,
            Entry<String, Short> approval) {
      ApprovalAttribute a = new ApprovalAttribute();
      a.type = approval.getKey();
      LabelType lt = labelTypes.byLabel(approval.getKey());
      if (lt != null) {
        a.description = lt.getName();
      }
      a.value = Short.toString(approval.getValue());
      return a;
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

  @Override
  public void onNewProjectCreated(NewProjectCreatedListener.Event event) {
    Project.NameKey project = new Project.NameKey(event.getProjectName());
    doProjectCreatedHook(project, event.getHeadName());
  }
}
