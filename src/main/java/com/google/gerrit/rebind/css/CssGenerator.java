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

package com.google.gerrit.rebind.css;

import com.google.gerrit.client.css.CssReference;
import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JArrayType;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JPackage;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.dev.util.Util;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

public class CssGenerator extends Generator {
  private static final String LDR_SUFFIX = "_Loader";

  @Override
  public String generate(TreeLogger logger, final GeneratorContext ctx,
      final String requestedClass) throws UnableToCompleteException {
    logger =
        logger.branch(TreeLogger.DEBUG,
            "Generating client proxy for css loading interface '"
                + requestedClass + "'", null);

    final TypeOracle typeOracle = ctx.getTypeOracle();
    final JClassType ldrInf = typeOracle.findType(requestedClass);
    if (ldrInf == null) {
      logger.log(TreeLogger.ERROR, "Unable to find metadata for type '"
          + requestedClass + "'", null);
      throw new UnableToCompleteException();
    }

    if (ldrInf.isInterface() == null) {
      logger.log(TreeLogger.ERROR, ldrInf.getQualifiedSourceName()
          + " is not an interface", null);
      throw new UnableToCompleteException();
    }

    final SourceWriter w = getSourceWriter(logger, ctx, ldrInf);
    if (w != null) {
      try {
        writeClass(logger, ctx, w, ldrInf);
      } catch (Exception e) {
        logger.log(TreeLogger.ERROR, "Cannot create '"
            + ldrInf.getQualifiedSourceName() + "'", e);
        throw new UnableToCompleteException();
      }
    }
    return getProxyQualifiedName(ldrInf);
  }

  private void writeClass(TreeLogger logger, final GeneratorContext ctx,
      final SourceWriter w, final JClassType ldrInf) throws IOException,
      UnableToCompleteException {
    final String srcname =
        ldrInf.getQualifiedSourceName().replace('.', '/') + ".css";

    final InputStream in =
        getClass().getClassLoader().getResourceAsStream(srcname);
    if (in == null) {
      throw new FileNotFoundException(srcname);
    }

    final ByteArrayOutputStream tmp = new ByteArrayOutputStream();
    try {
      final byte[] buf = new byte[2048];
      int n;
      while ((n = in.read(buf)) >= 0) {
        tmp.write(buf, 0, n);
      }
      tmp.close();
    } finally {
      in.close();
    }
    final byte[] rawcss = tmp.toByteArray();
    final String outname = Util.computeStrongName(rawcss) + ".cache.css";

    final OutputStream out = ctx.tryCreateResource(logger, outname);
    if (out != null) {
      out.write(rawcss);
      ctx.commitResource(logger, out);
    }

    w.println("public CssReference load() {");
    w.indent();
    w.println("return new CssReference(\"" + escape(outname) + "\");");
    w.outdent();
    w.println("}");
    w.commit(logger);
  }

  private static SourceWriter getSourceWriter(final TreeLogger logger,
      final GeneratorContext ctx, final JClassType ldrInf) {
    final JPackage servicePkg = ldrInf.getPackage();
    final String pkgn = servicePkg == null ? "" : servicePkg.getName();
    final PrintWriter pw;
    final ClassSourceFileComposerFactory cf;

    pw = ctx.tryCreate(logger, pkgn, getProxySimpleName(ldrInf));
    if (pw == null) {
      return null;
    }

    cf = new ClassSourceFileComposerFactory(pkgn, getProxySimpleName(ldrInf));
    cf.addImport(CssReference.class.getName());
    cf.addImplementedInterface(ldrInf.getErasedType().getQualifiedSourceName());
    return cf.createSourceWriter(ctx, pw);
  }

  private static String getProxyQualifiedName(final JClassType ldrInf) {
    final String[] name = synthesizeTopLevelClassName(ldrInf, LDR_SUFFIX);
    return name[0].length() == 0 ? name[1] : name[0] + "." + name[1];
  }

  private static String getProxySimpleName(final JClassType ldrInf) {
    return synthesizeTopLevelClassName(ldrInf, LDR_SUFFIX)[1];
  }

  private static String[] synthesizeTopLevelClassName(JClassType type,
      String suffix) {
    // Gets the basic name of the type. If it's a nested type, the type name
    // will contains dots.
    //
    String className;
    String packageName;

    JType leafType = type.getLeafType();
    if (leafType.isPrimitive() != null) {
      className = leafType.getSimpleSourceName();
      packageName = "";
    } else {
      JClassType classOrInterface = leafType.isClassOrInterface();
      assert (classOrInterface != null);
      className = classOrInterface.getName();
      packageName = classOrInterface.getPackage().getName();
    }

    JArrayType isArray = type.isArray();
    if (isArray != null) {
      className += "_Array_Rank_" + isArray.getRank();
    }

    // Add the meaningful suffix.
    //
    className += suffix;

    // Make it a top-level name.
    //
    className = className.replace('.', '_');

    return new String[] {packageName, className};
  }
}
