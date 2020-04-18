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

import static org.apache.log4j.Logger.getLogger;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

public class TestLoggingActivator {
  private static final ImmutableMap<String, Level> LOG_LEVELS =
      ImmutableMap.<String, Level>builder()
          .put("com.google.gerrit", getGerritLogLevel())

          // Silence non-critical messages from MINA SSHD.
          .put("org.apache.mina", Level.WARN)
          .put("org.apache.sshd.client", Level.WARN)
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
          .put("org.eclipse.jgit.internal.transport.sshd", Level.WARN)
          .put("org.eclipse.jgit.util.FileUtils", Level.WARN)
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
    LogManager.resetConfiguration();
    FloggerInitializer.initBackend();

    PatternLayout layout = new PatternLayout();
    layout.setConversionPattern("%-5p %c %x: %m%n");

    ConsoleAppender dst = new ConsoleAppender();
    dst.setLayout(layout);
    dst.setTarget("System.err");
    dst.setThreshold(Level.DEBUG);
    dst.activateOptions();

    Logger root = LogManager.getRootLogger();
    root.removeAllAppenders();
    root.addAppender(dst);

    LOG_LEVELS.entrySet().stream().forEach(e -> getLogger(e.getKey()).setLevel(e.getValue()));
  }

  private TestLoggingActivator() {}
}
