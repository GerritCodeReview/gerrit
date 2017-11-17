// Copyright (C) 2017 The Android Open Source Project
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
//
// Originally from https://github.com/DNSBelgium/log4j-jsonevent-layout/

package com.google.gerrit.pgm.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.message.Message;

/** Message implementation that combines a String message with a map of fields. */
public class MessageMapMessage implements Message {
  private static final Pattern fieldPattern = Pattern.compile("\\{(.+?)\\}");

  private String message;

  private Map<String, Object> fields = new TreeMap<>();

  public MessageMapMessage(String message) {
    this.message = message;
  }

  public MessageMapMessage add(String field, Object value) {
    this.fields.put(field, value);
    return this;
  }

  @Override
  public String getFormattedMessage() {
    if (StringUtils.isBlank(message)) {
      return message;
    }

    StringBuffer sb = new StringBuffer(message.length());
    Matcher m = fieldPattern.matcher(message);
    while (m.find()) {
      String found = m.group(1);

      String replacement = null;
      if (fields.containsKey(found)) {
        replacement = Objects.toString(fields.get(found));
      } else {
        replacement = "{" + found + "}";
      }

      m.appendReplacement(sb, replacement);
    }
    m.appendTail(sb);

    return sb.toString();
  }

  @Override
  public String getFormat() {
    return message;
  }

  @Override
  public Object[] getParameters() {
    return fields.values().toArray();
  }

  @Override
  public Throwable getThrowable() {
    return null; // TODO
  }

  public Map<String, Object> getFields() {
    return fields;
  }
}
