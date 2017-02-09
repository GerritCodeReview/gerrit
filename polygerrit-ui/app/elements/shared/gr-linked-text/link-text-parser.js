// Copyright (C) 2015 The Android Open Source Project
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

'use strict';

function GrLinkTextParser(linkConfig, callback, opt_removeZeroWidthSpace) {
  this.linkConfig = linkConfig;
  this.callback = callback;
  this.removeZeroWidthSpace = opt_removeZeroWidthSpace;
  Object.preventExtensions(this);
}

GrLinkTextParser.prototype.addText = function(text, href) {
  if (!text) {
    return;
  }
  this.callback(text, href);
};

GrLinkTextParser.prototype.processLinks = function(text, outputArray) {
  this.sortArrayReverse(outputArray);
  var fragment = document.createDocumentFragment();
  var cursor = text.length;

  // Start inserting linkified URLs from the end of the String. That way, the
  // string positions of the items don't change as we iterate through.
  outputArray.forEach(function(item) {
    // Add any text between the current linkified item and the item added before
    // if it exists.
    if (item.position + item.length !== cursor) {
      fragment.insertBefore(
          document.createTextNode(
              text.slice(item.position + item.length, cursor)),
          fragment.firstChild);
    }
    fragment.insertBefore(item.html, fragment.firstChild);
    cursor = item.position;
  });

  // Add the beginning portion at the end.
  if (cursor !== 0) {
    fragment.insertBefore(
        document.createTextNode(text.slice(0, cursor)), fragment.firstChild);
  }

  this.callback(null, null, fragment);
};

GrLinkTextParser.prototype.sortArrayReverse = function(outputArray) {
  outputArray.sort(function(a, b) {return b.position - a.position});
};

GrLinkTextParser.prototype.addItem =
    function(text, href, html, position, length, outputArray) {
  var htmlOutput = '';

  if (href) {
    var a = document.createElement('a');
    a.href = href;
    a.textContent = text;
    a.target = '_blank';
    a.rel = 'noopener';
    htmlOutput = a;
  } else if (html) {
    var fragment = document.createDocumentFragment();
    // Create temporary div to hold the nodes in.
    var div = document.createElement('div');
    div.innerHTML = html;
    while (div.firstChild) {
      fragment.appendChild(div.firstChild);
    }
    htmlOutput = fragment;
  }

  outputArray.push({
    html: htmlOutput,
    position: position,
    length: length,
  });
};

GrLinkTextParser.prototype.addLink =
    function(text, href, position, length, outputArray) {
  if (!text) {
    return;
  }
  if (!this.hasOverlap(position, length, outputArray)) {
    this.addItem(text, href, null, position, length, outputArray);
  }
};

GrLinkTextParser.prototype.addHTML =
    function(html, position, length, outputArray) {
  if (!this.hasOverlap(position, length, outputArray)) {
    this.addItem(null, null, html, position, length, outputArray);
  }
};

GrLinkTextParser.prototype.hasOverlap =
    function(position, length, outputArray) {
  var endPosition = position + length;
  for (var i = 0; i < outputArray.length; i++) {
    var arrayItemStart = outputArray[i].position;
    var arrayItemEnd = outputArray[i].position + outputArray[i].length;
    if ((position >= arrayItemStart && position < arrayItemEnd) ||
      (endPosition > arrayItemStart && endPosition <= arrayItemEnd) ||
      (position === arrayItemStart && position === arrayItemEnd)) {
          return true;
    }
  }
  return false;
};

GrLinkTextParser.prototype.parse = function(text) {
  linkify(text, {
    callback: this.parseChunk.bind(this),
  });
};

GrLinkTextParser.prototype.parseChunk = function(text, href) {
  // TODO(wyatta) switch linkify sequence, see issue 5526.
  if (this.removeZeroWidthSpace) {
    // Remove the zero-width space added in gr-change-view.
    text = text.replace(/^R=\u200B/gm, 'R=');
  }

  if (href) {
    this.addText(text, href);
  } else {
    this.parseLinks(text, this.linkConfig);
  }
};

GrLinkTextParser.prototype.parseLinks = function(text, patterns) {
  // The outputArray is used to store all of the matches found for all patterns.
  var outputArray = [];
  for (var p in patterns) {
    if (patterns[p].enabled != null && patterns[p].enabled == false) {
      continue;
    }
    // PolyGerrit doesn't use hash-based navigation like GWT.
    // Account for this.
    // TODO(andybons): Support Gerrit being served from a base other than /,
    // e.g. https://git.eclipse.org/r/
    if (patterns[p].html) {
      patterns[p].html =
          patterns[p].html.replace(/<a href=\"#\//g, '<a href="/');
    } else if (patterns[p].link) {
      if (patterns[p].link[0] == '#') {
        patterns[p].link = patterns[p].link.substr(1);
      }
    }

    var pattern = new RegExp(patterns[p].match, 'g');

    var match;
    var textToCheck = text;
    var susbtrIndex = 0;

    while ((match = pattern.exec(textToCheck)) != null) {
      textToCheck = textToCheck.substr(match.index + match[0].length);
      var result = match[0].replace(pattern,
          patterns[p].html || patterns[p].link);

      // Skip portion of replacement string that is equal to original.
      for (var i = 0; i < result.length; i++) {
        if (result[i] !== match[0][i]) {
          break;
        }
      }
      result = result.slice(i);

      if (patterns[p].html) {
        this.addHTML(
          result,
          susbtrIndex + match.index + i,
          match[0].length - i,
          outputArray);
      } else if (patterns[p].link) {
        this.addLink(
          match[0],
          result,
          susbtrIndex + match.index + i,
          match[0].length - i,
          outputArray);
      } else {
        throw Error('linkconfig entry ' + p +
            ' doesn’t contain a link or html attribute.');
      }

      // Update the substring location so we know where we are in relation to
      // the initial full text string.
      susbtrIndex = susbtrIndex + match.index + match[0].length;
    }
  }
  this.processLinks(text, outputArray);
};
