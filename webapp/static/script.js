// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Generic helpers

/**
 * Create a new XMLHttpRequest in a cross-browser-compatible way.
 * @return XMLHttpRequest object
 */
function M_getXMLHttpRequest() {
  try {
    return new XMLHttpRequest();
  } catch (e) { }

  try {
    return new ActiveXObject("Msxml2.XMLHTTP");
  } catch (e) { }

  try {
    return new ActiveXObject("Microsoft.XMLHTTP");
  } catch (e) { }

  return null;
}

/**
 * Finds the element's parent in the DOM tree.
 * @param {Element} element The element whose parent we want to find
 * @return The parent element of the given element
 */
function M_getParent(element) {
  if (element.parentNode) {
    return element.parentNode;
  } else if (element.parentElement) {
    // IE compatibility. Why follow standards when you can make up your own?
    return element.parentElement;
  }
  return null;
}

/**
 * Finds the event's target in a way that works on all browsers.
 * @param {Event} e The event object whose target we want to find
 * @return The element receiving the event
 */
function M_getEventTarget(e) {
  var src = e.srcElement ? e.srcElement : e.target;
  return src;
}

/**
 * Function to determine if we are in a KHTML-based browser(Konq/Safari).
 * @return Boolean of whether we are in a KHTML browser
 */
function M_isKHTML() {
  var agt = navigator.userAgent.toLowerCase();
  return (agt.indexOf("safari") != -1) || (agt.indexOf("khtml") != -1);
}

/**
 * Function to determine if we are running in an IE browser.
 * @return Boolean of whether we are running in IE
 */
function M_isIE() {
  return (navigator.userAgent.toLowerCase().indexOf("msie") != -1) &&
         !window.opera;
}

/**
 * Stop the event bubbling in a browser-independent way. Sometimes required
 * when it is not easy to return true when an event is handled.
 * @param {Window} win The window in which this event is happening
 * @param {Event} e The event that we want to cancel
 */
function M_stopBubble(win, e) {
  if (!e) {
    e = win.event;
  }
  e.cancelBubble = true;
  if (e.stopPropagation) {
    e.stopPropagation();
  }
}

/**
 * Return distance in pixels from the top of the document to the given element.
 * @param {Element} element The element whose offset we want to find
 * @return Integer value of the height of the element from the top
 */
function M_getPageOffsetTop(element) {
  var y = element.offsetTop;
  if (element.offsetParent != null) {
    y += M_getPageOffsetTop(element.offsetParent);
  }
  return y;
}

/**
 * Return distance in pixels of the given element from the left of the document.
 * @param {Element} element The element whose offset we want to find
 * @return Integer value of the horizontal position of the element
 */
function M_getPageOffsetLeft(element) {
  var x = element.offsetLeft;
  if (element.offsetParent != null) {
    x += M_getPageOffsetLeft(element.offsetParent);
  }
  return x;
}

/**
 * Find the height of the window viewport.
 * @param {Window} win The window whose viewport we would like to measure
 * @return Integer value of the height of the given window
 */
function M_getWindowHeight(win) {
  return M_getWindowPropertyByBrowser_(win, M_getWindowHeightGetters_);
}

/**
 * Find the vertical scroll position of the given window.
 * @param {Window} win The window whose scroll position we want to find
 * @return Integer value of the scroll position of the given window
 */
function M_getScrollTop(win) {
  return M_getWindowPropertyByBrowser_(win, M_getScrollTopGetters_);
}

/**
 * Scroll the target element into view at 1/3rd of the window height only if
 * the scrolling direction matches the direction that was asked for.
 * @param {Window} win The window in which the element resides
 * @param {Element} element The element that we want to bring into view
 * @param {Integer} direction Positive for scroll down, negative for scroll up
 */
function M_scrollIntoView(win, element, direction) {
  var elTop = M_getPageOffsetTop(element);
  var winHeight = M_getWindowHeight(win);
  var targetScroll = elTop - winHeight / 3;
  var scrollTop = M_getScrollTop(win);

  if ((direction > 0 && scrollTop < targetScroll) ||
      (direction < 0 && scrollTop > targetScroll)) {
    win.scrollTo(M_getPageOffsetLeft(element), targetScroll);
  }
}

/**
 * Returns whether the element is visible.
 * @param {Window} win The window that the element resides in
 * @param {Element} element The element whose visibility we want to determine
 * @return Boolean of whether the element is visible in the window or not
 */
function M_isElementVisible(win, element) {
  var elTop = M_getPageOffsetTop(element);
  var winHeight = M_getWindowHeight(win);
  var winTop = M_getScrollTop(win);
  if (elTop < winTop || elTop > winTop + winHeight) {
    return false;
  }
  return true;
}

// Cross-browser compatibility quirks and methodology borrowed from
// common.js

var M_getWindowHeightGetters_ = {
  ieQuirks_: function(win) {
    return win.document.body.clientHeight;
  },
  ieStandards_: function(win) {
    return win.document.documentElement.clientHeight;
  },
  dom_: function(win) {
    return win.innerHeight;
  }
};

var M_getScrollTopGetters_ = {
  ieQuirks_: function(win) {
    return win.document.body.scrollTop;
  },
  ieStandards_: function(win) {
    return win.document.documentElement.scrollTop;
  },
  dom_: function(win) {
    return win.pageYOffset;
  }
};

/**
 * Slightly modified from common.js: Konqueror has the CSS1Compat property
 * but requires the standard DOM functionlity, not the IE one.
 */
function M_getWindowPropertyByBrowser_(win, getters) {
  try {
    if (!M_isKHTML() && "compatMode" in win.document &&
        win.document.compatMode == "CSS1Compat") {
      return getters.ieStandards_(win);
    } else if (M_isIE()) {
      return getters.ieQuirks_(win);
    }
  } catch (e) {
    // Ignore for now and fall back to DOM method
  }

  return getters.dom_(win);
}

// Global search box magic (global.html)

/**
 * Handle the onblur action of the search box, replacing it with greyed out
 * instruction text when it is empty.
 * @param {Element} element The search box element
 */
function M_onSearchBlur(element) {
  var defaultMsg = "Enter a changelist#, user, or group";
  if (element.value.length == 0 || element.value == defaultMsg) {
    element.style.color = "gray";
    element.value = defaultMsg;
  } else {
    element.style.color = "";
  }
}

/**
 * Handle the onfocus action of the search box, emptying it out if no new text
 * was entered.
 * @param {Element} element The search box element
 */
function M_onSearchFocus(element) {
  if (element.style.color == "gray") {
    element.style.color = "";
    element.value = "";
  }
}

// Inline diffs (changelist.html)

/**
 * Creates an iframe to load the diff in the background and when that's done,
 * calls a function to transfer the contents of the iframe into the current DOM.
 * @param {Integer} suffix The number associated with that diff
 * @param {String} url The URL that the diff should be fetched from
 * @return false (for event bubbling purposes)
 */
function M_showInlineDiff(suffix, url) {
  var hide = document.getElementById("hide-" + suffix);
  var show = document.getElementById("show-" + suffix);
  var frameDiv = document.getElementById("frameDiv-" + suffix);
  var dumpDiv = document.getElementById("dumpDiv-" + suffix);
  var diffTR = document.getElementById("diffTR-" + suffix);
  var hideAll = document.getElementById("hide-alldiffs");
  var showAll = document.getElementById("show-alldiffs");

  /* Twiddle the "show/hide all diffs" link */
  if (hide.style.display != "") {
    M_CL_hiddenInlineDiffCount -= 1;
    if (M_CL_hiddenInlineDiffCount == M_CL_maxHiddenInlineDiffCount) {
      showAll.style.display = "inline";
      hideAll.style.display = "none";
    } else {
      showAll.style.display = "none";
      hideAll.style.display = "inline";
    }
  }

  hide.style.display = "";
  show.style.display = "none";
  dumpDiv.style.display = "block"; // XXX why not ""?
  diffTR.style.display = "";
  if (!frameDiv.innerHTML) {
    if (M_isKHTML()) {
      frameDiv.style.display = "block"; // XXX why not ""?
    }
    frameDiv.innerHTML = "<iframe src='" + url + "'" +
    " onload='M_dumpInlineDiffContent(this, \"" + suffix + "\")'"+
    "height=1>your browser does not support iframes!</iframe>";
  }
  return false;
}

/**
 * Hides the diff that was retrieved with M_showInlineDiff.
 * @param {Integer} suffix The number associated with the diff we want to hide
 */
function M_hideInlineDiff(suffix) {
  var hide = document.getElementById("hide-" + suffix);
  var show = document.getElementById("show-" + suffix);
  var dumpDiv = document.getElementById("dumpDiv-" + suffix);
  var diffTR = document.getElementById("diffTR-" + suffix);
  var hideAll = document.getElementById("hide-alldiffs");
  var showAll = document.getElementById("show-alldiffs");

  /* Twiddle the "show/hide all diffs" link */
  if (hide.style.display != "none") {
    M_CL_hiddenInlineDiffCount += 1;
    if (M_CL_hiddenInlineDiffCount == M_CL_maxHiddenInlineDiffCount) {
      showAll.style.display = "inline";
      hideAll.style.display = "none";
    } else {
      showAll.style.display = "none";
      hideAll.style.display = "inline";
    }
  }

  hide.style.display = "none";
  show.style.display = "inline";
  diffTR.style.display = "none";
  dumpDiv.style.display = "none";
  return false;
}

/**
 * Dumps the content of the given iframe into the appropriate div in order
 * for the diff to be displayed.
 * @param {Element} iframe The IFRAME that contains the diff data
 * @param {Integer} suffix The number associated with the diff
 */
function M_dumpInlineDiffContent(iframe, suffix) {
  var dumpDiv = document.getElementById("dumpDiv-" + suffix);
  dumpDiv.style.display = "block"; // XXX why not ""?
  dumpDiv.innerHTML = iframe.contentWindow.document.body.innerHTML;
  // TODO: The following should work on all browsers instead of the
  // innerHTML hack above. At this point I don't remember what the exact
  // problem was, but it didn't work for some reason.
  // dumpDiv.appendChild(iframe.contentWindow.document.body);
  if (M_isKHTML()) {
    var frameDiv = document.getElementById("frameDiv-" + suffix);
    frameDiv.style.display = "none";
  }
}

/**
 * Goes through all the diffs and triggers the onclick action on them which
 * should start the mechanism for displaying them.
 * @param {Integer} num The number of diffs to display (0-indexed)
 */
function M_showAllDiffs(num) {
  for (var i = 0; i < num; i++) {
    var link = document.getElementById('show-' + i);
    // Since the user may not have JS, the template only shows the diff inline
    // for the onclick action, not the href. In order to activate it, we must
    // call the link's onclick action.
    if (link.className.indexOf("reverted") == -1) {
      link.onclick();
    }
  }
}

/**
 * Goes through all the diffs and hides them by triggering the hide link.
 * @param {Integer} num The number of diffs to hide (0-indexed)
 */
function M_hideAllDiffs(num) {
  for (var i = 0; i < num; i++) {
    var link = document.getElementById('hide-' + i);
    // If the user tries to hide, that means they have JS, which in turn means
    // that we can just set href in the href of the hide link.
    link.onclick();
  }
}

