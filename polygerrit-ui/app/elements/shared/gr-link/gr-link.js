// Copyright (C) 2017 The Android Open Source Project
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

  const JS_SCHEME_PATTERN = /^javascript\:/;
  const ABSOLUTE_URL_PATTERN = /^(https?)?\:/

  const EMPTY = '';
  const BLANK = '_blank';
  const NOOPENER = 'noopener';

  Polymer({
    is: 'gr-link',

    properties: {
      url: String,
      absolute: {
        type: Boolean,
        value: false,
      },
      relative: {
        type: Boolean,
        value: false,
      },
      targetBlank: {
        type: Boolean,
        vlaue: false,
      },
      title: String,
      hoverUnderline: {
        type: Boolean,
        value: false,
      },
      ariaLabel: String,
    },

    observers: [
      '_urlChanged(url, absolute, relative)',
    ],

    ready() {
      if (this.hoverUnderline) {
        this.classList.add('underlineOnHover');
      }
      if (this.targetBlank) {
        this.$.a.rel = NOOPENER;
        this.$.a.target = BLANK;
      }
    },

    _urlChanged(url, absolute, relative) {
      if (!absolute && !relative) {
        console.error('Link not specified as relative or absolute');
        return;
      }

      if (JS_SCHEME_PATTERN.test(url)) {
        console.error('Refused JS scheme href', url);
        return;
      }

      if (relative && ABSOLUTE_URL_PATTERN.test(url)) {
        console.error('Refused absolute href', url);
        return;
      }

      if (absolute && !ABSOLUTE_URL_PATTERN.test(url)) {
        console.error('Refused relative href', url);
        return;
      }

      // Assign the href directly now that it has been verified as safe.
      this.$.a.href = url;
    },
  });
})();
