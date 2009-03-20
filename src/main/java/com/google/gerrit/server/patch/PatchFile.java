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

package com.google.gerrit.server.patch;

import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchContent;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.CorruptEntityException;
import com.google.gerrit.client.rpc.NoDifferencesException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gerrit.git.RepositoryCache;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.lib.AbbreviatedObjectId;
import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectLoader;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.patch.CombinedFileHeader;
import org.spearce.jgit.patch.FileHeader;
import org.spearce.jgit.patch.FormatError;
import org.spearce.jgit.util.IntList;
import org.spearce.jgit.util.RawParseUtils;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** State supporting processing of a single {@link Patch} instance. */
public class PatchFile {
  private static final byte[] EMPTY_FILE = {};

  private final Repository repo;
  private final ReviewDb db;
  private final Patch patch;
  private List<PatchSet.Id> requestedVersions;

  private String patchContent;
  private FileHeader fileHeader;
  private byte[][] fileContents;
  private IntList[] fileLines;

  public PatchFile(final RepositoryCache rc, final Change chg,
      final ReviewDb db, final Patch patch) throws InvalidRepositoryException {
    this(openRepository(rc, chg), db, patch);
  }

  private static Repository openRepository(final RepositoryCache rc,
      final Change chg) throws InvalidRepositoryException {
    return rc.get(chg.getDest().getParentKey().get());
  }

  public PatchFile(final Repository repo, final ReviewDb db, final Patch patch) {
    this.repo = repo;
    this.db = db;
    this.patch = patch;
  }

  void setRequestedVersions(final List<PatchSet.Id> rv) {
    if (fileHeader != null) {
      throw new IllegalStateException("setRequestedVersions before fileHeader");
    }
    requestedVersions = rv;
  }

  /** @return the source repository where the full data is stored. */
  public Repository getRepository() {
    return repo;
  }

  /** @return the raw patch represented by the delta. */
  public String getPatchContent() throws CorruptEntityException, OrmException {
    if (patchContent == null) {
      final PatchContent.Key key = patch.getContent();
      if (key == null) {
        throw new CorruptEntityException(patch.getKey());
      }

      final PatchContent pc = db.patchContents().get(key);
      if (pc == null || pc.getContent() == null) {
        throw new CorruptEntityException(patch.getKey());
      }

      patchContent = pc.getContent();
    }
    return patchContent;
  }

  /**
   * @return the parsed patch header, with its hunk information.
   * @throws NoDifferencesException
   * @throws IOException
   * @throws NoSuchEntityException
   */
  public FileHeader getFileHeader() throws CorruptEntityException,
      OrmException, NoSuchEntityException, IOException, NoDifferencesException {
    if (fileHeader == null) {
      if (requestedVersions == null) {
        fileHeader = parseCached();
      } else {
        fileHeader = parseExecute();
      }
    }
    return fileHeader;
  }


  /**
   * @return the total number of files/sides in this patch.
   * @throws NoDifferencesException
   * @throws IOException
   * @throws NoSuchEntityException
   */
  public int getFileCount() throws CorruptEntityException, OrmException,
      NoSuchEntityException, IOException, NoDifferencesException {
    final FileHeader fh = getFileHeader();
    if (fh instanceof CombinedFileHeader) {
      return ((CombinedFileHeader) fh).getParentCount() + 1;
    }
    return 2;
  }

  /**
   * Get the raw file content of a single side of the patch.
   * 
   * @param file file number; 0..{@link #getFileCount()}-1.
   * @return the raw binary content of the new file.
   * @throws CorruptEntityException the patch cannot be read.
   * @throws OrmException the patch cannot be read.
   * @throws IOException the patch or complete file content cannot be read.
   * @throws NoDifferencesException
   * @throws NoSuchEntityException
   */
  public byte[] getFileContent(final int file) throws CorruptEntityException,
      OrmException, IOException, NoSuchEntityException, NoDifferencesException {
    if (fileContents == null) {
      fileContents = new byte[getFileCount()][];
    }
    if (fileContents[file] == null) {
      final FileHeader fh = getFileHeader();
      if (file == fileContents.length - 1) {
        // Request for the last file is always the new image.
        //
        if (fh.getNewId() == null || !fh.getNewId().isComplete()) {
          throw new CorruptEntityException(patch.getKey());
        }
        fileContents[file] = read(fh.getNewId().toObjectId());

      } else {
        // All other file ids are some sort of old image.
        //
        if (fh instanceof CombinedFileHeader) {
          final CombinedFileHeader ch = (CombinedFileHeader) fh;
          final AbbreviatedObjectId old = ch.getOldId(file);
          if (old == null || !old.isComplete()) {
            throw new CorruptEntityException(patch.getKey());
          }
          fileContents[file] = read(old.toObjectId());

        } else {
          if (fh.getOldId() == null || !fh.getOldId().isComplete()) {
            throw new CorruptEntityException(patch.getKey());
          }
          fileContents[file] = read(fh.getOldId().toObjectId());
        }
      }
    }
    return fileContents[file];
  }

  /**
   * Get the table of line numbers to byte positions in the file content.
   * 
   * @param file file number; 0..{@link #getFileCount()}-1.
   * @return the raw binary content of the new file.
   * @throws CorruptEntityException the patch cannot be read.
   * @throws OrmException the patch cannot be read.
   * @throws IOException the patch or complete file content cannot be read.
   * @throws NoDifferencesException
   * @throws NoSuchEntityException
   */
  public IntList getLineMap(final int file) throws CorruptEntityException,
      OrmException, IOException, NoSuchEntityException, NoDifferencesException {
    if (fileLines == null) {
      fileLines = new IntList[getFileCount()];
    }
    if (fileLines[file] == null) {
      final byte[] c = getFileContent(file);
      fileLines[file] = RawParseUtils.lineMap(c, 0, c.length);
    }
    return fileLines[file];
  }

