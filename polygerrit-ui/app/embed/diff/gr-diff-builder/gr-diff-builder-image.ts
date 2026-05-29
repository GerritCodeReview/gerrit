/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Base64ImageFile} from '../../../types/common';
import {Side} from '../../../api/diff';
import '../gr-diff-image-viewer/gr-image-viewer';
import {html, LitElement, nothing} from 'lit';
import {property, query, state} from 'lit/decorators.js';

// MIME types for images we allow showing. Do not include SVG, it can contain
// arbitrary JavaScript.
const IMAGE_MIME_PATTERN = /^image\/(bmp|gif|x-icon|jpeg|jpg|png|tiff|webp)$/;

class GrDiffImageNew extends LitElement {
  @property() baseImage?: Base64ImageFile;

  @property() revisionImage?: Base64ImageFile;

  @property() automaticBlink = false;

  @property() columnCount = 0;

  /**
   * The browser API for handling selection does not (yet) work for selection
   * across multiple shadow DOM elements. So we are rendering gr-diff components
   * into the light DOM instead of the shadow DOM by overriding this method,
   * which was the recommended workaround by the lit team.
   * See also https://github.com/WICG/webcomponents/issues/79.
   */
  override createRenderRoot() {
    return this;
  }

  override render() {
    return html`
      <tbody class="gr-diff image-diff">
        <tr class="gr-diff">
          <td class="gr-diff" colspan=${this.columnCount}>
            <gr-image-viewer
              class="gr-diff"
              .baseUrl=${imageSrc(this.baseImage)}
              .revisionUrl=${imageSrc(this.revisionImage)}
              .automaticBlink=${this.automaticBlink}
            >
            </gr-image-viewer>
          </td>
        </tr>
      </tbody>
    `;
  }
}

class GrDiffImageOld extends LitElement {
  @property() baseImage?: Base64ImageFile;

  @property() revisionImage?: Base64ImageFile;

  @property() columnCount = 0;

  @query('img.left') baseImageEl?: HTMLImageElement;

  @query('img.right') revisionImageEl?: HTMLImageElement;

  @state() baseError?: string;

  @state() revisionError?: string;

  /**
   * The browser API for handling selection does not (yet) work for selection
   * across multiple shadow DOM elements. So we are rendering gr-diff components
   * into the light DOM instead of the shadow DOM by overriding this method,
   * which was the recommended workaround by the lit team.
   * See also https://github.com/WICG/webcomponents/issues/79.
   */
  override createRenderRoot() {
    return this;
  }

  override render() {
    return html`
      <tbody class="gr-diff image-diff">
        ${this.renderImagePairRow()} ${this.renderImageLabelRow()}
      </tbody>
      ${this.renderEndpoint()}
    `;
  }

  private renderEndpoint() {
    return html`
      <tbody class="gr-diff endpoint">
        <tr class="gr-diff">
          <td class="gr-diff" colspan=${this.columnCount}>
            <gr-endpoint-decorator class="gr-diff" name="image-diff">
              ${this.renderEndpointParam('baseImage', this.baseImage)}
              ${this.renderEndpointParam('revisionImage', this.revisionImage)}
            </gr-endpoint-decorator>
          </td>
        </tr>
      </tbody>
    `;
  }

  private renderEndpointParam(name: string, value: unknown) {
    if (!value) return nothing;
    return html`
      <gr-endpoint-param class="gr-diff" name=${name} .value=${value}>
      </gr-endpoint-param>
    `;
  }

  private renderImagePairRow() {
    return html`
      <tr class="gr-diff">
        <td class="gr-diff left lineNum blank"></td>
        <td class="gr-diff left">${this.renderImage(Side.LEFT)}</td>
        <td class="gr-diff right lineNum blank"></td>
        <td class="gr-diff right">${this.renderImage(Side.RIGHT)}</td>
      </tr>
    `;
  }

  private renderImage(side: Side) {
    const image = side === Side.LEFT ? this.baseImage : this.revisionImage;
    if (!image) return nothing;
    const error = side === Side.LEFT ? this.baseError : this.revisionError;
    if (error) return error;
    const src = imageSrc(image);
    if (!src) return nothing;

    return html`
      <img
        class="gr-diff ${side}"
        src=${src}
        @load=${this.handleLoad}
        @error=${(e: Event) => this.handleError(e, side)}
      >
      </img>
    `;
  }

  private handleLoad() {
    this.requestUpdate();
  }

  private handleError(e: Event, side: Side) {
    const msg = `[Image failed to load] ${e.type}`;
    if (side === Side.LEFT) this.baseError = msg;
    if (side === Side.RIGHT) this.revisionError = msg;
  }

  private renderImageLabelRow() {
    return html`
      <tr class="gr-diff">
        <td class="gr-diff left lineNum blank"></td>
        <td class="gr-diff left">
          <label class="gr-diff">
            ${this.renderName(this.baseImage?._name ?? '')}
            <span class="gr-diff label">${this.imageLabel(Side.LEFT)}</span>
          </label>
        </td>
        <td class="gr-diff right lineNum blank"></td>
        <td class="gr-diff right">
          <label class="gr-diff">
            ${this.renderName(this.revisionImage?._name ?? '')}
            <span class="gr-diff label"> ${this.imageLabel(Side.RIGHT)} </span>
          </label>
        </td>
      </tr>
    `;
  }

  private renderName(name?: string) {
    const addNamesInLabel =
      this.baseImage &&
      this.revisionImage &&
      this.baseImage._name !== this.revisionImage._name;
    if (!addNamesInLabel) return nothing;
    return html`
      <span class="gr-diff name">${name}</span><br class="gr-diff" />
    `;
  }

  private imageLabel(side: Side) {
    const image = side === Side.LEFT ? this.baseImage : this.revisionImage;
    const imageEl =
      side === Side.LEFT ? this.baseImageEl : this.revisionImageEl;
    if (image) {
      const type = image.type ?? image._expectedType;
      if (imageEl?.naturalWidth && imageEl.naturalHeight) {
        return `${imageEl?.naturalWidth}×${imageEl.naturalHeight} ${type}`;
      } else {
        return type;
      }
    }
    return 'No image';
  }
}

function imageSrc(image?: Base64ImageFile): string {
  return image?.type && IMAGE_MIME_PATTERN.test(image.type)
    ? `data:${image.type};base64,${image.body}`
    : '';
}

customElements.define('gr-diff-image-new', GrDiffImageNew);
customElements.define('gr-diff-image-old', GrDiffImageOld);

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-image-new': GrDiffImageNew;
    'gr-diff-image-old': GrDiffImageOld;
  }
}
