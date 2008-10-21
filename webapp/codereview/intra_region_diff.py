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

"""Intra-region diff utilities.

Intra-region diff highlights the blocks of code which have been changed or
deleted within a region. So instead of highlighting the whole region marked as
changed, the user can see what exactly was changed within that region.

Terminology:
  'region' is a list of consecutive code lines.
  'word' is the unit of intra-region diff. Its definition is arbitrary based on
   what we think as to be a good unit of difference between two regions.
  'block' is a small section of code within a region. It can span multiple
  lines. There can be multiple non overlapping blocks within a region. A block
  can potentially span the whole region.

The blocks have two representations. One is of the format (offset1, offset2,
size) which is returned by the SequenceMatcher to indicate a match of
length 'size' starting at offset1 in the first/old line and starting at offset2
in the second/new line. We convert this representation to a pair of tuples i.e.
(offset1, size) and (offset2, size) for rendering each side of the diff
separately. This latter representation is also more efficient for doing
compaction of adjacent blocks which reduces the size of the HTML markup. See
CompactBlocks for more details.

SequenceMatcher always returns one special matching block at the end with
contents (len(line1), len(line2), 0). We retain this special block as it
simplifies for loops in rendering the last non-matching block. All functions
which deal with the sequence of blocks assume presence of the special block at
the end of the sequence and retain it.
"""

import cgi
import difflib
import re

# Tag to begin a diff chunk.
BEGIN_TAG = "<span class=\"%s\">"
# Tag to end a diff block.
END_TAG = "</span>"
# Tag used for visual tab indication
TAB_TAG = ("<span class=\"visualtab\" title=\"Visual tab indicator. "
           "Change settings above to hide.\">&raquo;</span>")
# Color scheme to govern the display properties of diff blocks and matching
# blocks. Each value e.g. 'oldlight' corresponds to a CSS style.
COLOR_SCHEME = {
  'old': {
          'match':      'oldlight',
          'diff':       'olddark',
          'bckgrnd':    'oldlight',
         },
  'new': {
          'match':      'newlight',
          'diff':       'newdark',
          'bckgrnd':    'newlight',
         },
  'oldmove': {
          'match':      'movelight',
          'diff':       'oldmovedark',
          'bckgrnd':    'movelight'
  },
  'newmove': {
          'match':      'newlight',
          'diff':       'newdark',
          'bckgrnd':    'newlight'
  },
}
# Regular expressions to tokenize lines. Default is 'b'.
EXPRS = {
         'a': r'(\w+|[^\w\s]+|\s+)',
         'b': r'([A-Za-z0-9]+|[^A-Za-z0-9])',
         'c': r'([A-Za-z0-9_]+|[^A-Za-z0-9_])',
        }
# Maximum total characters in old and new lines for doing intra-region diffs.
# Intra-region diff for larger regions is hard to comprehend and wastes CPU
# time.
MAX_TOTAL_LEN = 10000


def ExpandTabs(text, tabsize=8, tab_marker=None):
  """Expand tab characters in a string into spaces with an optional marker.

  Args:
    text: a string containing tab characters.
    tabsize: the number of spaces that a tab represents
    tab_marker: a character; if not None, we replace the first character
                of each tab expansion with this.
  """
  tabpos = text.find("\t")
  while tabpos >= 0:
    fillwidth = tabsize - (tabpos % tabsize)
    if fillwidth == 0:
      fillwidth = tabsize
    if tab_marker:
      fill = tab_marker + " " * (fillwidth - 1)
    else:
      fill = " " * fillwidth
    # We avoid str.replace in case tab_marker is \t
    text = text[:tabpos] + fill + text[tabpos+1:]
    tabpos = text.find("\t", tabpos + 1)
  return text


