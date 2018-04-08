// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.init.api;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.securestore.SecureStore;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Helper to edit a section of the configuration files. */
public class Section {
  public interface Factory {
    Section get(@Assisted("section") String section, @Assisted("subsection") String subsection);
  }

  private final InitFlags flags;
  private final SitePaths site;
  private final ConsoleUI ui;
  private final String section;
  private final String subsection;
  private final SecureStore secureStore;

  @Inject
  public Section(
      final InitFlags flags,
      final SitePaths site,
      final SecureStore secureStore,
      final ConsoleUI ui,
      @Assisted("section") final String section,
      @Assisted("subsection") @Nullable final String subsection) {
    this.flags = flags;
    this.site = site;
    this.ui = ui;
    this.section = section;
    this.subsection = subsection;
    this.secureStore = secureStore;
  }

  public String get(String name) {
    return flags.cfg.getString(section, subsection, name);
  }

  public void set(String name, String value) {
    final ArrayList<String> all = new ArrayList<>();
    all.addAll(Arrays.asList(flags.cfg.getStringList(section, subsection, name)));

    if (value != null) {
      if (all.size() == 0 || all.size() == 1) {
        flags.cfg.setString(section, subsection, name, value);
      } else {
        all.set(0, value);
        flags.cfg.setStringList(section, subsection, name, all);
      }

    } else if (all.size() == 1) {
      flags.cfg.unset(section, subsection, name);
    } else if (all.size() != 0) {
      all.remove(0);
      flags.cfg.setStringList(section, subsection, name, all);
    }
  }

  public <T extends Enum<?>> void set(String name, T value) {
    if (value != null) {
      set(name, value.name());
    } else {
      unset(name);
    }
  }

  public void unset(String name) {
    set(name, (String) null);
  }

  public String string(String title, String name, String dv) {
    return string(title, name, dv, false);
  }

  public String string(final String title, String name, String dv, boolean nullIfDefault) {
    final String ov = get(name);
    String nv = ui.readString(ov != null ? ov : dv, "%s", title);
    if (nullIfDefault && nv.equals(dv)) {
      nv = null;
    }
    if (!Objects.equals(ov, nv)) {
      set(name, nv);
    }
    return nv;
  }

  public Path path(String title, String name, String defValue) {
    return site.resolve(string(title, name, defValue));
  }

  public <T extends Enum<?>, E extends EnumSet<? extends T>> T select(
      String title, String name, T defValue) {
    return select(title, name, defValue, false);
  }

  public <T extends Enum<?>, E extends EnumSet<? extends T>> T select(
      String title, String name, T defValue, boolean nullIfDefault) {
    @SuppressWarnings("unchecked")
    E allowedValues = (E) EnumSet.allOf(defValue.getClass());
    return select(title, name, defValue, allowedValues, nullIfDefault);
  }

  public <T extends Enum<?>, E extends EnumSet<? extends T>> T select(
      String title, String name, T defValue, E allowedValues) {
    return select(title, name, defValue, allowedValues, false);
  }

  public <T extends Enum<?>, A extends EnumSet<? extends T>> T select(
      String title, String name, T defValue, A allowedValues, boolean nullIfDefault) {
    final boolean set = get(name) != null;
    T oldValue = flags.cfg.getEnum(section, subsection, name, defValue);
    T newValue = ui.readEnum(oldValue, allowedValues, "%s", title);
    if (nullIfDefault && newValue == defValue) {
      newValue = null;
    }
    if (!set || oldValue != newValue) {
      if (newValue != null) {
        set(name, newValue);
      } else {
        unset(name);
      }
    }
    return newValue;
  }

  public String select(final String title, String name, String dv, Set<String> allowedValues) {
    final String ov = get(name);
    String nv = ui.readString(ov != null ? ov : dv, allowedValues, "%s", title);
    if (!Objects.equals(ov, nv)) {
      set(name, nv);
    }
    return nv;
  }

  public String password(String username, String password) {
    final String ov = getSecure(password);

    String user = flags.sec.get(section, subsection, username);
    if (user == null) {
      user = get(username);
    }

    if (user == null) {
      flags.sec.unset(section, subsection, password);
      return null;
    }

    if (ov != null) {
      // If the user already has a password stored, try to reuse it
      // rather than prompting for a whole new one.
      //
      if (ui.isBatch() || !ui.yesno(false, "Change %s's password", user)) {
        return ov;
      }
    }

    final String nv = ui.password("%s's password", user);
    if (!Objects.equals(ov, nv)) {
      setSecure(password, nv);
    }
    return nv;
  }

  public String passwordForKey(String prompt, String passwordKey) {
    String ov = getSecure(passwordKey);
    if (ov != null) {
      // If the password is already stored, try to reuse it
      // rather than prompting for a whole new one.
      //
      if (ui.isBatch() || !ui.yesno(false, "Change %s", passwordKey)) {
        return ov;
      }
    }

    final String nv = ui.password("%s", prompt);
    if (!Objects.equals(ov, nv)) {
      setSecure(passwordKey, nv);
    }
    return nv;
  }

  public String getSecure(String name) {
    return flags.sec.get(section, subsection, name);
  }

  public void setSecure(String name, String value) {
    if (value != null) {
      secureStore.set(section, subsection, name, value);
    } else {
      secureStore.unset(section, subsection, name);
    }
  }

  String getName() {
    return section;
  }
}
