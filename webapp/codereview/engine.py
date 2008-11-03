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

"""Diff rendering in HTML for Gerrit."""

# Python imports
import re
import cgi
import difflib
import logging
import urlparse

# AppEngine imports
from google.appengine.api import urlfetch
from google.appengine.api import users
from google.appengine.ext import db

# Django imports
from django.template import loader

# Local imports
import library
import models
import patching
import intra_region_diff


# NOTE: this function is duplicated in upload.py, keep them in sync.
def SplitPatch(data):
  """Splits a patch into separate pieces for each file.

  Args:
    data: A string containing the output of svn diff.

  Returns:
    A list of 2-tuple (filename, text) where text is the svn diff output
      pertaining to filename.
  """
  patches = []
  filename = None
  diff = []
  for line in data.splitlines(True):
    new_filename = None
    if line.startswith('Index:'):
      unused, new_filename = line.split(':', 1)
      new_filename = new_filename.strip()
    elif line.startswith('Property changes on:'):
      unused, temp_filename = line.split(':', 1)
      # When a file is modified, paths use '/' between directories, however
      # when a property is modified '\' is used on Windows.  Make them the same
      # otherwise the file shows up twice.
      temp_filename = temp_filename.strip().replace('\\', '/')
      if temp_filename != filename:
        # File has property changes but no modifications, create a new diff.
        new_filename = temp_filename
    if new_filename:
      if filename and diff:
        patches.append((filename, ''.join(diff)))
      filename = new_filename
      diff = [line]
      continue
    if diff is not None:
      diff.append(line)
  if filename and diff:
    patches.append((filename, ''.join(diff)))
  return patches

def RenderDiffTableRows(request, old_lines, chunks, patch,
                        colwidth=100,
                        debug=False,
                        context=models.DEFAULT_CONTEXT):
  """Render the HTML table rows for a side-by-side diff for a patch.

  Args:
    request: Django Request object.
    old_lines: List of lines representing the original file.
    chunks: List of chunks as returned by patching.ParsePatchToChunks().
    patch: A models.Patch instance.
    colwidth: Optional column width (default 100).
    debug: Optional debugging flag (default False).
    context: Maximum number of rows surrounding a change (default CONTEXT).

  Yields:
    Strings, each of which represents the text rendering one complete
    pair of lines of the side-by-side diff, possibly including comments.
    Each yielded string may consist of several <tr> elements.
  """
  rows =  _RenderDiffTableRows(request, old_lines, chunks, patch,
                               colwidth, debug)
  return _CleanupTableRowsGenerator(rows, context)


def RenderDiff2TableRows(request, old_lines, old_patch, new_lines, new_patch,
                         colwidth=100,
                         debug=False,
                         context=models.DEFAULT_CONTEXT):
  """Render the HTML table rows for a side-by-side diff between two patches.

  Args:
    request: Django Request object.
    old_lines: List of lines representing the patched file on the left.
    old_patch: The models.Patch instance corresponding to old_lines.
    new_lines: List of lines representing the patched file on the right.
    new_patch: The models.Patch instance corresponding to new_lines.
    colwidth: Optional column width (default 100).
    debug: Optional debugging flag (default False).
    context: Maximum number of visible context lines (default models.DEFAULT_CONTEXT).

  Yields:
    Strings, each of which represents the text rendering one complete
    pair of lines of the side-by-side diff, possibly including comments.
    Each yielded string may consist of several <tr> elements.
  """
  rows = _RenderDiff2TableRows(request, old_lines, old_patch,
                               new_lines, new_patch, colwidth, debug)
  return _CleanupTableRowsGenerator(rows, context)


