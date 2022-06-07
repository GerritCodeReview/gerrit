/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {GrDiffBuilderSideBySide} from './gr-diff-builder-side-by-side';
import {ImageInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrEndpointParam} from '../../../elements/plugins/gr-endpoint-param/gr-endpoint-param';
import {RenderPreferences} from '../../../api/diff';
import '../gr-diff-image-viewer/gr-image-viewer';
import {GrImageViewer} from '../gr-diff-image-viewer/gr-image-viewer';
import {createElementDiff} from '../gr-diff/gr-diff-utils';

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
    const section = createElementDiff('tbody', 'image-diff');

    if (this._useNewImageDiffUi) {
      this._emitImageViewer(section);

      this.outputEl.appendChild(section);
    } else {
      this._emitImagePair(section);
      this._emitImageLabels(section);

      this.outputEl.appendChild(section);
      this.outputEl.appendChild(this._createEndpoint());
    }
  }

  private _createEndpoint() {
    const tbody = createElementDiff('tbody');
    const tr = createElementDiff('tr');
    const td = createElementDiff('td');

    // TODO(kaspern): Support blame for image diffs and remove the hardcoded 4
    // column limit.
    td.setAttribute('colspan', '4');
    const endpointDomApi = createElementDiff('gr-endpoint-decorator');
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
    const endpointParam = createElementDiff(
      'gr-endpoint-param'
    ) as GrEndpointParam;
    endpointParam.name = name;
    endpointParam.value = value;
    return endpointParam;
  }

  private _emitImageViewer(section: HTMLElement) {
    const tr = createElementDiff('tr');
    const td = createElementDiff('td');
    // TODO(hermannloose): Support blame for image diffs, see above.
    td.setAttribute('colspan', '4');
    const imageViewer = createElementDiff('gr-image-viewer') as GrImageViewer;

    imageViewer.baseUrl = this._getImageSrc(this._baseImage);
    imageViewer.revisionUrl = this._getImageSrc(this._revisionImage);
    imageViewer.automaticBlink =
      !!this.renderPrefs?.image_diff_prefs?.automatic_blink;

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
    const tr = createElementDiff('tr');

    tr.appendChild(createElementDiff('td', 'left lineNum blank'));
    tr.appendChild(this._createImageCell(this._baseImage, 'left', section));

    tr.appendChild(createElementDiff('td', 'right lineNum blank'));
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
    const td = createElementDiff('td', className);
    const src = this._getImageSrc(image);
    if (image && src) {
      const imageEl = createElementDiff('img') as HTMLImageElement;
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
    const tr = createElementDiff('tr');

    let addNamesInLabel = false;

    if (
      this._baseImage &&
      this._revisionImage &&
      this._baseImage._name !== this._revisionImage._name
    ) {
      addNamesInLabel = true;
    }

    tr.appendChild(createElementDiff('td', 'left lineNum blank'));
    let td = createElementDiff('td', 'left');
    let label = createElementDiff('label');
    let nameSpan;
    let labelSpan = createElementDiff('span', 'label');

    if (addNamesInLabel) {
      nameSpan = createElementDiff('span', 'name');
      nameSpan.textContent = this._baseImage?._name ?? '';
      label.appendChild(nameSpan);
      label.appendChild(createElementDiff('br'));
    }

    this._setLabelText(labelSpan, this._baseImage);

    label.appendChild(labelSpan);
    td.appendChild(label);
    tr.appendChild(td);

    tr.appendChild(createElementDiff('td', 'right lineNum blank'));
    td = createElementDiff('td', 'right');
    label = createElementDiff('label');
    labelSpan = createElementDiff('span', 'label');

    if (addNamesInLabel) {
      nameSpan = createElementDiff('span', 'name');
      nameSpan.textContent = this._revisionImage?._name ?? '';
      label.appendChild(nameSpan);
      label.appendChild(createElementDiff('br'));
    }

    this._setLabelText(labelSpan, this._revisionImage);

    label.appendChild(labelSpan);
    td.appendChild(label);
    tr.appendChild(td);

    section.appendChild(tr);
  }

  override updateRenderPrefs(renderPrefs: RenderPreferences) {
    const imageViewer = this.outputEl.querySelector(
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
