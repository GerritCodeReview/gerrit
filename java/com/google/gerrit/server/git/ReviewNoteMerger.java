/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com> and other copyright
 * owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.gerrit.server.git;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMerger;
import org.eclipse.jgit.util.io.UnionInputStream;

class ReviewNoteMerger implements NoteMerger {
  @Override
  public Note merge(Note base, Note ours, Note theirs, ObjectReader reader, ObjectInserter inserter)
      throws IOException {
    if (ours == null) {
      return theirs;
    }
    if (theirs == null) {
      return ours;
    }
    if (ours.getData().equals(theirs.getData())) {
      return ours;
    }

    ObjectLoader lo = reader.open(ours.getData());
    byte[] sep = new byte[] {'\n'};
    ObjectLoader lt = reader.open(theirs.getData());
    try (ObjectStream os = lo.openStream();
        ByteArrayInputStream b = new ByteArrayInputStream(sep);
        ObjectStream ts = lt.openStream();
        UnionInputStream union = new UnionInputStream(os, b, ts)) {
      ObjectId noteData =
          inserter.insert(Constants.OBJ_BLOB, lo.getSize() + sep.length + lt.getSize(), union);
      return new Note(ours, noteData);
    }
  }
}