def _CleanupTableRowsGenerator(rows, context):
  """Cleanup rows returned by _TableRowGenerator for output.

  Args:
    rows: List of tuples (tag, text)
    context: Maximum number of visible context lines.

  Yields:
    Rows marked as 'equal' are possibly contracted using _ShortenBuffer().
    Stops on rows marked as 'error'.
  """
  buffer = []
  for tag, text in rows:
    if tag == 'equal':
      buffer.append(text)
      continue
    else:
      for t in _ShortenBuffer(buffer, context):
        yield t
      buffer = []
    yield text
    if tag == 'error':
      yield None
      break
  if buffer:
    for t in _ShortenBuffer(buffer, context):
      yield t


def _ShortenBuffer(buffer, context):
  """Render a possibly contracted series of HTML table rows.

  Args:
    buffer: a list of strings representing HTML table rows.
    context: Maximum number of visible context lines.

  Yields:
    If the buffer has fewer than 3 times context items, yield all
    the items.  Otherwise, yield the first context items, a single
    table row representing the contraction, and the last context
    items.
  """
  if len(buffer) < 3*context:
    for t in buffer:
      yield t
  else:
    last_id = None
    for t in buffer[:context]:
      m = re.match('^<tr( name="hook")? id="pair-(?P<rowcount>\d+)">', t)
      if m:
        last_id = int(m.groupdict().get("rowcount"))
      yield t
    skip = len(buffer) - 2*context
    if skip <= 10:
      expand_link = ('<a href="javascript:M_expandSkipped(%(before)d, '
                     '%(after)d, \'b\', %(skip)d)">Show</a>')
    else:
      expand_link = ('<a href="javascript:M_expandSkipped(%(before)d, '
                     '%(after)d, \'t\', %(skip)d)">Show 10 above</a> '
                     '<a href="javascript:M_expandSkipped(%(before)d, '
                     '%(after)d, \'b\', %(skip)d)">Show 10 below</a> ')
    expand_link = expand_link % {'before': last_id+1,
                                 'after': last_id+skip,
                                 'skip': last_id}
    yield ('<tr id="skip-%d"><td colspan="2" align="center" '
           'style="background:lightblue">'
           '(...skipping <span id="skipcount-%d">%d</span> matching lines...) '
           '<span id="skiplinks-%d">%s</span>'
           '</td></tr>\n' % (last_id, last_id, skip,
                             last_id, expand_link))
    for t in buffer[-context:]:
      yield t


def _RenderDiff2TableRows(request, old_lines, old_patch, new_lines, new_patch,
                         colwidth, debug=False):
  """Internal version of RenderDiff2TableRows().

  Args:
    The same as for RenderDiff2TableRows.

  Yields:
    Tuples (tag, row) where tag is an indication of the row type.
  """
  old_dict = {}
  new_dict = {}
  for patch, dct in [(old_patch, old_dict), (new_patch, new_dict)]:
    # XXX GQL doesn't support OR yet...  Otherwise we'd be using that.
    for comment in models.Comment.gql(
        'WHERE patch = :1 AND left = FALSE ORDER BY date', patch):
      if comment.draft and comment.author != request.user:
        continue  # Only show your own drafts
      comment.complete(patch)
      lst = dct.setdefault(comment.lineno, [])
      lst.append(comment)
      library.prefetch_names([comment.author])
  return _TableRowGenerator(old_patch, old_dict, len(old_lines)+1, 'new',
                            new_patch, new_dict, len(new_lines)+1, 'new',
                            _GenerateTriples(old_lines, new_lines),
                            colwidth, debug)


def _GenerateTriples(old_lines, new_lines):
  """Helper for _RenderDiff2TableRows yielding input for _TableRowGenerator.

  Args:
    old_lines: List of lines representing the patched file on the left.
    new_lines: List of lines representing the patched file on the right.

  Yields:
    Tuples (tag, old_slice, new_slice) where tag is a tag as returned by
    difflib.SequenceMatchser.get_opcodes(), and old_slice and new_slice
    are lists of lines taken from old_lines and new_lines.
  """
  sm = difflib.SequenceMatcher(None, old_lines, new_lines)
  for tag, i1, i2, j1, j2 in sm.get_opcodes():
    yield tag, old_lines[i1:i2], new_lines[j1:j2]


