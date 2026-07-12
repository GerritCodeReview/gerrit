// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.plugins.servlet;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.annotations.RootRelative;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The single servlet-API-coupled seam of the plugin loader.
 *
 * <p>Every reference to a servlet type lives here, so the plugin loader ({@code
 * PluginGuiceEnvironment}, {@code AutoRegisterModules}) stays decoupled from the servlet API. This
 * is its own library leaf that {@code server} depends on; it must NOT depend on {@code server} (and
 * cannot live in {@code httpd}, which depends on {@code server}).
 *
 * <p>The type-name constants below stay {@code String}s so callers keep using their own {@code
 * is()} hierarchy walk -- the seam never needs to reach back into the loader.
 */
public final class PluginServletOverlay {
  private PluginServletOverlay() {}

  /**
   * Servlet-API type names the plugin injector must not copy from the parent (see {@code
   * PluginGuiceEnvironment#shouldCopy}).
   */
  public static final ImmutableList<String> SERVLET_API_TYPE_NAMES =
      ImmutableList.of(
          "jakarta.servlet.Filter",
          "jakarta.servlet.ServletContext",
          "jakarta.servlet.ServletRequest",
          "jakarta.servlet.ServletResponse",
          "jakarta.servlet.http.HttpServlet",
          "jakarta.servlet.http.HttpServletRequest",
          "jakarta.servlet.http.HttpServletResponse",
          "jakarta.servlet.http.HttpSession");

  /** The {@code HttpServlet} type name, for {@code @Export} auto-registration. */
  public static final String HTTP_SERVLET = "jakarta.servlet.http.HttpServlet";

  /**
   * Copies the parent injector's {@code @RootRelative} HttpServletRequest/Response bindings into a
   * plugin injector when present. {@code @RootRelative} is the sanctioned way for plugins to obtain
   * the Gerrit-root-relative request (see dev-plugins.txt).
   */
  public static void bindRootRelative(Injector src, Binder binder) {
    Binding<HttpServletRequest> requestBinding =
        src.getExistingBinding(Key.get(HttpServletRequest.class));
    if (requestBinding != null) {
      binder
          .bind(HttpServletRequest.class)
          .annotatedWith(RootRelative.class)
          .toProvider(requestBinding.getProvider());
    }
    Binding<HttpServletResponse> responseBinding =
        src.getExistingBinding(Key.get(HttpServletResponse.class));
    if (responseBinding != null) {
      binder
          .bind(HttpServletResponse.class)
          .annotatedWith(RootRelative.class)
          .toProvider(responseBinding.getProvider());
    }
  }
}
