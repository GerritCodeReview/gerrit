/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ImageInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {FILE, RenderPreferences, Side} from '../../../api/diff';
import '../gr-diff-image-viewer/gr-image-viewer';
import {html, LitElement, nothing} from 'lit';
import {property, query, state} from 'lit/decorators.js';
import {GrDiffBuilder} from './gr-diff-builder';
import {GrDiffGroup} from '../gr-diff/gr-diff-group';
import {isNewDiff, createElementDiff} from '../gr-diff/gr-diff-utils';

// MIME types for images we allow showing. Do not include SVG, it can contain
// arbitrary JavaScript.
const IMAGE_MIME_PATTERN = /^image\/(bmp|gif|x-icon|jpeg|jpg|png|tiff|webp)$/;

export class GrDiffBuilderImage extends GrDiffBuilder {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement,
    private readonly baseImage: ImageInfo | null,
    private readonly revisionImage: ImageInfo | null,
    renderPrefs?: RenderPreferences,
    private readonly useNewImageDiffUi: boolean = false
  ) {
    super(diff, prefs, outputEl, [], renderPrefs);
  }

  override buildSectionElement(group: GrDiffGroup): HTMLElement {
    const section = createElementDiff('tbody');
    // Do not create a diff row for LOST.
    if (group.lines[0].beforeNumber !== FILE) return section;
    return super.buildSectionElement(group);
  }

  public renderImageDiff() {
    const imageDiff = this.useNewImageDiffUi
      ? this.createImageDiffNew()
      : this.createImageDiffOld();
    this.outputEl.appendChild(imageDiff);
  }

  private createImageDiffNew() {
    // TODO(newdiff-cleanup): Remove cast when newdiff migration is complete.
    const imageDiff = document.createElement(
      'gr-diff-image-new'
    ) as GrDiffImageNew;
    imageDiff.automaticBlink = this.autoBlink();
    imageDiff.baseImage = this.baseImage ?? undefined;
    imageDiff.revisionImage = this.revisionImage ?? undefined;
    return imageDiff;
  }

  private createImageDiffOld() {
    // TODO(newdiff-cleanup): Remove cast when newdiff migration is complete.
    const imageDiff = document.createElement(
      'gr-diff-image-old'
    ) as GrDiffImageOld;
    imageDiff.baseImage = this.baseImage ?? undefined;
    imageDiff.revisionImage = this.revisionImage ?? undefined;
    return imageDiff;
  }

  private autoBlink(): boolean {
    return !!this.renderPrefs?.image_diff_prefs?.automatic_blink;
  }

  override updateRenderPrefs(renderPrefs: RenderPreferences) {
    this.renderPrefs = renderPrefs;

    // We have to update `imageDiff.automaticBlink` manually, because `this` is
    // not a LitElement.
    const imageDiff = this.outputEl.querySelector(
      'gr-diff-image-new'
    ) as GrDiffImageNew;
    if (imageDiff) imageDiff.automaticBlink = this.autoBlink();
  }
}

class GrDiffImageNew extends LitElement {
  @property() baseImage?: ImageInfo;

  @property() revisionImage?: ImageInfo;

  @property() automaticBlink = false;

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
          <td class="gr-diff" colspan="4">
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
  @property() baseImage?: ImageInfo;

  @property() revisionImage?: ImageInfo;

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
          <td class="gr-diff" colspan="4">
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

function imageSrc(image?: ImageInfo): string {
  return image && IMAGE_MIME_PATTERN.test(image.type)
    ? `data:${image.type};base64,${image.body}`
    : '';
}

// TODO(newdiff-cleanup): Remove once newdiff migration is completed.
if (isNewDiff()) {
  customElements.define('gr-diff-image-new', GrDiffImageNew);
  customElements.define('gr-diff-image-old', GrDiffImageOld);
}

declare global {
  interface HTMLElementTagNameMap {
    // TODO(newdiff-cleanup): Replace once newdiff migration is completed.
    'gr-diff-image-new': LitElement;
    'gr-diff-image-old': LitElement;
  }
}
