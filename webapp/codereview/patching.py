# Copyright 2008 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Utility to read and apply a unified diff without forking patch(1).

For a discussion of the unified diff format, see my blog on Artima:
http://www.artima.com/weblogs/viewpost.jsp?thread=164293
"""

import difflib
import logging
import re
import sys


_CHUNK_RE = re.compile(r"""
  @@
  \s+
  -
  (?: (\d+) (?: , (\d+) )?)
  \s+
  \+
  (?: (\d+) (?: , (\d+) )?)
  \s+
  @@
""", re.VERBOSE)


def PatchLines(old_lines, patch_lines, name="<patch>"):
  """Patches the old_lines with patches read from patch_lines.

  This only reads unified diffs.  The header lines are ignored.
  Yields (tag, old, new) tuples where old and new are lists of lines.
  The tag can either start with "error" or be a tag from difflib: "equal",
  "insert", "delete", "replace".  After "error" is yielded, no more
  tuples are yielded.  It is possible that consecutive "equal" tuples
  are yielded.
  """
  chunks = ParsePatchToChunks(patch_lines, name)
  if chunks is None:
    return iter([("error: ParsePatchToChunks failed", [], [])])
  return PatchChunks(old_lines, chunks)


def PatchChunks(old_lines, chunks):
  """Patche old_lines with chunks.

  Yields (tag, old, new) tuples where old and new are lists of lines.
  The tag can either start with "error" or be a tag from difflib: "equal",
  "insert", "delete", "replace".  After "error" is yielded, no more
  tuples are yielded.  It is possible that consecutive "equal" tuples
  are yielded.
  """
  if not chunks:
    # The patch is a no-op
    yield ("equal", old_lines, old_lines)
    return

  old_pos = 0
  for (old_i, old_j), (new_i, new_j), old_chunk, new_chunk in chunks:
    eq = old_lines[old_pos:old_i]
    if eq:
      yield "equal", eq, eq
    old_pos = old_i
    # Check that the patch matches the target file
    if old_lines[old_i:old_j] != old_chunk:
      logging.error("mismatch:%s.%s.", old_lines[old_i:old_j], old_chunk)
      yield ("error: old chunk mismatch", old_lines[old_i:old_j], old_chunk)
      return
    # TODO(guido): ParsePatch knows the diff details, but throws the info away
    sm = difflib.SequenceMatcher(None, old_chunk, new_chunk)
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
      yield tag, old_chunk[i1:i2], new_chunk[j1:j2]
    old_pos = old_j

  # Copy the final matching chunk if any.
  eq = old_lines[old_pos:]
  if eq:
    yield ("equal", eq, eq)


_NO_NEWLINE_MESSAGE = "\\ No newline at end of file"


def ParsePatchToChunks(lines, name="<patch>"):
  """Parses a patch from a list of lines.

  Return a list of chunks, where each chunk is a tuple:

    old_range, new_range, old_lines, new_lines

  Returns a list of chunks (possibly empty); or None if there's a problem.
  """
  lineno = 0
  raw_chunk = []
  chunks = []
  old_range = new_range = None
  old_last = new_last = 0
  in_prelude = True
  for line in lines:
    lineno += 1
    if in_prelude:
      # Skip leading lines until after we've seen one starting with '+++'
      if line.startswith("+++"):
        in_prelude = False
      continue
    match = _CHUNK_RE.match(line)
    if match:
      if raw_chunk:
        # Process the lines in the previous chunk
        old_chunk = []
        new_chunk = []
        for tag, rest in raw_chunk:
          if tag in (" ", "-"):
            old_chunk.append(rest)
          if tag in (" ", "+"):
            new_chunk.append(rest)
        # Check consistency
        old_i, old_j = old_range
        new_i, new_j = new_range
        if len(old_chunk) != old_j - old_i or len(new_chunk) != new_j - new_i:
          logging.warn("%s:%s: previous chunk has incorrect length",
                       name, lineno)
          return None
        chunks.append((old_range, new_range, old_chunk, new_chunk))
        raw_chunk = []
      # Parse the @@ header
      old_ln, old_n, new_ln, new_n = match.groups()
      old_ln, old_n, new_ln, new_n = map(long,
                                         (old_ln, old_n or 1,
                                          new_ln, new_n or 1))
      # Convert the numbers to list indices we can use
      if old_n == 0:
        old_i = old_ln
      else:
        old_i = old_ln - 1
      old_j = old_i + old_n
      old_range = old_i, old_j
      if new_n == 0:
        new_i = new_ln
      else:
        new_i = new_ln - 1
      new_j =new_i + new_n
      new_range = new_i, new_j
      # Check header consistency with previous header
      if old_i < old_last or new_i < new_last:
        logging.warn("%s:%s: chunk header out of order: %r",
                     name, lineno, line)
        return None
      if old_i - old_last != new_i - new_last:
        logging.warn("%s:%s: inconsistent chunk header: %r",
                     name, lineno, line)
        return None
      old_last = old_j
      new_last = new_j
    else:
      tag, rest = line[0], line[1:]
      if tag in (" ", "-", "+"):
        raw_chunk.append((tag, rest))
      elif line.startswith(_NO_NEWLINE_MESSAGE):
        # TODO(guido): need to check that no more lines follow for this file
        if raw_chunk:
          last_tag, last_rest = raw_chunk[-1]
          if last_rest.endswith("\n"):
            raw_chunk[-1] = (last_tag, last_rest[:-1])
      else:
        # Only log if it's a non-blank line.  Blank lines we see a lot.
        if line and line.strip():
          logging.warn("%s:%d: indecypherable input: %r", name, lineno, line)
        if chunks or raw_chunk:
          break  # Trailing garbage isn't so bad
        return None
  if raw_chunk:
    # Process the lines in the last chunk
    old_chunk = []
    new_chunk = []
    for tag, rest in raw_chunk:
      if tag in (" ", "-"):
        old_chunk.append(rest)
      if tag in (" ", "+"):
        new_chunk.append(rest)
    # Check consistency
    old_i, old_j = old_range
    new_i, new_j = new_range
    if len(old_chunk) != old_j - old_i or len(new_chunk) != new_j - new_i:
      print >>sys.stderr, ("%s:%s: last chunk has incorrect length" %
                           (name, lineno))
      return None
    chunks.append((old_range, new_range, old_chunk, new_chunk))
    raw_chunk = []
  return chunks


# TODO: can we share some of this code with ParsePatchToChunks?
def ParsePatchToLines(lines):
  """Parses a patch from a list of lines.

  Returns None on error, otherwise a list of 3-tuples:
    (old_line_no, new_line_no, line)

    A line number can be 0 if it doesn't exist in the old/new file.
  """
  result = []
  in_prelude = True
  for line in lines:
    if in_prelude:
      result.append((0, 0, line))
      # Skip leading lines until after we've seen one starting with '+++'
      if line.startswith("+++"):
        in_prelude = False
    elif line.startswith("@"):
      result.append((0, 0, line))
      match = _CHUNK_RE.match(line)
      if not match:
        logging.warn("ParsePatchToLines match failed on %s", line)
        return None
      old_ln = int(match.groups()[0])
      new_ln = int(match.groups()[2])
    else:
      if line[0] == "-":
        result.append((old_ln, 0, line))
        old_ln += 1
      elif line[0] == "+":
        result.append((0, new_ln, line))
        new_ln += 1
      elif line[0] == " ":
        result.append((old_ln, new_ln, line))
        old_ln += 1
        new_ln += 1
      elif line.startswith(_NO_NEWLINE_MESSAGE):
        continue
      else:  # Something else, could be property changes etc.
        result.append((0, 0, line))
  return result
