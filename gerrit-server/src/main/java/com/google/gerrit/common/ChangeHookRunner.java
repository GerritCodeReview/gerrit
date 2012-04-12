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

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.events.ApprovalAttribute;
import com.google.gerrit.server.events.ChangeAbandonedEvent;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gerrit.server.events.ChangeMergedEvent;
import com.google.gerrit.server.events.ChangeRestoreEvent;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/** Spawns local executables when a hook action occurs. */
@Singleton
public class ChangeHookRunner implements ChangeHooks {
    /** A logger for this class. */
    private static final Logger log = LoggerFactory.getLogger(ChangeHookRunner.class);

    public static class Module extends AbstractModule {
      @Override
      protected void configure() {
        bind(ChangeHookRunner.class);
        bind(ChangeHooks.class).to(ChangeHookRunner.class);
      }
    }

    private static class ChangeListenerHolder {
        final ChangeListener listener;
        final IdentifiedUser user;

        ChangeListenerHolder(ChangeListener l, IdentifiedUser u) {
            listener = l;
            user = u;
        }
    }

    /** Listeners to receive changes as they happen. */
    private final Map<ChangeListener, ChangeListenerHolder> listeners =
      new ConcurrentHashMap<ChangeListener, ChangeListenerHolder>();

    /** Filename of the new patchset hook. */
    private final File patchsetCreatedHook;

    /** Filename of the new comments hook. */
    private final File commentAddedHook;

    /** Filename of the change merged hook. */
    private final File changeMergedHook;

    /** Filename of the change abandoned hook. */
    private final File changeAbandonedHook;

    /** Filename of the change abandoned hook. */
    private final File changeRestoredHook;

    /** Filename of the ref updated hook. */
    private final File refUpdatedHook;

    /** Filename of the cla signed hook. */
    private final File claSignedHook;

    private final String anonymousCowardName;

    /** Repository Manager. */
    private final GitRepositoryManager repoManager;

    /** Queue of hooks that need to run. */
    private final WorkQueue.Executor hookQueue;

    private final ProjectCache projectCache;

    private final AccountCache accountCache;

    private final ApprovalTypes approvalTypes;

