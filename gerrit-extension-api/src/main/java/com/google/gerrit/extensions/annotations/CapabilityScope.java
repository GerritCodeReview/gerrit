// Copyright (C) 2013 The Android Open Source Project
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

/** Declared scope of a capability named by {@link RequiresCapability}. */
public enum CapabilityScope {
  /**
   * Scope is assumed based on the context.
   *
   * <p>When {@code @RequiresCapability} is used within a plugin the scope of the capability is
   * assumed to be that plugin.
   *
   * <p>If {@code @RequiresCapability} is used within the core Gerrit Code Review server (and thus
   * is outside of a plugin) the scope is the core server and will use {@code
   * com.google.gerrit.common.data.GlobalCapability}.
   */
  CONTEXT,

  /** Scope is only the plugin. */
  PLUGIN,

  /** Scope is the core server. */
  CORE
}