def Fold(text, limit=85, indent=5, offset=0, tabsize=8, mark_tabs=False):
  """Break a long string into multiple lines.

  Lines longer than 'limit' are broken up into pieces of at most
  'limit' characters; continuation lines start with 'indent' spaces.

  'offset' is used to indicate if 'text' itself doesn't align with
  the beginning of line e.g. we are trying to Fold a line when we have
  already printed 'offset' number of characters to the output.

  This also translates tabs into 'tabsize' spaces. If 'mark_tabs' is true,
  then we indicate the first character of each expanded tab visually.

  Input and output are assumed to be in UTF-8; the computation is done
  in Unicode.  (Still not good enough if zero-width characters are
  present.) If the input is not valid UTF-8, then the encoding is
  passed through, potentially breaking up multi-byte characters.
  We pass the line through cgi.escape before returning it.

  A trailing newline is always stripped from the input first.
  """
  assert tabsize > 0, tabsize
  if text.endswith("\n"):
    text = text[:-1]
  try:
    text = unicode(text, "utf-8")
  except:
    pass
  if "\t" in text:
    # If mark_tabs is true, we retain one \t character as a marker during
    # expansion so that we later replace it with an HTML snippet.
    tab_marker = mark_tabs and "\t" or None
    rest = text[indent-offset:]
    text = text[:indent-offset] + ExpandTabs(rest, tabsize, tab_marker)
  # Perform wrapping.
  if len(text) > limit - offset:
    parts = []
    prefix = ""
    i = 0
    j = limit - offset
    while i < len(text):
      parts.append(prefix + text[i:j])
      i = j
      j += limit - indent
      prefix = " " * indent
    text = "\n".join(parts)
  # Colorize tab markers (after calling escape)
  text = cgi.escape(text)
  text = text.replace("\t", TAB_TAG)
  if isinstance(text, unicode):
    return text.encode("utf-8", "replace")
  return text


def CompactBlocks(blocks):
  """Compacts adjacent code blocks.

  In many cases 2 adjacent blocks can be merged into one. This allows
  to do some further processing on those blocks.

  Args:
    blocks: [(offset1, size), ...]

  Returns:
    A list with the same structure as the input with adjacent blocks
    merged.  However, the last block (which is always assumed to have
    a zero size) is never merged.  For example, the input
    [(0, 2), (2, 8), (10, 5), (15, 0)]
    will produce the output [(0, 15), (15, 0)].
  """
  if len(blocks) == 1:
    return blocks
  result = [blocks[0]]
  for block in blocks[1:-1]:
    last_start, last_len = result[-1]
    curr_start, curr_len = block
    if last_start + last_len == curr_start:
      result[-1] = last_start, last_len + curr_len
    else:
      result.append(block)
  result.append(blocks[-1])
  return result


def FilterBlocks(blocks, filter_func):
  """Gets rid of any blocks if filter_func evaluates false for them.

  Args:
    blocks: [(offset1, offset2, size), ...]; must have at least 1 entry
    filter_func: a boolean function taking a single argument of the form
                 (offset1, offset2, size)

  Returns:
    A list with the same structure with entries for which filter_func()
    returns false removed.  However, the last block is always included.
  """
  # We retain the 'special' block at the end.
  res = [b for b in blocks[:-1] if filter_func(b)]
  res.append(blocks[-1])
  return res


def GetDiffParams(expr='b', min_match_ratio=0.6, min_match_size=2, dbg=False):
  """Returns a tuple of various parameters which affect intra region diffs.

  Args:
    expr: regular expression id to use to identify 'words' in the intra region
          diff
    min_match_ratio: minimum similarity between regions to qualify for intra
                     region diff
    min_match_size: the smallest matching block size to use. Blocks smaller
                    than this are ignored.
    dbg: to turn on generation of debugging information for the diff

  Returns:
    4 tuple (expr, min_match_ratio, min_match_size, dbg) that can be used to
    customize diff. It can be passed to functions like WordDiff and
    IntraLineDiff.
  """
  assert expr in EXPRS
  assert min_match_size in xrange(1,5)
  assert min_match_ratio > 0.0 and min_match_ratio < 1.0
  return (expr, min_match_ratio, min_match_size, dbg)


