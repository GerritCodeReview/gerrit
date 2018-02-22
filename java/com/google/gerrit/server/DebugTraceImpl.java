// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server;

import java.util.Optional;
import java.util.UUID;

import org.apache.log4j.AsyncAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.server.DebugTraceApi.DebugTrace;
import com.google.gerrit.server.util.SystemLog;
import com.google.inject.Inject;
import com.google.inject.Singleton;

public class DebugTraceImpl implements DebugTrace {
  private static final String LOG_NAME_PREFIX = "debug_log_";

  @Singleton
  public static class Factory implements DebugTrace.Factory {
    private final SystemLog systemLog;

    @Inject
    Factory(SystemLog systemLog) {
      this.systemLog = systemLog;
    }

    @Override
    public DebugTrace create() {
      return new DebugTraceImpl(systemLog, null);
    }

    @Override
    public DebugTrace create(String traceId) {
      return new DebugTraceImpl(systemLog, traceId);
    }
  }

  private final String traceId;
  private final AsyncAppender log;
  private final Level level;

  private DebugTraceImpl(SystemLog systemLog, @Nullable String traceId) {
    this.traceId = traceId != null ? traceId : newTraceId();
    this.log = systemLog.createAsyncAppender(LOG_NAME_PREFIX + this.traceId, new DebugLogLayout());    
    this.level = Level.DEBUG;
  }
  
  private DebugTraceImpl(String traceId, AsyncAppender log, Level level) {
    this.traceId = traceId;
    this.log = log;
    this.level = level;
  }
  
  @Override
  public Optional<String> getTraceUuid() {
    return Optional.of(traceId);
  }
  
  @Override
  public DebugTraceApi atInfo() {
    return new DebugTraceImpl(traceId, log, Level.INFO);
  }
  
  @Override
  public DebugTraceApi atWarning() {
    return new DebugTraceImpl(traceId, log, Level.WARN);
  }
  
  @Override
  public DebugTraceApi atSevere() {
    return new DebugTraceImpl(traceId, log, Level.ERROR);
  }

  @Override
  public DebugTraceApi log(String msg, Object... args) {    
    LocationInfo locationInfo = getLocationInfoForCaller();
    log.append(new LoggingEvent(Logger.class.getName(), Logger.getLogger(locationInfo.getClassName()), TimeUtil.nowMs(),
        level, String.format(msg, args), Thread.currentThread().getName(), null, null, locationInfo, null));
    return this;
  }
  
  @Override
  public void close() {
    log.close();
  }

  private static LocationInfo getLocationInfoForCaller() {
    for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
      if (!e.getClassName().equals(DebugTraceImpl.class.getName())
          && e.getClassName().indexOf(Thread.class.getName()) != 0) {
        return new LocationInfo(e.getFileName(), e.getClassName(), e.getMethodName(),
            Integer.toString(e.getLineNumber()));
      }
    }
    // should never happen
    return new LocationInfo(null, DebugTraceImpl.class.getName());
  }

  private static String newTraceId() {
    return UUID.randomUUID().toString();
  }
}
