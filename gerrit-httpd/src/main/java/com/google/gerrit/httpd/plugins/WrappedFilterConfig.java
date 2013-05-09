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

import com.google.inject.servlet.GuiceFilter;

import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

class WrappedFilterConfig implements FilterConfig {
  private final ServletContext context;

  WrappedFilterConfig(ServletContext context) {
    this.context = context;
  }

  @Override
  public String getFilterName() {
    return GuiceFilter.class.getName();
  }

  @Override
  public String getInitParameter(String name) {
    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Enumeration getInitParameterNames() {
    return Collections.enumeration(Collections.emptyList());
  }

  @Override
  public ServletContext getServletContext() {
    return context;
  }
}
