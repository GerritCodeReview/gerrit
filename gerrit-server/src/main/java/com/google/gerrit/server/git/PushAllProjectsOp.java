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
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.config.WildProject;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
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

  private final SchemaFactory<ReviewDb> schema;
  private final ReplicationQueue replication;
  private final Project wildProject;
  private final String urlMatch;

  @Inject
  public PushAllProjectsOp(final WorkQueue wq,
      final SchemaFactory<ReviewDb> sf, final ReplicationQueue rq,
      @WildProject final Project wp,
      @Assisted @Nullable final String urlMatch) {
    super(wq);
    this.schema = sf;
    this.replication = rq;
    this.wildProject = wp;
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
      final ReviewDb db = schema.open();
      try {
        for (final Project project : db.projects().all()) {
          if (!project.getNameKey().equals(wildProject.getNameKey())) {
            replication.scheduleFullSync(project.getNameKey(), urlMatch);
          }
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
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
