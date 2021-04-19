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
import '@polymer/paper-button/paper-button';
import '@polymer/paper-card/paper-card';
import '@polymer/paper-checkbox/paper-checkbox';
import '@polymer/paper-dropdown-menu/paper-dropdown-menu';
import '@polymer/paper-item/paper-item';
import '@polymer/paper-listbox/paper-listbox';
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
import {classMap} from 'lit-html/directives/class-map';
import {StyleInfo, styleMap} from 'lit-html/directives/style-map';

import {
  createEvent,
  Dimensions,
  fitToFrame,
  FrameConstrainer,
  Point,
  Rect,
} from './util';
import {ImageDiffAction} from '../../../api/diff';

const DRAG_DEAD_ZONE_PIXELS = 5;

/**
 * This components allows the user to rapidly switch between two given images
 * rendered in the same location, to make subtle differences more noticeable.
 * Images can be magnified to compare details.
 */
@customElement('gr-image-viewer')
export class GrImageViewer extends LitElement {
  // URL for the image to use as base.
  @property({type: String}) baseUrl = '';

  // URL for the image to use as revision.
  @property({type: String}) revisionUrl = '';

  @internalProperty() protected baseSelected = true;

  @internalProperty() protected scaledSelected = true;

  @internalProperty() protected followMouse = false;

  @internalProperty() protected scale = 1;

  @internalProperty() protected checkerboardSelected = true;

  @internalProperty() protected zoomedImageStyle: StyleInfo = {};

  @query('.imageArea') protected imageArea!: HTMLDivElement;

  @query('gr-zoomed-image') protected zoomedImage!: Element;

  @query('#source-image') protected sourceImage!: HTMLImageElement;

  private imageSize: Dimensions = {width: 0, height: 0};

  @internalProperty()
  protected magnifierSize: Dimensions = {width: 0, height: 0};

  @internalProperty()
  protected magnifierFrame: Rect = {
    origin: {x: 0, y: 0},
    dimensions: {width: 0, height: 0},
  };

  @internalProperty()
  protected overviewFrame: Rect = {
    origin: {x: 0, y: 0},
    dimensions: {width: 0, height: 0},
  };

  protected readonly zoomLevels: Array<'fit' | number> = [
    'fit',
    1,
    1.25,
    1.5,
    1.75,
    2,
  ];

  @internalProperty() protected grabbing = false;

  private ownsMouseDown = false;

  private centerOnDown: Point = {x: 0, y: 0};

