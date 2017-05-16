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
      target: String,
      rel: String,
      title: String,
      hoverUnderline: {
        type: Boolean,
        value: false,
      },

      _href: {
        type: String,
        computed: '_computeHref(url, absolute, relative)',
      },
      _target: {
        type: String,
        value: EMPTY,
        computed: '_computeTarget(target)',
      },
      _rel: {
        type: String,
        value: EMPTY,
        computed: '_computeRel(_target, rel)',
      },
    },

    ready() {
      if (this.hoverUnderline) {
        this.classList.add('underlineOnHover');
      }
    },

    _computeHref(url, absolute, relative) {
      if (!absolute && !relative) {
        console.error('Link not specified as relative or absolute');
        return EMPTY;
      }

      if (JS_SCHEME_PATTERN.test(url)) {
        console.error('Refused JS scheme href', url);
        return EMPTY;
      }

      if (relative && ABSOLUTE_URL_PATTERN.test(url)) {
        console.error('Refused absolute href', url);
        return EMPTY;
      }

      if (absolute && !ABSOLUTE_URL_PATTERN.test(url)) {
        console.error('Refused relative href', url);
        return EMPTY;
      }

      return url;
    },

    _computeTarget(target) {
      if (target !== BLANK && target !== EMPTY) {
        console.error('Invalid target value', target);
        return;
      }

      if (target === BLANK) {
        return BLANK;
      }
      return EMPTY;
    },

    _computeRel(target, rel) {
      if (rel !== NOOPENER && rel !== EMPTY) {
        console.error('Invalid rel value', rel);
      }
      if (target === BLANK) {
        if (rel !== NOOPENER) {
          console.warn('Forcing rel to be noopener');
        }
        return NOOPENER;
      }
      return EMPTY;
    }
  });
})();
