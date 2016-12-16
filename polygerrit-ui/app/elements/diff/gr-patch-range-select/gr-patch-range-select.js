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

  // Maximum length for patch set descriptions.
  var PATCH_DESC_MAX_LENGTH = 500;

  Polymer({
    is: 'gr-patch-range-select',

    properties: {
      availablePatches: Array,
      changeNum: String,
      filesWeblinks: Object,
      path: String,
      patchRange: {
        type: Object,
        observer: '_updateSelected',
      },
      revisions: Object,
      _rightSelected: String,
      _leftSelected: String,
    },

    behaviors: [Gerrit.PatchSetBehavior],

    _updateSelected: function() {
      this._rightSelected = this.patchRange.patchNum;
      this._leftSelected = this.patchRange.basePatchNum;
    },

    _handlePatchChange: function(e) {
      var leftPatch = this._leftSelected;
      var rightPatch = this._rightSelected;
      var rangeStr = rightPatch;
      if (leftPatch != 'PARENT') {
        rangeStr = leftPatch + '..' + rangeStr;
      }
      page.show('/c/' + this.changeNum + '/' + rangeStr + '/' + this.path);
      e.target.blur();
    },

    _computeLeftDisabled: function(patchNum, patchRange) {
      return parseInt(patchNum, 10) >= parseInt(patchRange.patchNum, 10);
    },

    _computeRightDisabled: function(patchNum, patchRange) {
      if (patchRange.basePatchNum == 'PARENT') { return false; }
      return parseInt(patchNum, 10) <= parseInt(patchRange.basePatchNum, 10);
    },

    // On page load, the dom-if for options getting added occurs after
    // the value was set in the select. This ensures that after they
    // are loaded, the correct value will get selected.  I attempted to
    // debounce these, but because they are detecting two different
    // events, sometimes the timing was off and one ended up missing.
    _synchronizeSelectionRight: function() {
      this.$.rightPatchSelect.value = this._rightSelected;
    },

    _synchronizeSelectionLeft: function() {
      this.$.leftPatchSelect.value = this._leftSelected;
    },

    _computePatchSetDescription: function(revisions, patchNum) {
      var rev = this.getRevisionByPatchNum(revisions, patchNum);
      return (rev && rev.description) ?
          rev.description.substring(0, PATCH_DESC_MAX_LENGTH) : '';
    },
  });
})();