def CanDoIRDiff(old_lines, new_lines):
  """Tells if it would be worth computing the intra region diff.

  Calculating IR diff is costly and is usually helpful only for small regions.
  We use a heuristic that if the total number of characters is more than a
  certain threshold then we assume it is not worth computing the IR diff.

  Args:
    old_lines: an array of strings containing old text
    new_lines: an array of strings containing new text

  Returns:
    True if we think it is worth computing IR diff for the region defined
    by old_lines and new_lines, False otherwise.

  TODO: Let GetDiffParams handle MAX_TOTAL_LEN param also.
  """
  total_chars = (sum(len(line) for line in old_lines) +
                 sum(len(line) for line in new_lines))
  return total_chars <= MAX_TOTAL_LEN


def WordDiff(line1, line2, diff_params):
  """Returns blocks with positions indiciating word level diffs.

  Args:
    line1: string representing the left part of the diff
    line2: string representing the right part of the diff
    diff_params: return value of GetDiffParams

  Returns:
    A tuple (blocks, ratio) where:
      blocks: [(offset1, offset2, size), ...] such that
              line1[offset1:offset1+size] == line2[offset2:offset2+size]
              and the last block is always (len(line1), len(line2), 0)
      ratio: a float giving the diff ratio computed by SequenceMatcher.
  """
  match_expr, min_match_ratio, min_match_size, dbg = diff_params
  exp = EXPRS[match_expr]
  # We want to split at proper character boundaries in UTF8 text.
  try:
    line1_u = unicode(line1, "utf8")
  except:
    line1_u = line1
  try:
    line2_u = unicode(line2, "utf8")
  except:
    line2_u = line2
  def _ToUTF8(s):
    if isinstance(s, unicode):
      return s.encode("utf8")
    return s
  a = map(_ToUTF8, re.findall(exp, line1_u, re.U))
  b = map(_ToUTF8, re.findall(exp, line2_u, re.U))
  s = difflib.SequenceMatcher(None, a, b)
  matching_blocks = s.get_matching_blocks()
  ratio = s.ratio()
  # Don't show intra region diffs if both lines are too different and there is
  # more than one block of difference. If there is only one change then we
  # still show the intra region diff regardless of how different the blocks
  # are.
  # Note: We compare len(matching_blocks) with 3 because one block of change
  # results in 2 matching blocks. We add the one special block and we get 3
  # matching blocks per one block of change.
  if ratio < min_match_ratio and len(matching_blocks) > 3:
    return ([(0, 0, 0)], ratio)
  # For now convert to character level blocks because we already have
  # the code to deal with folding across lines for character blocks.
  # Create arrays lena an lenb which have cumulative word lengths
  # corresponding to word positions in a and b
  lena = []
  last = 0
  for w in a:
    lena.append(last)
    last += len(w)
  lenb = []
  last = 0
  for w in b:
    lenb.append(last)
    last += len(w)
  lena.append(len(line1))
  lenb.append(len(line2))
  # Convert to character blocks
  blocks = []
  for s1, s2, blen in matching_blocks[:-1]:
    apos = lena[s1]
    bpos = lenb[s2]
    block_len = lena[s1+blen] - apos
    blocks.append((apos, bpos, block_len))
  # Recreate the special block.
  blocks.append((len(line1), len(line2), 0))
  # Filter any matching blocks which are smaller than the desired threshold.
  # We don't remove matching blocks with only a newline character as doing so
  # results in showing the matching newline character as non matching which
  # doesn't look good.
  blocks = FilterBlocks(blocks, lambda b: (b[2] >= min_match_size or
                                           line1[b[0]:b[0]+b[2]] == '\n'))
  return (blocks, ratio)


def IntraLineDiff(line1, line2, diff_params, diff_func=WordDiff):
  """Computes intraline diff blocks.

  Args:
    line1: string representing the left part of the diff
    line2: string representing the right part of the diff
    diff_params: return value of GetDiffParams
    diff_func: a function whose signature matches that of WordDiff() above

  Returns:
    A tuple of (blocks1, blocks2) corresponding to line1 and line2.
    Each element of the tuple is an array of (start_pos, length)
    tuples denoting a diff block.
  """
  blocks, ratio = diff_func(line1, line2, diff_params)
  blocks1 = [(start1, length) for (start1, start2, length) in blocks]
  blocks2 = [(start2, length) for (start1, start2, length) in blocks]

  return (blocks1, blocks2, ratio)


