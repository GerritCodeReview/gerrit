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

package com.google.gerrit.server.ssh.commands;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Setter;

import java.lang.annotation.Annotation;

class CmdOption implements Option, Setter<Short> {
  private String metaVar;
  private boolean multiValued;
  private String name;
  private boolean required;
  private String usage;

  private String approvalKey;
  private Short approvalMax;
  private Short approvalMin;
  private String descrName;

  private Short value;

  public CmdOption(final String name, final String usage, final String key,
      final Short min, final Short max, final String descrName) {
    this.name = name;
    this.usage = usage;

    this.metaVar = "";
    this.multiValued = false;
    this.required = false;
    this.value = null;

    this.approvalKey = key;
    this.approvalMax = max;
    this.approvalMin = min;
    this.descrName = descrName;
  }

  @Override
  public final String[] aliases() {
    return new String[0];
  }

  @Override
  public final Class<? extends OptionHandler<Short>> handler() {
    return Handler.class;
  }

  @Override
  public final String metaVar() {
    return metaVar;
  }

  @Override
  public final boolean multiValued() {
    return multiValued;
  }

  @Override
  public final String name() {
    return name;
  }

  @Override
  public final boolean required() {
    return required;
  }

  @Override
  public final String usage() {
    return usage;
  }

  public final Short value() {
    return value;
  }

  public final String approvalKey() {
    return approvalKey;
  }

  public final Short approvalMax() {
    return approvalMax;
  }

  public final Short approvalMin() {
    return approvalMin;
  }

  public final String descrName() {
    return descrName;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return null;
  }

  @Override
  public void addValue(final Short val) {
    this.value = val;
  }

  @Override
  public Class<Short> getType() {
    return Short.class;
  }

  @Override
  public boolean isMultiValued() {
    return false;
  }

  public static class Handler extends OneArgumentOptionHandler<Short> {
    private final CmdOption cmdOption;

    public Handler(final CmdLineParser parser, final OptionDef option,
        final Setter<Short> setter) {
      super(parser, option, setter);
      this.cmdOption = (CmdOption) setter;
    }

    @Override
    protected Short parse(final String token) throws NumberFormatException,
        CmdLineException {
      String argument = token;
      if (argument.startsWith("+")) {
        argument = argument.substring(1);
      }

      final short value = Short.parseShort(argument);
      final short min = cmdOption.approvalMin;
      final short max = cmdOption.approvalMax;

      if (value < min || value > max) {
        final String name = cmdOption.name();
        final String e =
            "\"" + token + "\" must be in range " + format(min) + ".."
                + format(max) + " for \"" + name + "\"";
        throw new CmdLineException(owner, e);
      }
      return value;
    }
  }

  static String format(final short min) {
    return min > 0 ? "+" + min : Short.toString(min);
  }
}
