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
    return new Promise(function(resolve) {
      var section = this._createElement('tbody', 'image-diff');

      this._emitImagePair(section).then(function() {
        this._emitImageLabels(section);
        this._outputEl.appendChild(section);
        resolve();
      }.bind(this));
    }.bind(this));
  };

  GrDiffBuilderImage.prototype._emitImagePair = function(section) {
    return new Promise(function(resolve) {
      var tr = this._createElement('tr');
      tr.appendChild(this._createElement('td'));
      this._createImageCell(this._baseImage, 'left').then(function(td){
        tr.appendChild(td);
        tr.appendChild(this._createElement('td'));
        this._createImageCell(this._revisionImage, 'right').then(function(td){
          tr.appendChild(td);
          section.appendChild(tr);
          resolve();
        }.bind(this));
      }.bind(this));
    }.bind(this));
  };

  GrDiffBuilderImage.prototype._createImageCell = function(image, className) {
    return new Promise(function(resolve) {
      var td = this._createElement('td', className);
      if (image) {
        var imageEl = this._createElement('img');
        imageEl.onload = function() {
          image._height = imageEl.naturalHeight;
          image._width = imageEl.naturalWidth;
          td.appendChild(imageEl);
          resolve(td);
        }
        imageEl.src = 'data:' + image.type + ';base64, ' + image.body;
        imageEl.addEventListener('error', function(e) {
          imageEl.remove();
          td.textContent = '[Image failed to load]';
        });
      }
    }.bind(this));
  };

  GrDiffBuilderImage.prototype._emitImageLabels = function(section) {
    var tr = this._createElement('tr');

    var addNamesInLabel = false;

    if (this._baseImage._name !== this._revisionImage._name) {
      addNamesInLabel = true;
    }

    tr.appendChild(this._createElement('td'));
    var td = this._createElement('td', 'left');
    var label = this._createElement('label');
    if (addNamesInLabel) {
      label.textContent = this._getName(this._baseImage);
      label.appendChild(this._createElement('br'));
    }
    label.appendChild(document.createTextNode(
        this._getImageLabel(this._baseImage)));
    td.appendChild(label);
    tr.appendChild(td);

    tr.appendChild(this._createElement('td'));
    td = this._createElement('td', 'right');
    label = this._createElement('label');

    if (addNamesInLabel) {
      label.textContent = this._getName(this._revisionImage);
      label.appendChild(this._createElement('br'));
    }

    label.appendChild(document.createTextNode(
        this._getImageLabel(this._revisionImage)));
    td.appendChild(label);
    tr.appendChild(td);

    section.appendChild(tr);
  };

  GrDiffBuilderImage.prototype._getName = function(image) {
    return image._name;
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
