// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.pgm.http.jetty;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import javax.servlet.AsyncEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectQoSFilterTest {

  @Mock ProjectQoSFilter.TaskThunk taskThunk;
  @Mock AsyncEvent asyncEvent;

  @Test
  public void shouldCallTaskEndOnListenerComplete() throws IOException {
    ProjectQoSFilter.Listener listener =
        new ProjectQoSFilter.Listener(Futures.immediateFuture(true), taskThunk);

    listener.onComplete(asyncEvent);

    verify(taskThunk, times(1)).end();
  }

  @Test
  public void shouldCallTaskEndOnListenerTimeout() throws IOException {
    ProjectQoSFilter.Listener listener =
        new ProjectQoSFilter.Listener(Futures.immediateFuture(true), taskThunk);

    listener.onTimeout(asyncEvent);

    verify(taskThunk, times(1)).end();
  }

  @Test
  public void shouldCallTaskEndOnListenerError() throws IOException {
    ProjectQoSFilter.Listener listener =
        new ProjectQoSFilter.Listener(Futures.immediateFuture(true), taskThunk);

    listener.onError(asyncEvent);

    verify(taskThunk, times(1)).end();
  }
}
