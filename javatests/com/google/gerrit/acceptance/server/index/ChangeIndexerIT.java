// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.index;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.inject.Inject;
import org.junit.Test;

public class ChangeIndexerIT extends AbstractDaemonTest {

  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void deleteFiresListenerWithNoProjectName() throws Exception {
    Change.Id changeId = createChange().getChange().getId();
    ChangeIndexedListener listener = mock(ChangeIndexedListener.class);

    try (Registration registration = extensionRegistry.newRegistration().add(listener)) {
      indexer.delete(changeId);
    }

    verify(listener).onChangeDeleted(changeId.get());
  }
}
