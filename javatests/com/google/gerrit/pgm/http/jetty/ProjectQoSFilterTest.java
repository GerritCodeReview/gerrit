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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.git.QueueProvider;
import com.google.inject.Provider;
import org.eclipse.jetty.server.Request;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectQoSFilterTest {

  @Mock ProjectQoSFilter.TaskThunk taskThunk;
  @Mock AsyncEvent asyncEvent;
  @Mock AsyncContext asyncContext;


  @Mock AccountLimits.Factory limitsFactory;
  @Mock Provider <CurrentUser> userProvider;
  @Mock QueueProvider queue;
  @Mock ServletContext context;

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

  @Test
  public void shouldCallTaskEndOnListenerCompleteFromDifferentThread() throws IOException {
    HttpServletRequest servletRequest = new FakeHttpServletRequest();
    Config config = new Config();
    config.setInt( "httpd", null, "maxwait", 1);

    when(userProvider.get()).thenReturn(new MockUser("testUser"));

    ProjectQoSFilter projectQoSFilter = new ProjectQoSFilter(limitsFactory, userProvider, queue, context, config);
    ProjectQoSFilter.TaskThunk currentTaskThunk = projectQoSFilter.new TaskThunk(asyncContext, servletRequest);
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);

    when(asyncContext.getRequest()).thenReturn(servletRequest);

    Future f = scheduledThreadPoolExecutor.submit(currentTaskThunk);
    currentTaskThunk.begin(Thread.currentThread());
    ProjectQoSFilter.Listener listener =
        new ProjectQoSFilter.Listener(f, currentTaskThunk);

    listener.onComplete(asyncEvent);

    assertThat(currentTaskThunk.isDone()).isTrue();
  }

  @Test
  public void shouldCallTaskEndOnListenerTimeoutFromDifferentThread() throws IOException {
    HttpServletRequest servletRequest = new FakeHttpServletRequest();
    Config config = new Config();
    config.setInt( "httpd", null, "maxwait", 1);

    when(userProvider.get()).thenReturn(new MockUser("testUser"));

    ProjectQoSFilter projectQoSFilter = new ProjectQoSFilter(limitsFactory, userProvider, queue, context, config);
    ProjectQoSFilter.TaskThunk currentTaskThunk = projectQoSFilter.new TaskThunk(asyncContext, servletRequest);
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);

    when(asyncContext.getRequest()).thenReturn(servletRequest);

    Future f = scheduledThreadPoolExecutor.submit(currentTaskThunk);
    currentTaskThunk.begin(Thread.currentThread());
    ProjectQoSFilter.Listener listener =
        new ProjectQoSFilter.Listener(f, currentTaskThunk);

    listener.onTimeout(asyncEvent);

    assertThat(currentTaskThunk.isDone()).isTrue();
  }

  @Test
  public void shouldCallTaskEndOnListenerErrorFromDifferentThread() throws IOException {
    HttpServletRequest servletRequest = new FakeHttpServletRequest();
    Config config = new Config();
    config.setInt( "httpd", null, "maxwait", 1);

    when(userProvider.get()).thenReturn(new MockUser("testUser"));

    ProjectQoSFilter projectQoSFilter = new ProjectQoSFilter(limitsFactory, userProvider, queue, context, config);
    ProjectQoSFilter.TaskThunk currentTaskThunk = projectQoSFilter.new TaskThunk(asyncContext, servletRequest);
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);

    when(asyncContext.getRequest()).thenReturn(servletRequest);

    Future f = scheduledThreadPoolExecutor.submit(currentTaskThunk);
    currentTaskThunk.begin(Thread.currentThread());
    ProjectQoSFilter.Listener listener =
        new ProjectQoSFilter.Listener(f, currentTaskThunk);

    listener.onError(asyncEvent);

    assertThat(currentTaskThunk.isDone()).isTrue();
  }

  private static class MockUser extends CurrentUser {
    private final String username;

    MockUser(String name) {
      username = name;
    }

    @Override
    public GroupMembership getEffectiveGroups() {
      return null;
    }

    @Override
    public Object getCacheKey() {
      return new Object();
    }

    @Override
    public Optional <String> getUserName() {
      return Optional.ofNullable(username);
    }
  }

  private static final class FakeHttpServletRequest extends HttpServletRequestWrapper {

    FakeHttpServletRequest() {
      super(new Request(null, null));
    }

    @Override
    public String getRemoteHost() {
      return "1.2.3.4";
    }

    @Override
    public String getRemoteUser() {
      return "bob";
    }

    @Override
    public String getServletPath() {
      return "http://testulr/a/plugins_replication/info/refs?service=git-upload-pack";
    }
  }
}
