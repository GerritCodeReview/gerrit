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
import {StyleInfo, styleMap} from 'lit-html/directives/style-map';

import {Dimensions, fitToFrame, Point, Rect} from './util';

/**
 * Displays a scaled-down version of an image with a draggable frame for
 * choosing a portion of the image to be magnified by other components.
 *
 * Slotted content can be arbitrary elements, but should be limited to images or
 * stacks of image-like elements (e.g. for overlays) with limited interactivity,
 * to prevent confusion, as the component only captures a limited set of events.
 * Slotted content is scaled to fit the bounds of the component, with
 * letterboxing if aspect ratios differ. For slotted content smaller than the
 * component, it will cap the scale at 1x and also apply letterboxing.
 */
@customElement('gr-overview-image')
export class GrOverviewImage extends LitElement {
  @property({type: Object})
  frameRect: Rect = {origin: {x: 0, y: 0}, dimensions: {width: 0, height: 0}};

  @internalProperty() protected contentStyle: StyleInfo = {};

  @internalProperty() protected contentTransformStyle: StyleInfo = {};

  @internalProperty() protected frameStyle: StyleInfo = {};

  @internalProperty() protected overlayStyle: StyleInfo = {};

  @internalProperty() protected dragging = false;

  @query('.content-box') protected contentBox!: HTMLDivElement;

  @query('.content') protected content!: HTMLDivElement;

  @query('.content-transform') protected contentTransform!: HTMLDivElement;

  @query('.frame') protected frame!: HTMLDivElement;

  private contentBounds: Dimensions = {width: 0, height: 0};

  private imageBounds: Dimensions = {width: 0, height: 0};

  private scale = 1;

  // When grabbing the frame to drag it around, this stores the offset of the
  // cursor from the center of the frame at the start of the drag.
  private grabOffset: Point = {x: 0, y: 0};

  private readonly resizeObserver = new ResizeObserver(
    (entries: ResizeObserverEntry[]) => {
      for (const entry of entries) {
        if (entry.target === this.contentBox) {
          this.contentBounds = {
            width: entry.contentRect.width,
            height: entry.contentRect.height,
          };
        }
        if (entry.target === this.contentTransform) {
          this.imageBounds = {
            width: entry.contentRect.width,
            height: entry.contentRect.height,
          };
        }
        this.updateScale();
      }
    }
  );

  static styles = css`
    :host {
      --background-color: #000;
      --frame-color: #f00;
      display: flex;
    }
    * {
      box-sizing: border-box;
    }
    ::slotted(*) {
      display: block;
    }
    .content-box {
      border: 1px solid var(--background-color);
      background-color: var(--background-color);
      width: 100%;
      position: relative;
    }
    .content {
      position: absolute;
      cursor: pointer;
    }
    .content-transform {
      position: absolute;
      transform-origin: top left;
      will-change: transform;
    }
    .frame {
      border: 1px solid var(--frame-color);
      position: absolute;
      will-change: transform;
    }
    .overlay {
      position: absolute;
      z-index: 10000;
      cursor: grabbing;
    }
  `;

  render() {
    return html`
      <div class="content-box">
        <div
          class="content"
          style="${styleMap({
            ...this.contentStyle,
          })}"
          @mousemove="${this.maybeDragFrame}"
          @mousedown=${this.clickOverview}
          @mouseup="${this.releaseFrame}"
        >
          <div
            class="content-transform"
            style="${styleMap(this.contentTransformStyle)}"
          >
            <slot></slot>
          </div>
          <div
            class="frame"
            style="${styleMap({
              ...this.frameStyle,
              cursor: this.dragging ? 'grabbing' : 'grab',
            })}"
            @mousedown="${this.grabFrame}"
          ></div>
        </div>
        <div
          class="overlay"
          style="${styleMap({
            ...this.overlayStyle,
            display: this.dragging ? 'block' : 'none',
          })}"
          @mousemove="${this.overlayMouseMove}"
          @mouseleave="${this.releaseFrame}"
          @mouseup="${this.releaseFrame}"
        ></div>
      </div>
    `;
  }

