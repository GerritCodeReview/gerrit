// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Shorts;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.InvalidNameException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.project.RefPattern;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

public class LabelDefinitionInputParser {
  public static LabelFunction parseFunction(String functionString) throws BadRequestException {
    Optional<LabelFunction> function = LabelFunction.parse(functionString.trim());
    return function.orElseThrow(
        () -> new BadRequestException("unknown function: " + functionString));
  }

  public static List<LabelValue> parseValues(Map<String, String> values)
      throws BadRequestException {
    List<LabelValue> valueList = new ArrayList<>();
    Set<Short> allValues = new HashSet<>();
    for (Entry<String, String> e : values.entrySet()) {
      short value;
      try {
        value = Shorts.checkedCast(PermissionRule.parseInt(e.getKey().trim()));
      } catch (NumberFormatException ex) {
        throw new BadRequestException("invalid value: " + e.getKey(), ex);
      }
      if (!allValues.add(value)) {
        throw new BadRequestException("duplicate value: " + value);
      }
      String valueDescription = e.getValue().trim();
      if (valueDescription.isEmpty()) {
        throw new BadRequestException("description for value '" + e.getKey() + "' cannot be empty");
      }
      valueList.add(LabelValue.create(value, valueDescription));
    }
    return valueList;
  }

  public static short parseDefaultValue(LabelType.Builder labelType, short defaultValue)
      throws BadRequestException {
    if (!labelType.getValues().stream().anyMatch(v -> v.getValue() == defaultValue)) {
      throw new BadRequestException("invalid default value: " + defaultValue);
    }
    return defaultValue;
  }

  public static ImmutableList<String> parseBranches(List<String> branches)
      throws BadRequestException {
    ImmutableList.Builder<String> validBranches = ImmutableList.builder();
    for (String branch : branches) {
      String newBranch = branch.trim();
      if (newBranch.isEmpty()) {
        continue;
      }
      if (!RefPattern.isRE(newBranch) && !newBranch.startsWith(RefNames.REFS)) {
        newBranch = RefNames.REFS_HEADS + newBranch;
      }
      try {
        RefPattern.validate(newBranch);
      } catch (InvalidNameException e) {
        throw new BadRequestException("invalid branch: " + branch, e);
      }
      validBranches.add(newBranch);
    }
    return validBranches.build();
  }

  private LabelDefinitionInputParser() {}
}
