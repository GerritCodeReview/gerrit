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
    is: 'gr-patch-range-select',

    properties: {
      availablePatches: Array,
      changeNum: String,
      patchRange: Object,
      path: String,
    },

    _handlePatchChange: function(e) {
      var leftPatch = this.$.leftPatchSelect.value;
      var rightPatch = this.$.rightPatchSelect.value;
      var rangeStr = rightPatch;
      if (leftPatch != 'PARENT') {
        rangeStr = leftPatch + '..' + rangeStr;
      }
      page.show('/c/' + this.changeNum + '/' + rangeStr + '/' + this.path);
    },

    _computeLeftSelected: function(patchNum, patchRange) {
      return patchNum == patchRange.basePatchNum;
    },

    _computeRightSelected: function(patchNum, patchRange) {
      return patchNum == patchRange.patchNum;
    },

    _computeLeftDisabled: function(patchNum, patchRange) {
      return parseInt(patchNum, 10) >= parseInt(patchRange.patchNum, 10);
    },

    _computeRightDisabled: function(patchNum, patchRange) {
      if (patchRange.basePatchNum == 'PARENT') { return false; }
      return parseInt(patchNum, 10) <= parseInt(patchRange.basePatchNum, 10);
    },
  });
})();
