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

package com.google.gerrit.server;

import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import eu.medsea.mimeutil.MimeException;
import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.lib.RepositoryConfig;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileTypeRegistry {
  private static final String KEY_SAFE = "safe";
  private static final String SECTION_MIMETYPE = "mimetype";
  private static final Logger log =
      LoggerFactory.getLogger(FileTypeRegistry.class);
  private static final FileTypeRegistry INSTANCE = new FileTypeRegistry();

  /** Get the global registry. */
  public static FileTypeRegistry getInstance() {
    return INSTANCE;
  }

  private MimeUtil2 mimeUtil;

  private FileTypeRegistry() {
    mimeUtil = new MimeUtil2();
    register("eu.medsea.mimeutil.detector.ExtensionMimeDetector");
    register("eu.medsea.mimeutil.detector.MagicMimeMimeDetector");
    if (isWin32()) {
      register("eu.medsea.mimeutil.detector.WindowsRegistryMimeDetector");
    }
  }

  private void register(String name) {
    mimeUtil.registerMimeDetector(name);
  }

  private static boolean isWin32() {
    final String osDotName =
        AccessController.doPrivileged(new PrivilegedAction<String>() {
          public String run() {
            return System.getProperty("os.name");
          }
        });
    return osDotName != null
        && osDotName.toLowerCase().indexOf("windows") != -1;
  }

  /**
   * Get the most specific MIME type available for a file.
   * 
   * @param path name of the file. The base name (component after the last '/')
   *        may be used to help determine the MIME type, such as by examining
   *        the extension (portion after the last '.' if present).
   * @param content the complete file content. If non-null the content may be
   *        used to guess the MIME type by examining the beginning for common
   *        file headers.
   * @return the MIME type for this content. If the MIME type is not recognized
   *         or cannot be determined, {@link MimeUtil2#UNKNOWN_MIME_TYPE} which
   *         is an alias for {@code application/octet-stream}.
   */
  public MimeType getMimeType(final String path, final byte[] content) {
    Set<MimeType> mimeTypes = new HashSet<MimeType>();
    if (content != null && content.length > 0) {
      try {
        mimeTypes.addAll(mimeUtil.getMimeTypes(content));
      } catch (MimeException e) {
        log.warn("Unable to determine MIME type from content", e);
      }
    }
    try {
      mimeTypes.addAll(mimeUtil.getMimeTypes(path));
    } catch (MimeException e) {
      log.warn("Unable to determine MIME type from path", e);
    }

    if (isUnknownType(mimeTypes)) {
      return MimeUtil2.UNKNOWN_MIME_TYPE;
    }

    final List<MimeType> types = new ArrayList<MimeType>(mimeTypes);
    Collections.sort(types, new Comparator<MimeType>() {
      @Override
      public int compare(MimeType a, MimeType b) {
        return b.getSpecificity() - a.getSpecificity();
      }
    });
    return types.get(0);
  }

  /**
   * Is this content type safe to transmit to a browser directly?
   * 
   * @param type the MIME type of the file content.
   * @return true if the Gerrit administrator wants to permit this content to be
   *         served as-is; false if the administrator does not trust this
   *         content type and wants it to be protected (typically by wrapping
   *         the data in a ZIP archive).
   */
  public boolean isSafeInline(final MimeType type) {
    if (MimeUtil2.UNKNOWN_MIME_TYPE.equals(type)) {
      // Most browsers perform content type sniffing when they get told
      // a generic content type. This is bad, so assume we cannot send
      // the file inline.
      //
      return false;
    }

    final RepositoryConfig cfg = getGerritConfig();
    if (cfg != null) {
      final boolean any = isSafe(cfg, "*/*", false);
      final boolean genericMedia = isSafe(cfg, type.getMediaType() + "/*", any);
      return isSafe(cfg, type.toString(), genericMedia);
    }

    // Assume we cannot send the content inline.
    //
    return false;
  }

  private static boolean isSafe(RepositoryConfig cfg, String type, boolean def) {
    return cfg.getBoolean(SECTION_MIMETYPE, type, KEY_SAFE, def);
  }

  private static RepositoryConfig getGerritConfig() {
    try {
      return GerritServer.getInstance().getGerritConfig();
    } catch (OrmException e) {
      log.warn("Cannot obtain GerritServer", e);
      return null;
    } catch (XsrfException e) {
      log.warn("Cannot obtain GerritServer", e);
      return null;
    }
  }

  private static boolean isUnknownType(Collection<MimeType> mimeTypes) {
    if (mimeTypes.isEmpty()) {
      return true;
    }
    return mimeTypes.size() == 1
        && mimeTypes.contains(MimeUtil2.UNKNOWN_MIME_TYPE);
  }
}
