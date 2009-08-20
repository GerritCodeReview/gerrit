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
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Setter;

import java.lang.annotation.Annotation;

class CmdOption implements Option, Setter {
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
  public final Class<? extends OptionHandler> handler() {
    return OptionHandler.class;
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
  public void addValue(final Object value) throws CmdLineException {
    Short val = (Short) value;
    if (val < approvalMin || val > approvalMax) {
      throw new CmdLineException(name() + " valid values are "
          + approvalMin.toString() + ".." + approvalMax.toString());
    }

    this.value = (Short) value;
  }

  @Override
  public Class getType() {
    return Short.class;
  }

  @Override
  public boolean isMultiValued() {
    return false;
  }
}
