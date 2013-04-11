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

package com.google.gwtexpui.css.rebind;

import com.google.gwt.core.ext.LinkerContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.linker.AbstractLinker;
import com.google.gwt.core.ext.linker.Artifact;
import com.google.gwt.core.ext.linker.ArtifactSet;
import com.google.gwt.core.ext.linker.LinkerOrder;
import com.google.gwt.core.ext.linker.PublicResource;
import com.google.gwt.core.ext.linker.impl.StandardLinkerContext;
import com.google.gwt.core.ext.linker.impl.StandardStylesheetReference;
import com.google.gwt.dev.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

@LinkerOrder(LinkerOrder.Order.PRE)
public class CssLinker extends AbstractLinker {
  @Override
  public String getDescription() {
    return "CssLinker";
  }

  @Override
  public ArtifactSet link(final TreeLogger logger, final LinkerContext context,
      final ArtifactSet artifacts) throws UnableToCompleteException {
    final ArtifactSet returnTo = new ArtifactSet();
    int index = 0;

    final HashMap<String, PublicResource> css =
        new HashMap<String, PublicResource>();

    for (final StandardStylesheetReference ssr : artifacts
        .<StandardStylesheetReference> find(StandardStylesheetReference.class)) {
      css.put(ssr.getSrc(), null);
    }
    for (final PublicResource pr : artifacts
        .<PublicResource> find(PublicResource.class)) {
      if (css.containsKey(pr.getPartialPath())) {
        css.put(pr.getPartialPath(), new CssPubRsrc(name(logger, pr), pr));
      }
    }

    for (Artifact<?> a : artifacts) {
      if (a instanceof PublicResource) {
        final PublicResource r = (PublicResource) a;
        if (css.containsKey(r.getPartialPath())) {
          a = css.get(r.getPartialPath());
        }
      } else if (a instanceof StandardStylesheetReference) {
        final StandardStylesheetReference r = (StandardStylesheetReference) a;
        final PublicResource p = css.get(r.getSrc());
        a = new StandardStylesheetReference(p.getPartialPath(), index);
      }

      returnTo.add(a);
      index++;
    }
    return returnTo;
  }

  private String name(final TreeLogger logger, final PublicResource r)
      throws UnableToCompleteException {
    final InputStream in = r.getContents(logger);
    final ByteArrayOutputStream tmp = new ByteArrayOutputStream();
    try {
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
    } catch (IOException e) {
      final UnableToCompleteException ute = new UnableToCompleteException();
      ute.initCause(e);
      throw ute;
    }

    String base = r.getPartialPath();
    final int s = base.lastIndexOf('/');
    if (0 < s) {
      base = base.substring(0, s + 1);
    } else {
      base = "";
    }
    return base + Util.computeStrongName(tmp.toByteArray()) + ".cache.css";
  }

  private static class CssPubRsrc extends PublicResource {
    private static final long serialVersionUID = 1L;
    private final PublicResource src;

    CssPubRsrc(final String partialPath, final PublicResource r) {
      super(StandardLinkerContext.class, partialPath);
      src = r;
    }

    @Override
    public InputStream getContents(final TreeLogger logger)
        throws UnableToCompleteException {
      return src.getContents(logger);
    }

    @Override
    public long getLastModified() {
      return src.getLastModified();
    }
  }
}
