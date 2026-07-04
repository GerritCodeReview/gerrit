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

package com.google.gerrit.server.plugins;

import com.google.gerrit.common.Nullable;
import java.util.Locale;

/**
 * Servlet flavour (EE8 {@code javax.servlet} vs EE10 {@code jakarta.servlet}) a Gerrit build ships,
 * and the guard that keeps flavour-mismatched plugins from loading.
 *
 * <p>Plugins declare their flavour with a {@code Gerrit-Flavour} manifest attribute. Four cases:
 *
 * <ul>
 *   <li><b>absent</b> — un-audited; treated as {@code ee8}. Loads only on an EE8 Gerrit. It cannot
 *       be assumed servlet-neutral, so an EE10 Gerrit rejects it rather than risk loading a
 *       {@code javax.servlet}-coupled plugin that would fail at runtime.
 *   <li><b>{@code any}</b> — audited servlet-neutral; loads under any flavour.
 *   <li><b>{@code ee8}</b> — {@code javax.servlet}; loads only on an EE8 Gerrit.
 *   <li><b>{@code ee10}</b> — {@code jakarta.servlet}; loads only on an EE10 Gerrit. The bazlets
 *       {@code gerrit_plugin(flavour = "ee10")} macro injects this.
 * </ul>
 */
public enum GerritServerFlavour {
  EE8("ee8"),
  EE10("ee10"),
  /** Plugin-only marker: an audited servlet-neutral plugin that loads under any flavour. */
  ANY("any");

  /** Plugin JAR manifest attribute carrying the flavour marker. */
  public static final String MANIFEST_ATTRIBUTE = "Gerrit-Flavour";

  private static final GerritServerFlavour CURRENT = detect();

  private final String marker;

  GerritServerFlavour(String marker) {
    this.marker = marker;
  }

  /** The {@code Gerrit-Flavour} value for this flavour ({@code ee8} / {@code ee10}). */
  public String marker() {
    return marker;
  }

  /** Flavour of the running Gerrit, derived from the bundled servlet-API namespace. */
  public static GerritServerFlavour current() {
    return CURRENT;
  }

  private static GerritServerFlavour detect() {
    // A release WAR bundles exactly one servlet-API namespace, and that namespace is the flavour:
    // jakarta.servlet (EE10) or javax.servlet (EE8, the default).
    return isPresent("jakarta.servlet.Servlet") ? EE10 : EE8;
  }

  private static boolean isPresent(String className) {
    try {
      Class.forName(className, false, GerritServerFlavour.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * Rejects a plugin whose declared flavour does not match {@code server}.
   *
   * <p>An {@code any} marker loads under either flavour; an absent marker is treated as {@code ee8}
   * (see the class doc for why absent is not {@code any}).
   *
   * @param pluginName plugin being loaded, used in the error message.
   * @param pluginFlavour value of the plugin's {@code Gerrit-Flavour} manifest attribute, or {@code
   *     null} when absent.
   * @param server flavour of the running Gerrit.
   * @throws InvalidPluginException if the plugin's flavour is incompatible with {@code server}.
   */
  public static void checkPluginFlavour(
      String pluginName, @Nullable String pluginFlavour, GerritServerFlavour server)
      throws InvalidPluginException {
    GerritServerFlavour declared = parsePluginFlavour(pluginName, pluginFlavour);
    if (declared == ANY || declared == server) {
      return;
    }
    throw new InvalidPluginException(
        String.format(
            "Plugin '%s' is built for flavour '%s' but this Gerrit instance is '%s'. See"
                + " https://gerrit-review.googlesource.com/Documentation/dev-plugins.html for how"
                + " to publish a matching '%s' build.",
            pluginName, declared.marker, server.marker, server.marker));
  }

  /**
   * Parses a plugin's {@code Gerrit-Flavour} manifest value into a flavour.
   *
   * @param pluginName plugin being loaded, used in the error message.
   * @param value the attribute value, or {@code null} when absent (treated as {@link #EE8}).
   * @return the declared flavour ({@link #EE8}, {@link #EE10} or {@link #ANY}).
   * @throws InvalidPluginException if a non-null value is not one of ee8/ee10/any.
   */
  public static GerritServerFlavour parsePluginFlavour(String pluginName, @Nullable String value)
      throws InvalidPluginException {
    if (value == null) {
      return EE8;
    }
    String normalized = value.trim().toLowerCase(Locale.US);
    for (GerritServerFlavour f : values()) {
      if (f.marker.equals(normalized)) {
        return f;
      }
    }
    throw new InvalidPluginException(
        String.format(
            "Plugin '%s' declares an unknown %s '%s'; expected ee8, ee10 or any.",
            pluginName, MANIFEST_ATTRIBUTE, value));
  }
}
