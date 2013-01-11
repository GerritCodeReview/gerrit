// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.BadRequestException;

import java.util.List;

public class BadRequestHandler {

  private final List<String> errors = Lists.newLinkedList();
  private String action;

  public BadRequestHandler(final String action) {
    this.action = action;
  }

  public void addError(final String message) {
    errors.add(message);
  }

  public void addError(final Throwable t) {
    errors.add(t.getMessage());
  }

  public void failOnError()
      throws BadRequestException {
    if (errors.isEmpty()) {
      return;
    }

    if (errors.size() == 1) {
      throw new BadRequestException(action + " failed: " + errors.get(0));
    }

    final StringBuilder b = new StringBuilder();
    b.append("Multiple errors on " + action + ":");
    for (final String error : errors) {
      b.append("\n");
      b.append(error);
    }
    throw new BadRequestException(b.toString());
  }
}
