// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.testing;

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
import static org.apache.logging.log4j.LogManager.getLogger;

=======
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
import java.io.Serializable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
=======
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')

public class TestLoggingActivator {
  private static final String PATTERN_LAYOUT = "[%d] [%t] %-5p %c %x: %m%n";

  private static final ImmutableMap<String, Level> LOG_LEVELS =
      ImmutableMap.<String, Level>builder()
          .put("com.google.gerrit", getGerritLogLevel())

          // Silence non-critical messages from MINA SSHD.
          .put("org.apache.mina", Level.WARNING)
          .put("org.apache.sshd.client", Level.WARNING)
          .put("org.apache.sshd.common", Level.WARNING)
          .put("org.apache.sshd.server", Level.WARNING)
          .put("org.apache.sshd.common.keyprovider.FileKeyPairProvider", Level.INFO)
          .put("com.google.gerrit.sshd.GerritServerSession", Level.WARNING)

          // Silence non-critical messages from mime-util.
          .put("eu.medsea.mimeutil", Level.WARNING)

          // Silence non-critical messages from openid4java.
          .put("org.apache.xml", Level.WARNING)
          .put("org.openid4java", Level.WARNING)
          .put("org.openid4java.consumer.ConsumerManager", Level.SEVERE)
          .put("org.openid4java.discovery.Discovery", Level.SEVERE)
          .put("org.openid4java.server.RealmVerifier", Level.SEVERE)
          .put("org.openid4java.message.AuthSuccess", Level.SEVERE)

          // Silence non-critical messages from apache.http.
          .put("org.apache.http", Level.WARNING)

          // Silence non-critical messages from Jetty.
          .put("org.eclipse.jetty", Level.WARNING)

          // Silence non-critical messages from JGit.
          .put("org.eclipse.jgit.transport.PacketLineIn", Level.WARNING)
          .put("org.eclipse.jgit.transport.PacketLineOut", Level.WARNING)
          .put("org.eclipse.jgit.internal.transport.sshd", Level.WARNING)
          .put("org.eclipse.jgit.util.FileUtils", Level.WARNING)
          .put("org.eclipse.jgit.internal.storage.file.FileSnapshot", Level.WARNING)
          .put("org.eclipse.jgit.util.FS", Level.WARNING)
          .put("org.eclipse.jgit.util.SystemReader", Level.WARNING)
          .build();

  private static Level getGerritLogLevel() {
    String value = Strings.nullToEmpty(System.getenv("GERRIT_LOG_LEVEL"));
    if (value.isEmpty()) {
      value = Strings.nullToEmpty(System.getProperty("gerrit.logLevel"));
    }

    try {
      return Level.parse(value);
    } catch (IllegalArgumentException e) {
      // for backwards compatibility handle log4j log levels
      if (value.equalsIgnoreCase("FATAL") || value.equalsIgnoreCase("ERROR")) {
        return Level.SEVERE;
      }
      if (value.equalsIgnoreCase("WARN")) {
        return Level.WARNING;
      }
      if (value.equalsIgnoreCase("DEBUG")) {
        return Level.FINE;
      }
      if (value.equalsIgnoreCase("TRACE")) {
        return Level.FINEST;
      }

      return Level.INFO;
    }
  }

  public static void configureLogging() {
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    context.reconfigure();
=======
    LogManager.getLogManager().reset();
    FloggerInitializer.initBackend();
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
    Layout<? extends Serializable> layout =
        PatternLayout.newBuilder().withPattern(PATTERN_LAYOUT).build();
    final ConsoleAppender dst =
        ConsoleAppender.newBuilder()
            .withLayout(layout)
            .withName("Console")
            .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
            .setFilter(ThresholdFilter.createFilter(Level.DEBUG, null, null))
            .build();
    dst.start();
=======
    ConsoleHandler dst = new ConsoleHandler();
    dst.setLevel(Level.FINEST);
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration config = ctx.getConfiguration();
=======
    Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).addHandler(dst);
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
    LoggerConfig root = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
    for (Appender appender : root.getAppenders().values()) {
      root.removeAppender(appender.toString());
    }

    root.addAppender(dst, null, null);

    LOG_LEVELS.entrySet().stream().forEach(e -> getLoggerKey(e.getKey()).setLevel(e.getValue()));

    ctx.updateLoggers();
=======
    LOG_LEVELS.entrySet().stream()
        .forEach(
            e -> {
              Logger logger = Logger.getLogger(e.getKey());
              logger.setLevel(e.getValue());
              logger.addHandler(dst);
            });
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
  }

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
  private static Logger getLoggerKey(String key) {
    return (Logger) getLogger(key);
  }

=======
  private TestLoggingActivator() {}
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
}