def _GetComments(request):
  """Helper that returns comments for a patch.

  Args:
    request: Django Request object.

  Returns:
    A 2-tuple of (old, new) where old/new are dictionaries that holds comments
      for that file, mapping from line number to a Comment entity.
  """
  old_dict = {}
  new_dict = {}
  # XXX GQL doesn't support OR yet...  Otherwise we'd be using
  # .gql('WHERE patch = :1 AND (draft = FALSE OR author = :2) ORDER BY data',
  #      patch, request.user)
  for comment in models.Comment.gql('WHERE patch = :1 ORDER BY date',
                                    request.patch):
    if comment.draft and comment.author != request.user:
      continue  # Only show your own drafts
    comment.complete(request.patch)
    if comment.left:
      dct = old_dict
    else:
      dct = new_dict
    dct.setdefault(comment.lineno, []).append(comment)
    library.prefetch_names([comment.author])
  return old_dict, new_dict


def _RenderDiffTableRows(request, old_lines, chunks, patch,
                         colwidth, debug=False):
  """Internal version of RenderDiffTableRows().

  Args:
    The same as for RenderDiffTableRows.

  Yields:
    Tuples (tag, row) where tag is an indication of the row type.
  """
  old_dict = {}
  new_dict = {}
  if patch:
    old_dict, new_dict = _GetComments(request)
  old_max, new_max = _ComputeLineCounts(old_lines, chunks)
  return _TableRowGenerator(patch, old_dict, old_max, 'old',
                            patch, new_dict, new_max, 'new',
                            patching.PatchChunks(old_lines, chunks),
                            colwidth, debug)


