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
import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ContributorAgreement;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
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
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.client.OrmException;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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

    private static class ChangeListenerHolder {
        final ChangeListener listener;
        final IdentifiedUser user;

        ChangeListenerHolder(ChangeListener l, IdentifiedUser u) {
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

    /** Filename of the update hook. */
    private final File refUpdateHook;

    private final String anonymousCowardName;

    /** Repository Manager. */
    private final GitRepositoryManager repoManager;

    /** Queue of hooks that need to run. */
    private final WorkQueue.Executor hookQueue;

    private final ProjectCache projectCache;

    private final AccountCache accountCache;

    private final ApprovalTypes approvalTypes;

    private final EventFactory eventFactory;

    /** Thread pool used to monitor sync hooks */
    private final ExecutorService syncHookThreadPool = Executors.newCachedThreadPool();

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
        refUpdateHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "refUpdateHook", "ref-update")).getPath());
        syncHookTimeout = config.getInt("hooks", "syncHookTimeout", 30);
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

    /**
     * Fire the update hook
     *
     */
    public HookResult doRefUpdateHook(final Project project, final String refname,
        final Account uploader, final ObjectId oldId, final ObjectId newId) {

      final List<String> args = new ArrayList<String>();
      addArg(args, "--project", project.getName());
      addArg(args, "--refname", refname);
      addArg(args, "--uploader", getDisplayName(uploader));
      addArg(args, "--oldrev", oldId.getName());
      addArg(args, "--newrev", newId.getName());

      HookResult hookResult;

      try {
        hookResult = runSyncHook(project.getNameKey(), refUpdateHook, args);
      } catch (TimeoutException e) {
        hookResult = new HookResult(-1, "Synchronous hook timed out");
      }

      return hookResult;
    }

    /**
     * Fire the Patchset Created Hook.
     *
     * @param change The change itself.
     * @param patchSet The Patchset that was created.
     * @throws OrmException
     */
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
        addArg(args, "--cla-id", cla.getId().toString());

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
      hookQueue.execute(new AsyncHookTask(project, hook, args));
    }
  }

  private synchronized void runHook(File hook, List<String> args) {
    if (hook.exists()) {
      hookQueue.execute(new AsyncHookTask(null, hook, args));
    }
  }

  private HookResult runSyncHook(Project.NameKey project,
      File hook, List<String> args) throws TimeoutException {

    if (!hook.exists()) {
      return null;
    }

    HookResult result = null;
    SyncHookTask syncHook = new SyncHookTask(project, hook, args);
    FutureTask<HookResult> task = new FutureTask<HookResult>(syncHook);

    syncHookThreadPool.execute(task);

    try {
      result = task.get(syncHookTimeout, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      task.cancel(true);
      syncHook.cancel();
      log.error("Synchronous hook timed out "  + hook.getAbsolutePath());
      result = new HookResult(syncHook.getOutput(), "Synchronous hook timed out");
    } catch (Exception e) {
      task.cancel(true);
      syncHook.cancel();
      log.error("Error running hook " + hook.getAbsolutePath(), e);
      result = new HookResult(syncHook.getOutput(), "Error running hook: " + e);
    }

    return result;
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
    private final File hook;
    private final List<String> args;
    private StringWriter output;
    private Process ps;

    protected HookTask(Project.NameKey project, File hook, List<String> args) {
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

        ps = pb.start();
        ps.getOutputStream().close();
        InputStream is = ps.getInputStream();
        String output = null;
        try {
          output = readOutput(is);
        } finally {
          try {
            is.close();
          } catch (IOException closeErr) {
          }
          ps.waitFor();
          result = new HookResult(ps.exitValue(), output);
        }
      } catch (InterruptedException iex) {
        // InterruptedExeception - timeout or cancel
      } catch (Throwable err) {
        log.error("Error running hook " + hook.getAbsolutePath(), err);
      } finally {
        if (repo != null) {
          repo.close();
        }
      }

      log.info("hook[" + getName() + "] exitValue:" + result.getExitValue());

      BufferedReader br =
          new BufferedReader(new StringReader(result.getOutput()));
      try {
        String line;
        while ((line = br.readLine()) != null) {
          log.info("hook[" + getName() + "] output: " + line);
        }
      }
      catch(IOException  iox) {
        log.error("Error writing hook output", iox);
      }

      return result;
    }

    private String readOutput(InputStream is) throws IOException {
      output = new StringWriter();
      InputStreamReader input = new InputStreamReader(is);
      char[] buffer = new char[4096];
      int n = 0;
      while ((n = input.read(buffer)) != -1) {
        output.write(buffer, 0, n);
      }

      return output.toString();
    }

    protected String getName() {
      return hook.getName();
    }

    @Override
    public String toString() {
      return "hook " + hook.getName();
    }

    public void cancel() {
      ps.destroy();
    }
  }

  /** Callable type used to run synchronous hooks */
  private final class SyncHookTask extends HookTask
      implements Callable<HookResult> {

    private SyncHookTask(Project.NameKey project, File hook, List<String> args) {
      super(project, hook, args);
    }

    @Override
    public HookResult call() throws Exception {
      return super.runHook();
    }
  }

  /** Runable type used to run async hooks */
  private final class AsyncHookTask extends HookTask implements Runnable {

    private AsyncHookTask(Project.NameKey project, File hook, List<String> args) {
      super(project, hook, args);
    }

    @Override
    public void run() {
      super.runHook();
    }
  }
}