def DumpDiff(blocks, line1, line2):
  """Helper function to debug diff related problems.

  Args:
    blocks: [(offset1, offset2, size), ...]
    line1: string representing the left part of the diff
    line2: string representing the right part of the diff
  """
  for offset1, offset2, size in blocks:
    print offset1, offset2, size
    print offset1, size, ":  ", line1[offset1:offset1+size]
    print offset2, size, ":  ", line2[offset2:offset2+size]


def RenderIntraLineDiff(blocks, line, tag, dbg_info=None, limit=80, indent=5,
                        tabsize=8, mark_tabs=False):
  """Renders the diff blocks returned by IntraLineDiff function.

  Args:
    blocks: [(start_pos,  size), ...]
    line: line of code on which the blocks are to be rendered.
    tag: 'new' or 'old' to control the color scheme.
    dbg_info: a string that holds debugging informaion header. Debug
              information is rendered only if dbg_info is not None.
    limit: folding limit to be passed to the Fold function.
    indent: indentation size to be passed to the Fold function.
    tabsize: the number of spaces that a tab represents
    mark_tabs: if True, mark the first character of each expanded tab visually

  Returns:
    A tuple of two elements. First element is the rendered version of
    the input 'line'. Second element tells if the line has a matching
    newline character.
  """
  res = ""
  prev_start, prev_len = 0, 0
  has_newline = False
  debug_info = dbg_info
  if dbg_info:
    debug_info += "\nBlock Count: %d\nBlocks: " % (len(blocks) - 1)
  for curr_start, curr_len in blocks:
    if dbg_info and curr_len > 0:
      debug_info += Fold("\n(%d, %d):|%s|" %
                         (curr_start, curr_len,
                          line[curr_start:curr_start+curr_len]),
                         limit, indent, tabsize, mark_tabs)
    res += FoldBlock(line, prev_start + prev_len, curr_start, limit, indent,
                     tag, 'diff', tabsize, mark_tabs)
    res += FoldBlock(line, curr_start, curr_start + curr_len, limit, indent,
                     tag, 'match', tabsize, mark_tabs)
    # TODO: This test should be out of loop rather than inside. Once we
    # filter out some junk from blocks (e.g. some empty blocks) we should do
    # this test only on the last matching block.
    if line[curr_start:curr_start+curr_len].endswith('\n'):
      has_newline = True
    prev_start, prev_len = curr_start, curr_len
  return (res, has_newline, debug_info)


def FoldBlock(src, start, end, limit, indent, tag, btype, tabsize=8,
              mark_tabs=False):
  """Folds and renders a block.

  Args:
    src: line of code
    start: starting position of the block within 'src'.
    end: ending position of the block within 'src'.
    limit: folding limit
    indent: indentation to use for folding.
    tag: 'new' or 'old' to control the color scheme.
    btype: block type i.e. 'match' or 'diff' to control the color schme.
    tabsize: the number of spaces that a tab represents
    mark_tabs: if True, mark the first character of each expanded tab visually

  Returns:
    A string represeting the rendered block.
  """
  text = src[start:end]
  # We ignore newlines because we do newline management ourselves.
  # Any other new lines with at the end will be stripped off by the Fold
  # method.
  if start >= end or text == '\n':
    return ""
  fbegin, lend, nl_plus_indent = GetTags(tag, btype, indent)
  # 'bol' is beginning of line
  offset_from_bol = start % limit
  res = ""
  # If this is the first block of the line and this is not the first line then
  # insert newline + indent. This special case is not dealt with in the for
  # loop below.
  if offset_from_bol == 0 and not start == 0:
    res = nl_plus_indent
  text = Fold(text, limit, 0, offset_from_bol, tabsize, mark_tabs)
  folded_lines = text.split("\n")
  for (j, l) in enumerate(folded_lines):
    if l:
      res += (fbegin + l + lend)
    # Add new line plus indent except for the last line.
    if j < len(folded_lines) - 1:
      res += nl_plus_indent
  return res


