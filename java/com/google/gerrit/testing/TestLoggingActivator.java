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

import static org.apache.logging.log4j.LogManager.getLogger;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
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

public class TestLoggingActivator {
  private static final String PATTERN_LAYOUT = "[%d] [%t] %-5p %c %x: %m%n";

  private static final ImmutableMap<String, Level> LOG_LEVELS =
      ImmutableMap.<String, Level>builder()
          .put("com.google.gerrit", getGerritLogLevel())

          // Silence non-critical messages from MINA SSHD.
          .put("org.apache.mina", Level.WARN)
          .put("org.apache.sshd.common", Level.WARN)
          .put("org.apache.sshd.server", Level.WARN)
          .put("org.apache.sshd.common.keyprovider.FileKeyPairProvider", Level.INFO)
          .put("com.google.gerrit.sshd.GerritServerSession", Level.WARN)

          // Silence non-critical messages from mime-util.
          .put("eu.medsea.mimeutil", Level.WARN)

          // Silence non-critical messages from openid4java.
          .put("org.apache.xml", Level.WARN)
          .put("org.openid4java", Level.WARN)
          .put("org.openid4java.consumer.ConsumerManager", Level.FATAL)
          .put("org.openid4java.discovery.Discovery", Level.ERROR)
          .put("org.openid4java.server.RealmVerifier", Level.ERROR)
          .put("org.openid4java.message.AuthSuccess", Level.ERROR)

          // Silence non-critical messages from c3p0 (if used).
          .put("com.mchange.v2.c3p0", Level.WARN)
          .put("com.mchange.v2.resourcepool", Level.WARN)
          .put("com.mchange.v2.sql", Level.WARN)

          // Silence non-critical messages from apache.http.
          .put("org.apache.http", Level.WARN)

          // Silence non-critical messages from Jetty.
          .put("org.eclipse.jetty", Level.WARN)

          // Silence non-critical messages from JGit.
          .put("org.eclipse.jgit.transport.PacketLineIn", Level.WARN)
          .put("org.eclipse.jgit.transport.PacketLineOut", Level.WARN)
          .put("org.eclipse.jgit.internal.storage.file.FileSnapshot", Level.WARN)
          .put("org.eclipse.jgit.util.FS", Level.WARN)
          .put("org.eclipse.jgit.util.SystemReader", Level.WARN)

          // Silence non-critical messages from Elasticsearch.
          .put("org.elasticsearch", Level.WARN)

          // Silence non-critical messages from Docker for Elasticsearch query tests.
          .put("org.testcontainers", Level.WARN)
          .put("com.github.dockerjava.core", Level.WARN)
          .build();

  private static Level getGerritLogLevel() {
    String value = Strings.nullToEmpty(System.getenv("GERRIT_LOG_LEVEL"));
    if (value.isEmpty()) {
      value = Strings.nullToEmpty(System.getProperty("gerrit.logLevel"));
    }
    return Level.toLevel(value, Level.INFO);
  }

  public static void configureLogging() {
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    context.reconfigure();

    Layout<? extends Serializable> layout =
        PatternLayout.newBuilder().withPattern(PATTERN_LAYOUT).build();
    final ConsoleAppender dst =
        ConsoleAppender.newBuilder()
            .withLayout(layout)
            .withName("Console")
            .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
            .setFilter(ThresholdFilter.createFilter(threshold, null, null))
            .build();
    dst.start();

    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration config = ctx.getConfiguration();

    LoggerConfig root = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
    for (Appender appender : root.getAppenders().values()) {
      root.removeAppender(appender.toString());
    }

    root.addAppender(dst, null, null);

    LOG_LEVELS.entrySet().stream().forEach(e -> getLoggerKey(e.getKey()).setLevel(e.getValue()));

    ctx.updateLoggers();
  }

  private static Logger getLoggerKey(String key) {
    return (Logger) getLogger(key);
  }

}