  private pointerOnDown: Point = {x: 0, y: 0};

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
      box-sizing: border-box;
      font-size: var(--font-size-normal);
      --image-border-width: 2px;
    }
    .imageArea {
      box-sizing: border-box;
      flex-grow: 1;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      align-items: center;
      margin: var(--spacing-m);
      padding: var(--image-border-width);
      max-height: 100%;
    }
    #spacer {
      visibility: hidden;
    }
    gr-zoomed-image {
      border: var(--image-border-width) solid;
      margin: calc(-1 * var(--image-border-width));
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
      margin: var(--spacing-m);
      padding-bottom: var(--spacing-xl);
    }
    paper-button {
      padding: var(--spacing-m);
      font: var(--image-diff-button-font);
      text-transform: var(--image-diff-button-text-transform, uppercase);
    }
    paper-button[unelevated] {
      color: var(--primary-button-text-color);
      background-color: var(--primary-button-background-color);
    }
    paper-button[outlined] {
      color: var(--primary-button-background-color);
      border-color: var(--primary-button-background-color);
    }
    #version-switcher {
      display: flex;
      margin: var(--spacing-xl);
    }
    #version-switcher paper-button {
      flex-basis: 0;
      flex-grow: 1;
      margin: 0;
    }
    #version-explanation {
      color: var(--deemphasized-text-color);
      text-align: center;
      margin: var(--spacing-xl);
    }
    gr-overview-image {
      min-width: 200px;
      min-height: 150px;
    }
    #zoom-control {
      margin: 0 var(--spacing-xl);
    }
    #follow-mouse {
      margin: var(--spacing-m) var(--spacing-xl) 0;
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
      <div id="version-explanation">
        This file is being ${this.revisionUrl ? 'added' : 'deleted'}.
      </div>
    `;

    // This uses the unelevated and outlined attributes from mwc-button with
    // manual styling, for a more seamless transition later.
    const versionToggle = html`
      <div id="version-switcher">
        <paper-button
          class="left"
          ?unelevated=${this.baseSelected}
          ?outlined=${!this.baseSelected}
          @click="${this.selectBase}"
        >
          Base
        </paper-button>
        <paper-button
          class="right"
          ?unelevated=${!this.baseSelected}
          ?outlined=${this.baseSelected}
          @click="${this.selectRevision}"
        >
          Revision
        </paper-button>
      </div>
    `;

    const versionSwitcher = html`
      ${this.baseUrl && this.revisionUrl ? versionToggle : versionExplanation}
    `;

    const overviewImage = html`
      <gr-overview-image
        .frameRect="${this.overviewFrame}"
        @center-updated="${this.onOverviewCenterUpdated}"
        @image-diff-action="${this.logImageDiffAction}"
      >
        <img src="${src}" class="checkerboard" />
      </gr-overview-image>
    `;

    const zoomControl = html`
      <paper-dropdown-menu id="zoom-control" label="Zoom">
        <paper-listbox
          slot="dropdown-content"
          selected="fit"
          attr-for-selected="value"
          @selected-changed="${this.zoomControlChanged}"
        >
          ${this.zoomLevels.map(
            zoomLevel => html`
              <paper-item value="${zoomLevel}">
                ${zoomLevel === 'fit' ? 'Fit' : `${zoomLevel * 100}%`}
              </paper-item>
            `
          )}
        </paper-listbox>
      </paper-dropdown-menu>
    `;

    const followMouse = html`
      <paper-checkbox
        id="follow-mouse"
        ?checked="${this.followMouse}"
        @change="${this.followMouseChanged}"
      >
        Magnifier follows mouse
      </paper-checkbox>
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
      <div
        id="spacer"
        style="${styleMap({
          width: `${spacerWidth}px`,
          height: `${spacerHeight}px`,
        })}"
      ></div>
    `;

    // To pass CSS mixins for @apply to Polymer components, they need to be
    // wrapped in a <custom-style>.
    const customStyle = html`
      <custom-style>
        <style>
            paper-button.left {
              --paper-button: {
                border-radius: 4px 0 0 4px;
                border-width: 1px 0 1px 1px;
              }
            }
            paper-button.left[outlined] {
              --paper-button: {
                border-radius: 4px 0 0 4px;
                border-width: 1px 0 1px 1px;
                border-style: solid;
                border-color: var(--primary-button-background-color);
              }
            }
            paper-button.right {
              --paper-button: {
                border-radius: 0 4px 4px 0;
                border-width: 1px 1px 1px 0;
              }
            }
            paper-button.right[outlined] {
              --paper-button: {
                border-radius: 0 4px 4px 0;
                border-width: 1px 1px 1px 0;
                border-style: solid;
                border-color: var(--primary-button-background-color);
              }
            }
            paper-item {
              cursor: pointer;
              --paper-item-min-height: 48;
              --paper-item: {
                min-height: 48px;
                padding: 0 var(--spacing-xl);
              }
              --paper-item-focused-before: {
                background-color: var(--selection-background-color);
              }
              --paper-item-focused: {
                background-color: var(--selection-background-color);
              }
            }
          }
          paper-item:hover {
            background-color: var(--hover-background-color);
          }
        </style>
      </custom-style>
    `;

    return html`
      ${customStyle}
      <div class="imageArea" @mousemove="${this.mousemoveMagnifier}">
        <gr-zoomed-image
          class="${classMap({
            base: this.baseSelected,
            revision: !this.baseSelected,
          })}"
          style="${styleMap({
            ...this.zoomedImageStyle,
            cursor: this.grabbing ? 'grabbing' : 'pointer',
          })}"
          .scale="${this.scale}"
          .frameRect="${this.magnifierFrame}"
          @mousedown="${this.mousedownMagnifier}"
          @mouseup="${this.mouseupMagnifier}"
          @mousemove="${this.mousemoveMagnifier}"
          @mouseleave="${this.mouseleaveMagnifier}"
          @dragstart="${this.dragstartMagnifier}"
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

  private logImageDiffAction(event: CustomEvent<ImageDiffAction>) {
    console.log(event.detail.type, event);
  }

  firstUpdated() {
    this.resizeObserver.observe(this.imageArea, {box: 'content-box'});
  }

  // We don't want property changes in updateSizes() to trigger infinite update
  // loops, so we perform this in update() instead of updated().
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
      this.frameConstrainer.requestCenter({x: 0, y: 0});
    }
  }

  selectBase() {
    if (!this.baseUrl) return;
    this.baseSelected = true;
    this.dispatchEvent(
      createEvent({type: 'version-switcher-clicked', button: 'base'})
    );
  }

  selectRevision() {
    if (!this.revisionUrl) return;
    this.baseSelected = false;
    this.dispatchEvent(
      createEvent({type: 'version-switcher-clicked', button: 'revision'})
    );
  }

  toggleImage() {
    if (this.baseUrl && this.revisionUrl) {
      this.baseSelected = !this.baseSelected;
    }
  }

  zoomControlChanged(event: CustomEvent) {
    const value = event.detail.value;
    if (!value) return;
    if (value === 'fit') {
      this.scaledSelected = true;
      this.dispatchEvent(
        createEvent({type: 'zoom-level-changed', scale: 'fit'})
      );
    }
    if (value > 0) {
      this.scaledSelected = false;
      this.scale = value;
      this.dispatchEvent(
        createEvent({type: 'zoom-level-changed', scale: value})
      );
    }
    this.updateSizes();
  }

  followMouseChanged() {
    this.followMouse = !this.followMouse;
    this.dispatchEvent(
      createEvent({type: 'follow-mouse-changed', value: this.followMouse})
    );
  }

  mousedownMagnifier(event: MouseEvent) {
    if (event.buttons === 1) {
      this.ownsMouseDown = true;
      this.centerOnDown = this.frameConstrainer.getCenter();
      this.pointerOnDown = {
        x: event.clientX,
        y: event.clientY,
      };
    }
  }

  mouseupMagnifier(event: MouseEvent) {
    const offsetX = event.clientX - this.pointerOnDown.x;
    const offsetY = event.clientY - this.pointerOnDown.y;
    const distance = Math.max(Math.abs(offsetX), Math.abs(offsetY));
    // Consider very short drags as clicks. These tend to happen more often on
    // external mice.
    if (this.ownsMouseDown && distance < DRAG_DEAD_ZONE_PIXELS) {
      this.toggleImage();
    }
    this.grabbing = false;
    this.ownsMouseDown = false;
  }

  mousemoveMagnifier(event: MouseEvent) {
    if (event.buttons === 1 && this.ownsMouseDown) {
      this.handleMagnifierDrag(event);
      return;
    }
    if (this.followMouse) {
      this.handleFollowMouse(event);
      return;
    }
  }

  private handleMagnifierDrag(event: MouseEvent) {
    this.grabbing = true;
    const offsetX = event.clientX - this.pointerOnDown.x;
    const offsetY = event.clientY - this.pointerOnDown.y;
    this.frameConstrainer.requestCenter({
      x: this.centerOnDown.x - offsetX / this.scale,
      y: this.centerOnDown.y - offsetY / this.scale,
    });
    this.updateFrames();
  }

  private handleFollowMouse(event: MouseEvent) {
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

  mouseleaveMagnifier() {
    this.grabbing = false;
    this.ownsMouseDown = false;
  }

  dragstartMagnifier(event: DragEvent) {
    event.preventDefault();
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

    this.frameConstrainer.setFrameSize({width, height});

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
