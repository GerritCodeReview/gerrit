package com.google.gerrit.server;

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.git.WorkQueue;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class EventQueue {
  private static final Logger log = LoggerFactory.getLogger(EventQueue.class);
  private static Map<String, File> hooks = null;

  // new-change <project name> <change id>
  private static final String ON_NEW_CHANGE_HOOK = "new-change";
  // update-change <project name> <change id> (not implemented yet)
  private static final String ON_UPDATE_CHANGE_HOOK = "update-change";

  public static void scheduleNewChangeEvent(final Project.NameKey project,
      final Change change) {
    if (getHooks().containsKey(ON_NEW_CHANGE_HOOK)) {
      final File hook = getHooks().get(ON_NEW_CHANGE_HOOK);
      final List<String> args = new ArrayList<String>();
      args.add(hook.getAbsolutePath());
      args.add(project.toString());
      args.add(change.getId().toString());
      scheduleEventWithArgs(args);
    }
  }

  private static synchronized void scheduleEventWithArgs(final List<String> args) {
    WorkQueue.schedule(new Runnable() {
      public void run() {
        try {
          Runtime rt = Runtime.getRuntime();
          Process ps = rt.exec(args.toArray(new String[args.size()]));
          ps.waitFor();
        } catch (RuntimeException e) {
          log.error("Unexpected error during hook execution", e);
        } catch (Error e) {
          log.error("Unexpected error during hook execution", e);
        } catch (Exception e) {
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
      final String[] hookFilenames = hooksPath.list();
      if (hookFilenames != null) {
        for (int i = 0; i < hookFilenames.length; i++) {
          String hookFilename = hookFilenames[i];
          if (hookFilename.equals(ON_NEW_CHANGE_HOOK)) {
            hooks.put(ON_NEW_CHANGE_HOOK, new File(hooksPath, hookFilename));
          } else if (hookFilename.equals(ON_UPDATE_CHANGE_HOOK)) {
            hooks.put(ON_UPDATE_CHANGE_HOOK, new File(hooksPath, hookFilename));
          }
        }
      }
    }
    return hooks;
  }
}