def _TableRowGenerator(old_patch, old_dict, old_max, old_snapshot,
                       new_patch, new_dict, new_max, new_snapshot,
                       triple_iterator, colwidth, debug=False,
                       mark_tabs=True):
  """Helper function to render side-by-side table rows.

  Args:
    old_patch: First models.Patch instance.
    old_dict: Dictionary with line numbers as keys and comments as values (left)
    old_max: Line count of the patch on the left.
    old_snapshot: A tag used in the comments form.
    new_patch: Second models.Patch instance.
    new_dict: Same as old_dict, but for the right side.
    new_max: Line count of the patch on the right.
    new_snapshot: A tag used in the comments form.
    triple_iterator: Iterator that yields (tag, old, new) triples.
    colwidth: column width (not optional)
    debug: Optional debugging flag (default False).
    mark_tabs: Optional flag to show tabs visually (default True).

  Yields:
    Tuples (tag, row) where tag is an indication of the row type and
    row is an HTML fragment representing one or more <td> elements.
  """
  diff_params = intra_region_diff.GetDiffParams(dbg=debug)
  ndigits = 1 + max(len(str(old_max)), len(str(new_max)))
  indent = 1 + ndigits
  old_offset = new_offset = 0
  row_count = 0

  # Render a row with a message if a side is empty or both sides are equal.
  if old_patch == new_patch and (old_max == 0 or new_max == 0):
    if old_max == 0:
      msg_old = '(Empty)'
    else:
      msg_old = ''
    if new_max == 0:
      msg_new = '(Empty)'
    else:
      msg_new = ''
    yield '', ('<tr><td class="info">%s</td>'
               '<td class="info">%s</td></tr>' % (msg_old, msg_new))
  # TODO(sop)
  #elif old_patch == new_patch:
  #  old_patch.patch_hash == new_patch.patch_hash
  #  yield '', ('<tr><td class="info" colspan="2">'
  #             '(Both sides are equal)</td></tr>')

  for tag, old, new in triple_iterator:
    if tag.startswith('error'):
      yield 'error', '<tr><td><h3>%s</h3></td></tr>\n' % cgi.escape(tag)
      return
    old1 = old_offset
    old_offset = old2 = old1 + len(old)
    new1 = new_offset
    new_offset = new2 = new1 + len(new)
    old_buff = []
    new_buff = []
    frag_list = []
    do_ir_diff = tag == 'replace' and intra_region_diff.CanDoIRDiff(old, new)

    for i in xrange(max(len(old), len(new))):
      row_count += 1
      old_lineno = old1 + i + 1
      new_lineno = new1 + i + 1
      old_valid = old1+i < old2
      new_valid = new1+i < new2

      # Start rendering the first row
      frags = []
      if i == 0 and tag != 'equal':
        # Mark the first row of each non-equal chunk as a 'hook'.
        frags.append('<tr name="hook"')
      else:
        frags.append('<tr')
      frags.append(' id="pair-%d">' % row_count)

      old_intra_diff = ''
      new_intra_diff = ''
      if old_valid:
        old_intra_diff = old[i]
      if new_valid:
        new_intra_diff = new[i]

      frag_list.append(frags)
      if do_ir_diff:
        # Don't render yet. Keep saving state necessary to render the whole
        # region until we have encountered all the lines in the region.
        old_buff.append([old_valid, old_lineno, old_intra_diff])
        new_buff.append([new_valid, new_lineno, new_intra_diff])
      else:
        # We render line by line as usual if do_ir_diff is false
        old_intra_diff = intra_region_diff.Fold(
          old_intra_diff, colwidth + indent, indent, indent,
          mark_tabs=mark_tabs)
        new_intra_diff = intra_region_diff.Fold(
          new_intra_diff, colwidth + indent, indent, indent,
          mark_tabs=mark_tabs)
        old_buff_out = [[old_valid, old_lineno,
                         (old_intra_diff, True, None)]]
        new_buff_out = [[new_valid, new_lineno,
                         (new_intra_diff, True, None)]]
        for tg, frag in _RenderDiffInternal(old_buff_out, new_buff_out,
                                            ndigits, tag, frag_list,
                                            do_ir_diff,
                                            old_dict, new_dict,
                                            old_patch, new_patch,
                                            old_snapshot, new_snapshot,
                                            colwidth, debug):
          yield tg, frag
        frag_list = []

    if do_ir_diff:
      # So this was a replace block which means that the whole region still
      # needs to be rendered.
      old_lines = [b[2] for b in old_buff]
      new_lines = [b[2] for b in new_buff]
      ret = intra_region_diff.IntraRegionDiff(old_lines, new_lines,
                                              diff_params)
      old_chunks, new_chunks, ratio = ret
      old_tag = 'old'
      new_tag = 'new'

      old_diff_out = intra_region_diff.RenderIntraRegionDiff(
        old_lines, old_chunks, old_tag, ratio,
        limit=colwidth, indent=indent,
        dbg=debug)
      new_diff_out = intra_region_diff.RenderIntraRegionDiff(
        new_lines, new_chunks, new_tag, ratio,
        limit=colwidth, indent=indent,
        dbg=debug)
      for (i, b) in enumerate(old_buff):
        b[2] = old_diff_out[i]
      for (i, b) in enumerate(new_buff):
        b[2] = new_diff_out[i]

      for tg, frag in _RenderDiffInternal(old_buff, new_buff,
                                          ndigits, tag, frag_list,
                                          do_ir_diff,
                                          old_dict, new_dict,
                                          old_patch, new_patch,
                                          old_snapshot, new_snapshot,
                                          colwidth, debug):
        yield tg, frag
      old_buff = []
      new_buff = []


def _CleanupTableRows(rows):
  """Cleanup rows returned by _TableRowGenerator.

  Args:
    rows: Sequence of (tag, text) tuples.

  Yields:
    Rows marked as 'equal' are possibly contracted using _ShortenBuffer().
    Stops on rows marked as 'error'.
  """
  buffer = []
  for tag, text in rows:
    if tag == 'equal':
      buffer.append(text)
      continue
    else:
      for t in _ShortenBuffer(buffer):
        yield t
      buffer = []
    yield text
    if tag == 'error':
      yield None
      break
  if buffer:
    for t in _ShortenBuffer(buffer):
      yield t


