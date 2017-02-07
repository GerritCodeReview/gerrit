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

package com.google.gerrit.server.events;

import com.google.gerrit.common.EventListener;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class EventsMetrics implements EventListener {
  private final Counter1<String> events;

  @Inject
  public EventsMetrics(MetricMaker metricMaker) {
    events =
        metricMaker.newCounter(
            "events",
            new Description("Triggered events").setRate().setUnit("triggered events"),
            Field.ofString("type"));
  }

  @Override
  public void onEvent(com.google.gerrit.server.events.Event event) {
    events.increment(event.getType());
  }
}
