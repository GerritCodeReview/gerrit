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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

public abstract class JsonLayout extends Layout {
  private final Gson gson;
  protected final LogTimestampFormatter timestampFormatter;

  public JsonLayout() {
    timestampFormatter = new LogTimestampFormatter();
    gson = newGson();
  }

  public abstract JsonLogEntry toJsonLogEntry(LoggingEvent event);

  @Override
  public String format(LoggingEvent event) {
    return gson.toJson(toJsonLogEntry(event)) + "\n";
  }

  private static Gson newGson() {
    GsonBuilder gb =
        new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .disableHtmlEscaping();
    return gb.create();
  }

  @Override
  public void activateOptions() {}

  @Override
  public boolean ignoresThrowable() {
    return false;
  }
}
