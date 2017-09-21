// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gwtjsonrpc.server.SqlTimestampDeserializer;
import java.sql.Timestamp;

/** Standard output format used by an API call. */
public enum OutputFormat {
  /**
   * The output is a human readable text format. It may also be regular enough to be machine
   * readable. Whether or not the text format is machine readable and will be committed to as a long
   * term format that tools can build upon is specific to each API call.
   */
  TEXT,

  /**
   * Pretty-printed JSON format. This format uses whitespace to make the output readable by a human,
   * but is also machine readable with a JSON library. The structure of the output is a long term
   * format that tools can rely upon.
   */
  JSON,

  /**
   * Same as {@link #JSON}, but with unnecessary whitespace removed to save generation time and copy
   * costs. Typically JSON_COMPACT format is used by a browser based HTML client running over the
   * network.
   */
  JSON_COMPACT;

  /** @return true when the format is either JSON or JSON_COMPACT. */
  public boolean isJson() {
    return this == JSON_COMPACT || this == JSON;
  }

  /** @return a new Gson instance configured according to the format. */
  public GsonBuilder newGsonBuilder() {
    if (!isJson()) {
      throw new IllegalStateException(String.format("%s is not JSON", this));
    }
    GsonBuilder gb =
        new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(Timestamp.class, new SqlTimestampDeserializer());
    if (this == OutputFormat.JSON) {
      gb.setPrettyPrinting();
    }
    return gb;
  }

  /** @return a new Gson instance configured according to the format. */
  public Gson newGson() {
    return newGsonBuilder().create();
  }
}
