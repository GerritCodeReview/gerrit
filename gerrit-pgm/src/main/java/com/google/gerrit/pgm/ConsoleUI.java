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

package com.google.gerrit.pgm;

import static org.eclipse.jgit.util.StringUtils.equalsIgnoreCase;

import java.io.Console;
import java.lang.reflect.InvocationTargetException;

/** Console based interaction with the invoking user. */
public abstract class ConsoleUI {
  /** Get a UI instance, assuming interactive mode. */
  public static ConsoleUI getInstance() {
    return getInstance(false);
  }

  /** Get a UI instance, possibly forcing batch mode. */
  public static ConsoleUI getInstance(final boolean batchMode) {
    Console console = batchMode ? null : System.console();
    return console != null ? new Interactive(console) : new Batch();
  }

  /** Constructs an exception indicating the user aborted the operation. */
  protected static Die abort() {
    return new Die("aborted by user");
  }

  /** Obtain all values from an enumeration. */
  @SuppressWarnings("unchecked")
  protected static <T extends Enum<?>> T[] all(final T value) {
    try {
      return (T[]) value.getClass().getMethod("values").invoke(null);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (SecurityException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    }
  }

  /** @return true if this is a batch UI that has no user interaction. */
  public abstract boolean isBatch();

  /** Display a header message before a series of prompts. */
  public abstract void header(String fmt, Object... args);

  /** Request the user to answer a yes/no question. */
  public abstract boolean yesno(String fmt, Object... args);

  /** Prints a message asking the user to let us know when its safe to continue. */
  public abstract void waitForUser();

  /** Prompt the user for a string, suggesting a default, and returning choice. */
  public final String readString(String def, String fmt, Object... args) {
    if (def != null && def.isEmpty()) {
      def = null;
    }
    return readStringImpl(def, fmt, args);
  }

  /** Prompt the user for a string, suggesting a default, and returning choice. */
  protected abstract String readStringImpl(String def, String fmt,
      Object... args);

  /** Prompt the user for a password, returning the string; null if blank. */
  public abstract String password(String fmt, Object... args);

  /** Prompt the user to make a choice from an enumeration's values. */
  public abstract <T extends Enum<?>> T readEnum(T def, String fmt,
      Object... args);


  private static class Interactive extends ConsoleUI {
    private final Console console;

    Interactive(final Console console) {
      this.console = console;
    }

    @Override
    public boolean isBatch() {
      return false;
    }

    @Override
    public boolean yesno(String fmt, Object... args) {
      final String prompt = String.format(fmt, args);
      for (;;) {
        final String yn = console.readLine("%-30s [y/n]? ", prompt);
        if (yn == null) {
          throw abort();
        }
        if (yn.equalsIgnoreCase("y") || yn.equalsIgnoreCase("yes")) {
          return true;
        }
        if (yn.equalsIgnoreCase("n") || yn.equalsIgnoreCase("no")) {
          return false;
        }
      }
    }

    @Override
    public void waitForUser() {
      if (console.readLine("Press enter to continue ") == null) {
        throw abort();
      }
    }

    @Override
    protected String readStringImpl(String def, String fmt, Object... args) {
      final String prompt = String.format(fmt, args);
      String r;
      if (def != null) {
        r = console.readLine("%-30s [%s]: ", prompt, def);
      } else {
        r = console.readLine("%-30s : ", prompt);
      }
      if (r == null) {
        throw abort();
      }
      r = r.trim();
      if (r.isEmpty()) {
        return def;
      }
      return r;
    }

    @Override
    public String password(String fmt, Object... args) {
      final String prompt = String.format(fmt, args);
      for (;;) {
        final char[] a1 = console.readPassword("%-30s : ", prompt);
        if (a1 == null) {
          throw abort();
        }

        final char[] a2 = console.readPassword("%30s : ", "confirm password");
        if (a2 == null) {
          throw abort();
        }

        final String s1 = new String(a1);
        final String s2 = new String(a2);
        if (!s1.equals(s2)) {
          console.printf("error: Passwords did not match; try again\n");
          continue;
        }
        return !s1.isEmpty() ? s1 : null;
      }
    }

    @Override
    public <T extends Enum<?>> T readEnum(T def, String fmt, Object... args) {
      final String prompt = String.format(fmt, args);
      final T[] options = all(def);
      for (;;) {
        String r = console.readLine("%-30s [%s/?]: ", prompt, def.toString());
        if (r == null) {
          throw abort();
        }
        r = r.trim();
        if (r.isEmpty()) {
          return def;
        }
        for (final T e : options) {
          if (equalsIgnoreCase(e.toString(), r)) {
            return e;
          }
        }
        if (!"?".equals(r)) {
          console.printf("error: '%s' is not a valid choice\n", r);
        }
        console.printf("       Supported options are:\n");
        for (final T e : options) {
          console.printf("         %s\n", e.toString().toLowerCase());
        }
      }
    }

    @Override
    public void header(String fmt, Object... args) {
      fmt = fmt.replaceAll("\n", "\n*** ");
      console.printf("\n*** " + fmt + "\n*** \n\n", args);
    }
  }

  private static class Batch extends ConsoleUI {
    @Override
    public boolean isBatch() {
      return true;
    }

    @Override
    public boolean yesno(String fmt, Object... args) {
      return true;
    }

    @Override
    protected String readStringImpl(String def, String fmt, Object... args) {
      return def;
    }

    @Override
    public void waitForUser() {
    }

    @Override
    public String password(String fmt, Object... args) {
      return null;
    }

    @Override
    public <T extends Enum<?>> T readEnum(T def, String fmt, Object... args) {
      return def;
    }

    @Override
    public void header(String fmt, Object... args) {
    }
  }
}
