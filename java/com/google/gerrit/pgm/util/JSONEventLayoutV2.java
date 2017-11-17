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
// Originally from https://github.com/logstash/log4j-jsonevent-layout/pull/56 and
// https://github.com/DNSBelgium/log4j-jsonevent-layout/

package com.google.gerrit.pgm.util;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import net.logstash.log4j.data.HostData;
import net.minidev.json.JSONObject;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.status.StatusLogger;

/** @author michaelkuechler, maartenbosteels */
@Plugin(name = "JSONEventLayoutV2", category = "Core", elementType = "layout", printObject = true)
public class JSONEventLayoutV2 extends AbstractStringLayout {
  /**
   * @param locationInfo If "true", includes the location information in the generated JSON,
   *     defaults to false.
   * @param userFields User fields for logstash, e.g. {@code field1:value1,field2:value2}. Note:
   *     system properties have precedence over log4j properties with the same name.
   * @param charset The character set to use, defaults to "UTF-8".
   * @param messageParameters If "true", log the parameters for the message
   * @return A JSON Layout customized for logstash.
   */
  @PluginFactory
  public static JSONEventLayoutV2 createLayout(
      @PluginAttribute(value = "locationInfo", defaultBoolean = false) boolean locationInfo,
      @PluginAttribute(value = "userFields") String userFields,
      @PluginAttribute(value = "charset", defaultString = "UTF-8") Charset charset,
      @PluginAttribute(value = "messageParameters", defaultBoolean = false)
          boolean messageParameters) {
    return new JSONEventLayoutV2(locationInfo, userFields, charset, messageParameters);
  }

  // for backwards-compatible
  public static JSONEventLayoutV2 createLayout(
      boolean locationInfo, String userFields, Charset charset) {
    return createLayout(locationInfo, userFields, charset, false);
  }

  private static final int LOGSTASH_JSON_EVENT_VERSION = 1;
  public static final String USER_FIELDS_PROPERTY =
      "com.google.gerrit.pgm.util.JSONEventLayoutV2.UserFields";
  private static final FastDateFormat ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS =
      FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC"));

  public static String dateFormat(long timestamp) {
    return ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS.format(timestamp);
  }

  private final String whoami = this.getClass().getSimpleName();
  private Map<String, Object> userFields = new HashMap<>();
  private final String hostname = new HostData().getHostName();
  private boolean messageParameters;

  /** @see #createLayout(boolean, String, Charset, boolean) */
  private boolean locationInfo = false;

  /** @see #createLayout(boolean, String, Charset, boolean) */
  public JSONEventLayoutV2(
      boolean locationInfo, String userFields, Charset charset, boolean messageParameters) {
    super(charset);
    this.locationInfo = locationInfo;
    this.userFields = createUserFields(userFields);
    this.messageParameters = messageParameters;
  }

  private Map<String, Object> createUserFields(String log4jPropertyUserFields) {
    Map<String, Object> userFields = new HashMap<>();

    // extract user fields from log4j config, if defined
    LOGGER.debug(
        "[" + this.whoami + "] Adding user fields from log4j property: " + log4jPropertyUserFields);
    appendUserFields(userFields, log4jPropertyUserFields);

    // extract user fields from system properties, if defined. Note that CLI props will override conflicts with log4j config
    String systemPropertyUserFields = System.getProperty(USER_FIELDS_PROPERTY);
    if (systemPropertyUserFields != null) {
      LOGGER.debug(
          "["
              + this.whoami
              + "] Adding user fields from system property: "
              + systemPropertyUserFields);
      appendUserFields(userFields, systemPropertyUserFields);
    }

    return userFields;
  }

  /**
   * Formats a {@link org.apache.logging.log4j.core.LogEvent}.
   *
   * @param event The LogEvent.
   * @return The JSON representation of the LogEvent tailored for logstash.
   */
  @Override
  public String toSerializable(LogEvent event) {
    return toLogstashEvent(event).toString() + "\n";
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  private JSONObject toLogstashEvent(LogEvent event) {

    /*
     * NOTE: v1 of the event format only requires "@timestamp" and "@version", every other field is arbitrary
     */

    JSONObject logstashEvent = new JSONObject(this.userFields);
    logstashEvent.put("@timestamp", dateFormat(event.getTimeMillis()));
    logstashEvent.put("@version", LOGSTASH_JSON_EVENT_VERSION);

    // now we start injecting our own stuff
    logstashEvent.put("source_host", this.hostname);
    logstashEvent.put("message", event.getMessage().getFormattedMessage());

    if (event.getThrownProxy() != null) {
      Map<String, Object> exceptionInformation = new HashMap<>();
      ThrowableProxy thrownProxy = event.getThrownProxy();
      if (thrownProxy.getThrowable().getClass().getCanonicalName() != null) {
        exceptionInformation.put(
            "exception_class", thrownProxy.getThrowable().getClass().getCanonicalName());
      }
      if (thrownProxy.getThrowable().getMessage() != null) {
        exceptionInformation.put("exception_message", thrownProxy.getThrowable().getMessage());
      }
      if (thrownProxy.getExtendedStackTraceAsString() != null) {
        exceptionInformation.put("stacktrace", thrownProxy.getExtendedStackTraceAsString());
      }
      append(logstashEvent, "exception", exceptionInformation);
    }

    if (this.locationInfo) {
      StackTraceElement source = event.getSource();
      append(logstashEvent, "file", source.getFileName());
      append(logstashEvent, "line_number", source.getLineNumber());
      append(logstashEvent, "class", source.getClassName());
      append(logstashEvent, "method", source.getMethodName());
    }

    append(logstashEvent, "logger_name", event.getLoggerName());
    append(logstashEvent, "mdc", event.getContextMap());
    append(logstashEvent, "ndc", event.getContextStack().asList());
    append(logstashEvent, "level", "" + event.getLevel());
    append(logstashEvent, "thread_name", event.getThreadName());

    Message message = event.getMessage();
    if (message instanceof MessageMapMessage) {
      Map<String, Object> fields = ((MessageMapMessage) message).getFields();
      append(logstashEvent, "message_parameters", fields);
    } else if (messageParameters) {
      Map<String, Object> params = new HashMap<>();
      // kibana can't access individual items in an array so converting the parameter array to a map
      int i = 0;
      for (Object o : event.getMessage().getParameters()) {
        params.put("param_" + i++, o);
      }
      append(logstashEvent, "message_parameters", params);
    }

    return logstashEvent;
  }

  @Override
  public String getContentType() {
    return "application/json; charset=" + this.getCharset();
  }

  private static void appendUserFields(Map<String, Object> logstashEvent, String userFieldsString) {
    if (userFieldsString != null) {
      for (String pair : userFieldsString.trim().split(",")) {
        String[] field = pair.trim().split(":", 2);
        if (field.length != 2) {
          StatusLogger.getLogger()
              .warn(
                  "Invalid UserFields "
                      + "(should be of the form key1:value1,key2:value2 but was "
                      + userFieldsString);
        } else {
          append(logstashEvent, field[0], field[1]);
        }
      }
    }
  }

  private static void append(Map<String, Object> logstashEvent, String keyname, Object keyval) {
    if (null != keyname && null != keyval) {
      logstashEvent.put(keyname, keyval);
    }
  }
}
