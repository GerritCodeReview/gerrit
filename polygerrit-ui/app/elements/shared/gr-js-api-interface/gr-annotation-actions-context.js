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
(function(window) {
  'use strict';

  function GrAnnotationActionsContext(el, line, path, changeNum, patchNum) {
    this._el = el;

    this.line = line;
    this.path = path;
    this.changeNum = parseInt(changeNum);
    this.patchNum = parseInt(patchNum);
  }

  GrAnnotationActionsContext.prototype.annotateRange = function(
      start, end, cssClass) {
    GrAnnotation.annotateElement(this._el, start, end, cssClass);
  };

  window.GrAnnotationActionsContext = GrAnnotationActionsContext;
})(window);
