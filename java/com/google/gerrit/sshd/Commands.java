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

package com.google.gerrit.sshd;

import com.google.auto.value.AutoAnnotation;
import com.google.inject.Key;
import java.lang.annotation.Annotation;
import org.apache.sshd.server.Command;

/** Utilities to support {@link CommandName} construction. */
public class Commands {
  /** Magic value signaling the top level. */
  public static final String ROOT = "";

  /** Magic value signaling the top level. */
  public static final CommandName CMD_ROOT = named(ROOT);

  public static Key<Command> key(String name) {
    return key(named(name));
  }

  public static Key<Command> key(CommandName name) {
    return Key.get(Command.class, name);
  }

  public static Key<Command> key(CommandName parent, String name) {
    return Key.get(Command.class, named(parent, name));
  }

  public static Key<Command> key(CommandName parent, String name, String descr) {
    return Key.get(Command.class, named(parent, name, descr));
  }

  /** Create a CommandName annotation for the supplied name. */
  @AutoAnnotation
  public static CommandName named(String value) {
    return new AutoAnnotation_Commands_named(value);
  }

  /** Create a CommandName annotation for the supplied name. */
  public static CommandName named(CommandName parent, String name) {
    return new NestedCommandNameImpl(parent, name);
  }

  /** Create a CommandName annotation for the supplied name and description. */
  public static CommandName named(CommandName parent, String name, String descr) {
    return new NestedCommandNameImpl(parent, name, descr);
  }

  /** Return the name of this command, possibly including any parents. */
  public static String nameOf(CommandName name) {
    if (name instanceof NestedCommandNameImpl) {
      return nameOf(((NestedCommandNameImpl) name).parent) + " " + name.value();
    }
    return name.value();
  }

  /** Is the second command a direct child of the first command? */
  public static boolean isChild(CommandName parent, CommandName name) {
    if (name instanceof NestedCommandNameImpl) {
      return parent.equals(((NestedCommandNameImpl) name).parent);
    }
    if (parent == CMD_ROOT) {
      return true;
    }
    return false;
  }

  static CommandName parentOf(CommandName name) {
    if (name instanceof NestedCommandNameImpl) {
      return ((NestedCommandNameImpl) name).parent;
    }
    return null;
  }

  static final class NestedCommandNameImpl implements CommandName {
    private final CommandName parent;
    private final String name;
    private final String descr;

    NestedCommandNameImpl(CommandName parent, String name) {
      this.parent = parent;
      this.name = name;
      this.descr = "";
    }

    NestedCommandNameImpl(CommandName parent, String name, String descr) {
      this.parent = parent;
      this.name = name;
      this.descr = descr;
    }

    @Override
    public String value() {
      return name;
    }

    public String descr() {
      return descr;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return CommandName.class;
    }

    @Override
    public int hashCode() {
      return parent.hashCode() * 31 + value().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof NestedCommandNameImpl
          && parent.equals(((NestedCommandNameImpl) obj).parent)
          && value().equals(((NestedCommandNameImpl) obj).value());
    }

    @Override
    public String toString() {
      return "CommandName[" + nameOf(this) + "]";
    }
  }

  private Commands() {}
}
