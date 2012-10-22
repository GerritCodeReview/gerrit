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

package com.google.gerrit.pgm.init;

import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import javax.annotation.Nullable;

/** Helper to edit a section of the configuration files. */
public class Section {
  public interface Factory {
    Section get(@Assisted("section") String section,
        @Assisted("subsection") String subsection);
  }

  private final InitFlags flags;
  private final SitePaths site;
  private final ConsoleUI ui;
  private final String section;
  private final String subsection;

  @Inject
  public Section(final InitFlags flags, final SitePaths site,
      final ConsoleUI ui, @Assisted("section") final String section,
      @Assisted("subsection") @Nullable final String subsection) {
    this.flags = flags;
    this.site = site;
    this.ui = ui;
    this.section = section;
    this.subsection = subsection;
  }

  String get(String name) {
    return flags.cfg.getString(section, null, name);
  }

  public void set(final String name, final String value) {
    final ArrayList<String> all = new ArrayList<String>();
    all.addAll(Arrays.asList(flags.cfg.getStringList(section, subsection, name)));

    if (value != null) {
      if (all.size() == 0 || all.size() == 1) {
        flags.cfg.setString(section, subsection, name, value);
      } else {
        all.set(0, value);
        flags.cfg.setStringList(section, subsection, name, all);
      }

    } else if (all.size() == 0) {
    } else if (all.size() == 1) {
      flags.cfg.unset(section, subsection, name);
    } else {
      all.remove(0);
      flags.cfg.setStringList(section, subsection, name, all);
    }
  }

  public <T extends Enum<?>> void set(final String name, final T value) {
    if (value != null) {
      set(name, value.name());
    } else {
      unset(name);
    }
  }

  public void unset(String name) {
    set(name, (String) null);
  }

  public String string(final String title, final String name, final String dv) {
    return string(title, name, dv, false);
  }

  public String string(final String title, final String name, final String dv,
      final boolean nullIfDefault) {
    final String ov = get(name);
    String nv = ui.readString(ov != null ? ov : dv, "%s", title);
    if (nullIfDefault && nv == dv) {
      nv = null;
    }
    if (!eq(ov, nv)) {
      set(name, nv);
    }
    return nv;
  }

  public File path(final String title, final String name, final String defValue) {
    return site.resolve(string(title, name, defValue));
  }

  public <T extends Enum<?>> T select(final String title, final String name,
      final T defValue) {
    return select(title, name, defValue, false);
  }

  public <T extends Enum<?>> T select(final String title, final String name,
      final T defValue, final boolean nullIfDefault) {
    final boolean set = get(name) != null;
    T oldValue = ConfigUtil.getEnum(flags.cfg, section, subsection, name, defValue);
    T newValue = ui.readEnum(oldValue, "%s", title);
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

  public String select(final String title, final String name, final String dv,
      Set<String> allowedValues) {
    final String ov = get(name);
    String nv = ui.readString(ov != null ? ov : dv, allowedValues, "%s", title);
    if (!eq(ov, nv)) {
      set(name, nv);
    }
    return nv;
  }

  public String password(final String username, final String password) {
    final String ov = getSecure(password);

    String user = flags.sec.getString(section, subsection, username);
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
    if (!eq(ov, nv)) {
      setSecure(password, nv);
    }
    return nv;
  }

  public String getSecure(String name) {
    return flags.sec.getString(section, subsection, name);
  }

  public void setSecure(String name, String value) {
    if (value != null) {
      flags.sec.setString(section, subsection, name, value);
    } else {
      flags.sec.unset(section, subsection, name);
    }
  }

  String getName() {
    return section;
  }

  private static boolean eq(final String a, final String b) {
    if (a == null && b == null) {
      return true;
    }
    return a != null ? a.equals(b) : false;
  }
}
