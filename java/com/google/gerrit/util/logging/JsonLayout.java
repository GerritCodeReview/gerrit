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
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.nio.charset.Charset;

/** Abstract JSON layout for Log4j 2, serializing events via Gson. */
@Plugin(name = "JsonLayout", category = "Core", elementType = Layout.ELEMENT_TYPE, printObject = true)
public abstract class JsonLayout extends AbstractStringLayout {

  private final Gson gson;
  protected final LogTimestampFormatter timestampFormatter;

  protected JsonLayout(Charset charset) {
    super(charset);
    timestampFormatter = new LogTimestampFormatter();
    gson = newGson();
  }

  /** Convert a LogEvent into a JSON-serializable object. */
  public abstract JsonLogEntry toJsonLogEntry(LogEvent event);

  @Override
  public String toSerializable(LogEvent event) {
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
  public boolean requiresLocation() {
    return false;
  }
}

/** Example concrete implementation of JsonLayout for Gerrit logs. */
@Plugin(name = "GerritJsonLayout", category = "Core", elementType = Layout.ELEMENT_TYPE, printObject = true)
class GerritJsonLayout extends JsonLayout {

  protected GerritJsonLayout(Charset charset) {
    super(charset);
  }

  @Override
  public JsonLogEntry toJsonLogEntry(LogEvent event) {
    return new JsonLogEntry() {
      @Override
      public String getMdcString(LogEvent e, String key) {
        return e.getContextData().getValue(key);
      }
    };
  }

  @PluginFactory
  public static GerritJsonLayout createLayout() {
    return new GerritJsonLayout(Charset.defaultCharset());
  }
}
