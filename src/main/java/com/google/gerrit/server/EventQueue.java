// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.git.WorkQueue;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.Repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class EventQueue {
  private static final Logger log = LoggerFactory.getLogger(EventQueue.class);
  private static Map<String, File> hooks;

  // new-change <project name> <change id>
  private static final String ON_NEW_CHANGE_HOOK = "new-change";
  // update-change <project name> <change id> (not implemented yet)
  private static final String ON_UPDATE_CHANGE_HOOK = "update-change";

  public static void scheduleNewChangeEvent(final Project.NameKey project,
      final Repository repo, final Change change) {
    final File hook = getHooks().get(ON_NEW_CHANGE_HOOK);
    if (hook != null) {
      final List<String> args = new ArrayList<String>();
      args.add(hook.getAbsolutePath());
      args.add(project.toString());
      args.add(change.getId().toString());
      scheduleEventWithArgs(args, repo);
    }
  }

  private static synchronized void scheduleEventWithArgs(final List<String> args,
      final Repository repo) {
    WorkQueue.schedule(new Runnable() {
      public void run() {
        try {
          final ProcessBuilder pb = new ProcessBuilder(args);
          pb.redirectErrorStream(true);
          pb.directory(repo.getDirectory());
          final Map<String, String> env = pb.environment();
          env.put("GIT_DIR", repo.getDirectory().getAbsolutePath());

          Process ps = pb.start();
          ps.getOutputStream().close();

          BufferedReader br = new BufferedReader(
              new InputStreamReader(ps.getInputStream()));
          try {
            String line;
            while ((line = br.readLine()) != null) {
              log.info("hook output: " + line);
            }
          }
          finally {
            try {
              br.close();
            } catch (IOException e2) { }

            ps.waitFor();
          }
        } catch (Throwable e) {
          log.error("Unexpected error during hook execution", e);
        }
      }
    }, 0, TimeUnit.SECONDS);
  }

  private static synchronized Map<String, File> getHooks() {
    if (hooks == null) {
      hooks = new HashMap<String, File>();
      final File path;
      try {
        final GerritServer gs = GerritServer.getInstance();
        path = gs.getSitePath();
        if (path == null || gs.getRepositoryCache() == null) {
          return Collections.emptyMap();
        }
      } catch (OrmException e) {
        return Collections.emptyMap();
      } catch (XsrfException e) {
        return Collections.emptyMap();
      }

      final File hooksPath = new File(path, "hooks");
      final File[] allHooks = hooksPath.listFiles();
      if (allHooks != null) {
        for (final File f : allHooks) {
          hooks.put(f.getName(), f);
        }
      }
    }
    return hooks;
  }
}