    private final EventFactory eventFactory;

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
    public ChangeHookRunner(final WorkQueue queue,
      final GitRepositoryManager repoManager,
      final @GerritServerConfig Config config,
      final @AnonymousCowardName String anonymousCowardName,
      final SitePaths sitePath, final ProjectCache projectCache,
      final AccountCache accountCache, final ApprovalTypes approvalTypes,
      final EventFactory eventFactory) {
        this.anonymousCowardName = anonymousCowardName;
        this.repoManager = repoManager;
        this.hookQueue = queue.createQueue(1, "hook");
        this.projectCache = projectCache;
        this.accountCache = accountCache;
        this.approvalTypes = approvalTypes;
        this.eventFactory = eventFactory;

        final File hooksPath = sitePath.resolve(getValue(config, "hooks", "path", sitePath.hooks_dir.getAbsolutePath()));

        patchsetCreatedHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "patchsetCreatedHook", "patchset-created")).getPath());
        commentAddedHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "commentAddedHook", "comment-added")).getPath());
        changeMergedHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "changeMergedHook", "change-merged")).getPath());
        changeAbandonedHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "changeAbandonedHook", "change-abandoned")).getPath());
        changeRestoredHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "changeRestoredHook", "change-restored")).getPath());
        refUpdatedHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "refUpdatedHook", "ref-updated")).getPath());
        claSignedHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "claSignedHook", "cla-signed")).getPath());
    }

    public void addChangeListener(ChangeListener listener, IdentifiedUser user) {
        listeners.put(listener, new ChangeListenerHolder(listener, user));
    }

    public void removeChangeListener(ChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Helper Method for getting values from the config.
     *
     * @param config Config file to get value from.
     * @param section Section to look in.
     * @param setting Setting to get.
     * @param fallback Fallback value.
     * @return Setting value if found, else fallback.
     */
    private String getValue(final Config config, final String section, final String setting, final String fallback) {
        final String result = config.getString(section, null, setting);
        return (result == null) ? fallback : result;
    }

    /**
     * Get the Repository for the given change, or null on error.
     *
     * @param change Change to get repo for,
     * @return Repository or null.
     */
    private Repository openRepository(final Change change) {
        return openRepository(change.getProject());
    }

    /**
     * Get the Repository for the given project name, or null on error.
     *
     * @param name Project to get repo for,
     * @return Repository or null.
     */
    private Repository openRepository(final Project.NameKey name) {
        try {
            return repoManager.openRepository(name);
        } catch (RepositoryNotFoundException err) {
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

    public void doPatchsetCreatedHook(final Change change, final PatchSet patchSet,
          final ReviewDb db) throws OrmException {
        final PatchSetCreatedEvent event = new PatchSetCreatedEvent();
        final AccountState uploader = accountCache.get(patchSet.getUploader());

        event.change = eventFactory.asChangeAttribute(change);
        event.patchSet = eventFactory.asPatchSetAttribute(patchSet);
        event.uploader = eventFactory.asAccountAttribute(uploader.getAccount());
        fireEvent(change, event, db);

        final List<String> args = new ArrayList<String>();
        addArg(args, "--change", event.change.id);
        addArg(args, "--change-url", event.change.url);
        addArg(args, "--project", event.change.project);
        addArg(args, "--branch", event.change.branch);
        addArg(args, "--uploader", getDisplayName(uploader.getAccount()));
        addArg(args, "--commit", event.patchSet.revision);
        addArg(args, "--patchset", event.patchSet.number);

        runHook(change.getProject(), patchsetCreatedHook, args);
    }

    public void doCommentAddedHook(final Change change, final Account account,
          final PatchSet patchSet, final String comment, final Map<ApprovalCategory.Id,
          ApprovalCategoryValue.Id> approvals, final ReviewDb db) throws OrmException {
        final CommentAddedEvent event = new CommentAddedEvent();

        event.change = eventFactory.asChangeAttribute(change);
        event.author =  eventFactory.asAccountAttribute(account);
        event.patchSet = eventFactory.asPatchSetAttribute(patchSet);
        event.comment = comment;

        if (approvals.size() > 0) {
            event.approvals = new ApprovalAttribute[approvals.size()];
            int i = 0;
            for (Map.Entry<ApprovalCategory.Id, ApprovalCategoryValue.Id> approval : approvals.entrySet()) {
                event.approvals[i++] = getApprovalAttribute(approval);
            }
        }

        fireEvent(change, event, db);

        final List<String> args = new ArrayList<String>();
        addArg(args, "--change", event.change.id);
        addArg(args, "--change-url", event.change.url);
        addArg(args, "--project", event.change.project);
        addArg(args, "--branch", event.change.branch);
        addArg(args, "--author", getDisplayName(account));
        addArg(args, "--commit", event.patchSet.revision);
        addArg(args, "--comment", comment == null ? "" : comment);
        for (Map.Entry<ApprovalCategory.Id, ApprovalCategoryValue.Id> approval : approvals.entrySet()) {
            addArg(args, "--" + approval.getKey().get(), Short.toString(approval.getValue().get()));
        }

        runHook(change.getProject(), commentAddedHook, args);
    }

    public void doChangeMergedHook(final Change change, final Account account,
          final PatchSet patchSet, final ReviewDb db) throws OrmException {
        final ChangeMergedEvent event = new ChangeMergedEvent();

        event.change = eventFactory.asChangeAttribute(change);
        event.submitter = eventFactory.asAccountAttribute(account);
        event.patchSet = eventFactory.asPatchSetAttribute(patchSet);
        fireEvent(change, event, db);

        final List<String> args = new ArrayList<String>();
        addArg(args, "--change", event.change.id);
        addArg(args, "--change-url", event.change.url);
        addArg(args, "--project", event.change.project);
        addArg(args, "--branch", event.change.branch);
        addArg(args, "--submitter", getDisplayName(account));
        addArg(args, "--commit", event.patchSet.revision);

        runHook(change.getProject(), changeMergedHook, args);
    }

    public void doChangeAbandonedHook(final Change change, final Account account,
          final String reason, final ReviewDb db) throws OrmException {
        final ChangeAbandonedEvent event = new ChangeAbandonedEvent();

        event.change = eventFactory.asChangeAttribute(change);
        event.abandoner = eventFactory.asAccountAttribute(account);
        event.reason = reason;
        fireEvent(change, event, db);

        final List<String> args = new ArrayList<String>();
        addArg(args, "--change", event.change.id);
        addArg(args, "--change-url", event.change.url);
        addArg(args, "--project", event.change.project);
        addArg(args, "--branch", event.change.branch);
        addArg(args, "--abandoner", getDisplayName(account));
        addArg(args, "--reason", reason == null ? "" : reason);

        runHook(change.getProject(), changeAbandonedHook, args);
    }

    public void doChangeRestoreHook(final Change change, final Account account,
          final String reason, final ReviewDb db) throws OrmException {
        final ChangeRestoreEvent event = new ChangeRestoreEvent();

        event.change = eventFactory.asChangeAttribute(change);
        event.restorer = eventFactory.asAccountAttribute(account);
        event.reason = reason;
        fireEvent(change, event, db);

        final List<String> args = new ArrayList<String>();
        addArg(args, "--change", event.change.id);
        addArg(args, "--change-url", event.change.url);
        addArg(args, "--project", event.change.project);
        addArg(args, "--branch", event.change.branch);
        addArg(args, "--restorer", getDisplayName(account));
        addArg(args, "--reason", reason == null ? "" : reason);

        runHook(change.getProject(), changeRestoredHook, args);
    }

    public void doRefUpdatedHook(final Branch.NameKey refName, final RefUpdate refUpdate, final Account account) {
      doRefUpdatedHook(refName, refUpdate.getOldObjectId(), refUpdate.getNewObjectId(), account);
    }

    public void doRefUpdatedHook(final Branch.NameKey refName, final ObjectId oldId, final ObjectId newId, final Account account) {
      final RefUpdatedEvent event = new RefUpdatedEvent();

      if (account != null) {
        event.submitter = eventFactory.asAccountAttribute(account);
      }
      event.refUpdate = eventFactory.asRefUpdateAttribute(oldId, newId, refName);
      fireEvent(refName, event);

      final List<String> args = new ArrayList<String>();
      addArg(args, "--oldrev", event.refUpdate.oldRev);
      addArg(args, "--newrev", event.refUpdate.newRev);
      addArg(args, "--refname", event.refUpdate.refName);
      addArg(args, "--project", event.refUpdate.project);
      if (account != null) {
        addArg(args, "--submitter", getDisplayName(account));
      }

      runHook(refName.getParentKey(), refUpdatedHook, args);
    }

    public void doClaSignupHook(Account account, ContributorAgreement cla) {
      if (account != null) {
        final List<String> args = new ArrayList<String>();
        addArg(args, "--submitter", getDisplayName(account));
        addArg(args, "--user-id", account.getId().toString());
        addArg(args, "--cla-name", cla.getName());

        runHook(claSignedHook, args);
      }
    }

    private void fireEvent(final Change change, final ChangeEvent event, final ReviewDb db) throws OrmException {
      for (ChangeListenerHolder holder : listeners.values()) {
          if (isVisibleTo(change, holder.user, db)) {
              holder.listener.onChangeEvent(event);
          }
      }
    }

    private void fireEvent(Branch.NameKey branchName, final ChangeEvent event) {
      for (ChangeListenerHolder holder : listeners.values()) {
          if (isVisibleTo(branchName, holder.user)) {
              holder.listener.onChangeEvent(event);
          }
      }
    }

    private boolean isVisibleTo(Change change, IdentifiedUser user, ReviewDb db) throws OrmException {
        final ProjectState pe = projectCache.get(change.getProject());
        if (pe == null) {
          return false;
        }
        final ProjectControl pc = pe.controlFor(user);
        return pc.controlFor(change).isVisible(db);
    }

    private boolean isVisibleTo(Branch.NameKey branchName, IdentifiedUser user) {
        final ProjectState pe = projectCache.get(branchName.getParentKey());
        if (pe == null) {
          return false;
        }
        final ProjectControl pc = pe.controlFor(user);
        return pc.controlForRef(branchName).isVisible();
    }

    /**
     * Create an ApprovalAttribute for the given approval suitable for serialization to JSON.
     * @param approval
     * @return object suitable for serialization to JSON
     */
    private ApprovalAttribute getApprovalAttribute(
            Entry<ApprovalCategory.Id, ApprovalCategoryValue.Id> approval) {
        ApprovalAttribute a = new ApprovalAttribute();
        a.type = approval.getKey().get();
        ApprovalType at = approvalTypes.byId(approval.getKey());
        if (at != null) {
          a.description = at.getCategory().getName();
        }
        a.value = Short.toString(approval.getValue().get());
        return a;
    }

    /**
     * Get the display name for the given account.
     *
     * @param account Account to get name for.
     * @return Name for this account.
     */
    private String getDisplayName(final Account account) {
        if (account != null) {
            String result = (account.getFullName() == null) ? anonymousCowardName : account.getFullName();
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
  private synchronized void runHook(Project.NameKey project, File hook,
      List<String> args) {
    if (project != null && hook.exists()) {
      hookQueue.execute(new HookTask(project, hook, args));
    }
  }

  private synchronized void runHook(File hook, List<String> args) {
    if (hook.exists()) {
      hookQueue.execute(new HookTask(null, hook, args));
    }
  }

  private final class HookTask implements Runnable {
    private final Project.NameKey project;
    private final File hook;
    private final List<String> args;

    private HookTask(Project.NameKey project, File hook, List<String> args) {
      this.project = project;
      this.hook = hook;
      this.args = args;
    }

    @Override
    public void run() {
      Repository repo = null;
      try {
        final List<String> argv = new ArrayList<String>(1 + args.size());
        argv.add(hook.getAbsolutePath());
        argv.addAll(args);

        final ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(true);

        if (project != null) {
          repo = openRepository(project);
        }

        if (repo != null) {
          pb.directory(repo.getDirectory());

          final Map<String, String> env = pb.environment();
          env.put("GIT_DIR", repo.getDirectory().getAbsolutePath());
        }

        Process ps = pb.start();
        ps.getOutputStream().close();

        BufferedReader br =
            new BufferedReader(new InputStreamReader(ps.getInputStream()));
        try {
          String line;
          while ((line = br.readLine()) != null) {
            log.info("hook[" + hook.getName() + "] output: " + line);
          }
        } finally {
          try {
            br.close();
          } catch (IOException closeErr) {
          }
          ps.waitFor();
        }
      } catch (Throwable err) {
        log.error("Error running hook " + hook.getAbsolutePath(), err);
      } finally {
        if (repo != null) {
          repo.close();
        }
      }
    }

    @Override
    public String toString() {
      return "hook " + hook.getName();
    }
  }
}
