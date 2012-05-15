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

import com.google.inject.Key;

import org.apache.sshd.server.Command;

import java.lang.annotation.Annotation;

/** Utilities to support {@link CommandName} construction. */
public class Commands {
  /** Magic value signaling the top level. */
  public static final String ROOT = "";

  /** Magic value signaling the top level. */
  public static final CommandName CMD_ROOT = named(ROOT);

  public static Key<Command> key(final String name) {
    return key(named(name));
  }

  public static Key<Command> key(final CommandName name) {
    return Key.get(Command.class, name);
  }

  public static Key<Command> key(final CommandName parent,
      final String name) {
    return Key.get(Command.class, named(parent, name));
  }

  /** Create a CommandName annotation for the supplied name. */
  public static CommandName named(final String name) {
    return new CommandName() {
      @Override
      public String value() {
        return name;
      }

      @Override
      public Class<? extends Annotation> annotationType() {
        return CommandName.class;
      }

      @Override
      public int hashCode() {
        // This is specified in java.lang.Annotation.
        return (127 * "value".hashCode()) ^ value().hashCode();
      }

      @Override
      public boolean equals(final Object obj) {
        return obj instanceof CommandName
            && value().equals(((CommandName) obj).value());
      }

      @Override
      public String toString() {
        return "@" + CommandName.class.getName() + "(value=" + value() + ")";
      }
    };
  }

  /** Create a CommandName annotation for the supplied name. */
  public static CommandName named(final CommandName parent, final String name) {
    return new NestedCommandNameImpl(parent, name);
  }

  /** Return the name of this command, possibly including any parents. */
  public static String nameOf(final CommandName name) {
    if (name instanceof NestedCommandNameImpl) {
      return nameOf(((NestedCommandNameImpl) name).parent) + " " + name.value();
    }
    return name.value();
  }

  /** Is the second command a direct child of the first command? */
  public static boolean isChild(final CommandName parent, final CommandName name) {
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

  private static final class NestedCommandNameImpl implements CommandName {
    private final CommandName parent;
    private final String name;

    NestedCommandNameImpl(final CommandName parent, final String name) {
      this.parent = parent;
      this.name = name;
    }

    @Override
    public String value() {
      return name;
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
    public boolean equals(final Object obj) {
      return obj instanceof NestedCommandNameImpl
          && parent.equals(((NestedCommandNameImpl) obj).parent)
          && value().equals(((NestedCommandNameImpl) obj).value());
    }

    @Override
    public String toString() {
      return "CommandName[" + nameOf(this) + "]";
    }
  }

  private Commands() {
  }
}