def _RenderDiffInternal(old_buff, new_buff, ndigits, tag, frag_list,
                        do_ir_diff, old_dict, new_dict,
                        old_patch, new_patch,
                        old_snapshot, new_snapshot,
                        colwidth, debug):
  """Helper for _TableRowGenerator()."""
  obegin = (intra_region_diff.BEGIN_TAG %
            intra_region_diff.COLOR_SCHEME['old']['match'])
  nbegin = (intra_region_diff.BEGIN_TAG %
            intra_region_diff.COLOR_SCHEME['new']['match'])
  oend = intra_region_diff.END_TAG
  nend = oend
  user = users.get_current_user()

  for i in xrange(len(old_buff)):
    tg = tag
    old_valid, old_lineno, old_out = old_buff[i]
    new_valid, new_lineno, new_out = new_buff[i]
    old_intra_diff, old_has_newline, old_debug_info = old_out
    new_intra_diff, new_has_newline, new_debug_info = new_out

    frags = frag_list[i]
    # Render left text column
    frags.append(_RenderDiffColumn(old_patch, old_valid, tag, ndigits,
                                   old_lineno, obegin, oend, old_intra_diff,
                                   do_ir_diff, old_has_newline, 'old'))

    # Render right text column
    frags.append(_RenderDiffColumn(new_patch, new_valid, tag, ndigits,
                                   new_lineno, nbegin, nend, new_intra_diff,
                                   do_ir_diff, new_has_newline, 'new'))

    # End rendering the first row
    frags.append('</tr>\n')

    if debug:
      frags.append('<tr>')
      if old_debug_info:
        frags.append('<td class="debug-info">%s</td>' %
                     old_debug_info.replace('\n', '<br>'))
      else:
        frags.append('<td></td>')
      if new_debug_info:
        frags.append('<td class="debug-info">%s</td>' %
                     new_debug_info.replace('\n', '<br>'))
      else:
        frags.append('<td></td>')
      frags.append('</tr>\n')

    if old_patch or new_patch:
      # Start rendering the second row
      if ((old_valid and old_lineno in old_dict) or
          (new_valid and new_lineno in new_dict)):
        tg += '_comment'
        frags.append('<tr class="inline-comments" name="hook">')
      else:
        frags.append('<tr class="inline-comments">')

      # Render left inline comments
      frags.append(_RenderInlineComments(old_valid, old_lineno, old_dict,
                                         user, old_patch, old_snapshot, 'old'))

      # Render right inline comments
      frags.append(_RenderInlineComments(new_valid, new_lineno, new_dict,
                                         user, new_patch, new_snapshot, 'new'))

      # End rendering the second row
      frags.append('</tr>\n')

    # Yield the combined fragments
    yield tg, ''.join(frags)


def _RenderDiffColumn(patch, line_valid, tag, ndigits, lineno, begin, end,
                      intra_diff, do_ir_diff, has_newline, prefix):
  """Helper function for _RenderDiffInternal().

  Returns:
    A rendered column.
  """
  if line_valid:
    cls_attr = '%s%s' % (prefix, tag)
    if tag == 'equal':
      lno = '%*d' % (ndigits, lineno)
    else:
      lno = _MarkupNumber(ndigits, lineno, 'u')
    if tag == 'replace':
      col_content = ('%s%s %s%s' % (begin, lno, end, intra_diff))
      # If IR diff has been turned off or there is no matching new line at
      # the end then switch to dark background CSS style.
      if not do_ir_diff or not has_newline:
        cls_attr = cls_attr + '1'
    else:
      col_content = '%s %s' % (lno, intra_diff)
    return '<td class="%s" id="%scode%d">%s</td>' % (cls_attr, prefix,
                                                     lineno, col_content)
  else:
    return '<td class="%sblank"></td>' % prefix


