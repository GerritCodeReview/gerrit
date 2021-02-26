// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.entities;

import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * Immutable parsed representation of a {@link org.eclipse.jgit.lib.Config} that can be cached.
 * Supports only a limited set of operations.
 */
public class ImmutableConfig {
  public static final ImmutableConfig EMPTY = new ImmutableConfig("", new Config());

  private final String stringCfg;
  private final Config cfg;

  private ImmutableConfig(String stringCfg, Config cfg) {
    this.stringCfg = stringCfg;
    this.cfg = cfg;
  }

  public static ImmutableConfig parse(String stringCfg) throws ConfigInvalidException {
    Config cfg = new Config();
    cfg.fromText(stringCfg);
    return new ImmutableConfig(stringCfg, cfg);
  }

  /** Returns a mutable copy of this config. */
  public Config mutableCopy() {
    Config cfg = new Config();
    try {
      cfg.fromText(this.cfg.toText());
    } catch (ConfigInvalidException e) {
      // Can't happen as we used JGit to format that config.
      throw new IllegalStateException(e);
    }
    return cfg;
  }

  /** @see Config#getSections() */
  public Set<String> getSections() {
    return cfg.getSections();
  }

  /** @see Config#getNames(String) */
  public Set<String> getNames(String section) {
    return cfg.getNames(section);
  }

  /** @see Config#getNames(String, String) */
  public Set<String> getNames(String section, String subsection) {
    return cfg.getNames(section, subsection);
  }

  /** @see Config#getStringList(String, String, String) */
  public String[] getStringList(String section, String subsection, String name) {
    return cfg.getStringList(section, subsection, name);
  }

  /** @see Config#getSubsections(String) */
  public Set<String> getSubsections(String section) {
    return cfg.getSubsections(section);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ImmutableConfig)) {
      return false;
    }
    return ((ImmutableConfig) o).stringCfg.equals(stringCfg);
  }

  @Override
  public int hashCode() {
    return stringCfg.hashCode();
  }
}
