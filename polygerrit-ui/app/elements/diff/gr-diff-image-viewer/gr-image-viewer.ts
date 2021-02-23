/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import '@material/mwc-button';
import '@material/mwc-checkbox';
import '@material/mwc-formfield';
import '@material/mwc-select';
import '@polymer/paper-card/paper-card';
import '@polymer/paper-styles/paper-styles';
import './gr-overview-image';
import './gr-zoomed-image';

import {
  css,
  customElement,
  html,
  internalProperty,
  LitElement,
  property,
  PropertyValues,
  query,
} from 'lit-element';
import { classMap } from 'lit-html/directives/class-map';
import { StyleInfo, styleMap } from 'lit-html/directives/style-map';

import { isEventMulti, SelectedEvent } from '@material/mwc-list/mwc-list-foundation';

import { Dimensions, fitToFrame, FrameConstrainer, Rect } from './util';

/**
 * This components allows the user to rapidly switch between two given images
 * rendered in the same location, to make subtle differences more noticeable.
 * Images can be magnified to compare details.
 */
@customElement('gr-image-viewer')
export class GrImageViewer extends LitElement {
  // URL for the image to use as base.
  @property({ type: String }) baseUrl = '';

  // URL for the image to use as revision.
  @property({ type: String }) revisionUrl = '';

  @internalProperty() protected baseImage?: HTMLImageElement;

  @internalProperty() protected revisionImage?: HTMLImageElement;

  @internalProperty() protected baseSelected = true;

  @internalProperty() protected scaledSelected = true;

  @internalProperty() protected followMouse = false;

  @internalProperty() protected scale = 1;

  @internalProperty() protected checkerboardSelected = true;

  @internalProperty() protected zoomedImageStyle: StyleInfo = {};

  @query('.imageArea') protected imageArea!: HTMLDivElement;

  @query('gr-zoomed-image') protected zoomedImage!: Element;

  @query('#source-image') protected sourceImage!: HTMLImageElement;

  private imageSize: Dimensions = { width: 0, height: 0 };

  @internalProperty()
  protected magnifierSize: Dimensions = { width: 0, height: 0 };

  @internalProperty()
  protected magnifierFrame: Rect = {
    origin: { x: 0, y: 0 },
    dimensions: { width: 0, height: 0 },
  };

  @internalProperty()
  protected overviewFrame: Rect = {
    origin: { x: 0, y: 0 },
    dimensions: { width: 0, height: 0 },
  };

  protected readonly zoomLevels: Array<'fit' | number> =
    ['fit', 1, 1.25, 1.5, 1.75, 2];

  private readonly frameConstrainer = new FrameConstrainer();

  private readonly resizeObserver = new ResizeObserver(
    (entries: ResizeObserverEntry[]) => {
      for (const entry of entries) {
        if (entry.target === this.imageArea) {
          this.magnifierSize = {
            width: entry.contentRect.width,
            height: entry.contentRect.height,
          };
        }
      }
    }
  );