// Inline comment submission forms (changelist.html, file.html)

/**
 * Changes the elements display style to "" which renders it visible.
 * @param {String} id The id of the target element
 */
function M_showElement(id) {
  var elt = document.getElementById(id);
  if (elt) elt.style.display = "";
}

/**
 * Changes the elements display style to "none" which renders it invisible.
 * @param {String} id The id of the target element
 */
function M_hideElement(id) {
  var elt = document.getElementById(id);
  if (elt) elt.style.display = "none";
}

/**
 * Toggle the visibility of a section. The little indicator triangle will also
 * be toggled.
 * @param {String} id The id of the target element
 */
var isSectionOpen = new Array();
function M_toggleSection(id) {
  var sectionStyle = document.getElementById(id).style;
  var pointerStyle = document.getElementById(id + "-pointer").style;

  if (sectionStyle.display == "none") {
    sectionStyle.display = "";
    pointerStyle.backgroundImage = "url('/static/opentriangle.gif')";
    isSectionOpen[id] = true;
  } else {
    sectionStyle.display = "none";
    pointerStyle.backgroundImage = "url('/static/closedtriangle.gif')";
    isSectionOpen[id] = false;
  }
}

var patchSetState = new Array();
function M_onPatchSetReady() {
  if (http_request.readyState != 4)
    return;

  var id = http_request.div_id;
  var s = document.getElementById(id);
  if (http_request.status == 200) {
	patchSetState[id] = 1;
    s.innerHTML = http_request.responseText;
  } else {
	patchSetState[id] = -1;
    s.innerHTML = '<div style="color:red">'
	  + 'Could not load the patchset.<br />'
	  + http_request.status
	  + '</div>';
  }
}

/**
 * Toggle the visiblity of a PatchSet section.
 * @param {String} change_id the id of the change.
 * @param {String} patchset_id the id of the patchset.
 */
function M_togglePatchSetSection(change_id, patchset_id) {
  var id = 'ps-' + patchset_id;
  M_toggleSection(id);
  if (isSectionOpen[id]) {
    if (patchSetState[id] == 1)
      return;

    var s = document.getElementById(id);
    http_request = M_getXMLHttpRequest();
    if (!http_request) {
      patchSetState[id] = -1;
      s.innerHTML = '<div style="color:red">Could not load.</div>';
      return;
    }

    s.innerHTML = '<div>Loading...</div>';
    var u = '/' + change_id + '/ajax_patchset/' + patchset_id;
    http_request.open('GET', u, true);
    http_request.onreadystatechange = M_onPatchSetReady;
    http_request.div_id = id;
    http_request.send(null);
  }
}

function M_onPagedReady () {
  if (http_request.readyState != 4)
    return;

  var section = http_request.section_name;
  var links = document.getElementById('paged-' + section + '-links');
  var progs = document.getElementById('paged-' + section + '-progress');

  if (http_request.status != 200) {
    links.style.display = '';
    progs.style.display = 'none';
    return;
  }

  var t = document.getElementById(http_request.table_id);
  var last;
  for (var i = 0; i < t.rows.length;) {
    var r = t.rows[i];
    if (r.className.indexOf('pagedrow-'+section) >= 0) {
      t.deleteRow(i);
      last = t.rows[i];
    } else {
      i++;
    }
  }

  var text = http_request.responseText;
  var lf = text.indexOf('\n');
  var info = text.substring(0,lf).split(',');

  var tmp = document.createElement('table');
  tmp.innerHTML = text.substring(lf + 1);
  while (0 < tmp.rows.length) {
    if (last)
      last.parentNode.insertBefore(tmp.rows[0], last);
    else
      t.tBodies[0].appendChild(tmp.rows[0]);
  }

  var opos = document.getElementById('paged-'+section+'-opos');
  var oend = document.getElementById('paged-'+section+'-oend');

  var opre = document.getElementById('paged-'+section+'-opre');
  var onex = document.getElementById('paged-'+section+'-onex');

  var prev = document.getElementById('paged-'+section+'-prev');
  var next = document.getElementById('paged-'+section+'-next');

  opos.innerHTML = info[0];
  oend.innerHTML = info[1];
  opre.innerHTML = info[2];
  onex.innerHTML = info[3];

  prev.style.display = info[2]!='' && parseInt(info[2]) ? '' : 'none';
  next.style.display = info[3]!='' && parseInt(info[3]) ? '' : 'none';

  links.style.display = '';
  progs.style.display = 'none';

  if (http_request.after_update)
    http_request.after_update();
}

function M_PagedExec(base_url, section, table_id, offset, after) {
  http_request = M_getXMLHttpRequest();
  if (!http_request)
    return;

  var links = document.getElementById('paged-' + section + '-links');
  var progs = document.getElementById('paged-' + section + '-progress');

  links.style.display = 'none';
  progs.style.display = '';

  http_request.open('GET', base_url + '/' + offset, true);
  http_request.onreadystatechange = M_onPagedReady;
  http_request.section_name = section;
  http_request.table_id = table_id;
  http_request.after_update = after;
  http_request.send(null);
}

function M_PagedPrev(base_url, section, table_id, after) {
  var p = document.getElementById('paged-' + section + '-opre');
  M_PagedExec(base_url, section, table_id, p.textContent, after);
}

function M_PagedNext(base_url, section, table_id, after) {
  var p = document.getElementById('paged-' + section + '-onex');
  M_PagedExec(base_url, section, table_id, p.textContent, after);
}

/**
 * Toggle the visibility of the "Quick LGTM" link on the changelist page.
 * @param {String} id The id of the target element
 */
function M_toggleQuickLGTM(id) {
  M_toggleSection(id);
  window.scrollTo(0, document.body.offsetHeight);
}

// Comment expand/collapse

/**
 * Toggles whether the specified changelist comment is expanded/collapsed.
 * @param {Integer} cid The comment id, 0-indexed
 */
function M_switchChangelistComment(cid) {
  M_switchCommentCommon_('cl', String(cid));
}

/**
 * Toggles whether the specified file comment is expanded/collapsed.
 * @param {Integer} cid The comment id, 0-indexed
 */
function M_switchFileComment(cid) {
  M_switchCommentCommon_('file', String(cid));
}

/**
 * Toggles whether the specified inline comment is expanded/collapsed.
 * @param {Integer} cid The comment id, 0-indexed
 * @param {Integer} lineno The lineno associated with the comment
 * @param {String} side The side (a/b) associated with the comment
 */
function M_switchInlineComment(cid, lineno, side) {
  M_switchCommentCommon_('inline', String(cid) + "-" + lineno + "-" + side);
}

/**
 * Toggles whether the specified comment is expanded/collapsed on
 * comment_form.html.
 * @param {Integer} cid The comment id, 0-indexed
 */
function M_switchReviewComment(cid) {
  M_switchCommentCommon_('cl', String(cid));
}

/**
 * Toggle whether a moved_out region is expanded/collapsed.
 * @param {Integer} start_line the line number of the first line to toggle
 * @param {Integer} end_line the line number of the first line not to toggle
 * We toggle all lines in [first_line, end_line).
 */
function M_switchMoveOut(start_line, end_line) {
  for (var x = start_line; x < end_line; x++) {
    var regionname = "move_out-" + x;
    var region = document.getElementById(regionname);
    if (region.style.display == "none") {
      region.style.display = "";
    } else {
      region.style.display = "none";
    }
  }
  hookState.gotoHook(0);
}

/**
 * Used to expand all comments, hiding the preview and showing the comment.
 * @param {String} prefix The level of the comment -- one of
 *                        ('cl', 'file', 'inline')
 * @param {Integer} num_comments The number of comments to show
 */
function M_showAllComments(prefix, num_comments) {
  for (var i = 0; i < num_comments; i++) {
    document.getElementById(prefix + "-preview-" + i).style.visibility =
        "hidden";
    document.getElementById(prefix + "-comment-" + i).style.display = "";
  }
}

/**
 * Used to collpase all comments, showing the preview and hiding the comment.
 * @param {String} prefix The level of the comment -- one of
 *                        ('cl', 'file', 'inline')
 * @param {Integer} num_comments The number of comments to hide
 */
function M_hideAllComments(prefix, num_comments) {
  for (var i = 0; i < num_comments; i++) {
    document.getElementById(prefix + "-preview-" + i).style.visibility =
        "visible";
    document.getElementById(prefix + "-comment-" + i).style.display = "none";
  }
}

// Common methods for comment handling (changelist.html, file.html,
// comment_form.html)

/**
 * Toggles whether the specified comment is expanded/collapsed. Works in
 * the review form.
 * @param {String} prefix The prefix of the comment element name.
 * @param {String} suffix The suffix of the comment element name.
 */
function M_switchCommentCommon_(prefix, suffix) {
  prefix && (prefix +=  '-');
  suffix && (suffix =  '-' + suffix);
  var previewSpan = document.getElementById(prefix + 'preview' + suffix);
  var commentDiv = document.getElementById(prefix + 'comment' + suffix);
  if (!previewSpan || !commentDiv) {
    alert('Failed to find comment element: ' +
          prefix + 'comment' + suffix + '. Please send ' +
          'this message with the URL to the app owner');
    return;
  }
  if (previewSpan.style.visibility == 'hidden') {
    previewSpan.style.visibility = 'visible';
    commentDiv.style.display = 'none';
  } else {
    previewSpan.style.visibility = 'hidden';
    commentDiv.style.display = '';
  }
}

/**
 * Expands all inline comments.
 */
function M_expandAllInlineComments() {
  M_showAllInlineComments();
  var comments = document.getElementsByName("inline-comment");
  var commentsLength = comments.length;
  for (var i = 0; i < commentsLength; i++) {
    comments[i].style.display = "";
  }
  var previews = document.getElementsByName("inline-preview");
  var previewsLength = previews.length;
  for (var i = 0; i < previewsLength; i++) {
    previews[i].style.display = "none";
  }
}

/**
 * Collapses all inline comments.
 */
function M_collapseAllInlineComments() {
  M_showAllInlineComments();
  var comments = document.getElementsByName("inline-comment");
  var commentsLength = comments.length;
  for (var i = 0; i < commentsLength; i++) {
    comments[i].style.display = "none";
  }
  var previews = document.getElementsByName("inline-preview");
  var previewsLength = previews.length;
  for (var i = 0; i < previewsLength; i++) {
    previews[i].style.display = "";
  }
}

// Non-inline comment actions

/**
 * Sets up a reply form for a given comment (non-inline).
 * @param {String} author The author of the comment being replied to
 * @param {String} written_time The formatted time when that comment was written
 * @param {String} ccs A string containing the ccs to default to
 * @param {Integer} cid The number of the comment being replied to, so that the
 *                      form may be placed in the appropriate location
 * @param {String} prefix The level of the comment -- one of
 *                        ('cl', 'file', 'inline')
 * @param {Integer} opt_lineno (optional) The line number the comment should be
 *                                        attached to
 * @param {String} opt_snapshot (optional) The snapshot ID of the comment being
 *                                         replied to
 */
