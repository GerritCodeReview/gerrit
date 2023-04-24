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
import static org.mockito.Mockito.when;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.git.QueueProvider;
import com.google.inject.Provider;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.eclipse.jetty.server.Request;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectQoSFilterTest {

  @Mock AsyncEvent asyncEvent;
  @Mock AsyncContext asyncContext;

  @Mock AccountLimits.Factory limitsFactory;
  @Mock Provider<CurrentUser> userProvider;
  @Mock QueueProvider queue;
  @Mock ServletContext context;

  @Test
  @SuppressWarnings("DoNotCall")
  public void shouldCallTaskEndOnListenerCompleteFromDifferentThread() {
    ProjectQoSFilter.TaskThunk taskThunk = getTaskThunk();
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);

    Future<?> f = scheduledThreadPoolExecutor.submit(taskThunk);
    taskThunk.begin(Thread.currentThread());

    new Thread() {
      @Override
      public void run() {
        ProjectQoSFilter.Listener listener = new ProjectQoSFilter.Listener(f, taskThunk);
        try {
          listener.onComplete(asyncEvent);
        } catch (Exception e) {
        }
      }
    }.run();

    assertThat(taskThunk.isDone()).isTrue();
  }

  @Test
  @SuppressWarnings("DoNotCall")
  public void shouldCallTaskEndOnListenerTimeoutFromDifferentThread() {
    ProjectQoSFilter.TaskThunk taskThunk = getTaskThunk();
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);

    Future<?> f = scheduledThreadPoolExecutor.submit(taskThunk);
    taskThunk.begin(Thread.currentThread());

    new Thread() {
      @Override
      public void run() {
        ProjectQoSFilter.Listener listener = new ProjectQoSFilter.Listener(f, taskThunk);
        try {
          listener.onTimeout(asyncEvent);
        } catch (Exception e) {
        }
      }
    }.run();

    assertThat(taskThunk.isDone()).isTrue();
  }

  @Test
  @SuppressWarnings("DoNotCall")
  public void shouldCallTaskEndOnListenerErrorFromDifferentThread() {
    ProjectQoSFilter.TaskThunk taskThunk = getTaskThunk();
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);

    Future<?> f = scheduledThreadPoolExecutor.submit(taskThunk);
    taskThunk.begin(Thread.currentThread());

    new Thread() {
      @Override
      public void run() {
        ProjectQoSFilter.Listener listener = new ProjectQoSFilter.Listener(f, taskThunk);
        try {
          listener.onError(asyncEvent);
        } catch (Exception e) {
        }
      }
    }.run();

    assertThat(taskThunk.isDone()).isTrue();
  }

  private ProjectQoSFilter.TaskThunk getTaskThunk() {
    HttpServletRequest servletRequest = new FakeHttpServletRequest();
    Config config = new Config();
    String HTTP_MAX_WAIT = "1 minute";
    config.setString("httpd", null, "maxwait", HTTP_MAX_WAIT);

    when(userProvider.get()).thenReturn(new FakeUser("testUser"));
    when(asyncContext.getRequest()).thenReturn(servletRequest);

    ProjectQoSFilter projectQoSFilter =
        new ProjectQoSFilter(limitsFactory, userProvider, queue, context, config);
    return projectQoSFilter.new TaskThunk(asyncContext, servletRequest);
  }

  private static class FakeUser extends CurrentUser {
    private final String username;

    FakeUser(String name) {
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
    public Optional<String> getUserName() {
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
