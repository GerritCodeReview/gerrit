// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.version;

import com.google.gerrit.common.Version;
import com.google.gerrit.extensions.common.VersionInfo;
import com.google.gerrit.index.project.ProjectSchemaDefinitions;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.index.group.GroupSchemaDefinitions;
import com.google.gerrit.server.schema.NoteDbSchemaVersions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class VersionInfoModule extends AbstractModule {
  @Provides
  @Singleton
  public VersionInfo createVersionInfo() {
    VersionInfo v = new VersionInfo();
    v.gerritVersion = Version.getVersion();
    v.noteDbVersion = NoteDbSchemaVersions.LATEST;
    v.changeIndexVersion = ChangeSchemaDefinitions.INSTANCE.getLatest().getVersion();
    v.accountIndexVersion = AccountSchemaDefinitions.INSTANCE.getLatest().getVersion();
    v.projectIndexVersion = ProjectSchemaDefinitions.INSTANCE.getLatest().getVersion();
    v.groupIndexVersion = GroupSchemaDefinitions.INSTANCE.getLatest().getVersion();
    return v;
  }
}
