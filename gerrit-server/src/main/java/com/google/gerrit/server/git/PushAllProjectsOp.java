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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public class PushAllProjectsOp extends DefaultQueueOp {
  public interface Factory {
    PushAllProjectsOp create(String urlMatch);
  }

  private static final Logger log =
      LoggerFactory.getLogger(PushAllProjectsOp.class);

  private final ProjectCache projectCache;
  private final ReplicationQueue replication;
  private final String urlMatch;

  @Inject
  public PushAllProjectsOp(final WorkQueue wq, final ProjectCache projectCache,
      final ReplicationQueue rq, @Assisted @Nullable final String urlMatch) {
    super(wq);
    this.projectCache = projectCache;
    this.replication = rq;
    this.urlMatch = urlMatch;
  }

  @Override
  public void start(final int delay, final TimeUnit unit) {
    if (replication.isEnabled()) {
      super.start(delay, unit);
    }
  }

  public void run() {
    try {
      for (final Project.NameKey nameKey : projectCache.all()) {
        replication.scheduleFullSync(nameKey, urlMatch);
      }
    } catch (RuntimeException e) {
      log.error("Cannot enumerate known projects", e);
    }
  }

  @Override
  public String toString() {
    String s = "Replicate All Projects";
    if (urlMatch != null) {
      s = s + " to " + urlMatch;
    }
    return s;
  }
}
