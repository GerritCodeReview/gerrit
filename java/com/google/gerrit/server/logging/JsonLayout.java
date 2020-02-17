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

package com.google.gerrit.server.logging;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

public abstract class JsonLayout extends Layout {
  private final SimpleDateFormat dateFormat;
  private long lastTimeMillis;
  private String lastTimeString;

  public final Gson gson;

  public JsonLayout() {
    final TimeZone tz = TimeZone.getDefault();
    dateFormat = setDateFormat();
    dateFormat.setTimeZone(tz);

    lastTimeMillis = System.currentTimeMillis();
    lastTimeString = dateFormat.format(new Date(lastTimeMillis));

    gson = newGson();
  }

  public abstract SimpleDateFormat setDateFormat();

  public abstract JsonLogEntry createLogEntry(LoggingEvent event);

  @Override
  public String format(LoggingEvent event) {
    JsonLogEntry logEntry = createLogEntry(event);
    return gson.toJson(logEntry) + ",\n";
  }

  private Gson newGson() {
    GsonBuilder gb =
        new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .disableHtmlEscaping();
    return gb.create();
  }

  public String formatDate(long now) {
    final long rounded = now - (int) (now % 1000);
    if (rounded != lastTimeMillis) {
      synchronized (dateFormat) {
        lastTimeMillis = rounded;
        lastTimeString = dateFormat.format(new Date(lastTimeMillis));
        return lastTimeString;
      }
    }

    return lastTimeString;
  }

  @Override
  public void activateOptions() {}

  @Override
  public boolean ignoresThrowable() {
    return false;
  }
}
