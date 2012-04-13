// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.audit;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gwtjsonrpc.server.MapDeserializer;
import com.google.gwtjsonrpc.server.SqlDateDeserializer;
import com.google.gwtjsonrpc.server.SqlTimestampDeserializer;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AuditRecord {

  public static final String UNKNOWN_USR = "N/A";
  public static final String UNKNOWN_SID = "000000000000000000000000000";

  private String sid;
  private String who;
  private long when;
  private String what;
  private List<?> params;
  private Object result;
  private long timeAtStart;
  private long elapsed;

  public AuditRecord(String sessionid, String who, String what, List<?> params) {

    if (what == null)
      throw new IllegalArgumentException("what is a mandatory param!");

    this.sid = parseNull(sessionid, UNKNOWN_SID);
    this.who = parseNull(who, UNKNOWN_USR);
    this.what = what;
    this.when = System.currentTimeMillis();
    this.timeAtStart = this.when;

    if (params != null)
      this.params = params;
    else
      this.params = Collections.emptyList();
  }

  public AuditRecord(String sessionid, String who, String what, long when, List<?> params, Object result, long elapsed) {
    this(sessionid, who, what, params);
    this.elapsed = elapsed;
    this.result = result;
    this.when = when;
  }

  private String parseNull(String who, String defval) {
    return (who != null && !who.isEmpty()) ? who : defval;
  }

  public String sid() {
    return sid;
  }

  public String who() {
    return who;
  }

  public long when() {
    return when;
  }

  public String what() {
    return what;
  }

  public List<?> params() {
    return params;
  }

  public Object result() {
    return result;
  }

  public long elapsed() {
    return elapsed;
  }

  public void setResult(String result) {
    if (this.result != null)
      throw new IllegalArgumentException("result is already set!");
    if (result == null)
      throw new IllegalArgumentException("result cannot be null!");

    this.result = result;
    this.elapsed = System.currentTimeMillis()-timeAtStart;
  }

  @Override
  public String toString() {
    return asString();
  }

  @Override
  public int hashCode() {
    return asString().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;

    AuditRecord other = (AuditRecord) obj;
    return this.asString().equals(other.asString());
  }

  private String asString() {
    Gson gson = createGson();

    StringBuilder sb = new StringBuilder();

    sb.append(sid);
    sb.append('|');
    sb.append(who);
    sb.append('|');
    sb.append(when);
    sb.append('|');
    sb.append(what);
    sb.append('|');
    sb.append(elapsed);
    sb.append('|');
    if (params != null) {
      sb.append('[');
      for (int i=0; i<params.size(); i++) {
        if (i > 0)
          sb.append(',');

        sb.append(gson.toJson(params.get(i)));
      }
      sb.append(']');
    }
    sb.append('|');
    if (result != null) {
      sb.append(gson.toJson(result));
    }

    return sb.toString();
  }

  private Gson createGson() {
    return createGsonBuilder()
      .setDateFormat(DateFormat.LONG)
      .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
      .setVersion(1.0)
      .create();
  }

  private GsonBuilder createGsonBuilder() {
    final GsonBuilder gb = new GsonBuilder();
    gb.registerTypeAdapter(java.util.Set.class,
        new InstanceCreator<java.util.Set<Object>>() {
          public Set<Object> createInstance(final Type arg0) {
            return new HashSet<Object>();
          }
        });
    gb.registerTypeAdapter(java.util.Map.class, new MapDeserializer());
    gb.registerTypeAdapter(java.sql.Date.class, new SqlDateDeserializer());
    gb.registerTypeAdapter(java.sql.Timestamp.class,
        new SqlTimestampDeserializer());
    return gb;
  }
}
