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

import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.GC;
import org.eclipse.jgit.storage.file.GC.RepoStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.List;
import java.util.Set;

public class GarbageCollection {
  private static final Logger log = LoggerFactory
      .getLogger(GarbageCollection.class);

  public static final String LOG_NAME = "gc_log";
  private static final Logger gcLog = LoggerFactory.getLogger(LOG_NAME);


  private final GitRepositoryManager repoManager;
  private final GarbageCollectionQueue gcQueue;

  public interface Factory {
    GarbageCollection create();
  }

  @Inject
  GarbageCollection(GitRepositoryManager repoManager, GarbageCollectionQueue gcQueue) {
    this.repoManager = repoManager;
    this.gcQueue = gcQueue;
  }

  public GarbageCollectionResult run(List<Project.NameKey> projectNames,
      PrintWriter writer) {
    GarbageCollectionResult result = new GarbageCollectionResult();
    List<Project.NameKey> projectsToGc = gcQueue.addAll(projectNames);
    for (Project.NameKey projectName : projectNames) {
      if (!projectsToGc.contains(projectName)) {
        result.addError(new GarbageCollectionResult.Error(
            GarbageCollectionResult.Error.Type.GC_ALREADY_SCHEDULED, projectName));
      }
    }
    for (Project.NameKey p : projectsToGc) {
      Repository repo = null;
      try {
        repo = repoManager.openRepository(p);
        if (!(repo instanceof FileRepository)) {
          logGcError(p,
              "garbage collection not supported for repository type: "
              + repo.getClass().getName());
          result.addError(new GarbageCollectionResult.Error(
              GarbageCollectionResult.Error.Type.GC_NOT_SUPPORTED, p));
          continue;
        }

        writer.print("collecting garbage for \"" + p + "\":\n");
        GC gc = new GC((FileRepository) repo);
        logGcOptions(p, repo.getConfig());
        logGcInfo(p, "before", gc.getStatistics());
        gc.setProgressMonitor(new TextProgressMonitor(writer));
        gc.gc();
        logGcInfo(p, "after ", gc.getStatistics());
        writer.print("done.\n\n");
      } catch (RepositoryNotFoundException e) {
        logGcError(p, e);
        result.addError(new GarbageCollectionResult.Error(
            GarbageCollectionResult.Error.Type.REPOSITORY_NOT_FOUND,
            p));
      } catch (IOException e) {
        logGcError(p, e);
        result.addError(new GarbageCollectionResult.Error(
            GarbageCollectionResult.Error.Type.GC_FAILED, p));
      } catch (ParseException e) {
        logGcError(p, e);
        result.addError(new GarbageCollectionResult.Error(
            GarbageCollectionResult.Error.Type.GC_FAILED, p));
      } finally {
        if (repo != null) {
          repo.close();
        }
        gcQueue.gcFinished(p);
      }
    }
    return result;
  }

  private static void logGcInfo(final Project.NameKey projectName, String msg) {
    logGcInfo(projectName, msg, null);
  }

  private static void logGcInfo(final Project.NameKey projectName, String msg,
      final RepoStatistics s) {
    final StringBuilder b = new StringBuilder();
    b.append("[").append(projectName.get()).append("] ");
    b.append(msg);
    if (s != null) {
      b.append(" ");
      b.append("packed objects = ").append(s.numberOfPackedObjects);
      b.append("; pack files = ").append(s.numberOfPackFiles);
      b.append("; loose objects = ").append(s.numberOfLooseObjects);
      b.append("; loose refs = ").append(s.numberOfLooseRefs);
      b.append("; packed refs = ").append(s.numberOfPackedRefs);
      b.append("; size of loose objects = ").append(s.sizeOfLooseObjects);
      b.append("; size of packed objects = ").append(s.sizeOfPackedObjects);
    }
    gcLog.info(b.toString());
  }

  private static void logGcOptions(final Project.NameKey projectName,
      final Config config) {
    final StringBuilder b = new StringBuilder();
    final String[] sections = {ConfigConstants.CONFIG_GC_SECTION};
    for (final String section : sections) {
      b.append(formatGcOptions(projectName, config, section, null));
      final Set<String> subsections = config.getSubsections(section);
      for (final String subsection : subsections) {
        b.append(formatGcOptions(projectName, config, section, subsection));
      }
    }
    if (b.length() == 0) {
      b.append("no options set for: ");
      for (int i = 0; i < sections.length; i++) {
        b.append(sections[i]);
        if (i < sections.length - 1) {
          b.append(", ");
        }
      }
    }
    logGcInfo(projectName, b.toString());
  }

  private static String formatGcOptions(final Project.NameKey projectName,
      final Config config, final String section, final String subsection) {
    final StringBuilder b = new StringBuilder();
    final Set<String> names = config.getNames(section, subsection);
    for (final String name : names) {
      final String value = config.getString(section, subsection, name);
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

  private static void logGcError(final Project.NameKey projectName, String msg) {
    final StringBuilder b = new StringBuilder();
    b.append("[").append(projectName.get()).append("] ");
    b.append(msg);
    gcLog.error(b.toString());
    log.error(b.toString());
  }

  private static void logGcError(final Project.NameKey projectName,
      final Exception e) {
    final StringBuilder b = new StringBuilder();
    b.append("[").append(projectName.get()).append("]");
    gcLog.error(b.toString(), e);
    log.error(b.toString(), e);
  }
}
