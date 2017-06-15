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
import static com.google.inject.Scopes.SINGLETON;
import static java.util.concurrent.TimeUnit.MINUTES;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gerrit.server.git.WorkQueue.CancelableRunnable;
import com.google.gerrit.sshd.CommandExecutorQueueProvider;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationListener;
import org.eclipse.jetty.continuation.ContinuationSupport;
import org.eclipse.jgit.lib.Config;

/**
 * Use Jetty continuations to defer execution until threads are available.
 *
 * <p>We actually schedule a task into the same execution queue as the SSH daemon uses for command
 * execution, and then park the web request in a continuation until an execution thread is
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
  private static final String CANCEL = ATT_SPACE + "/CANCEL";

  private static final String FILTER_RE = "^/(.*)/(git-upload-pack|git-receive-pack)$";
  private static final Pattern URI_PATTERN = Pattern.compile(FILTER_RE);

  public static class Module extends ServletModule {

    @Override
    protected void configureServlets() {
      bind(QueueProvider.class).to(CommandExecutorQueueProvider.class).in(SINGLETON);
      filterRegex(FILTER_RE).through(ProjectQoSFilter.class);
    }
  }

  private final Provider<CurrentUser> user;
  private final QueueProvider queue;

  private final ServletContext context;
  private final long maxWait;

  @Inject
  ProjectQoSFilter(
      Provider<CurrentUser> user,
      QueueProvider queue,
      ServletContext context,
      @GerritServerConfig Config cfg) {
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
    final Continuation cont = ContinuationSupport.getContinuation(req);

    ScheduledThreadPoolExecutor executor = getExecutor();

    if (cont.isInitial()) {
      TaskThunk task = new TaskThunk(executor, cont, req);
      if (maxWait > 0) {
        cont.setTimeout(maxWait);
      }
      cont.suspend(rsp);
      cont.addContinuationListener(task);
      cont.setAttribute(TASK, task);
      executor.submit(task);

    } else if (cont.isExpired()) {
      rsp.sendError(SC_SERVICE_UNAVAILABLE);

    } else if (cont.isResumed() && cont.getAttribute(CANCEL) == Boolean.TRUE) {
      rsp.sendError(SC_SERVICE_UNAVAILABLE);

    } else if (cont.isResumed()) {
      TaskThunk task = (TaskThunk) cont.getAttribute(TASK);
      try {
        task.begin(Thread.currentThread());
        chain.doFilter(req, rsp);
      } finally {
        task.end();
        Thread.interrupted();
      }

    } else {
      context.log("Unexpected QoS continuation state, aborting request");
      rsp.sendError(SC_SERVICE_UNAVAILABLE);
    }
  }

  private ScheduledThreadPoolExecutor getExecutor() {
    return queue.getQueue(user.get().getCapabilities().getQueueType());
  }

  @Override
  public void init(FilterConfig config) {}

  @Override
  public void destroy() {}

  private final class TaskThunk implements CancelableRunnable, ContinuationListener {

    private final ScheduledThreadPoolExecutor executor;
    private final Continuation cont;
    private final String name;
    private final Object lock = new Object();
    private boolean done;
    private Thread worker;

    TaskThunk(ScheduledThreadPoolExecutor executor, Continuation cont, HttpServletRequest req) {
      this.executor = executor;
      this.cont = cont;
      this.name = generateName(req);
    }

    @Override
    public void run() {
      cont.resume();

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
      cont.setAttribute(CANCEL, Boolean.TRUE);
      cont.resume();
    }

    @Override
    public void onComplete(Continuation self) {}

    @Override
    public void onTimeout(Continuation self) {
      executor.remove(this);
    }

    @Override
    public String toString() {
      return name;
    }

    private String generateName(HttpServletRequest req) {
      String userName = "";

      CurrentUser who = user.get();
      if (who.isIdentifiedUser()) {
        String name = who.asIdentifiedUser().getUserName();
        if (name != null && !name.isEmpty()) {
          userName = " (" + name + ")";
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
