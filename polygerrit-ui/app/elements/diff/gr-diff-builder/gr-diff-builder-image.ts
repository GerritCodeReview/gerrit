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

import {GrDiffBuilderSideBySide} from './gr-diff-builder-side-by-side';
import {ImageInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrEndpointParam} from '../../plugins/gr-endpoint-param/gr-endpoint-param';
import {RenderPreferences} from '../../../api/diff';
import '../gr-diff-image-viewer/gr-image-viewer';
import {GrImageViewer} from '../gr-diff-image-viewer/gr-image-viewer';

// MIME types for images we allow showing. Do not include SVG, it can contain
// arbitrary JavaScript.
const IMAGE_MIME_PATTERN = /^image\/(bmp|gif|x-icon|jpeg|jpg|png|tiff|webp)$/;

export class GrDiffBuilderImage extends GrDiffBuilderSideBySide {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement,
    private readonly _baseImage: ImageInfo | null,
    private readonly _revisionImage: ImageInfo | null,
    renderPrefs?: RenderPreferences,
    private readonly _useNewImageDiffUi: boolean = false
  ) {
    super(diff, prefs, outputEl, [], renderPrefs);
  }

  public renderDiff() {
    const section = this._createElement('tbody', 'image-diff');

    if (this._useNewImageDiffUi) {
      this._emitImageViewer(section);

      this._outputEl.appendChild(section);
    } else {
      this._emitImagePair(section);
      this._emitImageLabels(section);

      this._outputEl.appendChild(section);
      this._outputEl.appendChild(this._createEndpoint());
    }
  }

  private _createEndpoint() {
    const tbody = this._createElement('tbody');
    const tr = this._createElement('tr');
    const td = this._createElement('td');

    // TODO(kaspern): Support blame for image diffs and remove the hardcoded 4
    // column limit.
    td.setAttribute('colspan', '4');
    const endpointDomApi = this._createElement('gr-endpoint-decorator');
    endpointDomApi.setAttribute('name', 'image-diff');
    endpointDomApi.appendChild(
      this._createEndpointParam('baseImage', this._baseImage)
    );
    endpointDomApi.appendChild(
      this._createEndpointParam('revisionImage', this._revisionImage)
    );
    td.appendChild(endpointDomApi);
    tr.appendChild(td);
    tbody.appendChild(tr);
    return tbody;
  }

  private _createEndpointParam(name: string, value: ImageInfo | null) {
    const endpointParam = this._createElement(
      'gr-endpoint-param'
    ) as GrEndpointParam;
    endpointParam.name = name;
    endpointParam.value = value;
    return endpointParam;
  }

  private _emitImageViewer(section: HTMLElement) {
    const tr = this._createElement('tr');
    const td = this._createElement('td');
    // TODO(hermannloose): Support blame for image diffs, see above.
    td.setAttribute('colspan', '4');
    const imageViewer = this._createElement('gr-image-viewer') as GrImageViewer;

    imageViewer.baseUrl = this._getImageSrc(this._baseImage);
    imageViewer.revisionUrl = this._getImageSrc(this._revisionImage);
    imageViewer.automaticBlink =
      !!this._renderPrefs?.image_diff_prefs?.automatic_blink;

    td.appendChild(imageViewer);
    tr.appendChild(td);
    section.appendChild(tr);
  }

  private _getImageSrc(image: ImageInfo | null): string {
    return image && IMAGE_MIME_PATTERN.test(image.type)
      ? `data:${image.type};base64,${image.body}`
      : '';
  }

  private _emitImagePair(section: HTMLElement) {
    const tr = this._createElement('tr');

    tr.appendChild(this._createElement('td', 'left lineNum blank'));
    tr.appendChild(this._createImageCell(this._baseImage, 'left', section));

    tr.appendChild(this._createElement('td', 'right lineNum blank'));
    tr.appendChild(
      this._createImageCell(this._revisionImage, 'right', section)
    );

    section.appendChild(tr);
  }

  private _createImageCell(
    image: ImageInfo | null,
    className: string,
    section: HTMLElement
  ) {
    const td = this._createElement('td', className);
    const src = this._getImageSrc(image);
    if (image && src) {
      const imageEl = this._createElement('img') as HTMLImageElement;
      imageEl.onload = () => {
        image._height = imageEl.naturalHeight;
        image._width = imageEl.naturalWidth;
        this._updateImageLabel(section, className, image);
      };
      imageEl.addEventListener('error', (e: Event) => {
        imageEl.remove();
        td.textContent = '[Image failed to load] ' + e.type;
      });
      imageEl.setAttribute('src', src);
      td.appendChild(imageEl);
    }
    return td;
  }

  private _updateImageLabel(
    section: HTMLElement,
    className: string,
    image: ImageInfo
  ) {
    const label = section.querySelector(
      '.' + className + ' span.label'
    ) as HTMLElement;
    this._setLabelText(label, image);
  }

  private _setLabelText(label: HTMLElement, image: ImageInfo | null) {
    label.textContent = _getImageLabel(image);
  }

  private _emitImageLabels(section: HTMLElement) {
    const tr = this._createElement('tr');

    let addNamesInLabel = false;

    if (
      this._baseImage &&
      this._revisionImage &&
      this._baseImage._name !== this._revisionImage._name
    ) {
      addNamesInLabel = true;
    }

    tr.appendChild(this._createElement('td', 'left lineNum blank'));
    let td = this._createElement('td', 'left');
    let label = this._createElement('label');
    let nameSpan;
    let labelSpan = this._createElement('span', 'label');

    if (addNamesInLabel) {
      nameSpan = this._createElement('span', 'name');
      nameSpan.textContent = this._baseImage?._name ?? '';
      label.appendChild(nameSpan);
      label.appendChild(this._createElement('br'));
    }

    this._setLabelText(labelSpan, this._baseImage);

    label.appendChild(labelSpan);
    td.appendChild(label);
    tr.appendChild(td);

    tr.appendChild(this._createElement('td', 'right lineNum blank'));
    td = this._createElement('td', 'right');
    label = this._createElement('label');
    labelSpan = this._createElement('span', 'label');

    if (addNamesInLabel) {
      nameSpan = this._createElement('span', 'name');
      nameSpan.textContent = this._revisionImage?._name ?? '';
      label.appendChild(nameSpan);
      label.appendChild(this._createElement('br'));
    }

    this._setLabelText(labelSpan, this._revisionImage);

    label.appendChild(labelSpan);
    td.appendChild(label);
    tr.appendChild(td);

    section.appendChild(tr);
  }

  override updateRenderPrefs(renderPrefs: RenderPreferences) {
    const imageViewer = this._outputEl.querySelector(
      'gr-image-viewer'
    ) as GrImageViewer;
    if (this._useNewImageDiffUi && imageViewer) {
      imageViewer.automaticBlink =
        !!renderPrefs?.image_diff_prefs?.automatic_blink;
    }
  }
}

function _getImageLabel(image: ImageInfo | null) {
  if (image) {
    const type = image.type ?? image._expectedType;
    if (image._width && image._height) {
      return `${image._width}×${image._height} ${type}`;
    } else {
      return type;
    }
  }
  return 'No image';
}
