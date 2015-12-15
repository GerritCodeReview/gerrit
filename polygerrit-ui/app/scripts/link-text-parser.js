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

"use strict";

window.linkify = (function(){
  var
  SCHEME = "[a-z\\d.-]+://",
  IPV4 = "(?:(?:[0-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])\\.){3}(?:[0-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])",
  HOSTNAME = "(?:(?:[^\\s!@#$%^&*()_=+[\\]{}\\\\|;:'\",.<>/?]+)\\.)+",
  TLD = "(?:ac|ad|aero|ae|af|ag|ai|al|am|an|ao|aq|arpa|ar|asia|as|at|au|aw|ax|az|ba|bb|bd|be|bf|bg|bh|biz|bi|bj|bm|bn|bo|br|bs|bt|bv|bw|by|bz|cat|ca|cc|cd|cf|cg|ch|ci|ck|cl|cm|cn|coop|com|co|cr|cu|cv|cx|cy|cz|de|dj|dk|dm|do|dz|ec|edu|ee|eg|er|es|et|eu|fi|fj|fk|fm|fo|fr|ga|gb|gd|ge|gf|gg|gh|gi|gl|gm|gn|gov|gp|gq|gr|gs|gt|gu|gw|gy|hk|hm|hn|hr|ht|hu|id|ie|il|im|info|int|in|io|iq|ir|is|it|je|jm|jobs|jo|jp|ke|kg|kh|ki|km|kn|kp|kr|kw|ky|kz|la|lb|lc|li|lk|lr|ls|lt|lu|lv|ly|ma|mc|md|me|mg|mh|mil|mk|ml|mm|mn|mobi|mo|mp|mq|mr|ms|mt|museum|mu|mv|mw|mx|my|mz|name|na|nc|net|ne|nf|ng|ni|nl|no|np|nr|nu|nz|om|org|pa|pe|pf|pg|ph|pk|pl|pm|pn|pro|pr|ps|pt|pw|py|qa|re|ro|rs|ru|rw|sa|sb|sc|sd|se|sg|sh|si|sj|sk|sl|sm|sn|so|sr|st|su|sv|sy|sz|tc|td|tel|tf|tg|th|tj|tk|tl|tm|tn|to|tp|travel|tr|tt|tv|tw|tz|ua|ug|uk|um|us|uy|uz|va|vc|ve|vg|vi|vn|vu|wf|ws|xn--0zwm56d|xn--11b5bs3a9aj6g|xn--80akhbyknj4f|xn--9t4b11yi5a|xn--deba0ad|xn--g6w251d|xn--hgbk6aj7f53bba|xn--hlcj6aya9esc7a|xn--jxalpdlp|xn--kgbechtv|xn--zckzah|ye|yt|yu|za|zm|zw)",
  HOST_OR_IP = "(?:" + HOSTNAME + TLD + "|" + IPV4 + ")",
  PATH = "(?:[;/][^#?<>\\s]*)?",
  QUERY_FRAG = "(?:\\?[^#<>\\s]*)?(?:#[^<>\\s]*)?",
  URI1 = "\\b" + SCHEME + "[^<>\\s]+",
  URI2 = "\\b" + HOST_OR_IP + PATH + QUERY_FRAG + "(?!\\w)",

  MAILTO = "mailto:",
  EMAIL = "(?:" + MAILTO + ")?[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@" + HOST_OR_IP + QUERY_FRAG + "(?!\\w)",

  URI_RE = new RegExp( "(?:" + URI1 + "|" + URI2 + "|" + EMAIL + ")", "ig" ),
  SCHEME_RE = new RegExp( "^" + SCHEME, "i" ),

  quotes = {
    "'": "`",
    '>': '<',
    ')': '(',
    ']': '[',
    '}': '{',
    '»': '«',
    '›': '‹'
  },

  default_options = {
    callback: function( text, href ) {
      return href ? '<a href="' + href + '" title="' + href + '">' + text + '</a>' : text;
    },
    punct_regexp: /(?:[!?.,:;'"]|(?:&|&amp;)(?:lt|gt|quot|apos|raquo|laquo|rsaquo|lsaquo);)$/
  };

  return function( txt, options ) {
    options = options || {};

    // Temp variables.
    var arr,
    i,
    link,
    href,

      // Output HTML.
      html = '',

      // Store text / link parts, in order, for re-combination.
      parts = [],

      // Used for keeping track of indices in the text.
      idx_prev,
      idx_last,
      idx,
      link_last,

      // Used for trimming trailing punctuation and quotes from links.
      matches_begin,
      matches_end,
      quote_begin,
      quote_end;

    // Initialize options.
    for ( i in default_options ) {
      if ( options[ i ] === undefined ) {
        options[ i ] = default_options[ i ];
      }
    }

    // Find links.
    while ( arr = URI_RE.exec( txt ) ) {

      link = arr[0];
      idx_last = URI_RE.lastIndex;
      idx = idx_last - link.length;

      // Not a link if preceded by certain characters.
      if ( /[\/:]/.test( txt.charAt( idx - 1 ) ) ) {
        continue;
      }

      // Trim trailing punctuation.
      do {
        // If no changes are made, we don't want to loop forever!
        link_last = link;

        quote_end = link.substr( -1 )
        quote_begin = quotes[ quote_end ];

        // Ending quote character?
        if ( quote_begin ) {
          matches_begin = link.match( new RegExp( '\\' + quote_begin + '(?!$)', 'g' ) );
          matches_end = link.match( new RegExp( '\\' + quote_end, 'g' ) );

          // If quotes are unbalanced, remove trailing quote character.
          if ( ( matches_begin ? matches_begin.length : 0 ) < ( matches_end ? matches_end.length : 0 ) ) {
            link = link.substr( 0, link.length - 1 );
            idx_last--;
          }
        }

        // Ending non-quote punctuation character?
        if ( options.punct_regexp ) {
          link = link.replace( options.punct_regexp, function(a){
            idx_last -= a.length;
            return '';
          });
        }
      } while ( link.length && link !== link_last );

      href = link;

      // Add appropriate protocol to naked links.
      if ( !SCHEME_RE.test( href ) ) {
        href = ( href.indexOf( '@' ) !== -1 ? ( !href.indexOf( MAILTO ) ? '' : MAILTO )
          : !href.indexOf( 'irc.' ) ? 'irc://'
          : !href.indexOf( 'ftp.' ) ? 'ftp://'
          : 'http://' )
        + href;
      }

      // Push preceding non-link text onto the array.
      if ( idx_prev != idx ) {
        parts.push([ txt.slice( idx_prev, idx ) ]);
        idx_prev = idx_last;
      }

      // Push massaged link onto the array
      parts.push([ link, href ]);
    };

    // Push remaining non-link text onto the array.
    parts.push([ txt.substr( idx_prev ) ]);

    // Process the array items.
    for ( i = 0; i < parts.length; i++ ) {
      html += options.callback.apply( window, parts[i] );
    }

    // In case of catastrophic failure, return the original text;
    return html || txt;
  };

})();

function GrLinkTextParser(callback) {
  this.callback = callback;
  Object.preventExtensions(this);
}

// TODO(mmccoy): Move these patterns to Gerrit project config
// (https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#get-config)
GrLinkTextParser.CUSTOM_LINKS = [
  {
    'pattern': /^(Change-Id: )(.+)$/mi,
    'url': 'https://gerrit.googlesource.com/gerrit/+/'
  },
  {
    'pattern': /^(Feature: )(.+)$/mi,
    'url': 'https://code.google.com/p/gerrit/issues/detail?id='
  },
  {
    'pattern': /^(Bug: )(.+)$/mi,
    'url': 'https://code.google.com/p/gerrit/issues/detail?id='
  }
]

GrLinkTextParser.prototype.addText = function(text, href) {
  if (!text) {
    return;
  }
  this.callback(text, href);
};

GrLinkTextParser.prototype.addBugText = function(text, tracker, bugId) {
  if (tracker) {
    var href = tracker.url + encodeURIComponent(bugId);
    this.addText(text, href);
    return;
  }
  this.addText(text)
};

GrLinkTextParser.prototype.parse = function(text) {
  linkify(text, {
    callback: this.parseChunk.bind(this)
  });
};

GrLinkTextParser.prototype.parseChunk = function(text, href) {
  if (href) {
    this.addText(text, href);
  } else {
    this.parseLinks(text, GrLinkTextParser.CUSTOM_LINKS);
  }
};

GrLinkTextParser.prototype.parseLinks = function(text, patterns) {
  for (var i = patterns.length - 1; i >= 0; i--) {
    var PATTERN = patterns[i].pattern;
    var URL = patterns[i].url;

    var match = text.match(PATTERN);
    if (!match){
      continue;
    }

    var before = text.substr(0, match.index);
    this.addText(before);
    this.addText(match[1]);
    text = text.substr(match.index + match[0].length);
    if (match[1] !== 'Change-Id: ') {
      this.addBugText('Issue ' + match[2], patterns[i], match[2]);
    } else {
      this.addBugText(match[2], patterns[i], match[2]);
    };

  };
  this.addText(text);
};
