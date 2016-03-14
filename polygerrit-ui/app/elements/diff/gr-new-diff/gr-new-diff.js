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

  var DiffViewMode = {
    SIDE_BY_SIDE: 'SIDE_BY_SIDE',
    UNIFIED: 'UNIFIED_DIFF',
  };

  Polymer({
    is: 'gr-new-diff',

    properties: {
      availablePatches: Array,
      changeNum: String,
      patchRange: Object,
      path: String,
      prefs: {
        type: Object,
        notify: true,
      },
      projectConfig: Object,

      _loading: {
        type: Boolean,
        value: true,
      },
      _viewMode: {
        type: String,
        value: DiffViewMode.SIDE_BY_SIDE,
      },
    },

    observers: [
      '_prefsChanged(prefs.*)',
    ],

    reload: function() {
      this.$.diffTable.innerHTML = null;
      this._loading = true;

      return this._getDiff().then(function(diff) {
        var builder = this._getDiffBuilder(diff);
        builder.emitDiff(diff.content);

        this._loading = false;
      }.bind(this));
    },

    _prefsChanged: function(changeRecord) {
      var prefs = changeRecord.base;
      this.customStyle['--content-width'] = prefs.line_length + 'ch';
      this.updateStyles();
    },

    _getDiff: function() {
      return this.$.restAPI.getDiff(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path);
    },

    _getDiffBuilder: function(diff) {
      if (this._viewMode === DiffViewMode.SIDE_BY_SIDE) {
        return new GrDiffBuilderSideBySide(diff, this.$.diffTable);
      } else if (this._viewMode === DiffViewMode.UNIFIED) {
        return new GrDiffBuilderUnified(diff, this.$.diffTable);
      }
      throw Error('Unsupported diff view mode: ' + this._viewMode);
    },

  });
})();
