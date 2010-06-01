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
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.internal.Nullable;

import org.eclipse.jgit.lib.Config;
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

/**
 * This class implements hooks for certain gerrit events.
 */
@Singleton
public class ChangeHookRunner {
    /** A logger for this class. */
    private static final Logger log = LoggerFactory.getLogger(ChangeHookRunner.class);

    public static abstract class ChangeEvent {
    }

    public static class ApprovalAttribute {
        public String type;
        public String description;
        public String value;
    }

    public static class AuthorAttribute {
        public String name;
        public String email;
    }

    public static class ChangeAttribute {
        public String project;
        public String branch;
        public String id;
        public String number;
        public String subject;
        public AuthorAttribute owner;
        public String url;
    }

    public static class PatchSetAttribute {
        public String number;
        public String revision;
        public String ref;
    }

    public static class CommentAddedEvent extends ChangeEvent {
        public final String type = "comment-added";
        public ChangeAttribute change;
        public PatchSetAttribute patchSet;
        public AuthorAttribute author;
        public ApprovalAttribute[] approvals;
        public String comment;
    }

    public static class ChangeMergedEvent extends ChangeEvent {
        public final String type = "change-merged";
        public ChangeAttribute change;
        public PatchSetAttribute patchSet;
        public AuthorAttribute submitter;
    }

    public static class ChangeAbandonedEvent extends ChangeEvent {
        public final String type = "change-abandoned";
        public ChangeAttribute change;
        public PatchSetAttribute patchSet;
        public AuthorAttribute abandoner;
        public String reason;
    }

    public static class PatchSetCreatedEvent extends ChangeEvent {
        public final String type = "patchset-created";
        public ChangeAttribute change;
        public PatchSetAttribute patchSet;
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

    /** Repository Manager. */
    private final GitRepositoryManager repoManager;

    /** Queue of hooks that need to run. */
    private final WorkQueue.Executor hookQueue;

    private final ProjectCache projectCache;

    private final AccountCache accountCache;

    private final ApprovalTypes approvalTypes;