  firstUpdated() {
    this.resizeObserver.observe(this.contentBox);
    this.resizeObserver.observe(this.contentTransform);
  }

  updated(changedProperties: PropertyValues) {
    if (changedProperties.has('frameRect')) {
      this.updateFrameStyle();
    }
  }

  clickOverview(event: MouseEvent) {
    event.preventDefault();

    this.updateOverlaySize();

    this.dragging = true;
    const rect = this.content.getBoundingClientRect();
    this.notifyNewCenter({
      x: (event.clientX - rect.left) / this.scale,
      y: (event.clientY - rect.top) / this.scale,
    });
  }

  grabFrame(event: MouseEvent) {
    event.preventDefault();
    // Do not bubble up into clickOverview().
    event.stopPropagation();

    this.updateOverlaySize();

    this.dragging = true;
    const rect = this.frame.getBoundingClientRect();
    const frameCenterX = rect.x + rect.width / 2;
    const frameCenterY = rect.y + rect.height / 2;
    this.grabOffset = {
      x: event.clientX - frameCenterX,
      y: event.clientY - frameCenterY,
    };
  }

  maybeDragFrame(event: MouseEvent) {
    event.preventDefault();
    if (!this.dragging) return;
    const rect = this.content.getBoundingClientRect();
    const center = {
      x: (event.clientX - rect.left - this.grabOffset.x) / this.scale,
      y: (event.clientY - rect.top - this.grabOffset.y) / this.scale,
    };
    this.notifyNewCenter(center);
  }

  releaseFrame(event: MouseEvent) {
    event.preventDefault();
    this.dragging = false;
    this.grabOffset = {x: 0, y: 0};
  }

  overlayMouseMove(event: MouseEvent) {
    event.preventDefault();
    this.maybeDragFrame(event);
  }

  private updateScale() {
    const fitted = fitToFrame(this.imageBounds, this.contentBounds);
    this.scale = fitted.scale;

    this.contentStyle = {
      ...this.contentStyle,
      top: `${fitted.top}px`,
      left: `${fitted.left}px`,
      width: `${fitted.width}px`,
      height: `${fitted.height}px`,
    };

    this.contentTransformStyle = {
      transform: `scale(${this.scale})`,
    };

    this.updateFrameStyle();
  }

  private updateFrameStyle() {
    const x = this.frameRect.origin.x * this.scale;
    const y = this.frameRect.origin.y * this.scale;
    const width = this.frameRect.dimensions.width * this.scale;
    const height = this.frameRect.dimensions.height * this.scale;
    this.frameStyle = {
      ...this.frameStyle,
      transform: `translate(${x}px, ${y}px)`,
      width: `${width}px`,
      height: `${height}px`,
    };
  }

  private updateOverlaySize() {
    const rect = this.contentBox.getBoundingClientRect();
    // Create a whole-page overlay to capture mouse events, so that the drag
    // interaction continues until the user releases the mouse button. Since
    // innerWidth and innerHeight include scrollbars, we subtract 20 pixels each
    // to prevent the overlay from extending offscreen under any existing
    // scrollbar and causing the scrollbar for the other dimension to show up
    // unnecessarily.
    const width = window.innerWidth - 20;
    const height = window.innerHeight - 20;
    this.overlayStyle = {
      ...this.overlayStyle,
      top: `-${rect.top + 1}px`,
      left: `-${rect.left + 1}px`,
      width: `${width}px`,
      height: `${height}px`,
    };
  }

  private notifyNewCenter(center: Point) {
    this.dispatchEvent(
      new CustomEvent('center-updated', {
        detail: {...center},
        bubbles: true,
        composed: true,
      })
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-overview-image': GrOverviewImage;
  }
}