def GetTags(tag, btype, indent):
  """Returns various tags for rendering diff blocks.

  Args:
    tag: a key from COLOR_SCHEME
    btype: 'match' or 'diff'
    indent: indentation to use
  Returns
    A 3 tuple (begin_tag, end_tag, formatted_indent_block)
  """
  assert tag in COLOR_SCHEME
  assert btype in ['match', 'diff']
  fbegin = BEGIN_TAG % COLOR_SCHEME[tag][btype]
  bbegin = BEGIN_TAG % COLOR_SCHEME[tag]['bckgrnd']
  lend = END_TAG
  nl_plus_indent = '\n'
  if indent > 0:
    nl_plus_indent += bbegin + cgi.escape(" "*indent) + lend
  return fbegin, lend, nl_plus_indent


def ConvertToSingleLine(lines):
  """Transforms a sequence of strings into a single line.

  Returns the state that can be used to reconstruct the original lines with
  the newline separators placed at the original place.

  Args:
    lines: sequence of strings

  Returns:
    Returns (single_line, state) tuple. 'state' shouldn't be modified by the
    caller. It is only used to pass to other functions which will do certain
    operations on this state.

    'state' is an array containing a dictionary for each item in lines. Each
    dictionary has two elements 'pos' and 'blocks'. 'pos' is the end position
    of each line in the final converted string. 'blocks' is an array of blocks
    for each line of code. These blocks are added using MarkBlock function.
  """
  state = []
  total_length = 0
  for l in lines:
    total_length += len(l)
    # TODO: Use a tuple instead.
    state.append({     'pos': total_length, # the line split point
                    'blocks': []            # blocks which belong to this line
                 })
  result = "".join(lines)
  assert len(state) == len(lines)
  return (result, state)


def MarkBlock(state, begin, end):
  """Marks a block on a region such that it doesn't cross line boundaries.

  It is an operation that can be performed on the single line which was
  returned by the ConvertToSingleLine function. This operation marks arbitrary
  block [begin,end) on the text. It also ensures that if [begin,end) crosses
  line boundaries in the original region then it splits the section up in 2 or
  more blocks such that no block crosses the boundaries.

  Args:
    state: the state returned by ConvertToSingleLine function. The state
           contained is modified by this function.
    begin: Beginning of the block.
    end: End of the block (exclusive).

  Returns:
    None.
  """
  # TODO: Make sure already existing blocks don't overlap
  if begin == end:
    return
  last_pos = 0
  for entry in state:
    pos = entry['pos']
    if begin >= last_pos and begin < pos:
      if end < pos:
        # block doesn't cross any line boundary
        entry['blocks'].append((begin, end))
      else:
        # block crosses the line boundary
        entry['blocks'].append((begin, pos))
        MarkBlock(state, pos, end)
      break
    last_pos = pos


def GetBlocks(state):
  """Returns all the blocks corresponding to the lines in the region.

  Args:
    state: the state returned by ConvertToSingleLine().

  Returns:
    An array of [(start_pos, length), ..] with an entry for each line in the
    region.
  """
  result = []
  last_pos = 0
  for entry in state:
    pos = entry['pos']
    # Calculate block start points from the beginning of individual lines.
    blocks = [(s[0]-last_pos, s[1]-s[0]) for s in entry['blocks']]
    # Add one end marker block.
    blocks.append((pos-last_pos, 0))
    result.append(blocks)
    last_pos = pos
  return result


def IntraRegionDiff(old_lines, new_lines, diff_params):
  """Computes intra region diff.

  Args:
    old_lines: array of strings
    new_lines: array of strings
    diff_params: return value of GetDiffParams

  Returns:
    A tuple (old_blocks, new_blocks) containing matching blocks for old and new
    lines.
  """
  old_line, old_state = ConvertToSingleLine(old_lines)
  new_line, new_state = ConvertToSingleLine(new_lines)
  old_blocks, new_blocks, ratio = IntraLineDiff(old_line, new_line, diff_params)
  for begin, length in old_blocks:
    MarkBlock(old_state, begin, begin+length)
  old_blocks = GetBlocks(old_state)

  for begin, length in new_blocks:
    MarkBlock(new_state, begin, begin+length)
  new_blocks = GetBlocks(new_state)

  return (old_blocks, new_blocks, ratio)


