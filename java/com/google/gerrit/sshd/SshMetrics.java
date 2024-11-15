// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.RequestCounter;
import com.google.gerrit.server.RequestInfo;
import com.google.gerrit.server.logging.Metadata;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class SshMetrics implements RequestCounter {
  private final Counter1<String> count;
  private final Counter2<String, String> errorCount;

  @Inject
  SshMetrics(MetricMaker metrics) {
    this.count =
        metrics.newCounter(
            "ssh/success_count",
            new Description("Count of successful ssh requests").setRate(),
            Field.ofString("command_name", Metadata.Builder::commandName)
                .description("The command name of the request.")
                .build());

    this.errorCount =
        metrics.newCounter(
            "ssh/error_count",
            new Description("Number of failed requests").setRate(),
            Field.ofString("command_name", Metadata.Builder::commandName)
                .description("The command name of the request.")
                .build(),
            Field.ofString("exception", Metadata.Builder::exception)
                .description("Exception that failed the request.")
                .build());
  }

  @Override
  public void countRequest(RequestInfo requestInfo, @Nullable Throwable error) {
    if (error == null) {
      count.increment(requestInfo.commandName().get());
    } else {
      errorCount.increment(requestInfo.commandName().get(), error.getClass().getSimpleName());
    }
  }
}
