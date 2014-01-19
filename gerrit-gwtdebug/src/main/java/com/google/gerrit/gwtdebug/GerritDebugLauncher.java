/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gerrit.gwtdebug;

import com.google.gwt.core.ext.ServletContainer;
import com.google.gwt.core.ext.ServletContainerLauncher;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;

import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
//import com.google.gwt.dev.shell.jetty.JettyNullLogger;

@SuppressWarnings("deprecation")
public class GerritDebugLauncher extends ServletContainerLauncher {
  /**
   * Log jetty requests/responses to TreeLogger.
   */
  public static class JettyRequestLogger extends AbstractLifeCycle implements
      RequestLog {

    private final TreeLogger logger;

    public JettyRequestLogger(TreeLogger logger) {
      this.logger = logger;
    }

    /**
     * Log an HTTP request/response to TreeLogger.
     */
    public void log(Request request, Response response) {
      int status = response.getStatus();
      if (status < 0) {
        // Copied from NCSARequestLog
        status = 404;
      }
      TreeLogger.Type logStatus, logHeaders;
      if (status >= 500) {
        logStatus = TreeLogger.ERROR;
        logHeaders = TreeLogger.INFO;
      } else if (status >= 400) {
        logStatus = TreeLogger.WARN;
        logHeaders = TreeLogger.INFO;
      } else {
        logStatus = TreeLogger.INFO;
        logHeaders = TreeLogger.DEBUG;
      }
      String userString = request.getRemoteUser();
      if (userString == null) {
        userString = "";
      } else {
        userString += "@";
      }
      String bytesString = "";
      if (response.getContentCount() > 0) {
        bytesString = " " + response.getContentCount() + " bytes";
      }
      if (logger.isLoggable(logStatus)) {
        TreeLogger branch =
            logger.branch(logStatus, String.valueOf(status) + " - "
                + request.getMethod() + ' ' + request.getUri() + " ("
                + userString + request.getRemoteHost() + ')' + bytesString);
        if (branch.isLoggable(logHeaders)) {
          // Request headers
          TreeLogger headers = branch.branch(logHeaders, "Request headers");
          @SuppressWarnings("unchecked")
          Enumeration<String> headerNames = request.getHeaderNames();
          while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            @SuppressWarnings("unchecked")
            List<String> values = Collections.list(request.getHeaders(name));
            headers.log(logHeaders, name + ": "
                + values.get(0));
          }

          // Response headers
          headers = branch.branch(logHeaders, "Response headers");
          Collection<String> names = response.getHeaderNames();
          for (String name : names) {
            headers.log(logHeaders, name + ": "
                + response.getHeader(name));
          }
        }
      }
    }
  }

  /**
   * An adapter for the Jetty logging system to GWT's TreeLogger. This
   * implementation class is only public to allow {@link Log} to instantiate it.
   *
   * The weird static data / default construction setup is a game we play with
   * {@link Log}'s static initializer to prevent the initial log message from
   * going to stderr.
   */
  public static class JettyTreeLogger implements Logger {
    private final TreeLogger logger;

    public JettyTreeLogger(TreeLogger logger) {
      if (logger == null) {
        throw new NullPointerException();
      }
      this.logger = logger;
    }

    public void debug(String msg, Object arg0, Object arg1) {
      logger.log(TreeLogger.SPAM, format(msg, arg0, arg1));
    }

    public void debug(String msg, Throwable th) {
      logger.log(TreeLogger.SPAM, msg, th);
    }

    public Logger getLogger(String name) {
      return this;
    }

    public void info(String msg, Object arg0, Object arg1) {
      logger.log(TreeLogger.INFO, format(msg, arg0, arg1));
    }

    public boolean isDebugEnabled() {
      return logger.isLoggable(TreeLogger.SPAM);
    }

    public void setDebugEnabled(boolean enabled) {
      // ignored
    }

    public void warn(String msg, Object arg0, Object arg1) {
      logger.log(TreeLogger.WARN, format(msg, arg0, arg1));
    }

    public void warn(String msg, Throwable th) {
      logger.log(TreeLogger.WARN, msg, th);
    }

    public void debug(String msg, long value) {
      // ignored
    }
    @Override
    public void debug(String msg, Object... args) {
      // ignored

    }
    @Override
    public void debug(Throwable thrown) {
      // ignored
    }

    @Override
    public void warn(String msg, Object... args) {
      logger.log(TreeLogger.WARN, format(msg, args));
    }

    @Override
    public void warn(Throwable thrown) {
      logger.log(TreeLogger.WARN, thrown.getMessage(), thrown);
    }

    @Override
    public void info(String msg, Object... args) {
      logger.log(TreeLogger.INFO, format(msg, args));
    }

    @Override
    public void info(Throwable thrown) {
      logger.log(TreeLogger.INFO, thrown.getMessage(), thrown);
    }

    @Override
    public void info(String msg, Throwable thrown) {
      logger.log(TreeLogger.INFO, msg, thrown);
    }

    @Override
    public void ignore(Throwable ignored) {
    }

    @Override
    public String getName() {
      return this.getName();
    }

    /**
     * Copied from org.mortbay.log.StdErrLog.
     */
    private String format(String msg, Object arg0, Object arg1) {
      int i0 = msg.indexOf("{}");
      int i1 = i0 < 0 ? -1 : msg.indexOf("{}", i0 + 2);

      if (arg1 != null && i1 >= 0) {
        msg = msg.substring(0, i1) + arg1 + msg.substring(i1 + 2);
      }
      if (arg0 != null && i0 >= 0) {
        msg = msg.substring(0, i0) + arg0 + msg.substring(i0 + 2);
      }
      return msg;
    }

    private String format(String msg, Object... args) {
      StringBuilder builder = new StringBuilder();
      if (msg == null) {
          msg = "";
          for (int i = 0; i < args.length; i++) {
              msg += "{} ";
          }
      }
      String braces = "{}";
      int start = 0;
      for (Object arg : args) {
          int bracesIndex = msg.indexOf(braces,start);
          if (bracesIndex < 0) {
              escape(builder, msg.substring(start));
              builder.append(" ");
              builder.append(arg);
              start = msg.length();
          } else {
              escape(builder, msg.substring(start, bracesIndex));
              builder.append(String.valueOf(arg));
              start = bracesIndex + braces.length();
          }
      }
      escape(builder,msg.substring(start));
      return builder.toString();
    }

    private final static boolean __escape = true;

    private void escape(StringBuilder builder, String string) {
      if (__escape) {
        for (int i = 0; i < string.length(); ++i) {
          char c = string.charAt(i);
          if (Character.isISOControl(c)) {
            if (c == '\n') {
              builder.append('|');
            } else if (c == '\r') {
              builder.append('<');
            } else {
              builder.append('?');
            }
          } else {
            builder.append(c);
          }
        }
      } else {
        builder.append(string);
      }
    }
  }

  /**
   * The resulting {@link ServletContainer} this is launched.
   */
  protected static class JettyServletContainer extends ServletContainer {
    private final int actualPort;
    private final File appRootDir;
    private final TreeLogger logger;
    private final Server server;
    private final WebAppContext wac;

    public JettyServletContainer(TreeLogger logger, Server server,
        WebAppContext wac, int actualPort, File appRootDir) {
      this.logger = logger;
      this.server = server;
      this.wac = wac;
      this.actualPort = actualPort;
      this.appRootDir = appRootDir;
    }

    @Override
    public int getPort() {
      return actualPort;
    }

    @Override
    public void refresh() throws UnableToCompleteException {
      String msg =
          "Reloading web app to reflect changes in "
              + appRootDir.getAbsolutePath();
      TreeLogger branch = logger.branch(TreeLogger.INFO, msg);
      // Temporarily log Jetty on the branch.
      Log.setLog(new JettyTreeLogger(branch));
      try {
        wac.stop();
        wac.start();
        branch.log(TreeLogger.INFO, "Reload completed successfully");
      } catch (Exception e) {
        branch.log(TreeLogger.ERROR, "Unable to restart embedded Jetty server",
            e);
        throw new UnableToCompleteException();
      } finally {
        // Reset the top-level logger.
        Log.setLog(new JettyTreeLogger(logger));
      }
    }

    @Override
    public void stop() throws UnableToCompleteException {
      TreeLogger branch =
          logger.branch(TreeLogger.INFO, "Stopping Jetty server");
      // Temporarily log Jetty on the branch.
      Log.setLog(new JettyTreeLogger(branch));
      try {
        server.stop();
        server.setStopAtShutdown(false);
        branch.log(TreeLogger.INFO, "Stopped successfully");
      } catch (Exception e) {
        branch.log(TreeLogger.ERROR, "Unable to stop embedded Jetty server", e);
        throw new UnableToCompleteException();
      } finally {
        // Reset the top-level logger.
        Log.setLog(new JettyTreeLogger(logger));
      }
    }
  }

  /**
   * A {@link WebAppContext} tailored to GWT hosted mode. Features hot-reload
   * with a new {@link WebAppClassLoader} to pick up disk changes. The default
   * Jetty {@code WebAppContext} will create new instances of servlets, but it
   * will not create a brand new {@link ClassLoader}. By creating a new {@code
   * ClassLoader} each time, we re-read updated classes from disk.
   *
   * Also provides special class filtering to isolate the web app from the GWT
   * hosting environment.
   */
  protected final class MyWebAppContext extends WebAppContext {
    /**
     * Parent ClassLoader for the Jetty web app, which can only load JVM
     * classes. We would just use {@code null} for the parent ClassLoader
     * except this makes Jetty unhappy.
     */
    private final ClassLoader bootStrapOnlyClassLoader =
        new ClassLoader(null) {};

    private final ClassLoader systemClassLoader =
        Thread.currentThread().getContextClassLoader();

    private MyWebAppContext(String webApp, String contextPath) {
      super(webApp, contextPath);

      // Prevent file locking on Windows; pick up file changes.
      getInitParams().put(
          "org.mortbay.jetty.servlet.Default.useFileMappedBuffer", "false");

      // Since the parent class loader is bootstrap-only, prefer it first.
      setParentLoaderPriority(true);
    }

    @Override
    protected void doStart() throws Exception {
      setClassLoader(new MyLoader(this));
      super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
      super.doStop();
      setClassLoader(null);
    }

    private class MyLoader extends WebAppClassLoader {
      MyWebAppContext ctx;
      MyLoader(MyWebAppContext ctx) throws IOException {
        super(bootStrapOnlyClassLoader, MyWebAppContext.this);
        this.ctx = ctx;
        final URLClassLoader scl = (URLClassLoader) systemClassLoader;
        final URL[] urls = scl.getURLs();
        for (URL u : urls) {
          if ("file".equals(u.getProtocol())) {
            addClassPath(u.getPath());
          }
        }
      }

      @Override
      protected Class<?> findClass(String name) throws ClassNotFoundException {
        // For system path, always prefer the outside world.
        if (ctx.isSystemClass(name.replace('/', '.'))) {
          try {
            return systemClassLoader.loadClass(name);
          } catch (ClassNotFoundException e) {
          }
        }
        return super.findClass(name);
      }
    }
  }

  static {
    Log.getLog();

    /*
     * Make JDT the default Ant compiler so that JSP compilation just works
     * out-of-the-box. If we don't set this, it's very, very difficult to make
     * JSP compilation work.
     */
    String antJavaC =
        System.getProperty("build.compiler",
            "org.eclipse.jdt.core.JDTCompilerAdapter");
    System.setProperty("build.compiler", antJavaC);

    System.setProperty("Gerrit.GwtDevMode", "" + true);
  }

  private String bindAddress = null;

  @Override
  public void setBindAddress(String bindAddress) {
    this.bindAddress = bindAddress;
  }

  @Override
  public ServletContainer start(TreeLogger logger, int port, File warDir)
      throws Exception {
    TreeLogger branch =
        logger.branch(TreeLogger.INFO, "Starting Jetty on port " + port, null);
    checkStartParams(branch, port, warDir);

    // Setup our branch logger during startup.
    Log.setLog(new JettyTreeLogger(branch));

    // Turn off XML validation.
    System.setProperty("org.mortbay.xml.XmlParser.Validating", "false");

    AbstractConnector connector = getConnector();
    if (bindAddress != null) {
      connector.setHost(bindAddress);
    }
    connector.setPort(port);

    // Don't share ports with an existing process.
    connector.setReuseAddress(false);

    // Linux keeps the port blocked after shutdown if we don't disable this.
    connector.setSoLingerTime(0);

    Server server = new Server();
    server.addConnector(connector);

    File top;
    String root = System.getProperty("gerrit.source_root");
    if (root != null) {
      top = new File(root);
    } else {
      // Under Maven warDir is "$top/gerrit-gwtui/target/gwt-hosted-mode"
      top = warDir.getParentFile().getParentFile().getParentFile();
    }

    File app = new File(top, "gerrit-war/src/main/webapp");
    File webxml = new File(app, "WEB-INF/web.xml");

    // Jetty won't start unless this directory exists.
    if (!warDir.exists() && !warDir.mkdirs())
      logger.branch(TreeLogger.ERROR, "Cannot create "+warDir, null);

    // Create a new web app in the war directory.
    //
    WebAppContext wac =
        new MyWebAppContext(warDir.getAbsolutePath(), "/");
    wac.setDescriptor(webxml.getAbsolutePath());

    RequestLogHandler logHandler = new RequestLogHandler();
    logHandler.setRequestLog(new JettyRequestLogger(logger));
    logHandler.setHandler(wac);
    server.setHandler(logHandler);
    server.start();
    server.setStopAtShutdown(true);

    // Now that we're started, log to the top level logger.
    Log.setLog(new JettyTreeLogger(logger));

    return new JettyServletContainer(logger, server, wac,
        connector.getLocalPort(),
        warDir);
  }

  protected AbstractConnector getConnector() {
    return new SelectChannelConnector();
  }

  private void checkStartParams(TreeLogger logger, int port, File appRootDir) {
    if (logger == null) {
      throw new NullPointerException("logger cannot be null");
    }

    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException(
          "port must be either 0 (for auto) or less than 65536");
    }

    if (appRootDir == null) {
      throw new NullPointerException("app root direcotry cannot be null");
    }
  }
}