def NormalizeBlocks(blocks, line):
  """Normalizes block representation of an intra line diff.

  One diff can have multiple representations. Some times the diff returned by
  the difflib for similar text sections is different even within same region.
  For example if 2 already indented lines were indented with one additional
  space character, the difflib may return the non matching space character to
  be any of the already existing spaces. So one line may show non matching
  space character as the first space character and the second line may show it
  to be the last space character. This is sometimes confusing. This is the
  side effect of the new regular expression we are using in WordDiff for
  identifying indvidual words. This regular expression ('b') treats a sequence
  of punctuation and whitespace characters as individual characters. It has
  some visual advantages for showing a character level punctuation change as
  one character change rather than a group of character change.

  Making the normalization too generic can have performance implications. So
  this implementation of normalize blocks intends to handle only one case.
  Let's say S represents the space character and () marks a matching block.
  Then the normalize operation will do following:

     SSSS(SS)(ABCD) => SSSS(SS)(ABCD)
     (SS)SSSS(ABCD) => SSSS(SS)(ABCD)
     (SSSS)SS(ABCD) => SS(SSSS)(ABCD)

     and so on..

  Args:
    blocks: An array of (offset, len) tuples defined on 'line'. These blocks
            mark the matching areas. Anything between these matching blocks is
            considered non-matching.
    line: The text string on which the blocks are defined.

  Returns:
    An array of (offset, len) tuples representing the same diff but in
    normalized form.
  """
  result = []
  prev_start, prev_len = blocks[0]
  for curr_start, curr_len in blocks[1:]:
    # Note: nm_ is a prefix for non matching and m_ is a prefix for matching.
    m_len, nm_len  = prev_len, curr_start - (prev_start+prev_len)
    # This if condition checks if matching and non matching parts are greater
    # than zero length and are comprised of spaces ONLY. The last condition
    # deals with most of the observed cases of strange diffs.
    # Note: curr_start - prev_start == m_l + nm_l
    #       So line[prev_start:curr_start] == matching_part + non_matching_part.
    text = line[prev_start:curr_start]
    if m_len > 0 and nm_len > 0 and text == ' ' * len(text):
      # Move the matching block towards the end i.e. normalize.
      result.append((prev_start + nm_len, m_len))
    else:
      # Keep the existing matching block.
      result.append((prev_start, prev_len))
    prev_start, prev_len = curr_start, curr_len
  result.append(blocks[-1])
  assert len(result) == len(blocks)
  return result


def RenderIntraRegionDiff(lines, diff_blocks, tag, ratio, limit=80, indent=5,
                          tabsize=8, mark_tabs=False, dbg=False):
  """Renders intra region diff for one side.

  Args:
    lines: list of strings representing source code in the region
    diff_blocks: blocks that were returned for this region by IntraRegionDiff()
    tag: 'new' or 'old'
    ratio: similarity ratio returned by the diff computing function
    limit: folding limit
    indent: indentation size
    tabsize: the number of spaces that a tab represents
    mark_tabs: if True, mark the first character of each expanded tab visually
    dbg: indicates if debug information should be rendered

  Returns:
    A list of strings representing the rendered version of each item in input
    'lines'.
  """
  result = []
  dbg_info = None
  if dbg:
    dbg_info = 'Ratio: %.1f' % ratio
  for line, blocks in zip(lines, diff_blocks):
    blocks = NormalizeBlocks(blocks, line)
    blocks = CompactBlocks(blocks)
    diff = RenderIntraLineDiff(blocks,
                               line,
                               tag,
                               dbg_info=dbg_info,
                               limit=limit,
                               indent=indent,
                               tabsize=tabsize,
                               mark_tabs=mark_tabs)
    result.append(diff)
  assert len(result) == len(lines)
  return result
