// Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

  Polymer({
    is: 'gr-linked-text',

    properties: {
      removeZeroWidthSpace: Boolean,
      content: {
        type: String,
        observer: '_contentChanged',
      },
      pre: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      disabled: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      config: Object,
    },

    observers: [
      '_contentOrConfigChanged(content, config)',
    ],

    _contentChanged: function(content) {
      // In the case where the config may not be set (perhaps due to the
      // request for it still being in flight), set the content anyway to
      // prevent waiting on the config to display the text.
      if (this.config != null) { return; }
      this.$.output.textContent = content;
    },

    _contentOrConfigChanged: function(content, config) {
      var output = Polymer.dom(this.$.output);
      output.textContent = '';
      var parser = new GrLinkTextParser(
          config, function(text, href, fragment) {
        if (href) {
          var a = document.createElement('a');
          a.href = href;
          a.textContent = text;
          a.target = '_blank';
          a.rel = 'noopener';
          output.appendChild(a);
        } else if (fragment) {
          output.appendChild(fragment);
        }
      }, this.removeZeroWidthSpace);
      parser.parse(content);

      // Ensure that links originating from HTML commentlink configs open in a
      // new tab. @see Issue 5567
      output.querySelectorAll('a').forEach(function(anchor) {
        anchor.setAttribute('target', '_blank');
        anchor.setAttribute('rel', 'noopener');
      });
    },
  });
})();