  static styles = css`
    :host {
      display: flex;
      width: 100%;
      height: 100%;
      --border-color: #dadce0;
      --mdc-theme-primary: var(--primary-button-background-color);
      --mdc-typography-button-font-size: 14px;
      --mdc-typography-button-font-weight: 400;
      --mdc-typography-button-letter-spacing: normal;
      box-sizing: border-box;
    }
    .imageArea {
      box-sizing: border-box;
      flex-grow: 1;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      align-items: center;
      margin: 8px;
      padding: 2px;
      max-height: 100%;
    }
    #spacer {
      visibility: hidden;
    }
    gr-zoomed-image {
      border: 2px solid;
      margin: -2px;
      box-sizing: content-box;
      position: absolute;
      overflow: hidden;
      cursor: pointer;
    }
    gr-zoomed-image.base {
      border-color: var(--base-image-border-color, rgb(255, 205, 210));
    }
    gr-zoomed-image.revision {
      border-color: var(--revision-image-border-color, rgb(170, 242, 170));
    }
    .checkerboard {
      --square-size: var(--checkerboard-square-size, 10px);
      --square-color: var(--checkerboard-square-color, #808080);
      background-color: var(--checkerboard-background-color, #aaaaaa);
      background-image: linear-gradient(
          45deg,
          var(--square-color) 25%,
          transparent 25%
        ),
        linear-gradient(-45deg, var(--square-color) 25%, transparent 25%),
        linear-gradient(45deg, transparent 75%, var(--square-color) 75%),
        linear-gradient(-45deg, transparent 75%, var(--square-color) 75%);
      background-size: calc(var(--square-size) * 2) calc(var(--square-size) * 2);
      background-position: 0 0, 0 var(--square-size),
        var(--square-size) calc(-1 * var(--square-size)),
        calc(-1 * var(--square-size)) 0;
    }
    .controls {
      flex-grow: 0;
      display: flex;
      flex-direction: column;
      align-self: flex-start;
      margin: 8px;
      padding: 8px 0;
    }
    .control {
      margin: 8px 16px;
    }
    .version-switcher {
      display: flex;
      --mdc-button-outline-color: auto;
    }
    .version-switcher mwc-button {
      flex-basis: 0;
      flex-grow: 1;
    }
    .version-switcher > .left {
      --mdc-shape-small: 4px 0 0 4px;
    }
    .version-switcher > .right {
      --mdc-shape-small: 0 4px 4px 0;
    }
    .version-explanation {
      color: #555;
      text-align: center;
      margin: 8px 16px;
    }
    gr-overview-image {
      min-width: 200px;
      min-height: 150px;
      margin: 8px 0;
    }
  `;

  render() {
    const src = this.baseSelected ? this.baseUrl : this.revisionUrl;

    const sourceImage = html`
      <img
        id="source-image"
        src="${src}"
        class="${classMap({
      checkerboard: this.checkerboardSelected,
    })}"
        @load="${this.updateSizes}"
      />
    `;

    const versionExplanation = html`
        <div class="control version-explanation">
          This file is being ${this.revisionUrl ? 'added' : 'deleted'}.
        </div>
        `;

    const versionToggle = html`
        <div class="control version-switcher">
          <mwc-button
            label="Base"
            class="left"
            ?unelevated="${this.baseSelected}"
            ?outlined=${!this.baseSelected}
            @click="${this.selectBase}">
          </mwc-button>
          <mwc-button
            label="Revision"
            class="right"
            ?unelevated="${!this.baseSelected}"
            ?outlined=${this.baseSelected}
            @click="${this.selectRevision}">
          </mwc-button>
        </div>
        `;

    const versionSwitcher = html`
        ${this.baseUrl && this.revisionUrl ? versionToggle : versionExplanation}
        `;

    const overviewImage = html`
      <gr-overview-image
        .frameRect="${this.overviewFrame}"
        @center-updated="${this.onOverviewCenterUpdated}"
      >
        <img src="${src}" class="checkerboard" />
      </gr-overview-image>
    `;

    const zoomControl = html`
        <mwc-select
            label="Zoom"
            class="control"
            outlined
            value="fit"
            @selected="${this.zoomControlChanged}">
          ${this.zoomLevels.map(zoomLevel => html`
              <mwc-list-item value="${zoomLevel}">
                ${zoomLevel === 'fit' ? 'Fit' : `${zoomLevel * 100}%`}
              </mwc-list-item>
              `)}
        </mwc-select>
    `;

    const followMouse = html`
      <mwc-formfield label="Magnifier follows mouse" class="control">
        <mwc-checkbox
          ?checked="${this.followMouse}"
          @change="${this.followMouseChanged}"
        ></mwc-checkbox>
      </mwc-formfield>
    `;

    /* 
     * We want the content to fill the available space until it can display
     * without being cropped, the maximum of which will be determined by
     * (max-)width and (max-)height constraints on the host element; but we
     * are also limiting the displayed content to the measured dimensions of
     * the host element without overflow, so we need something else to take up
     * the requested space unconditionally.
     */
    const spacerScale = Math.max(this.scale, 1);
    const spacerWidth = this.imageSize.width * spacerScale;
    const spacerHeight = this.imageSize.height * spacerScale;
    const spacer = html`
        <div id="spacer" style="${styleMap({
      'width': `${spacerWidth}px`,
      'height': `${spacerHeight}px`,
    })}">
        </div>
        `;

    return html`
      <div class="imageArea" @mousemove="${this.mousemoveMagnifier}">
        <gr-zoomed-image
          class="${classMap({
      base: this.baseSelected,
      revision: !this.baseSelected,
    })}"
          style="${styleMap(this.zoomedImageStyle)}"
          .scale="${this.scale}"
          .frameRect="${this.magnifierFrame}"
          @click="${this.toggleImage}"
          @mousemove="${this.mousemoveMagnifier}"
        >
          ${sourceImage}
        </gr-zoomed-image>
        ${spacer}
      </div>

      <paper-card class="controls">
        ${versionSwitcher} ${overviewImage} ${zoomControl}
        ${!this.scaledSelected ? followMouse : ''}
      </paper-card>
    `;
  }