function M_replyToComment(author, written_time, ccs, cid, prefix, opt_lineno,
                          opt_snapshot) {
  var form = document.getElementById("comment-form-" + cid);
  if (!form) {
    form = document.getElementById("dareplyform");
    if (!form) {
      form = document.getElementById("daform"); // XXX for file.html
    }
    form = form.cloneNode(true);
    form.name = form.id = "comment-form-" + cid;
    M_createResizer_(form, cid);
    document.getElementById(prefix + "-comment-" + cid).appendChild(form);
  }
  form.style.display = "";
  form.reply_to.value = cid;
  form.ccs.value = ccs;
  if (typeof opt_lineno != 'undefined' && typeof opt_snapshot != 'undefined') {
    form.lineno.value = opt_lineno;
    form.snapshot.value = opt_snapshot;
  }
  form.text.value = "On " + written_time + ", " + author + " wrote:\n";
  var divs = document.getElementsByName("comment-text-" + cid);
  M_setValueFromDivs(divs, form.text);
  form.text.value += "\n";
  form.text.focus();
}


/**
/* TODO(andi): docstring
 */
function M_replyToMessage(message_id, written_time, author) {
  var form = document.getElementById('message-reply-form');
  form = form.cloneNode(true);
  if (typeof form.message == 'undefined') {
    var form_template = document.getElementById('message-reply-form');
    form = document.createElement('form');
    form.setAttribute('method', 'POST');
    form.setAttribute('action', form_template.getAttribute('action'));
    form.innerHTML = form_template.innerHTML;
  }
  container = document.getElementById('message-reply-'+message_id);
  container.appendChild(form);
  container.style.display = '';
  form.discard.onclick = function () {
    document.getElementById('message-reply-href-'+message_id).style.display = "";
    document.getElementById('message-reply-'+message_id).innerHTML = "";
    document.getElementById('message-reply-'+message_id).style.display = "none";
  }
  form.send_mail.id = 'message-reply-send-mail-'+message_id;
  var lbl = document.getElementById(form.send_mail.id).nextSibling.nextSibling;
  lbl.setAttribute('for', form.send_mail.id);
  form.message.value = "On " + written_time + ", " + author + " wrote:\n";
  var divs = document.getElementsByName("cl-message-" + message_id);
  M_setValueFromDivs(divs, form.message);
  form.message.value += "\n";
  form.message.focus();
  M_addTextResizer_(form);
  document.getElementById('message-reply-href-'+message_id).style.display = "none";
}


/**
 * Edits a non-inline draft comment.
 * @param {Integer} cid The number of the comment to be edited
 */
function M_editComment(cid) {
  var suffix = String(cid);
  var form = document.getElementById("comment-form-" + suffix);
  if (!form) {
    alert("Form " + suffix + " does not exist. Please send this message " +
          "with the URL to the app owner");
    return false;
  }
  var texts = document.getElementsByName("comment-text-" + suffix);
  var textsLength = texts.length;
  for (var i = 0; i < textsLength; i++) {
    texts[i].style.display = "none";
  }
  M_hideElement("edit-link-" + suffix);
  M_hideElement("undo-link-" + suffix);
  form.style.display = "";
  form.text.focus();
}

/**
 * Used to cancel comment editing, this will revert the text of the comment
 * and hide its form.
 * @param {Element} form The form that contains this comment
 * @param {Integer} cid The number of the comment being hidden
 */
function M_resetAndHideComment(form, cid) {
  form.text.blur();
  form.text.value = form.oldtext.value;
  form.style.display = "none";
  var texts = document.getElementsByName("comment-text-" + cid);
  var textsLength = texts.length;
  for (var i = 0; i < textsLength; i++) {
    texts[i].style.display = "";
  }
  M_showElement("edit-link-" + cid);
}

/**
 * Removing a draft comment is the same as setting its text contents to nothing.
 * @param {Element} form The form containing the draft comment to be discarded
 * @return true in order for the form submission to continue
 */
function M_removeComment(form) {
  form.text.value = "";
  return true;
}


// Inline comments (file.html)

/**
 * Helper method to assign an onclick handler to an inline 'Cancel' button.
 * @param {Element} form The form containing the cancel button
 * @param {Function} cancelHandler A function with one 'form' argument
 * @param {Array} opt_handlerParams An array whose first three elements are:
 *   {String} cid The number of the comment
 *   {String} lineno The line number of the comment
 *   {String} side 'a' or 'b'
 */
function M_assignToCancel_(form, cancelHandler, opt_handlerParams) {
  var elementsLength = form.elements.length;
  for (var i = 0; i < elementsLength; ++i) {
    if (form.elements[i].getAttribute("name") == "cancel") {
      form.elements[i].onclick = function() {
        if (typeof opt_handlerParams != "undefined") {
          var cid = opt_handlerParams[0];
          var lineno = opt_handlerParams[1];
          var side = opt_handlerParams[2];
          cancelHandler(form, cid, lineno, side);
        } else {
          cancelHandler(form);
        }
      };
      return;
    }
  }
}

/**
 * Helper method to assign an onclick handler to an inline '[+]' link.
 * @param {Element} form The form containing the resizer
 * @param {String} suffix The suffix of the comment form id: lineno-side
 */
function M_createResizer_(form, suffix) {
  if (!form.hasResizer) {
    var resizer = document.getElementById("resizer").cloneNode(true);
    resizer.onclick = function() {
      var form = document.getElementById("comment-form-" + suffix);
      if (!form) return;
      form.text.rows += 5;
      form.text.focus();
    };

    // Using form.elements would be far more concise, but this hack is
    // necessary because Konqueror/Safari don't populate form.elements at this
    // point if the form is cloned.
    var formContainer = null;
    for (formContainer = form.firstChild; formContainer;
         formContainer = formContainer.nextSibling) {
      if (formContainer.getAttribute &&
          formContainer.getAttribute("name") == "form-container") break;
    }
    if (!formContainer) return;

    for (var n = formContainer.firstChild; n; n = n.nextSibling) {
      if (n.nodeName == "TEXTAREA") {
        formContainer.insertBefore(resizer, n.nextSibling);
        resizer.style.display = "";
      }
    }
    form.hasResizer = true;
  }
}

/**
 * Like M_createResizer_(), but updates the form's first textarea field.
 * This is assumed not to be the last field.
 * @param {Element} form The form whose textarea field to update.
 */
function M_addTextResizer_(form) {
  var elementsLength = form.elements.length;
  for (var i = 0; i < elementsLength; ++i) {
    var node = form.elements[i];
    if (node.nodeName == "TEXTAREA") {
      var parent = M_getParent(node);
      var resizer = document.getElementById("resizer").cloneNode(true);
      var next = node.nextSibling;
      parent.insertBefore(resizer, next);
      resizer.onclick = function() {
	node.rows += 5;
	node.focus();
      };
      resizer.style.display = "";
      if (next && next.className == "resizer") { // Remove old resizer.
	parent.removeChild(next);
      }
      break;
    }
  }
}

/**
 * Helper method to assign an onclick handler to an inline 'Save' button.
 * @param {Element} form The form containing the save button
 * @param {String} cid The number of the comment
 * @param {String} lineno The line number of the comment
 * @param {String} side 'a' or 'b'
 */
function M_assignToSave_(form, cid, lineno, side) {
  var elementsLength = form.elements.length;
  for (var i = 0; i < elementsLength; ++i) {
    if (form.elements[i].getAttribute("name") == "save") {
      form.elements[i].onclick = function() {
        return M_submitInlineComment(form, cid, lineno, side);
      };
      return;
    }
  }
}

/**
 * Creates an inline comment at the given line number and side of the diff.
 * @param {String} lineno The line number of the new comment
 * @param {String} side Either 'a' or 'b' signifying the side of the diff
 */
function M_createInlineComment(lineno, side) {
  // The first field of the suffix is typically the cid, but we choose '-1'
  // here since the backend has not assigned the new comment a cid yet.
  var suffix = "-1-" + lineno + "-" + side;
  var form = document.getElementById("comment-form-" + suffix);
  if (!form) {
    form = document.getElementById("dainlineform").cloneNode(true);
    if (typeof form.save == "undefined") {
      // For Opera form elements of the cloned form aren't accessible
      // by name but using innerHTML works.
      form = document.createElement("form");
      form.innerHTML = document.getElementById("dainlineform").innerHTML;
    }
    form.name = form.id = "comment-form-" + suffix;
    M_assignToCancel_(form, M_removeTempInlineComment);
    M_createResizer_(form, suffix);
    M_assignToSave_(form, "-1", lineno, side);
    // There is a "text" node before the "div" node
    form.childNodes[1].setAttribute("name", "comment-border");
    var id = (side == 'a' ? "old" : "new") + "-line-" + lineno;
    var td = document.getElementById(id);
    td.appendChild(form);
    var tr = M_getParent(td);
    tr.setAttribute("name", "hook");
    hookState.updateHooks();
  }
  form.style.display = "";
  form.lineno.value = lineno;
  if (side == 'b') {
    form.snapshot.value = new_snapshot;
  } else {
    form.snapshot.value = old_snapshot;
  }
  form.side.value = side;
  var savedDraftKey = "new-" + form.lineno.value + "-" + form.snapshot.value;
  M_restoreDraftText_(savedDraftKey, form);
  form.text.focus();
  hookState.gotoHook(0);
}

/**
 * Removes a never-submitted 'Reply' inline comment from existence (created via
 * M_replyToInlineComment).
 * @param {Element} form The form that contains the comment to be removed
 * @param {String} cid The number of the comment
 * @param {String} lineno The line number of the comment
 * @param {String} side 'a' or 'b'
 */
function M_removeTempReplyInlineComment(form, cid, lineno, side) {
  var divInlineComment = M_getParent(form);
  var divCommentBorder = M_getParent(divInlineComment);
  var td = M_getParent(divCommentBorder);
  var tr = M_getParent(td);
  form.cancel.blur();
  // The order of the subsequent lines is sensitive to browser compatibility.
  var suffix = cid + "-" + lineno + "-" + side;
  M_saveDraftText_("reply-" + suffix, form.text.value);
  divInlineComment.removeChild(form);
  M_updateRowHook(tr);
}

/**
 * Removes a never-submitted inline comment from existence (created via
 * M_createInlineComment). Saves the existing text for the next time a draft is
 * created on the same line.
 * @param {Element} form The form that contains the comment to be removed
 */
function M_removeTempInlineComment(form) {
  var td = M_getParent(form);
  var tr = M_getParent(td);
  // The order of the subsequent lines is sensitive to browser compatibility.
  var savedDraftKey = "new-" + form.lineno.value + "-" + form.snapshot.value;
  M_saveDraftText_(savedDraftKey, form.text.value);
  form.cancel.blur();
  td.removeChild(form);
  M_updateRowHook(tr);
}

/**
 * Helper to edit a draft inline comment.
 * @param {String} cid The number of the comment
 * @param {String} lineno The line number of the comment
 * @param {String} side 'a' or 'b'
 * @return {Element} The form that contains the comment
 */
