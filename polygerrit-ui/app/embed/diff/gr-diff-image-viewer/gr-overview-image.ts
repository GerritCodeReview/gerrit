/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {StyleInfo, styleMap} from 'lit/directives/style-map';
import {ImageDiffAction} from '../../../api/diff';

import {createEvent, Dimensions, fitToFrame, Point, Rect} from './util';

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

  @state() protected contentStyle: StyleInfo = {};

  @state() protected contentTransformStyle: StyleInfo = {};

  @state() protected frameStyle: StyleInfo = {};

  @state() protected dragging = false;

  @query('.content-box') protected contentBox!: HTMLDivElement;

  @query('.content') protected content!: HTMLDivElement;

  @query('.content-transform') protected contentTransform!: HTMLDivElement;

  @query('.frame') protected frame!: HTMLDivElement;

  protected overlay?: HTMLDivElement;

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

  static override styles = css`
    :host {
      --background-color: var(--overview-image-background-color, #000);
      --frame-color: var(--overview-image-frame-color, #f00);
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
  `;

  override render() {
    return html`
      <div class="content-box">
        <div
          class="content"
          style=${styleMap({
            ...this.contentStyle,
          })}
          @mousemove=${this.maybeDragFrame}
          @mousedown=${this.clickOverview}
          @mouseup=${this.releaseFrame}
        >
          <div
            class="content-transform"
            style=${styleMap(this.contentTransformStyle)}
          >
            <slot></slot>
          </div>
          <div
            class="frame"
            style=${styleMap({
              ...this.frameStyle,
              cursor: this.dragging ? 'grabbing' : 'grab',
            })}
            @mousedown=${this.grabFrame}
          ></div>
        </div>
      </div>
    `;
  }

  override connectedCallback() {
    super.connectedCallback();
    if (this.isConnected) {
      this.overlay = document.createElement('div');
      // The overlay is added directly to document body to ensure it fills the
      // entire screen to capture events, without being clipped by any parent
      // overflow properties. This means it has to be styled manually, since
      // component styles will not affect it.
      this.overlay.style.position = 'fixed';
      this.overlay.style.top = '0';
      this.overlay.style.left = '0';
      // We subtract 20 pixels in each dimension to prevent the overlay from
      // extending offscreen under any existing scrollbar and causing the
      // scrollbar for the other dimension to show up unnecessarily.
      this.overlay.style.width = 'calc(100vw - 20px)';
      this.overlay.style.height = 'calc(100vh - 20px)';
      this.overlay.style.zIndex = '10000';
      this.overlay.style.display = 'none';

      this.overlay.addEventListener('mousemove', (event: MouseEvent) =>
        this.maybeDragFrame(event)
      );
      this.overlay.addEventListener('mouseleave', (event: MouseEvent) => {
        // Ignore mouseleave events that are due to closeOverlay() calls.
        if (this.overlay?.style.display !== 'none') {
          this.releaseFrame(event);
        }
      });
      this.overlay.addEventListener('mouseup', (event: MouseEvent) =>
        this.releaseFrame(event)
      );

      document.body.appendChild(this.overlay);
    }
  }

  override disconnectedCallback() {
    if (this.overlay) {
      document.body.removeChild(this.overlay);
      this.overlay = undefined;
    }
    super.disconnectedCallback();
  }

  override firstUpdated() {
    this.resizeObserver.observe(this.contentBox);
    this.resizeObserver.observe(this.contentTransform);
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('frameRect')) {
      this.updateFrameStyle();
    }
  }

  clickOverview(event: MouseEvent) {
    if (event.buttons !== 1) return;
    event.preventDefault();

    this.dragging = true;
    this.openOverlay();

    const rect = this.content.getBoundingClientRect();
    this.notifyNewCenter({
      x: (event.clientX - rect.left) / this.scale,
      y: (event.clientY - rect.top) / this.scale,
    });
  }

  grabFrame(event: MouseEvent) {
    if (event.buttons !== 1) return;
    event.preventDefault();
    // Do not bubble up into clickOverview().
    event.stopPropagation();

    this.dragging = true;
    this.openOverlay();

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

    const detail: ImageDiffAction = {
      type: this.dragging ? 'overview-frame-dragged' : 'overview-image-clicked',
    };
    this.dispatchEvent(createEvent(detail));

    this.dragging = false;
    this.closeOverlay();
    this.grabOffset = {x: 0, y: 0};
  }

  private openOverlay() {
    if (this.overlay) {
      this.overlay.style.display = 'block';
      this.overlay.style.cursor = 'grabbing';
    }
  }

  private closeOverlay() {
    if (this.overlay) {
      this.overlay.style.display = 'none';
      this.overlay.style.cursor = '';
    }
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
