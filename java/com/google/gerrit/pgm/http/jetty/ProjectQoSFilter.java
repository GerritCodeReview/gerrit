// Copyright (C) 2010 The Android Open Source Project
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

import static com.google.gerrit.server.config.ConfigUtil.getTimeUnit;
import static java.util.concurrent.TimeUnit.MINUTES;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gerrit.server.git.WorkQueue.CancelableRunnable;
import com.google.gerrit.sshd.CommandExecutorQueueProvider;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;

/**
 * Use AsyncContexts to defer execution until threads are available.
 *
 * <p>We actually schedule a task into the same execution queue as the SSH daemon uses for command
 * execution, and then park the web request in an AsyncContext until an execution thread is
 * available. This ensures that the overall JVM process doesn't exceed the configured limit on
 * concurrent Git requests.
 *
 * <p>During Git request execution however we have to use the Jetty service thread, not the thread
 * from the SSH execution queue. Trying to complete the request on the SSH execution queue caused
 * Jetty's HTTP parser to crash, so we instead block the SSH execution queue thread and ask Jetty to
 * resume processing on the web service thread.
 */
@Singleton
public class ProjectQoSFilter implements Filter {
  private static final String ATT_SPACE = ProjectQoSFilter.class.getName();
  private static final String TASK = ATT_SPACE + "/TASK";
  private static final String STATE = ATT_SPACE + "/STATE";

  private static final String FILTER_RE = "^/(.*)/(git-upload-pack|git-receive-pack)$";
  private static final Pattern URI_PATTERN = Pattern.compile(FILTER_RE);

  public static class Module extends ServletModule {
    @Override
    protected void configureServlets() {
      bind(QueueProvider.class).to(CommandExecutorQueueProvider.class);
      filterRegex(FILTER_RE).through(ProjectQoSFilter.class);
    }
  }

  public enum RequestState {
    INITIAL,
    SUSPENDED,
    RESUMED,
    EXPIRED,
    CANCELED
  }

  private final AccountLimits.Factory limitsFactory;
  private final Provider<CurrentUser> user;
  private final QueueProvider queue;
  private final ServletContext context;
  private final long maxWait;

  @Inject
  ProjectQoSFilter(
      AccountLimits.Factory limitsFactory,
      Provider<CurrentUser> user,
      QueueProvider queue,
      ServletContext context,
      @GerritServerConfig Config cfg) {
    this.limitsFactory = limitsFactory;
    this.user = user;
    this.queue = queue;
    this.context = context;
    this.maxWait = MINUTES.toMillis(getTimeUnit(cfg, "httpd", null, "maxwait", 5, MINUTES));
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest req = (HttpServletRequest) request;
    final HttpServletResponse rsp = (HttpServletResponse) response;

    switch (getState(request)) {
      case INITIAL:
        AsyncContext asyncContext = request.startAsync();
        request.setAttribute(STATE, RequestState.SUSPENDED);
        TaskThunk initial = new TaskThunk(asyncContext, req);
        if (maxWait > 0) {
          asyncContext.setTimeout(maxWait);
        }

        request.setAttribute(TASK, initial);

        Future<?> f = getExecutor().submit(initial);
        asyncContext.addListener(new Listener(f));
        break;
      case EXPIRED:
        rsp.sendError(SC_SERVICE_UNAVAILABLE);
        break;
      case CANCELED:
        rsp.sendError(SC_SERVICE_UNAVAILABLE);
        break;
      case RESUMED:
        TaskThunk resumed = (TaskThunk) request.getAttribute(TASK);
        try {
          resumed.begin(Thread.currentThread());
          chain.doFilter(req, rsp);
        } finally {
          resumed.end();
          Thread.interrupted();
        }
        break;
      case SUSPENDED:
      default:
        context.log("Unexpected QoS state, aborting request");
        rsp.sendError(SC_SERVICE_UNAVAILABLE);
        break;
    }
  }

  private RequestState getState(ServletRequest req) {
    if (req.getDispatcherType() != DispatcherType.ASYNC) {
      return RequestState.INITIAL;
    }
    return ((RequestState) req.getAttribute(STATE));
  }

  private ScheduledThreadPoolExecutor getExecutor() {
    QueueProvider.QueueType qt = limitsFactory.create(user.get()).getQueueType();
    return queue.getQueue(qt);
  }

  @Override
  public void init(FilterConfig config) {}

  @Override
  public void destroy() {}

  private static final class Listener implements AsyncListener {
    final Future<?> future;

    Listener(Future<?> future) {
      this.future = future;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {}

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
      future.cancel(true);
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {}

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {}
  }

  private final class TaskThunk implements CancelableRunnable {
    private final AsyncContext asyncContext;
    private final String name;
    private final Object lock = new Object();
    private boolean done;
    private Thread worker;

    TaskThunk(AsyncContext asyncContext, HttpServletRequest req) {
      this.asyncContext = asyncContext;
      this.name = generateName(req);
    }

    @Override
    public void run() {
      resume();

      synchronized (lock) {
        while (!done) {
          try {
            lock.wait();
          } catch (InterruptedException e) {
            if (worker != null) {
              worker.interrupt();
            } else {
              break;
            }
          }
        }
      }
    }

    void begin(Thread thread) {
      synchronized (lock) {
        worker = thread;
      }
    }

    void end() {
      synchronized (lock) {
        worker = null;
        done = true;
        lock.notifyAll();
      }
    }

    @Override
    public void cancel() {
      asyncContext.getRequest().setAttribute(STATE, RequestState.CANCELED);
      resume();
    }

    private void resume() {
      ServletRequest candidate = asyncContext.getRequest();
      if (getState(candidate).equals(RequestState.SUSPENDED)) {
        candidate.setAttribute(STATE, RequestState.RESUMED);
        asyncContext.dispatch();
      }
    }

    @Override
    public String toString() {
      return name;
    }

    private String generateName(HttpServletRequest req) {
      String userName = "";

      CurrentUser who = user.get();
      if (who.isIdentifiedUser()) {
        Optional<String> name = who.asIdentifiedUser().getUserName();
        if (name.isPresent()) {
          userName = " (" + name.get() + ")";
        }
      }

      String uri = req.getServletPath();
      Matcher m = URI_PATTERN.matcher(uri);
      if (m.matches()) {
        String path = m.group(1);
        String cmd = m.group(2);
        return cmd + " " + path + userName;
      }

      return req.getMethod() + " " + uri + userName;
    }
  }
}
