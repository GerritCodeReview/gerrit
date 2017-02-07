// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.nio.file.Files;
import java.util.Properties;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configures Velocity template engine for sending email. */
@Singleton
public class VelocityRuntimeProvider implements Provider<RuntimeInstance> {
  private final SitePaths site;

  @Inject
  VelocityRuntimeProvider(SitePaths site) {
    this.site = site;
  }

  @Override
  public RuntimeInstance get() {
    String rl = "resource.loader";
    String pkg = "org.apache.velocity.runtime.resource.loader";

    Properties p = new Properties();
    p.setProperty(RuntimeConstants.VM_PERM_INLINE_LOCAL, "true");
    p.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, Slf4jLogChute.class.getName());
    p.setProperty(RuntimeConstants.RUNTIME_REFERENCES_STRICT, "true");
    p.setProperty("runtime.log.logsystem.log4j.category", "velocity");

    if (Files.isDirectory(site.mail_dir)) {
      p.setProperty(rl, "file, class");
      p.setProperty("file." + rl + ".class", pkg + ".FileResourceLoader");
      p.setProperty("file." + rl + ".path", site.mail_dir.toAbsolutePath().toString());
      p.setProperty("class." + rl + ".class", pkg + ".ClasspathResourceLoader");
    } else {
      p.setProperty(rl, "class");
      p.setProperty("class." + rl + ".class", pkg + ".ClasspathResourceLoader");
    }

    RuntimeInstance ri = new RuntimeInstance();
    try {
      ri.init(p);
    } catch (Exception err) {
      throw new ProvisionException("Cannot configure Velocity templates", err);
    }
    return ri;
  }

  /** Connects Velocity to sfl4j. */
  public static class Slf4jLogChute implements LogChute {
    private static final Logger log = LoggerFactory.getLogger("velocity");

    @Override
    public void init(RuntimeServices rs) {}

    @Override
    public boolean isLevelEnabled(int level) {
      switch (level) {
        default:
        case DEBUG_ID:
          return log.isDebugEnabled();
        case INFO_ID:
          return log.isInfoEnabled();
        case WARN_ID:
          return log.isWarnEnabled();
        case ERROR_ID:
          return log.isErrorEnabled();
      }
    }

    @Override
    public void log(int level, String message) {
      log(level, message, null);
    }

    @Override
    public void log(int level, String msg, Throwable err) {
      switch (level) {
        default:
        case DEBUG_ID:
          log.debug(msg, err);
          break;
        case INFO_ID:
          log.info(msg, err);
          break;
        case WARN_ID:
          log.warn(msg, err);
          break;
        case ERROR_ID:
          log.error(msg, err);
          break;
      }
    }
  }
}
