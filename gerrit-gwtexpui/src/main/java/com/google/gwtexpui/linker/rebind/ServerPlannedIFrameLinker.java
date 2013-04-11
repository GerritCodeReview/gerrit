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

package com.google.gwtexpui.linker.rebind;

import com.google.gwt.core.ext.LinkerContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.linker.AbstractLinker;
import com.google.gwt.core.ext.linker.ArtifactSet;
import com.google.gwt.core.ext.linker.CompilationResult;
import com.google.gwt.core.ext.linker.LinkerOrder;
import com.google.gwt.core.ext.linker.SelectionProperty;
import com.google.gwt.core.ext.linker.StylesheetReference;

import java.util.Map;
import java.util.SortedMap;

/** Saves data normally used by the {@code nocache.js} file. */
@LinkerOrder(LinkerOrder.Order.POST)
public class ServerPlannedIFrameLinker extends AbstractLinker {
  @Override
  public String getDescription() {
    return "ServerPlannedIFrameLinker";
  }

  @Override
  public ArtifactSet link(final TreeLogger logger, final LinkerContext context,
      final ArtifactSet artifacts) throws UnableToCompleteException {
    ArtifactSet toReturn = new ArtifactSet(artifacts);

    StringBuilder table = new StringBuilder();
    for (StylesheetReference r : artifacts.find(StylesheetReference.class)) {
      table.append("css ");
      table.append(r.getSrc());
      table.append("\n");
    }

    for (CompilationResult r : artifacts.find(CompilationResult.class)) {
      table.append(r.getStrongName() + "\n");
      for (SortedMap<SelectionProperty, String> p : r.getPropertyMap()) {
        for (Map.Entry<SelectionProperty, String> e : p.entrySet()) {
          table.append("  ");
          table.append(e.getKey().getName());
          table.append("=");
          table.append(e.getValue());
          table.append('\n');
        }
      }
      table.append("\n");
    }

    toReturn.add(emitString(logger, table.toString(), "permutations"));
    return toReturn;
  }
}
