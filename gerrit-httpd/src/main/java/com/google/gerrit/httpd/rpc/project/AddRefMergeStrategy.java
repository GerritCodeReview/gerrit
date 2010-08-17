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
// limitations under the License

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefMergeStrategy;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;

import javax.annotation.Nullable;

/** RPC service implementation to add a new ref merge strategy. */
class AddRefMergeStrategy extends Handler<ProjectDetail> {
  interface Factory {
    AddRefMergeStrategy create(@Assisted Project.NameKey projectName,
        @Nullable @Assisted("refPattern") String refPattern,
        @Assisted("submitType") RefMergeStrategy.SubmitType submitType);
  }

  private final ProjectDetailFactory.Factory projectDetailFactory;
  private final ProjectCache projectCache;
  private final ReviewDb db;

  private final Project.NameKey projectName;
  private final String refPattern;
  private final RefMergeStrategy.SubmitType submitType;

  @Inject
  AddRefMergeStrategy(final ProjectDetailFactory.Factory projectDetailFactory,
      final ProjectCache projectCache,
      final ReviewDb db,

      @Assisted final Project.NameKey projectName,
      @Nullable @Assisted("refPattern") final String refPattern,
      @Assisted("submitType") final RefMergeStrategy.SubmitType submitType) {
    this.projectDetailFactory = projectDetailFactory;
    this.projectCache = projectCache;
    this.db = db;

    this.projectName = projectName;
    this.refPattern = refPattern;
    this.submitType = submitType;
  }

  @Override
  public ProjectDetail call() throws NoSuchProjectException, OrmException, InvalidNameException {
    String refPattern = this.refPattern.trim();

    if (!refPattern.isEmpty()) {
      refPattern = RefControl.validateParseRefPattern(refPattern);

      RefMergeStrategy.Key key = new RefMergeStrategy.Key(projectName, new RefMergeStrategy.RefPattern(refPattern));

      RefMergeStrategy rms = db.refMergeStrategies().get(key);
      if (rms == null) {
        rms = new RefMergeStrategy(key);
        rms.setSubmitType(submitType);
        db.refMergeStrategies().insert(Collections.singleton(rms));
      } else {
        rms.setSubmitType(submitType);
        db.refMergeStrategies().update(Collections.singleton(rms));
      }

      projectCache.evictAll();
    }

    return projectDetailFactory.create(projectName).call();
  }
}
