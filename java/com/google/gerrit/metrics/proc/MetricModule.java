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

package com.google.gerrit.metrics.proc;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;

/** Guice module to configure metrics on server startup. */
public abstract class MetricModule extends LifecycleModule {
  /** Configure metrics during server startup. */
  protected abstract void configure(MetricMaker metrics);

  @Override
  protected void configure() {
    listener()
        .toInstance(
            new LifecycleListener() {
              @Inject MetricMaker metrics;

              @Override
              public void start() {
                configure(metrics);
              }

              @Override
              public void stop() {}
            });
  }
}
