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

  Polymer({
    is: 'gr-copy-clipboard',

    properties: {
      command: String,
      title: String,
    },

    focusOnCopy() {
      this.$$('.copyToClipboard').focus();
    },

    _copyToClipboard(e) {
      e.target.parentElement.querySelector('.copyCommand').select();
      document.execCommand('copy');
      getSelection().removeAllRanges();
      e.target.textContent = 'done';
      setTimeout(() => { e.target.textContent = 'copy'; }, 1000);
    },
  });
})();
