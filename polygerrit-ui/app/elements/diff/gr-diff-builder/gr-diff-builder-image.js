// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the 'License');
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an 'AS IS' BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function(window, GrDiffBuilderSideBySide) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffBuilderImage) { return; }

  function GrDiffBuilderImage(diff, comments, prefs, outputEl, baseImage,
      revisionImage) {
    GrDiffBuilderSideBySide.call(this, diff, comments, prefs, outputEl, []);
    this._baseImage = baseImage;
    this._revisionImage = revisionImage;
  }

  GrDiffBuilderImage.prototype = Object.create(
      GrDiffBuilderSideBySide.prototype);
  GrDiffBuilderImage.prototype.constructor = GrDiffBuilderImage;

  GrDiffBuilderImage.prototype.renderDiffImages = function() {
    var section = this._createElement('tbody', 'image-diff');

    this._emitImagePair(section);
    this._emitImageLabels(section);

    this._outputEl.appendChild(section);
  };

  GrDiffBuilderImage.prototype._emitImagePair = function(section) {
    var tr = this._createElement('tr');

    tr.appendChild(this._createElement('td'));
    tr.appendChild(this._createImageCell(this._baseImage, 'left'));

    tr.appendChild(this._createElement('td'));
    tr.appendChild(this._createImageCell(this._revisionImage, 'right'));

    section.appendChild(tr);
  };

  GrDiffBuilderImage.prototype._createImageCell = function(image, className) {
    var td = this._createElement('td', className);
    if (image) {
      var imageEl = this._createElement('img');
      imageEl.src = 'data:' + image.type + ';base64, ' + image.body;
      image._height = imageEl.naturalHeight;
      image._width = imageEl.naturalWidth;
      imageEl.addEventListener('error', function(e) {
        imageEl.remove();
        td.textContent = '[Image failed to load]';
      });
      td.appendChild(imageEl);
    }
    return td;
  };

  GrDiffBuilderImage.prototype._emitImageLabels = function(section) {
    var tr = this._createElement('tr');

    tr.appendChild(this._createElement('td'));
    var td = this._createElement('td', 'left');
    var label = this._createElement('label');
    label.textContent = this._getImageLabel(this._baseImage);
    td.appendChild(label);
    tr.appendChild(td);

    tr.appendChild(this._createElement('td'));
    td = this._createElement('td', 'right');
    label = this._createElement('label');
    label.textContent = this._getImageLabel(this._revisionImage);
    td.appendChild(label);
    tr.appendChild(td);

    section.appendChild(tr);
  };

  GrDiffBuilderImage.prototype._getImageLabel = function(image) {
    if (image) {
      var type = image.type || image._expectedType;
      if (image._width && image._height) {
        return image._width + '⨉' + image._height + ' ' + type;
      } else {
        return type;
      }
    }
    return 'No image';
  };

  window.GrDiffBuilderImage = GrDiffBuilderImage;
})(window, GrDiffBuilderSideBySide);
