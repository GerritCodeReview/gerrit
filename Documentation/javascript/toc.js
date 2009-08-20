/* Author: Mihai Bazon, September 2002
 * http://students.infoiasi.ro/~mishoo
 *
 * Table Of Content generator
 * Version: 0.4.sp
 *
 * Feel free to use this script under the terms of the GNU General Public
 * License, as long as you do not remove or alter this notice.
 */

 /* modified by Troy D. Hanson, September 2006. License: GPL */
 /* modified by Stuart Rackham, October 2006. License: GPL */
 /* modified by Shawn Pearce, August 2009. License: GPL */

function getText(el) {
  var text = "";
  for (var i = el.firstChild; i != null; i = i.nextSibling) {
    if (i.nodeType == 3 /* Node.TEXT_NODE */) // IE doesn't speak constants.
      text += i.data;
    else if (i.firstChild != null)
      text += getText(i);
  }
  return text;
}

function TocEntry(el, text, toclevel) {
  this.element = el;
  this.text = text;
  this.toclevel = toclevel;
  this.assigned = false;

  if (el.id != '') {
    this.id = el.id;

  } else {
    var a = el.firstChild;
    if ((a.tagName == "a" || a.tagName == "A") && a.id != "") {
      this.id = a.id;
    } else {
      this.id = '';
    }
  }
}

function tocEntries(el, toclevels) {
  var result = new Array;
  var re = new RegExp('[hH]([2-'+(toclevels+1)+'])');
  // Function that scans the DOM tree for header elements (the DOM2
  // nodeIterator API would be a better technique but not supported by all
  // browsers).
  var iterate = function (el) {
    for (var i = el.firstChild; i != null; i = i.nextSibling) {
      if (i.nodeType == 1 /* Node.ELEMENT_NODE */) {
        var mo = re.exec(i.tagName)
        if (mo)
          result[result.length] = new TocEntry(i, getText(i), mo[1]-1);
        iterate(i);
      }
    }
  }
  iterate(el);
  return result;
}

// This function does the work. toclevels = 1..4.
function generateToc(toclevels) {
  var simple_re = new RegExp('^[a-zA-Z._ -]{1,}$');
  var entries = tocEntries(document.getElementsByTagName("body")[0], toclevels);
  var usedIds = new Array();

  for (var i = 0; i < entries.length; ++i) {
    var entry = entries[i];
    if (entry.id != "")
      usedIds[entry.id] = entry;
  }

  for (var i = 0; i < entries.length; ++i) {
    var entry = entries[i];
    if (entry.id != "" || !simple_re.exec(entry.text))
      continue;

    var n = entry.text.replace(/ /g, '_').toLowerCase();
    var e = usedIds[n];
    if (e) {
      if (e.assigned)
        e.id = '';
      continue;
    }

    entry.assigned = true;
    entry.id = n;
    entry.element.id = entry.id;
    usedIds[n] = entry;
  }

  for (var i = 0; i < entries.length; ++i) {
    var entry = entries[i];
    if (entry.id == '') {
      entry.id = "toc" + i;
      entry.element.id = entry.id;
    }
  }

  var toc = document.getElementById("toc");
  for (var i = 0; i < entries.length; ++i) {
    var entry = entries[i];
    var a = document.createElement("a");
    a.href = "#" + entry.id;
    a.appendChild(document.createTextNode(entry.text));
    var div = document.createElement("div");
    div.appendChild(a);
    div.className = "toclevel" + entry.toclevel;
    toc.appendChild(div);
  }
}