  /**
   * Get the number of lines in the file.
   * 
   * @param file the file to examine
   * @return total number of lines in the file
   * @throws CorruptEntityException the patch cannot be read.
   * @throws OrmException the patch cannot be read.
   * @throws IOException the patch or complete file content cannot be read.
   * @throws NoDifferencesException
   * @throws NoSuchEntityException
   */
  public int getLineCount(final int file) throws CorruptEntityException,
      OrmException, IOException, NoSuchEntityException, NoDifferencesException {
    final byte[] c = getFileContent(file);
    final IntList m = getLineMap(file);
    final int n = m.size();
    if (n > 0 && m.get(n - 1) == c.length) {
      return n - 1;
    }
    return n;
  }

  /**
   * Extract a line from the file, as a string.
   * 
   * @param file the file index to extract.
   * @param line the line number to extract (1 based; 1 is the first line).
   * @return the string version of the file line.
   * @throws CorruptEntityException the patch cannot be read.
   * @throws OrmException the patch cannot be read.
   * @throws IOException the patch or complete file content cannot be read.
   * @throws NoDifferencesException
   * @throws NoSuchEntityException
   * @throws CharacterCodingException the file is not a known character set.
   */
  public String getLine(final int file, final int line)
      throws CorruptEntityException, OrmException, IOException,
      NoSuchEntityException, NoDifferencesException {
    final byte[] c = getFileContent(file);
    final IntList m = getLineMap(file);
    final int b = m.get(line);
    int e = m.get(line + 1);
    if (b <= e - 1 && e - 1 < c.length && c[e - 1] == '\n') {
      e--;
    }
    return RawParseUtils.decodeNoFallback(Constants.CHARSET, c, b, e);
  }

  private byte[] read(final AnyObjectId id) throws CorruptEntityException,
      IOException {
    if (id == null || ObjectId.zeroId().equals(id)) {
      return EMPTY_FILE;
    }

    final ObjectLoader ldr = repo.openObject(id);
    if (ldr == null) {
      throw new CorruptEntityException(patch.getKey());
    }

    final byte[] content = ldr.getCachedBytes();
    if (ldr.getType() != Constants.OBJ_BLOB) {
      throw new IncorrectObjectTypeException(id.toObjectId(),
          Constants.TYPE_BLOB);
    }
    return content;
  }

  private FileHeader parseCached() throws CorruptEntityException, OrmException {
    final byte[] buf = Constants.encode(getPatchContent());
    final org.spearce.jgit.patch.Patch p = new org.spearce.jgit.patch.Patch();
    p.parse(buf, 0, buf.length);
    for (final FormatError err : p.getErrors()) {
      if (err.getSeverity() == FormatError.Severity.ERROR) {
        throw new CorruptEntityException(patch.getKey());
      }
    }
    if (p.getFiles().size() != 1) {
      throw new CorruptEntityException(patch.getKey());
    }
    return p.getFiles().get(0);
  }

  private FileHeader parseExecute() throws NoSuchEntityException, OrmException,
      IOException, NoDifferencesException {
    // TODO Fix this gross hack so we aren't running git diff-tree for
    // an interdiff file side by side view.
    //
    final PatchSet.Id psk = patch.getKey().getParentKey();
    final Map<PatchSet.Id, PatchSet> psMap =
        db.patchSets().toMap(db.patchSets().byChange(psk.getParentKey()));
    final List<String> args = new ArrayList<String>();
    args.add("git");
    args.add("--git-dir=.");
    args.add("diff-tree");
    args.add("--full-index");
    if (requestedVersions.size() > 2) {
      args.add("--cc");
    } else {
      args.add("--unified=5");
    }
    for (int i = 0; i < requestedVersions.size(); i++) {
      final PatchSet.Id psi = requestedVersions.get(i);
      if (psi == null) {
        throw new NoSuchEntityException();

      } else if (psi.equals(PatchSet.BASE)) {
        final PatchSet p = psMap.get(psk);
        if (p == null || p.getRevision() == null
            || p.getRevision().get() == null
            || !ObjectId.isId(p.getRevision().get())) {
          throw new NoSuchEntityException();
        }
        args.add(p.getRevision().get() + "^" + (i + 1));

      } else {
        final PatchSet p = psMap.get(psi);
        if (p == null || p.getRevision() == null
            || p.getRevision().get() == null
            || !ObjectId.isId(p.getRevision().get())) {
          throw new NoSuchEntityException();
        }
        args.add(p.getRevision().get());
      }
    }
    args.add("--");
    args.add(patch.getFileName());

    final Process proc =
        Runtime.getRuntime().exec(args.toArray(new String[args.size()]), null,
            repo.getDirectory());
    try {
      final org.spearce.jgit.patch.Patch p = new org.spearce.jgit.patch.Patch();
      proc.getOutputStream().close();
      proc.getErrorStream().close();
      p.parse(proc.getInputStream());
      proc.getInputStream().close();
      if (p.getFiles().isEmpty()) {
        throw new NoDifferencesException();
      }
      if (p.getFiles().size() != 1)
        throw new IOException("unexpected file count back");
      return p.getFiles().get(0);
    } finally {
      try {
        if (proc.waitFor() != 0) {
          throw new IOException("git diff-tree exited abnormally");
        }
      } catch (InterruptedException ie) {
      }
    }
  }
}
