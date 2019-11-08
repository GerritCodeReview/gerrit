/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function(window, GrDiffBuilderSideBySide) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffBuilderImage) { return; }

  // MIME types for images we allow showing. Do not include SVG, it can contain
  // arbitrary JavaScript.
  const IMAGE_MIME_PATTERN = /^image\/(bmp|gif|x-icon|jpeg|jpg|png|tiff|webp)$/;

  function GrDiffBuilderImage(diff, prefs, outputEl, baseImage, revisionImage) {
    GrDiffBuilderSideBySide.call(this, diff, prefs, outputEl, []);
    this._baseImage = baseImage;
    this._revisionImage = revisionImage;
  }

  GrDiffBuilderImage.prototype = Object.create(
      GrDiffBuilderSideBySide.prototype);
  GrDiffBuilderImage.prototype.constructor = GrDiffBuilderImage;

  GrDiffBuilderImage.prototype.renderDiff = function() {
    const section = this._createElement('tbody', 'image-diff');

    this._emitImagePair(section);
    this._emitImageLabels(section);

    this._outputEl.appendChild(section);
    this._outputEl.appendChild(this._createEndpoint());
  };

  GrDiffBuilderImage.prototype._createEndpoint = function() {
    const tbody = this._createElement('tbody');
    const tr = this._createElement('tr');
    const td = this._createElement('td');

    // TODO(kaspern): Support blame for image diffs and remove the hardcoded 4
    // column limit.
    td.setAttribute('colspan', '4');
    const endpoint = this._createElement('gr-endpoint-decorator');
    const endpointDomApi = Polymer.dom(endpoint);
    endpointDomApi.setAttribute('name', 'image-diff');
    endpointDomApi.appendChild(
        this._createEndpointParam('baseImage', this._baseImage));
    endpointDomApi.appendChild(
        this._createEndpointParam('revisionImage', this._revisionImage));
    td.appendChild(endpoint);
    tr.appendChild(td);
    tbody.appendChild(tr);
    return tbody;
  };

  GrDiffBuilderImage.prototype._createEndpointParam = function(name, value) {
    const endpointParam = this._createElement('gr-endpoint-param');
    endpointParam.setAttribute('name', name);
    endpointParam.value = value;
    return endpointParam;
  };

  GrDiffBuilderImage.prototype._emitImagePair = function(section) {
    const tr = this._createElement('tr');

    tr.appendChild(this._createElement('td', 'left lineNum blank'));
    tr.appendChild(this._createImageCell(this._baseImage, 'left', section));

    tr.appendChild(this._createElement('td', 'right lineNum blank'));
    tr.appendChild(this._createImageCell(
        this._revisionImage, 'right', section));

    section.appendChild(tr);
  };

  GrDiffBuilderImage.prototype._createImageCell = function(image, className,
      section) {
    const td = this._createElement('td', className);
    if (image && IMAGE_MIME_PATTERN.test(image.type)) {
      const imageEl = this._createElement('img');
      imageEl.onload = function() {
        image._height = imageEl.naturalHeight;
        image._width = imageEl.naturalWidth;
        this._updateImageLabel(section, className, image);
      }.bind(this);
      imageEl.setAttribute('src', `data:${image.type};base64, ${image.body}`);
      imageEl.addEventListener('error', () => {
        imageEl.remove();
        td.textContent = '[Image failed to load]';
      });
      td.appendChild(imageEl);
    }
    return td;
  };

  GrDiffBuilderImage.prototype._updateImageLabel = function(section, className,
      image) {
    const label = Polymer.dom(section)
        .querySelector('.' + className + ' span.label');
    this._setLabelText(label, image);
  };

  GrDiffBuilderImage.prototype._setLabelText = function(label, image) {
    label.textContent = this._getImageLabel(image);
  };

  GrDiffBuilderImage.prototype._emitImageLabels = function(section) {
    const tr = this._createElement('tr');

    let addNamesInLabel = false;

    if (this._baseImage && this._revisionImage &&
        this._baseImage._name !== this._revisionImage._name) {
      addNamesInLabel = true;
    }

    tr.appendChild(this._createElement('td', 'left lineNum blank'));
    let td = this._createElement('td', 'left');
    let label = this._createElement('label');
    let nameSpan;
    let labelSpan = this._createElement('span', 'label');

    if (addNamesInLabel) {
      nameSpan = this._createElement('span', 'name');
      nameSpan.textContent = this._baseImage._name;
      label.appendChild(nameSpan);
      label.appendChild(this._createElement('br'));
    }

    this._setLabelText(labelSpan, this._baseImage, addNamesInLabel);

    label.appendChild(labelSpan);
    td.appendChild(label);
    tr.appendChild(td);

    tr.appendChild(this._createElement('td', 'right lineNum blank'));
    td = this._createElement('td', 'right');
    label = this._createElement('label');
    labelSpan = this._createElement('span', 'label');

    if (addNamesInLabel) {
      nameSpan = this._createElement('span', 'name');
      nameSpan.textContent = this._revisionImage._name;
      label.appendChild(nameSpan);
      label.appendChild(this._createElement('br'));
    }

    this._setLabelText(labelSpan, this._revisionImage, addNamesInLabel);

    label.appendChild(labelSpan);
    td.appendChild(label);
    tr.appendChild(td);

    section.appendChild(tr);
  };

  GrDiffBuilderImage.prototype._getImageLabel = function(image) {
    if (image) {
      const type = image.type || image._expectedType;
      if (image._width && image._height) {
        return image._width + '×' + image._height + ' ' + type;
      } else {
        return type;
      }
    }
    return 'No image';
  };

  window.GrDiffBuilderImage = GrDiffBuilderImage;
})(window, GrDiffBuilderSideBySide);
