// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.git.ChangeReportFormatter;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import org.eclipse.jgit.transport.PostUploadHook;

/** Configures the Git support. */
public class GitModule extends FactoryModule {
  @Override
  protected void configure() {
    factory(MetaDataUpdate.InternalFactory.class);
    bind(MetaDataUpdate.Server.class);
    DynamicSet.bind(binder(), PostUploadHook.class).to(UploadPackMetricsHook.class);
    DynamicItem.itemOf(binder(), ChangeReportFormatter.class);
    DynamicItem.bind(binder(), ChangeReportFormatter.class).to(DefaultChangeReportFormatter.class);
  }
}
