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

package com.google.gerrit.extensions.annotations;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation applied to auto-registered, exported types.
 *
 * <p>Plugins or extensions using auto-registration should apply this annotation to any non-abstract
 * class they want exported for access.
 *
 * <p>For SSH commands the {@literal @Export} annotation names the subcommand:
 *
 * <pre>
 *   {@literal @Export("print")}
 *   class MyCommand extends SshCommand {
 * </pre>
 *
 * For HTTP servlets, the {@literal @Export} annotation names the URL the servlet is bound to,
 * relative to the plugin or extension's namespace within the Gerrit container.
 *
 * <pre>
 *  {@literal @Export("/index.html")}
 *  class ShowIndexHtml extends HttpServlet {
 * </pre>
 */
@Target({ElementType.TYPE})
@Retention(RUNTIME)
@BindingAnnotation
public @interface Export {
  String value();
}
