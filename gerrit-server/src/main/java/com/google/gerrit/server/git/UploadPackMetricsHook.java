// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.storage.pack.PackStatistics;
import org.eclipse.jgit.transport.PostUploadHook;

@Singleton
public class UploadPackMetricsHook implements PostUploadHook {
  enum Operation {
    CLONE,
    FETCH;
  }

  private final Counter1<Operation> upload;

  @Inject
  UploadPackMetricsHook(MetricMaker metricMaker) {
    upload = metricMaker.newCounter(
        "git/upload-pack/request_count",
        new Description("Total number of git-upload-pack requests")
          .setRate()
          .setUnit("requests"),
        Field.ofEnum(Operation.class, "operation"));
  }

  @Override
  public void onPostUpload(PackStatistics stats) {
    Operation op = Operation.FETCH;
    if (stats.getUninterestingObjects() == null
        || stats.getUninterestingObjects().isEmpty()) {
      op = Operation.CLONE;
    }
    upload.increment(op);
  }
}
