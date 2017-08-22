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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.FieldSetter;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Setter;

final class ApproveOption implements Option, Setter<Short> {
  private final String name;
  private final String usage;
  private final LabelType type;

  private Short value;

  ApproveOption(String name, String usage, LabelType type) {
    this.name = name;
    this.usage = usage;
    this.type = type;
  }

  @Override
  public String[] aliases() {
    return new String[0];
  }

  @Override
  public String[] depends() {
    return new String[] {};
  }

  @Override
  public boolean hidden() {
    return false;
  }

  @Override
  public Class<? extends OptionHandler<Short>> handler() {
    return Handler.class;
  }

  @Override
  public String metaVar() {
    return "N";
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public boolean required() {
    return false;
  }

  @Override
  public String usage() {
    return usage;
  }

  public Short value() {
    return value;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return null;
  }

  @Override
  public FieldSetter asFieldSetter() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AnnotatedElement asAnnotatedElement() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addValue(Short val) {
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

  String getLabelName() {
    return type.getName();
  }

  public static class Handler extends OneArgumentOptionHandler<Short> {
    private final ApproveOption cmdOption;

    // CS IGNORE RedundantModifier FOR NEXT 1 LINES. REASON: needed by org.kohsuke.args4j.Option
    public Handler(CmdLineParser parser, OptionDef option, Setter<Short> setter) {
      super(parser, option, setter);
      this.cmdOption = (ApproveOption) setter;
    }

    @Override
    protected Short parse(String token) throws NumberFormatException, CmdLineException {
      String argument = token;
      if (argument.startsWith("+")) {
        argument = argument.substring(1);
      }

      final short value = Short.parseShort(argument);
      final LabelValue min = cmdOption.type.getMin();
      final LabelValue max = cmdOption.type.getMax();

      if (value < min.getValue() || value > max.getValue()) {
        final String name = cmdOption.name();
        final String e =
            "\""
                + token
                + "\" must be in range "
                + min.formatValue()
                + ".."
                + max.formatValue()
                + " for \""
                + name
                + "\"";
        throw new CmdLineException(owner, e);
      }
      return value;
    }
  }
}
