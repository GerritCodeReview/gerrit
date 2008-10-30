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

from django.utils import safestring
from django.utils import html
from django.template import defaultfilters

import logging
import operator
import library

class FieldRendererException(Exception):
  def __init__(self, msg):
    self.msg = msg
  def __str__(self):
    return "FieldRendererException('%s')" % self.msg

def _render_star(change, current_user):
  id = change.key().id()

  list = []
  list.append("""<span id="change-star-%d">""" % id)
  if change.is_starred:
    list.append("""<a href="javascript:M_removeChangeStar(%d)"><img src="/static/star-lite.gif" width="15" height="15" border="0"></a>"""
        % id)
  else:
    if current_user:
      list.append("""<a href="javascript:M_addChangeStar(%d)"><img src="/static/star-dark.gif" width="15" height="15" border="0"></a>"""
          % id)
  list.append("</span>")
  return "".join(list)


class BaseFieldRenderer(object):
  """Base class for renderers for particular fields.
  
  Implement the render_title() and render_contents() methods.
  """
  def column_count(self):
    raise FieldRendererException, "column_count not implemented"

  def render_title(self):
    """Render the title for a column in the table.

    Returns:
      The string of the rendered title.
    """
    raise FieldRendererException, "render_title not implemented"
  def render_contents(self, change, current_user):
    raise FieldRendererException, "render_contents not implemented"


class IdFieldRenderer(BaseFieldRenderer):
  def column_count(self):
    return 3

  def render_title(self):
    return """<th class="header-columns" colspan="3" style="text-align: right;">Id</th>
    """

  def render_contents(self, change, current_user):
    logging.info("change=" + str(change))
    return """<td class="selection"><img src="/static/closedtriangle.gif" style="visibility: hidden;" width="12" height="9" /></td>
<td class="star" width="17" align="center">%(star)s</td>
<td class="id" align="right"><a href="/%(id)d">%(id)d</a></td>""" % {
        'id': change.key().id(),
        'star': _render_star(change, current_user)
      }


class FieldRenderer(BaseFieldRenderer):
  def __init__(self, cell_class, title, func):
    self.cell_class = cell_class
    self.title = title
    self.func = func

  def column_count(self):
    return 1

  def render_title(self):
    return """<th class="header-columns %(cell_class)s">%(title)s</th>""" % {
        'title': self.title,
        'cell_class': self.cell_class,
      }

  def render_contents(self, change, current_user):
    return """<td class="%(cell_class)s">%(cell_contents)s</td>""" % {
        'cell_contents': self.func(change),
        'cell_class': self.cell_class,
      }


class SubjectFieldRenderer(FieldRenderer):
  def __init__(self):
    FieldRenderer.__init__(self, 'subject', 'Subject',
        lambda c: """<a href="/%(id)d">%(subject)s</a> %(closed)s""" % {
            'id': c.key().id(),
            'subject': defaultfilters.truncatewords(c.subject, 11),
            'closed': library.closed_label(c),
          })

  def render_title(self):
    return """<th class="header-columns %(class)s">%(title)s</th>""" % {
              'title': self.title,
              'class': self.cell_class,
            }



class UserListFieldRenderer(FieldRenderer):
  def __init__(self, cell_class, title, func):
    FieldRenderer.__init__(self, cell_class, title, 
        lambda c: ", ".join(map(library.show_user, func(c))))


FIELD_ID = IdFieldRenderer()
FIELD_SUBJECT = SubjectFieldRenderer()
FIELD_OWNER = UserListFieldRenderer('owner', 'Owner', lambda c: [c.owner])
FIELD_REVIEWERS = UserListFieldRenderer('reviewers', 'Reviewers',
                      lambda c: c.reviewers)
FIELD_PROJECT = FieldRenderer('project', 'Project', lambda c: c.dest_project.name)
FIELD_MODIFIED = FieldRenderer('modified', 'Last Update',
                      lambda c: library.abbrevtimesince(c.modified))

# this one should be change so that it's what they see in repo
FIELD_BRANCH = FieldRenderer('branch', 'Branch', lambda c: c.dest_branch.name)

class ChangeTable(object):
  """An object to help render a list of changes as a table.
  
  Generally, changes are grouped into sections.  If a section's title is
  None, then the section header won't be rendered, and it will appear to
  be merged with the last section.
  """

  def __init__(self, current_user, fields):
    """Construct a ChangeTable object.
    
    Args:
      fields - A list of BaseFieldRenderer objects.  See the FIELD_ constants
               in this module for the common ones.
    """
    self.current_user = current_user
    self.fields = fields
    self.sections = []

  def add_section(self, title, changes):
    self.sections.append({'title': title, 'changes': changes})

  def render(self):
    def _render_none():
      return ("""<tr><td colspan="%d" class="disabled">(None)</td></tr>"""
            % column_count)
    column_count = reduce(operator.add,
                      map(lambda x: x.column_count(), self.fields))
    lines = []
    lines.append("""<table class="change-list" id="user-queues">""")
    lines.append("""<tr>""")
    for field in self.fields:
      lines.append(field.render_title())
    lines.append("""</tr>""")
    if len(self.sections) == 0:
      lines.append(_render_none())
    else:
      for section in self.sections:
        title = section['title']
        if not title is None:
          lines.append("""<tr><th class="header-title" colspan="%d">%s</th></tr>"""
              % (column_count, html.escape(title)))
        changes = section['changes']
        if not changes:
          lines.append(_render_none())
        else:
          for change in section['changes']:
            lines.append("""<tr name="change">""")
            for field in self.fields:
              lines.append(field.render_contents(change, self.current_user))
            lines.append("</tr>")
    lines.append("""</table>""")
    return safestring.mark_safe("\n".join(lines))

