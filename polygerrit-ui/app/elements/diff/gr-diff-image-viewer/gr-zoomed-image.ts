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
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {StyleInfo, styleMap} from 'lit/directives/style-map';
import {Rect} from './util';

/**
 * Displays its slotted content at a given scale, centered over a given point,
 * while ensuring the content always fills the container. The content does not
 * have to be a single image, it can be arbitrary HTML. To prevent user
 * confusion, it should ideally be image-like, i.e. have limited or no
 * interactivity, as the component does not prevent events or focus from
 * reaching the slotted content.
 */
@customElement('gr-zoomed-image')
export class GrZoomedImage extends LitElement {
  @property({type: Number}) scale = 1;

  @property({type: Object})
  frameRect: Rect = {origin: {x: 0, y: 0}, dimensions: {width: 0, height: 0}};

  @state() protected imageStyles: StyleInfo = {};

  static override styles = css`
    :host {
      display: block;
    }
    ::slotted(*) {
      display: block;
    }
    #clip {
      position: relative;
      width: 100%;
      height: 100%;
      overflow: hidden;
    }
    #transform {
      position: absolute;
      transform-origin: top left;
      will-change: transform;
    }
  `;

  override render() {
    return html`
      <div id="clip">
        <div id="transform" style="${styleMap(this.imageStyles)}">
          <slot></slot>
        </div>
      </div>
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('scale') || changedProperties.has('frameRect')) {
      this.updateImageStyles();
    }
  }

  private updateImageStyles() {
    const {x, y} = this.frameRect.origin;
    this.imageStyles = {
      'image-rendering': this.scale >= 1 ? 'pixelated' : 'auto',
      transform: `translate(${-x}px, ${-y}px) scale(${this.scale})`,
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-zoomed-image': GrZoomedImage;
  }
}
