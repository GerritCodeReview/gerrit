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
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

@Plugin(
    name = "JsonLayout",
    category = Node.CATEGORY,
    elementType = Layout.ELEMENT_TYPE,
    printObject = true)
public abstract class JsonLayout extends AbstractStringLayout {

  private final Gson gson;
  protected final LogTimestampFormatter timestampFormatter;

  /*public static class Builder<B extends Builder<B>> extends AbstractStringLayout.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<JsonLayout> {

    public Builder() {
      super();
      setCharset(StandardCharsets.UTF_8);
    }

    @Override
    public JsonLayout build() {
      return new JsonLayout(getConfiguration());
    }
  }*/

  /** @deprecated Use {@link #newBuilder()} instead */
  @Deprecated
  public JsonLayout() {
    this(null);
  }

  private JsonLayout(final Configuration config) {
    super(config, StandardCharsets.UTF_8, null, null);

    timestampFormatter = new LogTimestampFormatter();
    gson = newGson();
  }

  /** @deprecated Use {@link #newBuilder()} instead */
  /*@Deprecated
  public static JsonLayout createLayout() {
    return new JsonLayout(null);
  }

  @PluginBuilderFactory
  public static <B extends Builder<B>> B newBuilder() {
    return new Builder<B>().asBuilder();
  }*/

  public abstract JsonLogEntry toJsonLogEntry(final LogEvent event);

  /**
   * Formats a {@link org.apache.logging.log4j.core.LogEvent} in conformance with the BSD Log record
   * format.
   *
   * @param event The LogEvent
   * @return the event formatted as a String.
   */
  @Override
  public String toSerializable(final LogEvent event) {
    return gson.toJson(toJsonLogEntry(event)) + "\n";
  }

  private static Gson newGson() {
    GsonBuilder gb =
        new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .disableHtmlEscaping();
    return gb.create();
  }
}