  firstUpdated() {
    this.resizeObserver.observe(this.imageArea, { box: 'content-box' });
  }

  update(changedProperties: PropertyValues) {
    if (!this.baseUrl) this.baseSelected = false;
    if (!this.revisionUrl) this.baseSelected = true;
    this.updateSizes();
    super.update(changedProperties);
  }

  updated(changedProperties: PropertyValues) {
    if (
      (changedProperties.has('baseUrl') && this.baseSelected) ||
      (changedProperties.has('revisionUrl') && !this.baseSelected)
    ) {
      this.frameConstrainer.requestCenter({ x: 0, y: 0 });
    }
  }

  selectBase() {
    if (!this.baseUrl) return;
    this.baseSelected = true;
  }

  selectRevision() {
    if (!this.revisionUrl) return;
    this.baseSelected = false;
  }

  toggleImage() {
    if (this.baseUrl && this.revisionUrl) {
      this.baseSelected = !this.baseSelected;
    }
  }

  zoomControlChanged(event: SelectedEvent) {
    if (!isEventMulti(event)) {
      const index = event.detail.index;
      const value = this.zoomLevels[index];
      if (!value) return;
      if (value === 'fit') {
        this.scaledSelected = true;
      } else {
        if (value > 0) {
          this.scaledSelected = false;
          this.scale = value;
        }
      }
      this.updateSizes();
    }
  }

  followMouseChanged() {
    this.followMouse = !this.followMouse;
  }

  mousemoveMagnifier(event: MouseEvent) {
    if (!this.followMouse) return;
    const rect = this.imageArea!.getBoundingClientRect();
    const offsetX = event.clientX - rect.left;
    const offsetY = event.clientY - rect.top;
    const fractionX = offsetX / rect.width;
    const fractionY = offsetY / rect.height;
    this.frameConstrainer.requestCenter({
      x: this.imageSize.width * fractionX,
      y: this.imageSize.height * fractionY,
    });
    this.updateFrames();
  }

  onOverviewCenterUpdated(event: CustomEvent) {
    this.frameConstrainer.requestCenter({
      x: event.detail.x as number,
      y: event.detail.y as number,
    });
    this.updateFrames();
  }

  updateFrames() {
    this.magnifierFrame = this.frameConstrainer.getUnscaledFrame();
    this.overviewFrame = this.frameConstrainer.getScaledFrame();
  }

  updateSizes() {
    if (!this.sourceImage || !this.sourceImage.complete) return;

    this.imageSize = {
      width: this.sourceImage.naturalWidth || 0,
      height: this.sourceImage.naturalHeight || 0,
    };

    this.frameConstrainer.setBounds(this.imageSize);

    if (this.scaledSelected) {
      const fittedImage = fitToFrame(this.imageSize, this.magnifierSize);
      this.scale = Math.min(fittedImage.scale, 1);
    }

    this.frameConstrainer.setScale(this.scale);

    const scaledImageSize = {
      width: this.imageSize.width * this.scale,
      height: this.imageSize.height * this.scale,
    };

    const width = Math.min(this.magnifierSize.width, scaledImageSize.width);
    const height = Math.min(this.magnifierSize.height, scaledImageSize.height);

    this.frameConstrainer.setFrameSize({ width, height });

    this.updateFrames();

    this.zoomedImageStyle = {
      ...this.zoomedImageStyle,
      width: `${width}px`,
      height: `${height}px`,
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-image-viewer': GrImageViewer;
  }
}
