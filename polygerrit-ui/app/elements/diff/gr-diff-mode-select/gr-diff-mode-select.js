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
    is: 'gr-diff-mode-select',

    properties: {
      diffMode: {
        type: String,
        notify: true,
      },
      _isSideBySide: {
        type: Boolean,
        computed: '_modeIsSideBySide(diffMode)',
        notify: true,
      },
      _isUnified: {
        type: Boolean,
        computed: '_modeIsUnified(diffMode)',
        notify: true,
      }
    },

    _handleModeChange: function(e) {
      this.set('diffMode', this.$.modeSelect.value);
    },

    _modeIsSideBySide: function() {
      return this.diffMode === 'SIDE_BY_SIDE';
    },

    _modeIsUnified: function() {
      return this.diffMode === 'UNIFIED_DIFF';
    },
  });
})();
