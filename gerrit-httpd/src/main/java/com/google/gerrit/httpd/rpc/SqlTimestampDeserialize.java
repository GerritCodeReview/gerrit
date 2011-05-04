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

/*
 * NB: Methods: parseTimestamp and trimLeading0Code were ripped out of
 * com.google.gwtjsonrpc.client.impl.ser.JavaSqlTimestamp_JsonSerializer from
 * gwtjsonrpc1.2.3.jar
 */

package com.google.gerrit.httpd.rpc;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gwtjsonrpc.server.SqlTimestampDeserializer;

import java.lang.reflect.Type;
import java.util.Date;

public class SqlTimestampDeserialize extends SqlTimestampDeserializer {

  @Override
  public java.sql.Timestamp deserialize(final JsonElement json,
      final Type typeOfT, final JsonDeserializationContext context)
      throws JsonParseException {
    if (json.isJsonNull()) {
      return null;
    }
    if (!json.isJsonPrimitive()) {
      throw new JsonParseException("Expected string for timestamp type");
    }
    final JsonPrimitive p = (JsonPrimitive) json;
    if (!p.isString()) {
      throw new JsonParseException("Expected string for timestamp type");
    }
    return parseTimestamp(p.getAsString());
  }

  @SuppressWarnings("deprecation")
  private static java.sql.Timestamp parseTimestamp(final String s) {
    final String[] components = s.split(" ");
    if (components.length != 2) {
      throw new IllegalArgumentException("Invalid escape format: " + s);
    }

    final String[] timeComponents = components[1].split("\\.");
    if (timeComponents.length != 2) {
      throw new IllegalArgumentException("Invalid escape format: " + s);
    } /*
       * else if (timeComponents[1].length() != 9) { throw new
       * IllegalArgumentException("Invalid escape format: " + s); }
       */

    final String[] dSplit = components[0].split("-");
    final String[] tSplit = timeComponents[0].split(":");
    if (dSplit.length != 3 || tSplit.length != 3) {
      throw new IllegalArgumentException("Invalid escape format: " + s);
    }
    trimLeading0(dSplit);
    trimLeading0(tSplit);

    if (timeComponents[1].startsWith("0")) {
      timeComponents[1] = timeComponents[1].replaceFirst("^00*", "");
      if (timeComponents[1].length() == 0) {
        timeComponents[1] = "0";
      }
    }

    try {
      int yy = Integer.parseInt(dSplit[0]) - 1900;
      int mm = Integer.parseInt(dSplit[1]) - 1;
      int dd = Integer.parseInt(dSplit[2]);

      int hh = Integer.parseInt(tSplit[0]);
      int mi = Integer.parseInt(tSplit[1]);
      int ss = Integer.parseInt(tSplit[2]);
      int ms = Integer.valueOf(timeComponents[1]) / 1000000;

      return new java.sql.Timestamp(Date.UTC(yy, mm, dd, hh, mi, ss) + ms);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid escape format: " + s);
    }
  }

  private static void trimLeading0(final String[] dSplit) {
    for (int i = 0; i < 3; i++) {
      if (dSplit[i].startsWith("0")) {
        dSplit[i] = dSplit[i].substring(1);
      }
    }
  }
}
