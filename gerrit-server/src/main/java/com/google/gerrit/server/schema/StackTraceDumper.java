// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.common.base.Joiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class StackTraceDumper {
  static Logger log = LoggerFactory.getLogger(StackTraceDumper.class);
  static Method callerUrlMethod;

  protected void logStackTrace(Object... parameters) {
    StringBuilder stack = new StringBuilder();
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    String callerClassName = stackTrace[2].getClassName();
    String callerMethod = stackTrace[2].getMethodName();
    boolean endStack = false;
    StringBuilder callerUrl = new StringBuilder();
    for (int i = 3; i < stackTrace.length && !endStack; i++) {
      endStack =
          stackTrace[i].getClassName().equals(
              "com.google.gerrit.httpd.restapi.RestApiServlet");
      if (endStack) {
        callerUrl = getCallerUrl(i);
      }
      stack.append(" > ");
      stack.append(stackTrace[i].toString());
    }

    callerUrl.append(" - ");
    callerUrl.append(callerClassName.substring(
        callerClassName.lastIndexOf('.') + 1).replaceAll("Wrapper", "")
        + "." + callerMethod);
    callerUrl.append("(");
    callerUrl.append(Joiner.on(',').skipNulls().join(parameters));
    callerUrl.append(")");
    callerUrl.append(" - ");
    callerUrl.append(stack.toString());
  }

  private StringBuilder getCallerUrl(int stackLevel) {
    Class restApiServletClass = sun.reflect.Reflection.getCallerClass(stackLevel+1);
    try {
      if(callerUrlMethod == null) {
        callerUrlMethod = restApiServletClass.getMethod("getCallerUrl");
      }
      return (StringBuilder) callerUrlMethod.invoke(null);
    } catch (IllegalAccessException | IllegalArgumentException
        | InvocationTargetException | NoSuchMethodException
        | SecurityException e) {
      e.printStackTrace();
      return new StringBuilder();
    }
  }

}
