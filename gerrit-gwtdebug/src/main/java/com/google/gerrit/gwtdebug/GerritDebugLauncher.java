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
import com.google.gwt.dev.shell.jetty.JettyNullLogger;

import org.mortbay.component.AbstractLifeCycle;
import org.mortbay.jetty.AbstractConnector;
import org.mortbay.jetty.HttpFields.Field;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.RequestLog;
import org.mortbay.jetty.Response;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.RequestLogHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.webapp.WebAppClassLoader;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;

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
    @SuppressWarnings("unchecked")
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
          Iterator<Field> headerFields =
              request.getConnection().getRequestFields().getFields();
          while (headerFields.hasNext()) {
            Field headerField = headerFields.next();
            headers.log(logHeaders, headerField.getName() + ": "
                + headerField.getValue());
          }
          // Response headers
          headers = branch.branch(logHeaders, "Response headers");
          headerFields = response.getHttpFields().getFields();
          while (headerFields.hasNext()) {
            Field headerField = headerFields.next();
            headers.log(logHeaders, headerField.getName() + ": "
                + headerField.getValue());
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
     * classes. We would just use <code>null</code> for the parent ClassLoader
     * except this makes Jetty unhappy.
     */
    private final ClassLoader bootStrapOnlyClassLoader =
        new ClassLoader(null) {};

    private final ClassLoader systemClassLoader =
        Thread.currentThread().getContextClassLoader();

    @SuppressWarnings("unchecked")
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
      setClassLoader(new MyLoader());
      super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
      super.doStop();
      setClassLoader(null);
    }

    private class MyLoader extends WebAppClassLoader {
      MyLoader() throws IOException {
        super(bootStrapOnlyClassLoader, MyWebAppContext.this);

        final URLClassLoader scl = (URLClassLoader) systemClassLoader;
        final URL[] urls = scl.getURLs();
        for (URL u : urls) {
          if ("file".equals(u.getProtocol())) {
            addClassPath(u.getPath());
          }
        }
      }

      @Override
      public boolean isSystemPath(String name) {
        name = name.replace('/', '.');
        return super.isSystemPath(name) //
            || name.startsWith("org.bouncycastle.");
      }

      @Override
      protected Class<?> findClass(String name) throws ClassNotFoundException {
        // For system path, always prefer the outside world.
        if (isSystemPath(name)) {
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
    // Suppress spammy Jetty log initialization.
    System
        .setProperty("org.mortbay.log.class", JettyNullLogger.class.getName());
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
      connector.setHost(bindAddress.toString());
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

    return new JettyServletContainer(logger, server, wac, connector
        .getLocalPort(), warDir);
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
