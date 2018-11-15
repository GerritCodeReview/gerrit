// Copyright (C) 2012 The Android Open Source Project
// Copyright 2008 Google Inc.
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

package com.google.gerrit.plugin.rebind;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import java.io.PrintWriter;

/**
 * Write the top layer in the Gadget bootstrap sandwich and generate a stub manifest that will be
 * completed by the linker.
 *
 * <p>Based on gwt-gadgets GadgetGenerator class
 */
public class PluginGenerator extends Generator {
  @Override
  public String generate(TreeLogger logger, GeneratorContext context, String typeName)
      throws UnableToCompleteException {

    // The TypeOracle knows about all types in the type system
    TypeOracle typeOracle = context.getTypeOracle();

    // Get a reference to the type that the generator should implement
    JClassType sourceType = typeOracle.findType(typeName);

    // Ensure that the requested type exists
    if (sourceType == null) {
      logger.log(TreeLogger.ERROR, "Could not find requested typeName", null);
      throw new UnableToCompleteException();
    }

    // Make sure the Gadget type is correctly defined
    validateType(logger, sourceType);

    // Pick a name for the generated class to not conflict.
    String generatedSimpleSourceName = sourceType.getSimpleSourceName() + "PluginImpl";

    // Begin writing the generated source.
    ClassSourceFileComposerFactory f =
        new ClassSourceFileComposerFactory(
            sourceType.getPackage().getName(), generatedSimpleSourceName);
    f.addImport(GWT.class.getName());
    f.setSuperclass(typeName);

    // All source gets written through this Writer
    PrintWriter out =
        context.tryCreate(logger, sourceType.getPackage().getName(), generatedSimpleSourceName);

    // If an implementation already exists, we don't need to do any work
    if (out != null) {

      // We really use a SourceWriter since it's convenient
      SourceWriter sw = f.createSourceWriter(context, out);
      sw.commit(logger);
    }

    return f.getCreatedClassName();
  }

  protected void validateType(TreeLogger logger, JClassType type) throws UnableToCompleteException {
    if (!type.isDefaultInstantiable()) {
      logger.log(TreeLogger.ERROR, "Plugin types must be default instantiable", null);
      throw new UnableToCompleteException();
    }
  }
}
