package com.google.gerrit.server.git;

import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Rate;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.storage.pack.PackStatistics;
import org.eclipse.jgit.transport.PostUploadHook;

@Singleton
public class UploadPackMetricsHook implements PostUploadHook {
  final Rate upload;

  @Inject
  UploadPackMetricsHook(MetricMaker metricMaker) {
    upload = metricMaker.newRate(
        "git/upload-pack",
        new Description("Total number of git-upload-pack requests")
          .setCumulative()
          .setUnit("requests"));
  }

  @Override
  public void onPostUpload(PackStatistics stats) {
    upload.increment();
  }
}