def _RenderInlineComments(line_valid, lineno, data, user,
                          patch, snapshot, prefix):
  """Helper function for _RenderDiffInternal().

  Returns:
    Rendered comments.
  """
  comments = []
  if line_valid:
    comments.append('<td id="%s-line-%s">' % (prefix, lineno))
    if lineno in data:
      comments.append(
        _ExpandTemplate('inline_comment.html',
                        inline_draft_url='/inline_draft',
                        user=user,
                        patch=patch,
                        patchset=patch.patchset,
                        change=patch.patchset.change,
                        snapshot=snapshot,
                        side='a' if prefix == 'old' else 'b',
                        comments=data[lineno],
                        lineno=lineno,
                        ))
    comments.append('</td>')
  else:
    comments.append('<td></td>')
  return ''.join(comments)


def RenderUnifiedTableRows(request, parsed_lines):
  """Render the HTML table rows for a unified diff for a patch.

  Args:
    request: Django Request object.
    parsed_lines: List of tuples for each line that contain the line number,
      if they exist, for the old and new file.

  Returns:
    A list of html table rows.
  """
  old_dict, new_dict = _GetComments(request)

  rows = []
  for old_line_no, new_line_no, line_text in parsed_lines:
    row1_id = row2_id = ''
    # When a line is unchanged (i.e. both old_line_no and new_line_no aren't 0)
    # pick the old column line numbers when adding a comment.
    if old_line_no:
      row1_id = 'id="oldcode%d"' % old_line_no
      row2_id = 'id="old-line-%d"' % old_line_no
    elif new_line_no:
      row1_id = 'id="newcode%d"' % new_line_no
      row2_id = 'id="new-line-%d"' % new_line_no
    rows.append('<tr><td class="udiff" %s>%s</td></tr>' %
                (row1_id, RenderLineText(line_text)))

    frags = []
    if old_line_no in old_dict or new_line_no in new_dict:
      frags.append('<tr class="inline-comments" name="hook">')
      if old_line_no in old_dict:
        dct = old_dict
        line_no = old_line_no
        snapshot = 'old'
      else:
        dct = new_dict
        line_no = new_line_no
        snapshot = 'new'
      frags.append(_RenderInlineComments(True, line_no, dct, request.user,
                   request.patch, snapshot, snapshot))
    else:
      frags.append('<tr class="inline-comments">')
      frags.append('<td ' + row2_id +'></td>')
    frags.append('</tr>')
    rows.append(''.join(frags))
  return rows

def RenderLineText(line_text):
  r = intra_region_diff.TAB_TAG + '\t'
  return cgi.escape(line_text).replace('\t', r)

def _ComputeLineCounts(old_lines, chunks):
  """Compute the length of the old and new sides of a diff.

  Args:
    old_lines: List of lines representing the original file.
    chunks: List of chunks as returned by patching.ParsePatchToChunks().

  Returns:
    A tuple (old_len, new_len) representing len(old_lines) and
    len(new_lines), where new_lines is the list representing the
    result of applying the patch chunks to old_lines, however, without
    actually computing new_lines.
  """
  old_len = len(old_lines)
  new_len = old_len
  if chunks:
    (old_a, old_b), (new_a, new_b), old_lines, new_lines = chunks[-1]
    new_len += new_b - old_b
  return old_len, new_len


def _MarkupNumber(ndigits, number, tag):
  """Format a number in HTML in a given width with extra markup.

  Args:
    ndigits: the total width available for formatting
    number: the number to be formatted
    tag: HTML tag name, e.g. 'u'

  Returns:
    An HTML string that displays as ndigits wide, with the
    number right-aligned and surrounded by an HTML tag; for example,
    _MarkupNumber(42, 4, 'u') returns '  <u>42</u>'.
  """
  formatted_number = str(number)
  space_prefix = ' ' * (ndigits - len(formatted_number))
  return '%s<%s>%s</%s>' % (space_prefix, tag, formatted_number, tag)


def _ExpandTemplate(name, **params):
  """Wrapper around django.template.loader.render_to_string().

  For convenience, this takes keyword arguments instead of a dict.
  """
  return loader.render_to_string(name, params)