function M_editInlineCommentCommon_(cid, lineno, side) {
  var suffix = cid + "-" + lineno + "-" + side;
  var form = document.getElementById("comment-form-" + suffix);
  if (!form) {
    alert("Form " + suffix + " does not exist. Please send this message " +
          "with the URL to the app owner");
    return false;
  }
  M_createResizer_(form, suffix);
  var texts = document.getElementsByName("comment-text-" + suffix);
  var textsLength = texts.length;
  for (var i = 0; i < textsLength; i++) {
    texts[i].style.display = "none";
  }
  M_hideElement("edit-link-" + suffix);
  M_hideElement("undo-link-" + suffix);
  form.style.display = "";
  var parent = document.getElementById("inline-comment-" + suffix);
  if (parent && parent.style.display == "none") {
    M_switchInlineComment(cid, lineno, side);
  }
  form.text.focus();
  hookState.gotoHook(0);
  return form;
}

/**
 * Edits a draft inline comment.
 * @param {String} cid The number of the comment
 * @param {String} lineno The line number of the comment
 * @param {String} side 'a' or 'b'
 */
function M_editInlineComment(cid, lineno, side) {
  M_editInlineCommentCommon_(cid, lineno, side);
}

/**
 * Restores a canceled draft inline comment for editing.
 * @param {String} cid The number of the comment
 * @param {String} lineno The line number of the comment
 * @param {String} side 'a' or 'b'
 */
function M_restoreEditInlineComment(cid, lineno, side) {
  var form = M_editInlineCommentCommon_(cid, lineno, side);
  var savedDraftKey = "edit-" + cid + "-" + lineno + "-" + side;
  M_restoreDraftText_(savedDraftKey, form, false);
}

/**
 * Helper to reply to an inline comment.
 * @param {String} author The author of the comment being replied to
 * @param {String} written_time The formatted time when that comment was written
 * @param {String} ccs A string containing the ccs to default to
 * @param {String} cid The number of the comment being replied to, so that the
 *                     form may be placed in the appropriate location
 * @param {String} lineno The line number of the comment
 * @param {String} side 'a' or 'b'
 * @param {String} opt_reply The response to pre-fill with.
 * @param {Boolean} opt_submit This will submit the comment right after
 *                             creation. Only makes sense when opt_reply is set
 * @return {Element} The form that contains the comment
 */
function M_replyToInlineCommentCommon_(author, written_time, cid, lineno,
                                       side, opt_reply, opt_submit) {
  var suffix = cid + "-" + lineno + "-" + side;
  var form = document.getElementById("comment-form-" + suffix);
  if (!form) {
    form = document.getElementById("dainlineform").cloneNode(true);
    form.name = form.id = "comment-form-" + suffix;
    M_assignToCancel_(form, M_removeTempReplyInlineComment,
                      [cid, lineno, side]);
    M_assignToSave_(form, cid, lineno, side);
    M_createResizer_(form, suffix);
    var parent = document.getElementById("inline-comment-" + suffix);
    if (parent.style.display == "none") {
      M_switchInlineComment(cid, lineno, side);
    }
    parent.appendChild(form);
  }
  form.style.display = "";
  form.lineno.value = lineno;
  if (side == 'b') {
    form.snapshot.value = new_snapshot;
  } else {
    form.snapshot.value = old_snapshot;
  }
  form.side.value = side;
  if (!M_restoreDraftText_("reply-" + suffix, form, false) ||
      typeof opt_reply != "undefined") {
    form.text.value = "On " + written_time + ", " + author + " wrote:\n";
    var divs = document.getElementsByName("comment-text-" + suffix);
    M_setValueFromDivs(divs, form.text);
    form.text.value += "\n";
    if (typeof opt_reply != "undefined") {
      form.text.value += opt_reply;
    }
    if (opt_submit) {
      M_submitInlineComment(form, cid, lineno, side);
      return;
    }
  }
  form.text.focus();
  hookState.gotoHook(0);
  return form;
}

/**
 * Replies to an inline comment.
 * @param {String} author The author of the comment being replied to
 * @param {String} written_time The formatted time when that comment was written
 * @param {String} ccs A string containing the ccs to default to
 * @param {String} cid The number of the comment being replied to, so that the
 *                     form may be placed in the appropriate location
 * @param {String} lineno The line number of the comment
 * @param {String} side 'a' or 'b'
 * @param {String} opt_reply The response to pre-fill with.
 * @param {Boolean} opt_submit This will submit the comment right after
 *                             creation. Only makes sense when opt_reply is set
 */
function M_replyToInlineComment(author, written_time, cid, lineno, side,
                                opt_reply, opt_submit) {
  M_replyToInlineCommentCommon_(author, written_time, cid, lineno, side,
                                opt_reply, opt_submit);
}

/**
 * Restores a canceled draft inline comment for reply.
 * @param {String} author The author of the comment being replied to
 * @param {String} written_time The formatted time when that comment was written
 * @param {String} ccs A string containing the ccs to default to
 * @param {String} cid The number of the comment being replied to, so that the
 *                     form may be placed in the appropriate location
 * @param {String} lineno The line number of the comment
 * @param {String} side 'a' or 'b'
 */
function M_restoreReplyInlineComment(author, written_time, cid, lineno,
                                     side) {
  var form = M_replyToInlineCommentCommon_(author, written_time, cid,
                                           lineno, side);
  var savedDraftKey = "reply-" + cid + "-" + lineno + "-" + side;
  M_restoreDraftText_(savedDraftKey, form, false);
}

/**
 * Updates an inline comment td with the given HTML.
 * @param {Element} td The TD that contains the inline comment
 * @param {String} html The text to be put into .innerHTML of the td
 */
function M_updateInlineComment(td, html) {
  var tr = M_getParent(td);
  if (!tr) {
    alert("TD had no parent. Please notify the app owner.");
    return;
  }
  // The server sends back " " to make things empty, for Safari
  if (html.length <= 1) {
    td.innerHTML = "";
    M_updateRowHook(tr);
  } else {
    td.innerHTML = html;
    tr.name = "hook";
    hookState.updateHooks();
  }
}

/**
 * Updates a comment tr's name, depending on whether there are now comments
 * in it or not. Also updates the hook cache if required. Assumes that the
 * given TR already has name == "hook" and only tries to remove it if all
 * are empty.
 * @param {Element} tr The TR containing the potential comments
 */
function M_updateRowHook(tr) {
  if (!(tr && tr.cells)) return;
  // If all of the TR's cells are empty, remove the hook name
  var i = 0;
  var numCells = tr.cells.length;
  for (i = 0; i < numCells; i++) {
    if (tr.cells[i].innerHTML != "") {
      break;
    }
  }
  if (i == numCells) {
    tr.setAttribute("name",  "");
    hookState.updateHooks();
  }
  hookState.gotoHook(0);
}

/**
 * Submits an inline comment and updates the DOM in AJAX fashion with the new
 * comment data for that line.
 * @param {Element} form The form containing the submitting comment
 * @param {String} cid The number of the comment
 * @param {String} lineno The line number of the comment
 * @param {String} side 'a' or 'b'
 * @return true if AJAX fails and the form should be submitted the "old" way,
 *         or false if the form is submitted using AJAX, preventing the regular
 *         form submission from proceeding
 */
function M_submitInlineComment(form, cid, lineno, side) {
  var td = null;
  if (form.side.value == 'a') {
    td = document.getElementById("old-line-" + form.lineno.value);
  } else {
    td = document.getElementById("new-line-" + form.lineno.value);
  }
  if (!td) {
    alert("Could not find snapshot " + form.snapshot.value + "! Please let " +
          "the app owner know.");
    return true;
  }

  // Clear saved draft state for affected new, edited, and replied comments
  if (typeof cid != "undefined" && typeof lineno != "undefined" && side) {
    var suffix = cid + "-" + lineno + "-" + side;
    M_clearDraftText_("new-" + lineno + "-" + form.snapshot.value);
    M_clearDraftText_("edit-" + suffix);
    M_clearDraftText_("reply-" + suffix);
    M_hideElement("undo-link-" + suffix);
  }

  var httpreq = M_getXMLHttpRequest();
  if (!httpreq) {
    // No AJAX. Oh well. Go ahead and submit this the old way.
    return true;
  }

  // Konqueror jumps to a random location for some reason
  var scrollTop = M_getScrollTop(window);

  var aborted = false;

  reenable_form = function() {
    form.save.disabled = false;
    form.cancel.disabled = false;
    if (form.discard != null) {
      form.discard.disabled = false;
    }
    form.text.disabled = false;
    form.style.cursor = "auto";
  };

  // This timeout can potentially race with the request coming back OK. In
  // general, if it hasn't come back for 60 seconds, it won't ever come back.
  var httpreq_timeout = setTimeout(function() {
    aborted = true;
    httpreq.abort();
    reenable_form();
    alert("Comment could not be submitted for 60 seconds. Please ensure " +
          "connectivity (and that the server is up) and try again.");
  }, 60000);

  httpreq.onreadystatechange = function () {
    // Firefox 2.0, at least, runs this with readyState = 4 but all other
    // fields unset when the timeout aborts the request, against all
    // documentation.
    if (httpreq.readyState == 4 && !aborted) {
      clearTimeout(httpreq_timeout);
      if (httpreq.status == 200) {
        M_updateInlineComment(td, httpreq.responseText);
      } else {
        reenable_form();
        alert("An error occurred while trying to submit the comment: " +
              httpreq.statusText);
      }
      if (M_isKHTML()) {
        window.scrollTo(0, scrollTop);
      }
    }
  }
  httpreq.open("POST", "/inline_draft", true);
  httpreq.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
  var req = [];
  var len = form.elements.length;
  for (var i = 0; i < len; i++) {
    var element = form.elements[i];
    if (element.type == "hidden" || element.type == "textarea") {
      req.push(element.name + "=" + encodeURIComponent(element.value));
    }
  }
  req.push("side=" + side);

  // Disable forever. If this succeeds, then the form will end up getting
  // rewritten, and if it fails, the page should get a refresh anyways.
  form.save.blur();
  form.save.disabled = true;
  form.cancel.blur();
  form.cancel.disabled = true;
  if (form.discard != null) {
    form.discard.blur();
    form.discard.disabled = true;
  }
  form.text.blur();
  form.text.disabled = true;
  form.style.cursor = "wait";

  // Send the request
  httpreq.send(req.join("&"));

  // No need to resubmit this form.
  return false;
}

/**
 * Removes a draft inline comment.
 * @param {Element} form The form that contains the comment to be removed
 * @param {String} cid The number of the comment
 * @param {String} lineno The line number of the comment
 * @param {String} side 'a' or 'b'
 */
function M_removeInlineComment(form, cid, lineno, side) {
  // Update state to save the canceled edit text
  var snapshot = side == "a" ? old_snapshot : new_snapshot;
  var savedDraftKey = "new-" + lineno + "-" + snapshot;
  var savedText = form.text.value;
  form.text.value = "";
  var ret = M_submitInlineComment(form, cid, lineno, side);
  M_saveDraftText_(savedDraftKey, savedText);
  return ret;
}

/**
 * Combines all the divs from a single comment (generated by multiple buckets)
 * and undoes the escaping work done by Django filters, and inserts the result
 * into a given textarea.
 * @param {Array} divs An array of div elements to be combined
 * @param {Element} text The textarea whose value needs to be updated
 */
