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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Histogram1;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.server.logging.Metadata;
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

  private final Counter1<Operation> requestCount;
  private final Timer1<Operation> counting;
  private final Histogram1<Operation> bitmapIndexMissesCount;
  private final Counter1<Operation> noBitmapIndex;
  private final Timer1<Operation> compressing;
  private final Timer1<Operation> negotiating;
  private final Timer1<Operation> searchingForReuse;
  private final Timer1<Operation> searchingForSizes;
  private final Timer1<Operation> writing;
  private final Histogram1<Operation> packBytes;

  @Inject
  UploadPackMetricsHook(MetricMaker metricMaker) {
    Field<Operation> operationField =
        Field.ofEnum(Operation.class, "operation", Metadata.Builder::gitOperation)
            .description("The name of the operation (CLONE, FETCH).")
            .build();
    requestCount =
        metricMaker.newCounter(
            "git/upload-pack/request_count",
            new Description("Total number of git-upload-pack requests")
                .setRate()
                .setUnit("requests"),
            operationField);

    counting =
        metricMaker.newTimer(
            "git/upload-pack/phase_counting",
            new Description("Time spent in the 'Counting...' phase")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            operationField);

    bitmapIndexMissesCount =
        metricMaker.newHistogram(
            "git/upload-pack/bitmap_index_misses_count",
            new Description("Number of bitmap index misses per request")
                .setCumulative()
                .setUnit("misses"),
            operationField);

    noBitmapIndex =
        metricMaker.newCounter(
            "git/upload-pack/no_bitmap_index",
            new Description("Total number of requests executed without a bitmap index")
                .setRate()
                .setUnit("requests"),
            operationField);

    compressing =
        metricMaker.newTimer(
            "git/upload-pack/phase_compressing",
            new Description("Time spent in the 'Compressing...' phase")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            operationField);

    negotiating =
        metricMaker.newTimer(
            "git/upload-pack/phase_negotiating",
            new Description("Time spent in the negotiation phase")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            operationField);

    searchingForReuse =
        metricMaker.newTimer(
            "git/upload-pack/phase_searching_for_reuse",
            new Description(
                    "Time spent in the 'Finding sources...' while searching for reuse phase")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            operationField);

    searchingForSizes =
        metricMaker.newTimer(
            "git/upload-pack/phase_searching_for_sizes",
            new Description(
                    "Time spent in the 'Finding sources...' while searching for sizes phase")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            operationField);

    writing =
        metricMaker.newTimer(
            "git/upload-pack/phase_writing",
            new Description("Time spent transferring bytes to client")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            operationField);

    packBytes =
        metricMaker.newHistogram(
            "git/upload-pack/pack_bytes",
            new Description("Distribution of sizes of packs sent to clients")
                .setCumulative()
                .setUnit(Units.BYTES),
            operationField);
  }

  @Override
  public void onPostUpload(PackStatistics stats) {
    Operation op = Operation.FETCH;
    if (stats.getUninterestingObjects() == null || stats.getUninterestingObjects().isEmpty()) {
      op = Operation.CLONE;
    }

    requestCount.increment(op);
    counting.record(op, stats.getTimeCounting(), MILLISECONDS);
    long bitmapIndexMisses = stats.getBitmapIndexMisses();
    if (bitmapIndexMisses < 0) {
      noBitmapIndex.increment(op);
    } else {
      bitmapIndexMissesCount.record(op, bitmapIndexMisses);
    }
    compressing.record(op, stats.getTimeCompressing(), MILLISECONDS);
    negotiating.record(op, stats.getTimeNegotiating(), MILLISECONDS);
    searchingForReuse.record(op, stats.getTimeSearchingForReuse(), MILLISECONDS);
    searchingForSizes.record(op, stats.getTimeSearchingForSizes(), MILLISECONDS);
    writing.record(op, stats.getTimeWriting(), MILLISECONDS);
    packBytes.record(op, stats.getTotalBytes());
  }
}
