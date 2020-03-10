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

package com.google.gerrit.util.logging;

import com.google.common.flogger.AbstractLogger;
import com.google.common.flogger.LogContext;
import com.google.common.flogger.LoggingApi;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.parser.DefaultPrintfMessageParser;
import com.google.common.flogger.parser.MessageParser;
import java.util.logging.Level;

/**
 * FluentLogger.forEnclosingClass() searches for caller class name and passes it as String to
 * constructor FluentLogger.FluentLogger(LoggerBackend) (which is package protected).
 *
 * <p>This allows to create NamedFluentLogger with given name so that dedicated configuration can be
 * specified by a custom appender in the log4j.properties file. An example of this is the logger
 * used by the replication queue in the replication plugin, and gerrit's Garbage Collection log.
 */
public class NamedFluentLogger extends AbstractLogger<NamedFluentLogger.Api> {
  /** Copied from FluentLogger */
  public interface Api extends LoggingApi<Api> {}

  /** Copied from FluentLogger */
  private static final class NoOp extends LoggingApi.NoOp<Api> implements Api {}

  private static final NoOp NO_OP = new NoOp();

  public static NamedFluentLogger forName(String name) {
    return new NamedFluentLogger(Platform.getBackend(name));
  }

  private NamedFluentLogger(LoggerBackend backend) {
    super(backend);
  }

  @Override
  public Api at(Level level) {
    boolean isLoggable = isLoggable(level);
    boolean isForced = Platform.shouldForceLogging(getName(), level, isLoggable);
    return (isLoggable || isForced) ? new Context(level, isForced) : NO_OP;
  }

  /** Copied from FluentLogger */
  private final class Context extends LogContext<NamedFluentLogger, Api> implements Api {
    private Context(Level level, boolean isForced) {
      super(level, isForced);
    }

    @Override
    protected NamedFluentLogger getLogger() {
      return NamedFluentLogger.this;
    }

    @Override
    protected Api api() {
      return this;
    }

    @Override
    protected Api noOp() {
      return NO_OP;
    }

    @Override
    protected MessageParser getMessageParser() {
      return DefaultPrintfMessageParser.getInstance();
    }
  }
}