function M_setValueFromDivs(divs, text) {
  lines = [];
  var divsLength = divs.length;
  for (var i = 0; i < divsLength; i++) {
    lines = lines.concat(divs[i].innerHTML.split("\n"));
  }
  for (var i = 0; i < lines.length; i++) {
    // Undo the <a> tags added by urlize and enliven
    lines[i] = lines[i].replace(/<a[^>]*>/ig, "");
    lines[i] = lines[i].replace(/<\/a>/ig, "");
    // Undo the escape Django filter
    lines[i] = lines[i].replace(/&gt;/ig, ">");
    lines[i] = lines[i].replace(/&lt;/ig, "<");
    lines[i] = lines[i].replace(/&quot;/ig, "\"");
    lines[i] = lines[i].replace(/&#39;/ig, "'");
    lines[i] = lines[i].replace(/&amp;/ig, "&"); // Must be last
    text.value += "> " + lines[i] + "\n";
  }
}

/**
 * Undo an edit of a draft inline comment, i.e. discard changes.
 * @param {Element} form The form containing the edits
 * @param {String} cid The number of the comment
 * @param {String} lineno The line number of the comment
 * @param {String} side 'a' or 'b'
 */
function M_resetAndHideInlineComment(form, cid, lineno, side) {
  // Update canceled edit state
  var suffix = cid + "-" + lineno + "-" + side;
  M_saveDraftText_("edit-" + suffix, form.text.value);
  if (form.text.value != form.oldtext.value) {
    M_showElement("undo-link-" + suffix);
  }

  form.text.blur();
  form.text.value = form.oldtext.value;
  form.style.display = "none";
  var texts = document.getElementsByName("comment-text-" + suffix);
  var textsLength = texts.length;
  for (var i = 0; i < textsLength; i++) {
    texts[i].style.display = "";
  }
  M_showElement("edit-link-" + suffix);
  hookState.gotoHook(0);
}

/**
 * Toggles whether we display quoted text or not, both for inline and regular
 * comments. Inline comments will have lineno and side defined.
 * @param {String} cid The comment number
 * @param {String} bid The bucket number in that comment
 * @param {String} lineno (optional) Line number of the comment
 * @param {String} side (optional) 'a' or 'b'
 */
function M_switchQuotedText(cid, bid, lineno, side) {
  var tmp = ""
  if (typeof lineno != 'undefined' && typeof side != 'undefined')
    tmp = "-" + lineno + "-" + side;
  var div = document.getElementById("comment-text-" + cid + tmp + "-" + bid)
  if (div.style.display == "none") {
    div.style.display = "";
  } else {
    div.style.display = "none";
  }
  if (tmp != "") {
    hookState.gotoHook(0);
  }
}

/**
 * Handler for the double click event in the code table element. Creates a new
 * inline comment for that line of code on the right side of the diff.
 * @param {Event} evt The event object for this double-click event
 */
function M_handleTableDblClick(evt) {
  if (!logged_in) {
    if (!login_warned) {
      login_warned = true;
      alert("Please sign in to enter inline comments.");
    }
    return;
  }
  var evt = evt ? evt : (event ? event : null);
  var target = M_getEventTarget(evt);
  if (target.tagName == 'INPUT' || target.tagName == 'TEXTAREA') {
    return;
  }
  while (target != null && target.tagName != 'TD') {
    target = M_getParent(target);
  }
  if (target == null) {
    return;
  }
  var side = null;
  if (target.id.substr(0, 7) == "newcode") {
    side = 'b';
  } else if (target.id.substr(0, 7) == "oldcode") {
    side = 'a';
  }
  if (side != null) {
    M_createInlineComment(parseInt(target.id.substr(7)), side);
  }
}

/**
 * Makes all inline comments visible. This is the default view.
 */
function M_showAllInlineComments() {
  var hide = document.getElementById("hide-all-inline");
  var show = document.getElementById("show-all-inline");
  hide.style.display = "";
  var elements = document.getElementsByName("comment-border");
  var elementsLength = elements.length;
  for (var i = 0; i < elementsLength; i++) {
    var tr = M_getParent(M_getParent(elements[i]));
    tr.style.display = "";
    tr.name = "hook";
  }
  show.style.display = "none";
  hookState.updateHooks();
}

/**
 * Hides all inline comments, to make code easier ot read.
 */
function M_hideAllInlineComments() {
  var hide = document.getElementById("hide-all-inline");
  var show = document.getElementById("show-all-inline");
  show.style.display = "";
  var elements = document.getElementsByName("comment-border");
  var elementsLength = elements.length;
  for (var i = 0; i < elementsLength; i++) {
    var tr = M_getParent(M_getParent(elements[i]));
    tr.style.display = "none";
    tr.name = "";
  }
  hide.style.display = "none";
  hookState.updateHooks();
}

/**
 * Flips between making inline comments visible and invisible.
 */
function M_toggleAllInlineComments() {
  var show = document.getElementById("show-all-inline");
  if (!show) {
    return;
  }
  if (show.style.display == "none") {
    M_hideAllInlineComments();
  } else {
    M_showAllInlineComments();
  }
}

// File view keyboard navigation

/**
 * M_HookState class. Keeps track of the current 'hook' that we are on and
 * responds to n/p/N/P events.
 * @param {Window} win The window that the table is in.
 * @constructor
 */
function M_HookState(win) {
  /**
   * -2 == top of page; -1 == diff; or index into hooks array
   * @type Integer
   */
  this.hookPos = -2;

  /**
   * A cache of visible table rows with tr.name == "hook"
   * @type Array
   */
  this.visibleHookCache = [];

  /**
   * The indicator element that we move around
   * @type Element
   */
  this.indicator = document.getElementById("hook-sel");

  /**
   * Caches whether we are in an IE browser
   * @type Boolean
   */
  this.isIE = M_isIE();

  /**
   * The window that the table with the hooks is in
   * @type Window
   */
  this.win = win;
}

/**
 * Find all the hook locations in a browser-portable fashion, and store them
 * in a cache.
 * @return Array of TR elements.
 */
M_HookState.prototype.computeHooks_ = function() {
  var allHooks = null;
  if (this.isIE) {
    // IE only recognizes the 'name' attribute on tags that are supposed to
    // have one, such as... not TR.
    var tmpHooks = document.getElementsByTagName("TR");
    var tmpHooksLength = tmpHooks.length;
    allHooks = [];
    for (var i = 0; i < tmpHooksLength; i++) {
      if (tmpHooks[i].name == "hook") {
        allHooks.push(tmpHooks[i]);
      }
    }
  } else {
    allHooks = document.getElementsByName("hook");
  }
  var visibleHooks = [];
  var allHooksLength = allHooks.length;
  for (var i = 0; i < allHooksLength; i++) {
    var hook = allHooks[i];
    if (hook.style.display == "") {
      visibleHooks.push(hook);
    }
  }
  this.visibleHookCache = visibleHooks;
  return visibleHooks;
};

/**
 * Recompute all the hook positions, update the hookPos, and update the
 * indicator's position if necessary, but do not scroll.
 */
M_HookState.prototype.updateHooks = function() {
  var curHook = null;
  if (this.hookPos >= 0 && this.hookPos < this.visibleHookCache.length) {
    curHook = this.visibleHookCache[this.hookPos];
  }
  this.computeHooks_();
  var newHookPos = -1;
  if (curHook != null) {
    for (var i = 0; i < this.visibleHookCache.length; i++) {
      if (this.visibleHookCache[i] == curHook) {
        newHookPos = i;
        break;
      }
    }
  }
  if (newHookPos != -1) {
    this.hookPos = newHookPos;
  }
  this.gotoHook(0);
};

/**
 * Update the indicator's position to be at the top of the table row.
 * @param {Element} tr The tr whose top the indicator will be lined up with.
 */
M_HookState.prototype.updateIndicator_ = function(tr) {
  // Find out where the table's top is, and add one so that when we align
  // the position indicator, it takes off 1px from one tr and 1px from another.
  // This must be computed every time since the top of the table may move due
  // to window resizing.
  var tableTop = M_getPageOffsetTop(document.getElementById("table-top")) + 1;

  this.indicator.style.top = String(M_getPageOffsetTop(tr) -
                                    tableTop) + "px";
  var totWidth = 0;
  var numCells = tr.cells.length;
  for (var i = 0; i < numCells; i++) {
    totWidth += tr.cells[i].clientWidth;
  }
  this.indicator.style.left = "0px";
  this.indicator.style.width = totWidth + "px";
  this.indicator.style.display = "";
};

/**
 * Update the indicator's position, and potentially scroll to the proper
 * location. Computes the new position based on current scroll position, and
 * whether the previously selected hook was visible.
 * @param {Integer} direction Scroll direction: -1 for up only, 1 for down only,
 *                            0 for no scrolling.
 */
M_HookState.prototype.gotoHook = function(direction) {
  var hooks = this.visibleHookCache;

  // Hide the current selection image
  this.indicator.style.display = "none";

  // Add a border to all td's in the selected row
  if (this.hookPos < -1) {
    if (direction != 0) {
      window.scrollTo(0, 0);
    }
    this.hookPos = -2;
  } else if (this.hookPos == -1) {
    var diffs = document.getElementsByName("diffs");
    if (diffs && diffs.length >= 1) {
      diffs = diffs[0];
    }
    if (diffs && direction != 0) {
      window.scrollTo(0, M_getPageOffsetTop(diffs));
    }
    this.updateIndicator_(document.getElementById("thecode").rows[0]);
  } else {
    if (this.hookPos < hooks.length) {
      var hook = hooks[this.hookPos];
      for (var i = 0; i < hook.cells.length; i++) {
        var td = hook.cells[i];
        if (td.id != null && td.id != "") {
          if (direction != 0) {
            M_scrollIntoView(this.win, td, direction);
          }
          break;
        }
      }
      // Found one!
      this.updateIndicator_(hook);
    } else {
      if (direction != 0) {
        window.scrollTo(0, document.body.offsetHeight);
      }
      this.hookPos = hooks.length;
      var thecode = document.getElementById("thecode");
      this.updateIndicator_(thecode.rows[thecode.rows.length - 1]);
    }
  }
};

/**
 * Set this.hookPos to the next desired hook.
 * @param {Boolean} findComment Whether to look only for comment hooks
 */
M_HookState.prototype.incrementHook_ = function(findComment) {
  var hooks = this.visibleHookCache;
  if (findComment) {
    this.hookPos = Math.max(0, this.hookPos + 1);
    while (this.hookPos < hooks.length &&
           hooks[this.hookPos].className != "inline-comments") {
      this.hookPos++;
    }
  } else {
    this.hookPos = Math.min(hooks.length, this.hookPos + 1);
  }
};

/**
 * Set this.hookPos to the previous desired hook.
 * @param {Boolean} findComment Whether to look only for comment hooks
 */
M_HookState.prototype.decrementHook_ = function(findComment) {
  var hooks = this.visibleHookCache;
  if (findComment) {
    this.hookPos = Math.min(hooks.length - 1, this.hookPos - 1);
    while (this.hookPos >= 0 &&
           hooks[this.hookPos].className != "inline-comments") {
      this.hookPos--;
    }
  } else {
    this.hookPos = Math.max(-2, this.hookPos - 1);
  }
};

/**
 * Find the first document element in sorted array elts whose vertical position
 * is greater than the given height from the top of the document. Optionally
 * look only for comment elements.
 *
 * @param {Integer} height The height in pixels from the top
 * @param {Array.<Element>} elts Document elements
 * @param {Boolean} findComment Whether to look only for comment elements
 * @return {Integer} The index of such an element, or elts.length otherwise
 */
function M_findElementAfter_(height, elts, findComment) {
  for (var i = 0; i < elts.length; ++i) {
    if (M_getPageOffsetTop(elts[i]) > height) {
      if (!findComment || elts[i].className == "inline-comments") {
        return i;
      }
    }
  }
  return elts.length;
}

/**
 * Find the last document element in sorted array elts whose vertical position
 * is less than the given height from the top of the document. Optionally
 * look only for comment elements.
 *
 * @param {Integer} height The height in pixels from the top
 * @param {Array.<Element>} elts Document elements
 * @param {Boolean} findComment Whether to look only for comment elements
 * @return {Integer} The index of such an element, or -1 otherwise
 */
function M_findElementBefore_(height, elts, findComment) {
  for (var i = elts.length - 1; i >= 0; --i) {
    if (M_getPageOffsetTop(elts[i]) < height) {
      if (!findComment || elts[i].className == "inline-comments") {
        return i;
      }
    }
  }
  return -1;
}

/**
 * Move to the next hook indicator and scroll.
 * @param opt_findComment {Boolean} Whether to look only for comment hooks
 */
M_HookState.prototype.gotoNextHook = function(opt_findComment) {
  // If the current hook is not on the page, select the first hook that is
  // either on the screen or below.
  var hooks = this.visibleHookCache;
  var diffs = document.getElementsByName("diffs");
  var thecode = document.getElementById("thecode");
  var findComment = Boolean(opt_findComment);
  if (diffs && diffs.length >= 1) {
    diffs = diffs[0];
  }
  if (this.hookPos >= 0 && this.hookPos < hooks.length &&
      M_isElementVisible(this.win, hooks[this.hookPos].cells[0])) {
    this.incrementHook_(findComment);
  } else if (this.hookPos == -2 &&
             (M_isElementVisible(this.win, diffs) ||
              M_getScrollTop(this.win) < M_getPageOffsetTop(diffs))) {
    this.incrementHook_(findComment)
  } else if (this.hookPos < hooks.length || (this.hookPos >= hooks.length &&
             !M_isElementVisible(
               this.win, thecode.rows[thecode.rows.length - 1].cells[0]))) {
    var scrollTop = M_getScrollTop(this.win);
    this.hookPos = M_findElementAfter_(scrollTop, hooks, findComment);
  }
  this.gotoHook(1);
};

/**
 * Move to the previous hook indicator and scroll.
 * @param opt_findComment {Boolean} Whether to look only for comment hooks
 */
M_HookState.prototype.gotoPrevHook = function(opt_findComment) {
  // If the current hook is not on the page, select the last hook that is
  // above the bottom of the screen window.
  var hooks = this.visibleHookCache;
  var diffs = document.getElementsByName("diffs");
  var findComment = Boolean(opt_findComment);
  if (diffs && diffs.length >= 1) {
    diffs = diffs[0];
  }
  if (this.hookPos == 0 && findComment) {
    this.hookPos = -2;
  } else if (this.hookPos >= 0 && this.hookPos < hooks.length &&
      M_isElementVisible(this.win, hooks[this.hookPos].cells[0])) {
    this.decrementHook_(findComment);
  } else if (this.hookPos > hooks.length) {
    this.hookPos = hooks.length;
  } else if (this.hookPos == -1 && M_isElementVisible(this.win, diffs)) {
    this.decrementHook_(findComment);
  } else if (this.hookPos == -2 && M_getScrollTop(this.win) == 0) {
  } else {
    var scrollBot = M_getScrollTop(this.win) + M_getWindowHeight(this.win);
    this.hookPos = M_findElementBefore_(scrollBot, hooks, findComment);
  }
  // The top of the diffs table is irrelevant if we want comment hooks.
  if (findComment && this.hookPos <= -1) {
    this.hookPos = -2;
  }
  this.gotoHook(-1);
};

/**
 * If the currently selected hook is a comment, either respond to it or edit
 * the draft if there is one already. Prefer the right side of the table.
 */
M_HookState.prototype.respond = function() {
  var hooks = this.visibleHookCache;
  if (this.hookPos >= 0 && this.hookPos < hooks.length &&
      M_isElementVisible(this.win, hooks[this.hookPos].cells[0])) {
    // Go through this tr and try responding to the last comment. The general
    // hope is that these are returned in DOM order
    var comments = hooks[this.hookPos].getElementsByTagName("div");
    var commentsLength = comments.length;
    if (comments && commentsLength > 0) {
      var last = null;
      for (var i = commentsLength - 1; i >= 0; i--) {
        if (comments[i].getAttribute("name") == "comment-border") {
          last = comments[i];
          break;
        }
      }
      if (last) {
        var links = last.getElementsByTagName("a");
        if (links) {
          for (var i = links.length - 1; i >= 0; i--) {
            if (links[i].getAttribute("name") == "comment-reply" &&
                links[i].style.display != "none") {
              document.location.href = links[i].href;
              return;
            }
          }
        }
      }
    }
    // Create a comment at this line
    // TODO: Implement this in a sane fashion, e.g. opens up a comment
    // at the end of the diff chunk.
    /*
    var tr = hooks[this.hookPos];
    for (var i = tr.cells.length - 1; i >= 0; i--) {
      if (tr.cells[i].id.substr(0, 7) == "newcode") {
        createInlineComment(parseInt(tr.cells[i].id.substr(7)), 'b');
        return;
      } else if (tr.cells[i].id.substr(0, 7) == "oldcode") {
        createInlineComment(parseInt(tr.cells[i].id.substr(7)), 'a');
        return;
      }
    }
    */
  }
};

// Intra-line diff handling

/**
 * IntraLineDiff class. Initializes structures to keep track of highlighting
 * state.
 * @constructor
 */
function M_IntraLineDiff() {
  /**
   * Whether we are showing intra-line changes or not
   * @type Boolean
   */
  this.intraLine = true;

  /**
   * "oldreplace" css rule
   * @type CSSStyleRule
   */
  this.oldReplace = null;

  /**
   * "oldlight" css rule
   * @type CSSStyleRule
   */
  this.oldLight = null;

  /**
   * "newreplace" css rule
   * @type CSSStyleRule
   */
  this.newReplace = null;

  /**
   * "newlight" css rule
   * @type CSSStyleRule
   */
  this.newLight = null;

  /**
   * backup of the "oldreplace" css rule's background color
   * @type DOMString
   */
  this.saveOldReplaceBkgClr = null;

  /**
   * backup of the "newreplace" css rule's background color
   * @type DOMString
   */
  this.saveNewReplaceBkgClr = null;

  /**
   * "oldreplace1" css rule's background color
   * @type DOMString
   */
  this.oldIntraBkgClr = null;

  /**
   * "newreplace1" css rule's background color
   * @type DOMString
   */
  this.newIntraBkgClr = null;

  this.findStyles_();
}

/**
 * Finds the styles in the document and keeps references to them in this class
 * instance.
 */
M_IntraLineDiff.prototype.findStyles_ = function() {
  var ss = document.styleSheets[0];
  var rules = [];
  if (ss.cssRules) {
    rules = ss.cssRules;
  } else if (ss.rules) {
    rules = ss.rules;
  }
  for (var i = 0; i < rules.length; i++) {
    var rule = rules[i];
    if (rule.selectorText == ".oldreplace1") {
      this.oldIntraBkgClr = rule.style.backgroundColor;
    } else if (rule.selectorText == ".newreplace1") {
      this.newIntraBkgClr = rule.style.backgroundColor;
    } else if (rule.selectorText == ".oldreplace") {
      this.oldReplace = rule;
      this.saveOldReplaceBkgClr = this.oldReplace.style.backgroundColor;
    } else if (rule.selectorText == ".newreplace") {
      this.newReplace = rule;
      this.saveNewReplaceBkgClr = this.newReplace.style.backgroundColor;
    } else if (rule.selectorText == ".oldlight") {
      this.oldLight = rule;
    } else if (rule.selectorText == ".newlight") {
      this.newLight = rule;
    }
  }
};

/**
 * Toggle the highlighting of the intra line diffs, alternatively turning
 * them on and off.
 */
M_IntraLineDiff.prototype.toggle = function() {
  if (this.intraLine) {
    this.oldReplace.style.backgroundColor = this.oldIntraBkgClr;
    this.oldLight.style.backgroundColor = this.oldIntraBkgClr;
    this.newReplace.style.backgroundColor = this.newIntraBkgClr;
    this.newLight.style.backgroundColor = this.newIntraBkgClr;
    this.intraLine = false;
  } else {
    this.oldReplace.style.backgroundColor = this.saveOldReplaceBkgClr;
    this.oldLight.style.backgroundColor = this.saveOldReplaceBkgClr;
    this.newReplace.style.backgroundColor = this.saveNewReplaceBkgClr;
    this.newLight.style.backgroundColor = this.saveNewReplaceBkgClr;
    this.intraLine = true;
  }
};

/**
 * A click handler common to just about every page, set in global.html.
 * @param {Event} evt The event object that triggered this handler.
 * @return false if the event was handled.
 */
function M_clickCommon(evt) {
  if (helpDisplayed) {
    var help = document.getElementById("help");
    help.style.display = "none";
    helpDisplayed = false;
    return false;
  }
  return true;
}

/**
 * Common keypress handling code for all pages.
 * @param {Event} evt The event object that triggered this callback
 * @param {function(string)} handler Handles the specific key pressed;
 *        returns false if the key press was handled.
 * @param {function(Event, Node, int, string)} input_handler
 *        Handles the event in case that the event source is an input field.
 *        returns false if the key press was handled.
 * @return false if the event was handled
 */
function M_keyPressCommon(evt, handler, input_handler) {
  var evt = (evt) ? evt : ((event) ? event : null);
  if (evt) {
    var src = M_getEventTarget(evt);
    var nodename = src.nodeName;
    var key, code;
    if (evt.keyCode) {
      code = evt.keyCode;
    } else if (evt.which) {
      code = evt.which;
    }
    key = String.fromCharCode(code);
    if (nodename == "TEXTAREA" || nodename == "INPUT" ) {
      if (typeof input_handler != 'undefined') {
        return input_handler(evt, src, code, key);
      }
      return true;
    }
    if (evt.altKey || evt.altLeft ||
        evt.ctrlKey || evt.ctrlLeft ||
        evt.metaKey) {
      // Ignore if any modifier keys are set
      return true;
    }
    if (key == '?' ||
	code == (window.event ? 27 /* ESC */ : evt.DOM_VK_ESCAPE)) {
      var help = document.getElementById("help");
      if (help && typeof helpDisplayed != "undefined") {
	// Only allow the help to be turned on with the ? key.
	if (helpDisplayed || key == '?') {
	  helpDisplayed = !helpDisplayed;
	}
	help.style.display = helpDisplayed ? "" : "none";
      }
      return false;
    }
    return handler(key);
  }
  return true;
}

/**
 * Helper event handler for the keypress event in a comment textarea.
 * @param {Event} evt The event object that triggered this callback
 * @param {Node} src The textarea document element
 * @param {int} code The key code of the key press
 * @param {String} key The string describing the key press
 * @return false if the key press was handled
 */
function M_commentTextKeyPress_(evt, src, code, key) {
  if (src.nodeName == "TEXTAREA") {
    if (evt.ctrlKey || evt.ctrlLeft) {
      if (key == 's' || code == 19 /* ASCII code for ^S */) {
        // Save the form corresponding to this text area.
        M_disableCarefulUnload();
        if (src.form.save.onclick) {
          return src.form.save.onclick();
        } else {
          src.form.submit();
          return false;
        }
      }
    } else if (evt.altKey || evt.altLeft) {
    } else if (evt.shiftKey || evt.shiftLeft) {
    } else if (evt.metaKey) {
    } else {
      if (code == (window.event ? 27 /* ASCII code for Escape */
                                : evt.DOM_VK_ESCAPE)) {
        return src.form.cancel.onclick();
      }
    }
  }
  return true;
}

/**
 * Event handler for the keypress event in the file view.
 * @param {Event} evt The event object that triggered this callback
 * @return false if the key press was handled
 */
function M_keyPress(evt) {
  return M_keyPressCommon(evt, function(key) {
    if (key == 'n') {
      // next diff
      if (hookState) hookState.gotoNextHook();
    } else if (key == 'p') {
      // previous diff
      if (hookState) hookState.gotoPrevHook();
    } else if (key == 'N') {
      // next comment
      if (hookState) hookState.gotoNextHook(true);
    } else if (key == 'P') {
      // previous comment
      if (hookState) hookState.gotoPrevHook(true);
    } else if (key == 'j') {
      // next file
      var nextFile = document.getElementById('nextFile');
      if (nextFile) {
        document.location.href = nextFile.href;
      } else {
        M_upToChangelist();
      }
    } else if (key == 'k') {
      // prev file
      var prevFile = document.getElementById('prevFile');
      if (prevFile) {
        document.location.href = prevFile.href;
      } else {
        M_upToChangelist();
      }
    } else if (key == 'm') {
      document.location.href = publish_link;
    } else if (key == 'u') {
      // up to CL
      M_upToChangelist();
    } else if (key == 'i') {
      // toggle intra line diff
      if (intraLineDiff) intraLineDiff.toggle();
    } else if (key == 's') {
      // toggle show/hide inline comments
      M_toggleAllInlineComments();
    } else if (key == 'e') {
      M_expandAllInlineComments();
    } else if (key == 'c') {
      M_collapseAllInlineComments();
    } else if (key == '\r' || key == '\n') {
      // respond to current comment
      if (hookState) hookState.respond();
    } else {
      return true;
    }
    return false;
  }, M_commentTextKeyPress_);
}

/**
 * Event handler for the keypress event in the changelist view.
 * @param {Event} evt The event object that triggered this callback
 * @return false if the key press was handled
 */
function M_changelistKeyPress(evt) {
  return M_keyPressCommon(evt, function(key) {
    if (key == 'o' || key == '\r' || key == '\n') {
      if (dashboardState) {
	var child = dashboardState.curTR.cells[1].firstChild;
	while (child && child.nextSibling && child.nodeName != "A") {
	  child = child.nextSibling;
	}
	if (child && child.nodeName == "A") {
	  location.href = child.href;
	}
      }
    } else if (key == 'i') {
      if (dashboardState) {
	var child = dashboardState.curTR.cells[2].firstChild;
	while (child && child.nextSibling &&
	       (child.nodeName != "A" || child.style.display == "none")) {
	  child = child.nextSibling;
	}
	if (child && child.nodeName == "A") {
	  child.onclick();
	}
      }
    } else if (key == 'I') {
      if (M_CL_hiddenInlineDiffCount == M_CL_maxHiddenInlineDiffCount) {
        M_showAllDiffs(M_CL_maxHiddenInlineDiffCount);
      } else {
	M_hideAllDiffs(M_CL_maxHiddenInlineDiffCount);
      }
    } else if (key == 'k') {
      if (dashboardState) dashboardState.gotoPrev();
    } else if (key == 'j') {
      if (dashboardState) dashboardState.gotoNext();
    } else if (key == 'm') {
      document.location.href = publish_link;
    } else if (key == 'u') {
      // back to dashboard
      document.location.href = '/';
    } else {
      return true;
    }
    return false;
  });
}

/**
 * Goes from the file view back up to the changelist view.
 */
function M_upToChangelist() {
  var upCL = document.getElementById('upCL');
  if (upCL) {
    document.location.href = upCL.href;
  }
}

/**
 * Asynchronously request static analysis warnings as comments.
 * @param {String} cl The current changelist
 * @param {String} depot_path The id of the target element
 * @param {String} a The version number of the left side to be analyzed
 * @param {String} b The version number of the right side to be analyzed
 */
function M_getBugbotComments(cl, depot_path, a, b) {
  var httpreq = M_getXMLHttpRequest();
  if (!httpreq) {
    return;
  }

  // Konqueror jumps to a random location for some reason
  var scrollTop = M_getScrollTop(window);

  httpreq.onreadystatechange = function () {
    // Firefox 2.0, at least, runs this with readyState = 4 but all other
    // fields unset when the timeout aborts the request, against all
    // documentation.
    if (httpreq.readyState == 4) {
      if (httpreq.status == 200) {
        M_updateWarningStatus(httpreq.responseText);
      }
      if (M_isKHTML()) {
        window.scrollTo(0, scrollTop);
      }
    }
  }
  httpreq.open("GET", "/warnings/" + cl + "/" + depot_path +
               "?a=" + a + "&b=" + b, true);
  httpreq.send(null);
}

/**
 * Updates a warning status td with the given HTML.
 * @param {String} result The new html to replace the existing content
 */
function M_updateWarningStatus(result) {
  var elem = document.getElementById("warnings");
  elem.innerHTML = result;
  if (hookState) hookState.updateHooks();
}

/* Ripped off from Caribou */
var M_CONFIRM_DISCARD_NEW_MSG = "Your draft comment has not been saved " +
                                "or sent.\n\nDiscard your comment?";

var M_useCarefulUnload = true;


/**
 * Return an alert if the specified textarea is visible and non-empty.
 */
function M_carefulUnload(text_area_id) {
  return function () {
    var text_area = document.getElementById(text_area_id);
    if (!text_area) return;
    var text_parent = M_getParent(text_area);
    if (M_useCarefulUnload && text_area.style.display != "none"
                           && text_parent.style.display != "none"
                           && goog.string.trim(text_area.value)) {
      return M_CONFIRM_DISCARD_NEW_MSG;
    }
  };
}

function M_disableCarefulUnload() {
  M_useCarefulUnload = false;
}

// History Table

/**
 * Toggles visibility of the snapshots that belong to the given parent.
 * @param {String} parent The parent's index
 * @param {Boolean} opt_show If present, whether to show or hide the group
 */
function M_toggleGroup(parent, opt_show) {
  var children = M_historyChildren[parent];
  if (children.length == 1) {  // No children.
    return;
  }

  var show = (typeof opt_show != "undefined") ? opt_show :
    (document.getElementById("history-" + children[1]).style.display != "");
  for (var i = 1; i < children.length; i++) {
    var child = document.getElementById("history-" + children[i]);
    child.style.display = show ? "" : "none";
  }

  var arrow = document.getElementById("triangle-" + parent);
  if (arrow) {
    arrow.className = "triangle-" + (show ? "open" : "closed");
  }
}

/**
 * Makes the given groups visible.
 * @param {Array.<Number>} parents The indexes of the parents of the groups
 *     to show.
 */
function M_expandGroups(parents) {
  for (var i = 0; i < parents.length; i++) {
    M_toggleGroup(parents[i], true);
  }
  document.getElementById("history-expander").style.display = "none";
  document.getElementById("history-collapser").style.display = "";
}

/**
 * Hides the given parents, except for groups that contain the
 * selected radio buttons.
 * @param {Array.<Number>} parents The indexes of the parents of the groups
 *     to hide.
 */
function M_collapseGroups(parents) {
  // Find the selected snapshots
  var parentsToLeaveOpen = {};
  var form = document.getElementById("history-form");
  var formLength = form.a.length;
  for (var i = 0; i < formLength; i++) {
    if (form.a[i].checked || form.b[i].checked) {
      var element = "history-" + form.a[i].value;
      var name = document.getElementById(element).getAttribute("name");
      if (name != "parent") {
        // The name of a child is "parent-%d" % parent_index.
        var parentIndex = Number(name.match(/parent-(\d+)/)[1]);
        parentsToLeaveOpen[parentIndex] = true;
      }
    }
  }

  // Collapse the parents we need to collapse.
  for (var i = 0; i < parents.length; i++) {
    if (!(parents[i] in parentsToLeaveOpen)) {
      M_toggleGroup(parents[i], false);
    }
  }
  document.getElementById("history-expander").style.display = "";
  document.getElementById("history-collapser").style.display = "none";
}

/**
 * Expands the reverted files section of the files list in the changelist view.
 *
 * @param {String} tableid The id of the table element that contains hidden TR's
 * @param {String} hide The id of the element to hide after this is completed.
 */
function M_showRevertedFiles(tableid, hide) {
  var table = document.getElementById(tableid);
  if (!table) return;
  var rowsLength = table.rows.length;
  for (var i = 0; i < rowsLength; i++) {
    var row = table.rows[i];
    if (row.getAttribute("name") == "afile") row.style.display = "";
  }
  if (dashboardState) dashboardState.initialize();
  var h = document.getElementById(hide);
  if (h) h.style.display = "none";
}

// Undo draft cancel

/**
 * An associative array mapping keys that identify inline comments to draft
 * text values.
 *   New inline comments have keys 'new-lineno-snapshot_id'
 *   Edit inline comments have keys 'edit-cid-lineno-side'
 *   Reply inline comments have keys 'reply-cid-lineno-side'
 * @type Object
 */
var M_savedInlineDrafts = new Object();

/**
 * Saves draft text from a form.
 * @param {String} draftKey The key identifying the saved draft text
 * @param {String} text The draft text to be saved
 */
function M_saveDraftText_(draftKey, text) {
  M_savedInlineDrafts[draftKey] = text;
}

/**
 * Clears saved draft text. Does nothing with an invalid key.
 * @param {String} draftKey The key identifying the saved draft text
 */
function M_clearDraftText_(draftKey) {
  delete M_savedInlineDrafts[draftKey];
}

/**
 * Restores saved draft text to a form. Does nothing with an invalid key.
 * @param {String} draftKey The key identifying the saved draft text
 * @param {Element} form The form that contains the comment to be restored
 * @param {Element} opt_selectAll Whether the restored text should be selected.
 *                                True by default.
 * @return {Boolean} true if we found a saved draft and false otherwise
 */
function M_restoreDraftText_(draftKey, form, opt_selectAll) {
  if (M_savedInlineDrafts[draftKey]) {
    form.text.value = M_savedInlineDrafts[draftKey];
    if (typeof opt_selectAll == 'undefined' || opt_selectAll) {
      form.text.select();
    }
    return true;
  }
  return false;
}

// Dashboard CL navigation

/**
 * M_DashboardState class. Keeps track of the current position of
 * the selector on the dashboard, and moves it on keypress.
 * @param {Window} win The window that the dashboard table is in.
 * @param {String} trName The name of TRs that we will move between.
 * @param {String} cookieName The cookie name to store the marker position into.
 * @constructor
 */
function M_DashboardState(win, trName, cookieName) {
  /**
   * The position of the marker, 0-indexed into the trCache array.
   * @ype Integer
   */
  this.trPos = 0;

  /**
   * The current TR object that the marker is pointing at.
   * @type Element
   */
  this.curTR = null;

  /**
   * Array of tr rows that we are moving between. Computed once (updateable).
   * @type Array
   */
  this.trCache = [];

  /**
   * The window that the table is in, used for positioning information.
   * @type Window
   */
  this.win = win;

  /**
   * The expected name of tr's that we are going to cache.
   * @type String
   */
  this.trName = trName;

  /**
   * The name of the cookie value where the marker position is stored.
   * @type String
   */
  this.cookieName = cookieName;

  this.initialize();
}

/**
 * Initializes the clCache array, and moves the marker into the first position.
 */
M_DashboardState.prototype.initialize = function() {
  var filter = function(arr, lambda) {
    var ret = [];
    var arrLength = arr.length;
    for (var i = 0; i < arrLength; i++) {
      if (lambda(arr[i])) {
	ret.push(arr[i]);
      }
    }
    return ret;
  };
  var cache;
  if (M_isIE()) {
    // IE does not recognize the 'name' attribute on TR tags
    cache = filter(document.getElementsByTagName("TR"),
		   function (elem) { return elem.name == this.trName; });
  } else {
    cache = document.getElementsByName(this.trName);
  }

  this.trCache = filter(cache, function (elem) {
    return elem.style.display != "none";
  });

  if (document.cookie && this.cookieName) {
    cookie_values = document.cookie.split(";");
    for (var i=0; i<cookie_values.length; i++) {
      name = cookie_values[i].split("=")[0].replace(/ /g, '');
      if (name == this.cookieName) {
        this.trPos = cookie_values[i].split("=")[1];
      }
    }
  }

  this.goto_(0);
}

/**
 * Moves the cursor to the curCL position, and potentially scrolls the page to
 * bring the cursor into view.
 * @param {Integer} direction Positive for scrolling down, negative for
 *                            scrolling up, and 0 for no scrolling.
 */
M_DashboardState.prototype.goto_ = function(direction) {
  var oldTR = this.curTR;
  if (oldTR) {
    oldTR.cells[0].firstChild.style.visibility = "hidden";
  }
  this.curTR = this.trCache[this.trPos];
  this.curTR.cells[0].firstChild.style.visibility = "";
  if (this.cookieName) {
    document.cookie = this.cookieName+'='+this.trPos;
  }

  if (!M_isElementVisible(this.win, this.curTR)) {
    M_scrollIntoView(this.win, this.curTR, direction);
  }
}

/**
 * Moves the cursor up one.
 */
M_DashboardState.prototype.gotoPrev = function() {
  if (this.trPos > 0) this.trPos--;
  this.goto_(-1);
}

/**
 * Moves the cursor down one.
 */
M_DashboardState.prototype.gotoNext = function() {
  if (this.trPos < this.trCache.length - 1) this.trPos++;
  this.goto_(1);
}

/**
 * Event handler for dashboard key presses. Dispatches cursor moves, as well as
 * opening CLs.
 */
function M_dashboardKeyPress(evt) {
  return M_keyPressCommon(evt, function(key) {
    if (key == 'k') {
      if (dashboardState) dashboardState.gotoPrev();
    } else if (key == 'j') {
      if (dashboardState) dashboardState.gotoNext();
    } else if (key == 'o' || key == '\r' || key == '\n') {
      if (dashboardState) {
	var child = dashboardState.curTR.cells[2].firstChild;
	while (child && child.nodeName != "A") {
	  child = child.firstChild;
	}
	if (child) {
	  location.href = child.href;
	}
      }
    } else {
      return true;
    }
    return false;
  });
}

/*
 * Function to request more context between diff chunks.
 * See _ShortenBuffer() in codereview/engine.py.
 */
function M_expandSkipped(id_before, id_after, where, id_skip) {
  links = document.getElementById('skiplinks-'+id_skip).childNodes;
  for (var i=0; i<links.length; i++) {
	links[i].href = '#skiplinks-'+id_skip;
  }
  tr = document.getElementById('skip-'+id_skip);
  var httpreq = M_getXMLHttpRequest();
  if (!httpreq) {
    html = '<td colspan="2" style="text-align: center;">';
    html = html + 'Failed to retrieve additional lines. ';
    html = html + 'Please update your context settings.';
    html = html + '</td>';
    tr.innerHTML = html;
  }
  aborted = false;
  httpreq.onreadystatechange = function () {
    if (httpreq.readyState == 4 && !aborted) {
      if (httpreq.status == 200) {
        response = eval('('+httpreq.responseText+')');
        for (var i=0; i<response.length; i++) {
          var data = response[i];
          var row = document.createElement("tr");
          for (var j=0; j<data[0].length; j++) {
            row.setAttribute(data[0][j][0], data[0][j][1]);
          }
          if ( where == 't' ) {
            tr.parentNode.insertBefore(row, tr);
          } else {
            tr.parentNode.insertBefore(row, tr.nextSibling);
          }
          row.innerHTML = data[1];
        }
        var curr = document.getElementById('skipcount-'+id_skip);
        var new_count = parseInt(curr.innerHTML)-response.length/2;
        if ( new_count > 0 ) {
          if ( where == 'b' ) {
            var new_before = id_before;
            var new_after = id_after-response.length/2;
          } else {
            var new_before = id_before+response.length/2;
            var new_after = id_after;
          }
          curr.innerHTML = new_count;
          if ( new_count <= 10 ) {
  	    html = '<a href="javascript:M_expandSkipped('+new_before;
            html += ','+new_after+',\'b\','+id_skip+');">Show</a>  ';
          } else {
            var html = '<a href="javascript:M_expandSkipped('+new_before;
            html += ','+new_after+',\'t\', '+id_skip+');">Show 10 above</a> ';
            html += '<a href="javascript:M_expandSkipped('+new_before;
            html += ','+new_after+',\'b\','+id_skip+');">Show 10 below</a> ';
          }
          document.getElementById('skiplinks-'+(id_skip)).innerHTML = html;
        } else {
          tr.parentNode.removeChild(tr);
        }
        if (hookState.hookPos != -2 &&
            M_isElementVisible(window, hookState.indicator)) {
          hookState.gotoHook(-1);
        }
      }
    }
  }

  url = skipped_lines_url+id_before+'/'+id_after+'/'+where;
  httpreq.open('GET', url, true);
  httpreq.send('');
}

/**
 * Finds the element position.
 */
function M_getElementPosition(obj) {
  var curleft = curtop = 0;
  if (obj.offsetParent) {
    do {
      curleft += obj.offsetLeft;
      curtop += obj.offsetTop;
    } while (obj = obj.offsetParent);
  }
  return [curleft,curtop];
}

/**
 * Position the user info popup according to the mouse event coordinates
 */
function M_positionUserInfoPopup(obj, userPopupDiv) {
  pos = M_getElementPosition(obj);
  userPopupDiv.style.left = pos[0] + "px";
  userPopupDiv.style.top = pos[1] + 20 + "px";
}

/**
 * Brings up user info popup using ajax
 */
function M_showUserInfoPopup(obj) {
  var DIV_ID = "userPopupDiv";
  var userPopupDiv = document.getElementById(DIV_ID);
  var url = obj.getAttribute("href")
  var index = url.indexOf("/user/");
  var user_key = url.substring(index + 6);

  if (!userPopupDiv) {
    var userPopupDiv = document.createElement("div");
    userPopupDiv.className = "popup";
    userPopupDiv.id = DIV_ID;
    userPopupDiv.filter = 'alpha(opacity=85)';
    userPopupDiv.opacity = '0.85';
    userPopupDiv.innerHTML = "";
    userPopupDiv.onmouseout = function() {
      userPopupDiv.style.visibility = 'hidden';
    }
    document.body.appendChild(userPopupDiv);
  }
  M_positionUserInfoPopup(obj, userPopupDiv);

  var httpreq = M_getXMLHttpRequest();
  if (!httpreq) {
    return true;
  }

  var aborted = false;
  var httpreq_timeout = setTimeout(function() {
    aborted = true;
    httpreq.abort();
  }, 5000);

  httpreq.onreadystatechange = function () {
    if (httpreq.readyState == 4 && !aborted) {
      clearTimeout(httpreq_timeout);
      if (httpreq.status == 200) {
        userPopupDiv = document.getElementById(DIV_ID);
        userPopupDiv.innerHTML=httpreq.responseText;
        userPopupDiv.style.visibility = "visible";
      } else {
        //Better fail silently here because it's not
        //critical functionality
      }
    }
  }
  httpreq.open("GET", "/user_popup/" + user_key, true);
  httpreq.send(null);
  obj.onmouseout = function() {
    aborted = true;
    userPopupDiv.style.visibility = 'hidden';
    obj.onmouseout = null;
  }
}

/**
 * TODO(jiayao,andi): docstring
 */
function M_showPopUp(obj, id) {
  var popup = document.getElementById(id);
  var pos = M_getElementPosition(obj);
  popup.style.left = pos[0]+'px';
  popup.style.top = pos[1]+20+'px';
  popup.style.visibility = 'visible';
  obj.onmouseout = function() {
    popup.style.visibility = 'hidden';
    obj.onmouseout = null;
  }
}

/**
 * TODO(andi): docstring
 */
function M_jumpToPatch(select, change, patchset, unified) {
  if ( unified ) {
    part = 'patch';
  } else {
    part = 'diff';
  }
  document.location.href = '/'+change+'/'+part+'/'+patchset+'/'+select.value;
}

/**
 * Add or remove a star to/from the given change.
 * @param {Integer} id The change id.
 * @param {String} url The url fragment to append: "/star" or "/unstar".
 */
function M_setChangeStar_(id, url, xsrf) {
  var httpreq = M_getXMLHttpRequest();
  if (!httpreq) {
    return true;
  }
  httpreq.onreadystatechange = function () {
    if (httpreq.readyState == 4) {
      if (httpreq.status == 200) {
	  var elem = document.getElementById("change-star-" + id);
	  elem.innerHTML = httpreq.responseText;
      }
    }
  }

  body = ''
  body += 'change_id=' + escape('' + id);
  body += '&'
  body += 'xsrf=' + xsrf.replace(/\+/g, '%2B')

  httpreq.open("POST", url, true);
  httpreq.setRequestHeader('Content-Type',
                           'application/x-www-form-urlencoded');
  httpreq.send(body)
}
