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
(function(window) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffLine) { return; }

  function GrDiffLine(type) {
    this.type = type;
    this.highlights = [];
  }

  GrDiffLine.prototype.afterNumber = 0;

  GrDiffLine.prototype.beforeNumber = 0;

  GrDiffLine.prototype.contextGroup = null;

  GrDiffLine.prototype.text = '';

  GrDiffLine.Type = {
    ADD: 'add',
    BOTH: 'both',
    BLANK: 'blank',
    CONTEXT_CONTROL: 'contextControl',
    REMOVE: 'remove',
  };

  GrDiffLine.FILE = 'FILE';

  GrDiffLine.BLANK_LINE = new GrDiffLine(GrDiffLine.Type.BLANK);

  window.GrDiffLine = GrDiffLine;

})(window);
