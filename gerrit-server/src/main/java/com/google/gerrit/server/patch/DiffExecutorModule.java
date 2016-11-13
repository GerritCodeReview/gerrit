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

package com.google.gerrit.server.patch;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Module providing the {@link DiffExecutor}. */
public class DiffExecutorModule extends AbstractModule {

  @Override
  protected void configure() {}

  @Provides
  @Singleton
  @DiffExecutor
  public ExecutorService createDiffExecutor() {
    return Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("Diff-%d").setDaemon(true).build());
  }
}
