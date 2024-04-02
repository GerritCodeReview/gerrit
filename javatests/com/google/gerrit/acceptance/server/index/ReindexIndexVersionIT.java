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

package com.google.gerrit.acceptance.server.index;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.config.IndexVersionResource;
import com.google.gerrit.server.restapi.config.ReindexIndexVersion;
import com.google.inject.Inject;
import java.util.Collection;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;

public class ReindexIndexVersionIT extends AbstractDaemonTest {

  @Inject private ReindexIndexVersion reindexIndexVersion;
  @Inject private Collection<IndexDefinition<?, ?, ?>> indexDefs;
  @Inject private ExtensionRegistry extensionRegistry;

  private IndexDefinition<?, ?, ?> def;
  private Index<?, ?> changeIndex;
  private Change.Id C1;
  private Change.Id C2;

  private ChangeIndexedListener changeIndexedListener;
  private ReindexIndexVersion.Input input = new ReindexIndexVersion.Input();

  @Before
  public void setUp() throws Exception {
    def = indexDefs.stream().filter(i -> i.getName().equals("changes")).findFirst().get();
    changeIndex = def.getIndexCollection().getSearchIndex();
    C1 = createChange().getChange().getId();
    C2 = createChange().getChange().getId();
    changeIndexedListener = mock(ChangeIndexedListener.class);
    input = new ReindexIndexVersion.Input();
  }

  @Test
  public void reindexWithListenerNotification() throws Exception {
    input.notifyListeners = true;
    reindex();
    verify(changeIndexedListener, times(1)).onChangeIndexed(project.get(), C1.get());
    verify(changeIndexedListener, times(1)).onChangeIndexed(project.get(), C2.get());
  }

  @Test
  public void reindexWithoutListenerNotification() throws Exception {
    input.notifyListeners = false;
    reindex();
    verifyNoInteractions(changeIndexedListener);
  }

  private void reindex() throws ResourceNotFoundException {
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedListener)) {
      Response<?> rsp =
          reindexIndexVersion.apply(new IndexVersionResource(def, changeIndex), input);
      assertThat(rsp.statusCode()).isEqualTo(HttpServletResponse.SC_ACCEPTED);
    }
  }
}
