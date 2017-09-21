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

package com.google.gerrit.server.git;

import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.extensions.events.GarbageCollectorListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GcConfig;
import com.google.gerrit.server.extensions.events.AbstractNoNotifyEvent;
import com.google.inject.Inject;
import java.io.PrintWriter;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.eclipse.jgit.api.GarbageCollectCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GarbageCollection {
  private static final Logger log = LoggerFactory.getLogger(GarbageCollection.class);

  public static final String LOG_NAME = "gc_log";
  private static final Logger gcLog = LoggerFactory.getLogger(LOG_NAME);

  private final GitRepositoryManager repoManager;
  private final GarbageCollectionQueue gcQueue;
  private final GcConfig gcConfig;
  private final DynamicSet<GarbageCollectorListener> listeners;

  public interface Factory {
    GarbageCollection create();
  }

  @Inject
  GarbageCollection(
      GitRepositoryManager repoManager,
      GarbageCollectionQueue gcQueue,
      GcConfig config,
      DynamicSet<GarbageCollectorListener> listeners) {
    this.repoManager = repoManager;
    this.gcQueue = gcQueue;
    this.gcConfig = config;
    this.listeners = listeners;
  }

  public GarbageCollectionResult run(List<Project.NameKey> projectNames) {
    return run(projectNames, null);
  }

  public GarbageCollectionResult run(List<Project.NameKey> projectNames, PrintWriter writer) {
    return run(projectNames, gcConfig.isAggressive(), writer);
  }

  public GarbageCollectionResult run(
      List<Project.NameKey> projectNames, boolean aggressive, PrintWriter writer) {
    GarbageCollectionResult result = new GarbageCollectionResult();
    Set<Project.NameKey> projectsToGc = gcQueue.addAll(projectNames);
    for (Project.NameKey projectName :
        Sets.difference(Sets.newHashSet(projectNames), projectsToGc)) {
      result.addError(
          new GarbageCollectionResult.Error(
              GarbageCollectionResult.Error.Type.GC_ALREADY_SCHEDULED, projectName));
    }
    for (Project.NameKey p : projectsToGc) {
      try (Repository repo = repoManager.openRepository(p)) {
        logGcConfiguration(p, repo, aggressive);
        print(writer, "collecting garbage for \"" + p + "\":\n");
        GarbageCollectCommand gc = Git.wrap(repo).gc();
        gc.setAggressive(aggressive);
        logGcInfo(p, "before:", gc.getStatistics());
        gc.setProgressMonitor(
            writer != null ? new TextProgressMonitor(writer) : NullProgressMonitor.INSTANCE);
        Properties statistics = gc.call();
        logGcInfo(p, "after: ", statistics);
        print(writer, "done.\n\n");
        fire(p, statistics);
      } catch (RepositoryNotFoundException e) {
        logGcError(writer, p, e);
        result.addError(
            new GarbageCollectionResult.Error(
                GarbageCollectionResult.Error.Type.REPOSITORY_NOT_FOUND, p));
      } catch (Exception e) {
        logGcError(writer, p, e);
        result.addError(
            new GarbageCollectionResult.Error(GarbageCollectionResult.Error.Type.GC_FAILED, p));
      } finally {
        gcQueue.gcFinished(p);
      }
    }
    return result;
  }

  private void fire(Project.NameKey p, Properties statistics) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    Event event = new Event(p, statistics);
    for (GarbageCollectorListener l : listeners) {
      try {
        l.onGarbageCollected(event);
      } catch (RuntimeException e) {
        log.warn("Failure in GarbageCollectorListener", e);
      }
    }
  }

  private static void logGcInfo(Project.NameKey projectName, String msg) {
    logGcInfo(projectName, msg, null);
  }

  private static void logGcInfo(Project.NameKey projectName, String msg, Properties statistics) {
    StringBuilder b = new StringBuilder();
    b.append("[").append(projectName.get()).append("] ");
    b.append(msg);
    if (statistics != null) {
      b.append(" ");
      String s = statistics.toString();
      if (s.startsWith("{") && s.endsWith("}")) {
        s = s.substring(1, s.length() - 1);
      }
      b.append(s);
    }
    gcLog.info(b.toString());
  }

  private static void logGcConfiguration(
      Project.NameKey projectName, Repository repo, boolean aggressive) {
    StringBuilder b = new StringBuilder();
    Config cfg = repo.getConfig();
    b.append("gc.aggressive=").append(aggressive).append("; ");
    b.append(formatConfigValues(cfg, ConfigConstants.CONFIG_GC_SECTION, null));
    for (String subsection : cfg.getSubsections(ConfigConstants.CONFIG_GC_SECTION)) {
      b.append(formatConfigValues(cfg, ConfigConstants.CONFIG_GC_SECTION, subsection));
    }
    if (b.length() == 0) {
      b.append("no set");
    }

    logGcInfo(projectName, "gc config: " + b.toString());
    logGcInfo(projectName, "pack config: " + (new PackConfig(repo)).toString());
  }

  private static String formatConfigValues(Config config, String section, String subsection) {
    StringBuilder b = new StringBuilder();
    Set<String> names = config.getNames(section, subsection);
    for (String name : names) {
      String value = config.getString(section, subsection, name);
      b.append(section);
      if (subsection != null) {
        b.append(".").append(subsection);
      }
      b.append(".");
      b.append(name).append("=").append(value);
      b.append("; ");
    }
    return b.toString();
  }

  private static void logGcError(PrintWriter writer, Project.NameKey projectName, Exception e) {
    print(writer, "failed.\n\n");
    StringBuilder b = new StringBuilder();
    b.append("[").append(projectName.get()).append("]");
    gcLog.error(b.toString(), e);
    log.error(b.toString(), e);
  }

  private static void print(PrintWriter writer, String message) {
    if (writer != null) {
      writer.print(message);
    }
  }

  private static class Event extends AbstractNoNotifyEvent
      implements GarbageCollectorListener.Event {
    private final Project.NameKey p;
    private final Properties statistics;

    Event(Project.NameKey p, Properties statistics) {
      this.p = p;
      this.statistics = statistics;
    }

    @Override
    public String getProjectName() {
      return p.get();
    }

    @Override
    public Properties getStatistics() {
      return statistics;
    }
  }
}
