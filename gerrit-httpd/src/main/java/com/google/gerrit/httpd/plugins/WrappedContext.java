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

package com.google.gerrit.httpd.plugins;

import com.google.common.collect.Maps;
import com.google.gerrit.common.Version;
import com.google.gerrit.server.plugins.Plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

class WrappedContext implements ServletContext {
  private static final Logger log = LoggerFactory.getLogger("plugin");
  private final Plugin plugin;
  private final String contextPath;
  private final ConcurrentMap<String, Object> attributes;

  WrappedContext(Plugin plugin, String contextPath) {
    this.plugin = plugin;
    this.contextPath = contextPath;
    this.attributes = Maps.newConcurrentMap();
  }

  @Override
  public String getContextPath() {
    return contextPath;
  }

  @Override
  public String getInitParameter(String name) {
    return null;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Enumeration getInitParameterNames() {
    return Collections.enumeration(Collections.emptyList());
  }

  @Override
  public ServletContext getContext(String name) {
    return null;
  }

  @Override
  public RequestDispatcher getNamedDispatcher(String name) {
    return null;
  }

  @Override
  public RequestDispatcher getRequestDispatcher(String name) {
    return null;
  }

  @Override
  public URL getResource(String name) throws MalformedURLException {
    return null;
  }

  @Override
  public InputStream getResourceAsStream(String name) {
    return null;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Set getResourcePaths(String name) {
    return null;
  }

  @Override
  public Servlet getServlet(String name) throws ServletException {
    return null;
  }

  @Override
  public String getRealPath(String name) {
    return null;
  }

  @Override
  public String getServletContextName() {
    return plugin.getName();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Enumeration getServletNames() {
    return Collections.enumeration(Collections.emptyList());
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Enumeration getServlets() {
    return Collections.enumeration(Collections.emptyList());
  }

  @Override
  public void log(Exception reason, String msg) {
    log(msg, reason);
  }

  @Override
  public void log(String msg) {
    log(msg, null);
  }

  @Override
  public void log(String msg, Throwable reason) {
    log.warn(String.format("[plugin %s] %s", plugin.getName(), msg), reason);
  }

  @Override
  public Object getAttribute(String name) {
    return attributes.get(name);
  }

  @Override
  public Enumeration<String> getAttributeNames() {
    return Collections.enumeration(attributes.keySet());
  }

  @Override
  public void setAttribute(String name, Object value) {
    attributes.put(name, value);
  }

  @Override
  public void removeAttribute(String name) {
    attributes.remove(name);
  }

  @Override
  public String getMimeType(String file) {
    return null;
  }

  @Override
  public int getMajorVersion() {
    return 2;
  }

  @Override
  public int getMinorVersion() {
    return 5;
  }

  @Override
  public String getServerInfo() {
    String v = Version.getVersion();
    return "Gerrit Code Review/" + (v != null ? v : "dev");
  }
}