    private final Provider<String> urlProvider;


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
      @GerritServerConfig final Config config, final SitePaths sitePath,
      final ProjectCache projectCache,
      final AccountCache accountCache,
      final ApprovalTypes approvalTypes,
      @CanonicalWebUrl @Nullable final Provider<String> cwu) {
        this.repoManager = repoManager;
        this.hookQueue = queue.createQueue(1, "hook");
        this.projectCache = projectCache;
        this.accountCache = accountCache;
        this.approvalTypes = approvalTypes;
        this.urlProvider = cwu;

        final File hooksPath = sitePath.resolve(getValue(config, "hooks", "path", sitePath.hooks_dir.getAbsolutePath()));

        patchsetCreatedHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "patchsetCreatedHook", "patchset-created")).getPath());
        commentAddedHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "commentAddedHook", "comment-added")).getPath());
        changeMergedHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "changeMergedHook", "change-merged")).getPath());
        changeAbandonedHook = sitePath.resolve(new File(hooksPath, getValue(config, "hooks", "changeAbandonedHook", "change-abandoned")).getPath());
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
    private Repository getRepo(final Change change) {
        try {
            return repoManager.openRepository(change.getProject().get());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Fire the Patchset Created Hook.
     *
     * @param change The change itself.
     * @param patchSet The Patchset that was created.
     */
    public void doPatchsetCreatedHook(final Change change, final PatchSet patchSet) {
        final PatchSetCreatedEvent event = new PatchSetCreatedEvent();

        event.change = getChangeAttribute(change);
        event.patchSet = getPatchSetAttribute(patchSet);
        fireEvent(change, event);

        final List<String> args = new ArrayList<String>();
        args.add(patchsetCreatedHook.getAbsolutePath());

        args.add("--change");
        args.add(event.change.id);
        args.add("--change-number");
        args.add(event.change.number);
        args.add("--project");
        args.add(event.change.project);
        args.add("--branch");
        args.add(event.change.branch);
        args.add("--commit");
        args.add(event.patchSet.revision);
        args.add("--patchset");
        args.add(event.patchSet.number);

        runHook(getRepo(change), args);
    }

    /**
     * Fire the Comment Added Hook.
     *
     * @param change The change itself.
     * @param patchSet The patchset this comment is related to.
     * @param account The gerrit user who commited the change.
     * @param comment The comment given.
     * @param approvals Map of Approval Categories and Scores
     */
    public void doCommentAddedHook(final Change change, final Account account, final PatchSet patchSet, final String comment, final Map<ApprovalCategory.Id, ApprovalCategoryValue.Id> approvals) {
        final CommentAddedEvent event = new CommentAddedEvent();

        event.change = getChangeAttribute(change);
        event.author =  getAccountAttribute(account);
        event.patchSet = getPatchSetAttribute(patchSet);
        event.comment = comment;

        if (approvals.size() > 0) {
            event.approvals = new ApprovalAttribute[approvals.size()];
            int i = 0;
            for (Map.Entry<ApprovalCategory.Id, ApprovalCategoryValue.Id> approval : approvals.entrySet()) {
                event.approvals[i++] = getApprovalAttribute(approval);
            }
        }

        fireEvent(change, event);

        final List<String> args = new ArrayList<String>();
        args.add(commentAddedHook.getAbsolutePath());

        args.add("--change");
        args.add(event.change.id);
        args.add("--change-number");
        args.add(event.change.number);
        args.add("--project");
        args.add(event.change.project);
        args.add("--branch");
        args.add(event.change.branch);
        args.add("--author");
        args.add(getDisplayName(account));
        args.add("--commit");
        args.add(event.patchSet.revision);
        args.add("--comment");
        args.add(comment == null ? "" : comment);
        for (Map.Entry<ApprovalCategory.Id, ApprovalCategoryValue.Id> approval : approvals.entrySet()) {
            args.add("--" + approval.getKey().get());
            args.add(Short.toString(approval.getValue().get()));
        }

        runHook(getRepo(change), args);
    }

    /**
     * Fire the Change Merged Hook.
     *
     * @param change The change itself.
     * @param account The gerrit user who commited the change.
     * @param patchSet The patchset that was merged.
     */
    public void doChangeMergedHook(final Change change, final Account account, final PatchSet patchSet) {
        final ChangeMergedEvent event = new ChangeMergedEvent();

        event.change = getChangeAttribute(change);
        event.submitter =  getAccountAttribute(account);
        event.patchSet = getPatchSetAttribute(patchSet);
        fireEvent(change, event);

        final List<String> args = new ArrayList<String>();
        args.add(changeMergedHook.getAbsolutePath());

        args.add("--change");
        args.add(event.change.id);
        args.add("--change-number");
        args.add(event.change.number);
        args.add("--project");
        args.add(event.change.project);
        args.add("--branch");
        args.add(event.change.branch);
        args.add("--submitter");
        args.add(getDisplayName(account));
        args.add("--commit");
        args.add(event.patchSet.revision);

        runHook(getRepo(change), args);
    }

    /**
     * Fire the Change Abandoned Hook.
     *
     * @param change The change itself.
     * @param account The gerrit user who abandoned the change.
     * @param reason Reason for abandoning the change.
     */
    public void doChangeAbandonedHook(final Change change, final Account account, final String reason) {
        final ChangeAbandonedEvent event = new ChangeAbandonedEvent();

        event.change = getChangeAttribute(change);
        event.abandoner = getAccountAttribute(account);
        event.reason = reason;
        fireEvent(change, event);

        final List<String> args = new ArrayList<String>();
        args.add(changeAbandonedHook.getAbsolutePath());

        args.add("--change");
        args.add(event.change.id);
        args.add("--change-number");
        args.add(event.change.number);
        args.add("--project");
        args.add(event.change.project);
        args.add("--branch");
        args.add(event.change.branch);
        args.add("--abandoner");
        args.add(getDisplayName(account));
        args.add("--reason");
        args.add(reason == null ? "" : reason);

        runHook(getRepo(change), args);
    }

    private void fireEvent(final Change change, final ChangeEvent event) {
      for (ChangeListenerHolder holder : listeners.values()) {
          if (isVisibleTo(change, holder.user)) {
              holder.listener.onChangeEvent(event);
          }
      }
    }

    private boolean isVisibleTo(Change change, IdentifiedUser user) {
        final ProjectState pe = projectCache.get(change.getProject());
        if (pe == null) {
          return false;
        }
        final ProjectControl pc = pe.controlFor(user);
        return pc.controlFor(change).isVisible();
    }

    /** Get a link to the change; null if the server doesn't know its own address. */
    private String getChangeUrl(final Change change) {
        if (change != null && getGerritUrl() != null) {
            final StringBuilder r = new StringBuilder();
            r.append(getGerritUrl());
            r.append(change.getChangeId());
            return r.toString();
        }
        return null;
    }

    private String getGerritUrl() {
        return urlProvider.get();
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
        final ApprovalType at = approvalTypes.getApprovalType(approval.getKey());
        a.description = at.getCategory().getName();
        a.value = Short.toString(approval.getValue().get());
        return a;
    }

    /**
     * Create an AuthorAttribute for the given account suitable for serialization to JSON.
     *
     * @param account
     * @return object suitable for serialization to JSON
     */
    private AuthorAttribute getAccountAttribute(final Account account) {
        AuthorAttribute author = new AuthorAttribute();
        author.name = account.getFullName();
        author.email = account.getPreferredEmail();
        return author;
    }

    /**
     * Create a ChangeAttribute for the given change suitable for serialization to JSON.
     *
     * @param change
     * @return object suitable for serialization to JSON
     */
    private ChangeAttribute getChangeAttribute(final Change change) {
        ChangeAttribute a = new ChangeAttribute();
        a.project = change.getProject().get();
        a.branch = change.getDest().getShortName();
        a.id = change.getKey().get();
        a.number = change.getId().toString();
        a.subject = change.getSubject();
        final AccountState owner = accountCache.get(change.getOwner());
        a.owner = getAccountAttribute(owner.getAccount());
        a.url = getChangeUrl(change);
        return a;
    }

    /**
     * Create an PatchSetAttribute for the given patchset suitable for serialization to JSON.
     *
     * @param patchSet
     * @return object suitable for serialization to JSON
     */
    private PatchSetAttribute getPatchSetAttribute(final PatchSet patchSet) {
        PatchSetAttribute p = new PatchSetAttribute();
        p.revision = patchSet.getRevision().get();
        p.number = Integer.toString(patchSet.getPatchSetId());
        p.ref = patchSet.getRefName();
        return p;
    }

    /**
     * Get the display name for the given account.
     *
     * @param account Account to get name for.
     * @return Name for this account.
     */
    private String getDisplayName(final Account account) {
        if (account != null) {
            String result = (account.getFullName() == null) ? "Anonymous Coward" : account.getFullName();
            if (account.getPreferredEmail() != null) {
                result += " (" + account.getPreferredEmail() + ")";
            }
            return result;
        }

        return "Anonymous Coward";
    }

    /**
     * Run a hook.
     *
     * @param repo Repo to run the hook for.
     * @param args Arguments to use to run the hook.
     */
    private synchronized void runHook(final Repository repo, final List<String> args) {
        if (repo == null) {
            log.error("No repo found for hook.");
            return;
        }

        hookQueue.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (new File(args.get(0)).exists()) {
                        final ProcessBuilder pb = new ProcessBuilder(args);
                        pb.redirectErrorStream(true);
                        pb.directory(repo.getDirectory());
                        final Map<String, String> env = pb.environment();
                        env.put("GIT_DIR", repo.getDirectory().getAbsolutePath());

                        Process ps = pb.start();
                        ps.getOutputStream().close();

                        BufferedReader br = new BufferedReader(new InputStreamReader(ps.getInputStream()));
                        try {
                            String line;
                            while ((line = br.readLine()) != null) {
                                log.info("hook output: " + line);
                            }
                        } finally {
                            try {
                                br.close();
                            } catch (IOException e2) {
                            }

                            ps.waitFor();
                        }
                    }
                } catch (Throwable e) {
                    log.error("Unexpected error during hook execution", e);
                } finally {
                    repo.close();
                }
            }
        });
    }
}
