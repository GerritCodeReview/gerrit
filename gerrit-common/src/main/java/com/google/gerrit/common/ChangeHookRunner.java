// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.GitRepositoryManager;
import java.io.BufferedReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements hooks for certain gerrit events.
 *
 * @author Shane Mc Cormack.
 */
public class ChangeHookRunner {

    /** A logger for this class. */
    private static final Logger log = LoggerFactory.getLogger(ChangeHookRunner.class);

    /**
     * Filename of the new patchset hook.
     * Called with arguments: <change id> <repo name> <branch> <sha1> <patchset id>
     * If the patchset is is 1 then this is a new change.
     */
    private String patchsetCreatedHook = "patchset-created";

    /**
     * Filename of the new comments hook.
     * Called with arguments: <change id> <repo name> <branch> <comment author> <verified> <approved> <comment>
     */
    private String commentAddedHook = "comment-added";

    /**
     * Filename of the change merged hook.
     * Called with arguments: <change id> <repo name> <branch> <submiter> <sha1>
     */
    private String changeMergedHook = "change-merged";

    /**
     * Filename of the change abandoned hook.
     * Called with arguments: <change id> <repo name> <branch> <abandoner> <reason>
     */
    private String changeAbandonedHook = "change-abandoned";

    /** The location where the hooks are. */
    private String hooksPath = "./hooks/";

    /** Repository Manager. */
    private GitRepositoryManager repoManager = null;

    /** Instance of ChangeHookRunner. */
    private static ChangeHookRunner me;

    /** Queue of hooks that need to run. */
    private final BlockingQueue<Runnable> hookQueue = new LinkedBlockingQueue<Runnable>();

    /** Thread for hook queue. */
    private Thread hookQueueThread;

    /** Create a new ChangeHookRunner. */
    public ChangeHookRunner() {
        // Create a simple thread that takes elements from the hookQueue and
        // runs them.
        hookQueueThread = new Thread() {

            @Override
            public void run() {
                while (hookQueueThread == Thread.currentThread()) {
                    final Runnable event;
                    try {
                        event = hookQueue.take();
                        event.run();
                    } catch (InterruptedException ex) { /* Do Nothing */ }
                }
            }

        };
        hookQueueThread.start();
    }

    /**
     * Get a singleton instance of ChangeHookRunner.
     * 
     * @return The singleton instance of Changehook Runner.
     */
    public static ChangeHookRunner get() {
        if (me == null) {
            me = new ChangeHookRunner();
        }
        return me;
    }

    /**
     * Configure this instance of ChangeHookRunner.
     * 
     * @param repoManager The repository manager.
     * @param config Config file to use.
     * @param sitePath The sitepath of this gerrit install.
     */
    public void configure(final GitRepositoryManager repoManager, final Config config, final File sitePath) {
        this.repoManager = repoManager;

        patchsetCreatedHook = getValue(config, "hooks", "patchsetCreatedHook", patchsetCreatedHook);
        commentAddedHook = getValue(config, "hooks", "commentAddedHook", commentAddedHook);
        changeMergedHook = getValue(config, "hooks", "changeMergedHook", changeMergedHook);
        changeAbandonedHook = getValue(config, "hooks", "changeAbandonedHook", changeAbandonedHook);
        hooksPath = getValue(config, "hooks", "path", (sitePath != null && sitePath.exists()) ? sitePath.getAbsolutePath()+"/hooks" : hooksPath);
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
    @SuppressWarnings("deprecation")
    public void doPatchsetCreatedHook(final Change change, final PatchSet patchSet) {
        final File hook = new File(hooksPath + "/" + patchsetCreatedHook);
        if (hook != null && hook.exists()) {
            final List<String> args = new ArrayList<String>();
            args.add(hook.getAbsolutePath());

            args.add(change.getId().toString()); // Change Id
            args.add(change.getProject().get()); // Repo Name
            args.add(change.getDest().getShortName()); // Branch
            args.add(patchSet.getRevision().get()); // SHA1
            args.add(Integer.toString(patchSet.getPatchSetId())); // Patchset Id

            runHook(getRepo(change), args);
        }
    }

    /**
     * Fire the Comment Added Hook.
     *
     * @param change The change itself.
     * @param account The gerrit user who commited the change.
     * @param verified The score given for verified.
     * @param approved The score given for approved.
     * @param comment The comment given.
     */
    @SuppressWarnings("deprecation")
    public void doCommentAddedHook(final Change change, final Account account, final String verified, final String approved, final String comment) {
        final File hook = new File(hooksPath + "/" + commentAddedHook);
        if (hook != null && hook.exists()) {
            final List<String> args = new ArrayList<String>();
            args.add(hook.getAbsolutePath());

            args.add(change.getId().toString()); // Change Id
            args.add(change.getProject().get()); // Repo Name
            args.add(change.getDest().getShortName()); // Branch
            args.add(account.getFullName()); // Author
            args.add(verified); // Verified
            args.add(approved); // Approved
            args.add(comment); // Comment

            runHook(getRepo(change), args);
        }
    }

    /**
     * Fire the Change Merged Hook.
     *
     * @param change The change itself.
     * @param account The gerrit user who commited the change.
     * @param patchSet The patchset that was merged.
     */
    @SuppressWarnings("deprecation")
    public void doChangeMergedHook(final Change change, final Account account, final PatchSet patchSet) {
        final File hook = new File(hooksPath + "/" + changeMergedHook);
        if (hook != null && hook.exists()) {
            final List<String> args = new ArrayList<String>();
            args.add(hook.getAbsolutePath());

            args.add(change.getId().toString()); // Change Id
            args.add(change.getProject().get()); // Repo Name
            args.add(change.getDest().getShortName()); // Branch
            args.add(account.getFullName()); // Submitter
            args.add(patchSet.getRevision().get()); // SHA1

            runHook(getRepo(change), args);
        }
    }

    /**
     * Fire the Change Merged Hook.
     *
     * @param change The change itself.
     * @param account The gerrit user who commited the change.
     * @param reason Reason for abandoning the change.
     */
    @SuppressWarnings("deprecation")
    public void doChangeAbandonedHook(final Change change, final Account account, final String reason) {
        final File hook = new File(hooksPath + "/" + changeAbandonedHook);
        if (hook != null && hook.exists()) {
            final List<String> args = new ArrayList<String>();
            args.add(hook.getAbsolutePath());

            args.add(change.getId().toString()); // Change Id
            args.add(change.getProject().get()); // Repo Name
            args.add(change.getDest().getShortName()); // Branch
            args.add(account.getFullName()); // Submitter
            args.add(reason); // Reason

            runHook(getRepo(change), args);
        }
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

        hookQueue.add(new Runnable() {

            @Override
            public void run() {
                try {
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
                } catch (Throwable e) {
                    log.error("Unexpected error during hook execution", e);
                } finally {
                    repo.close();
                }
            }

        });
    }

}
