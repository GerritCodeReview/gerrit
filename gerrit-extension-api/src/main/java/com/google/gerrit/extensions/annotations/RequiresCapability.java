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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation on {@code com.google.gerrit.sshd.SshCommand} or {@code
 * com.google.gerrit.httpd.restapi.RestApiServlet} declaring a capability must be granted.
 */
@Target({ElementType.TYPE})
@Retention(RUNTIME)
public @interface RequiresCapability {
  /** Name of the capability required to invoke this action. */
  String value();

  /** Scope of the named capability. */
  CapabilityScope scope() default CapabilityScope.CONTEXT;

  /** Fall back to admin credentials. Only applies to plugin capability check. */
  boolean fallBackToAdmin() default true;
}
