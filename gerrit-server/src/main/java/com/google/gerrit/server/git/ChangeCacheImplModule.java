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

import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;

public class ChangeCacheImplModule extends AbstractModule {
  private final boolean slave;

  public ChangeCacheImplModule(boolean slave) {
    this.slave = slave;
  }

  @Override
  protected void configure() {
    if (slave) {
      install(ScanningChangeCacheImpl.module());
    } else {
      install(SearchingChangeCacheImpl.module());
      DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
          .to(SearchingChangeCacheImpl.class);
    }
  }
}
