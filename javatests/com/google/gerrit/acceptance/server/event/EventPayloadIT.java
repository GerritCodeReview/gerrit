// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.event;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class EventPayloadIT extends AbstractDaemonTest {
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void defaultOptions() throws Exception {
    RevisionCreatedListener listener =
        event -> {
          assertThat(event.getChange().submittable).isNotNull();
          assertThat(event.getRevision().files).isNotEmpty();
        };
    try (Registration ignored = extensionRegistry.newRegistration().add(listener)) {
      createChange();
    }
  }

  @Test
  @GerritConfig(name = "event.payload.listChangeOptions", value = "SKIP_DIFFSTAT")
  public void configuredOptions() throws Exception {
    RevisionCreatedListener listener =
        event -> {
          assertThat(event.getChange().submittable).isNull();
          assertThat(event.getChange().insertions).isNull();
          assertThat(event.getRevision().files).isNull();
          assertThat(event.getChange().subject).isNotEmpty();
        };
    try (Registration ignored = extensionRegistry.newRegistration().add(listener)) {
      createChange();
    }
  }
}
