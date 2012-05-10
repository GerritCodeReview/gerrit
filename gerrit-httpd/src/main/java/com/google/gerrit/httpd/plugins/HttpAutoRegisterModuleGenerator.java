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
import com.google.gerrit.extensions.Export;
import com.google.gerrit.server.plugins.InvalidPluginException;
import com.google.gerrit.server.plugins.ModuleGenerator;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;

import java.util.Map;

import javax.servlet.http.HttpServlet;

class HttpAutoRegisterModuleGenerator extends ServletModule
    implements ModuleGenerator {
  private final Map<String, Class<HttpServlet>> serve = Maps.newHashMap();

  @Override
  protected void configureServlets() {
    for (Map.Entry<String, Class<HttpServlet>> e : serve.entrySet()) {
      bind(e.getValue()).in(Scopes.SINGLETON);
      serve(e.getKey()).with(e.getValue());
    }
  }

  @Override
  public void setPluginName(String name) {
  }

  @SuppressWarnings("unchecked")
  @Override
  public void export(Export export, Class<?> type)
      throws InvalidPluginException {
    if (HttpServlet.class.isAssignableFrom(type)) {
      Class<HttpServlet> old = serve.get(export.value());
      if (old != null) {
        throw new InvalidPluginException(String.format(
            "@Export(\"%s\") has duplicate bindings:\n  %s\n  %s",
            export.value(), old.getName(), type.getName()));
      }
      serve.put(export.value(), (Class<HttpServlet>) type);
    } else {
      throw new InvalidPluginException(String.format(
          "Class %s with @Export(\"%s\") must extend %s",
          type.getName(), export.value(),
          HttpServlet.class.getName()));
    }
  }

  @Override
  public Module create() throws InvalidPluginException {
    return this;
  }
}
