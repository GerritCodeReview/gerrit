import {css, customElement, html, internalProperty, LitElement, property, PropertyValues, query} from 'lit-element';
import {StyleInfo, styleMap} from 'lit-html/directives/style-map';

import {FrameConstrainer} from './util';

/**
 * Displays its slotted content at a given scale, centered over a given point,
 * while ensuring the content always fills the container. The content does not
 * have to be a single image; it can be arbitrary HTML.
 */
@customElement('gr-zoomed-image')
export class GrZoomedImage extends LitElement {
  @property({type: Number}) centerX: number = 0;
  @property({type: Number}) centerY: number = 0;
  @property({type: Number}) scale: number = 1;

  @internalProperty() protected imageStyles: StyleInfo = {};

  @query('#transform') transform!: HTMLDivElement;

  private readonly frameConstrainer = new FrameConstrainer();

  private readonly resizeObserver =
      new ResizeObserver((entries: ResizeObserverEntry[]) => {
        for (const entry of entries) {
          if (entry.target === this) {
            this.frameConstrainer.setFrameSize({
              width: entry.contentRect.width,
              height: entry.contentRect.height,
            });
          }
          if (entry.target === this.transform) {
            // The slotted content will have its unscaled width and height due
            // to position: absolute; this is the base image size we work with.
            this.frameConstrainer.setBounds({
              width: entry.contentRect.width,
              height: entry.contentRect.height,
            });
          }
          this.updateImageStyles();
        }
      });

  static styles = css`
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

  render() {
    return html`
        <div id="clip">
          <div id="transform" style="${styleMap(this.imageStyles)}">
            <slot></slot>
          </div>
        </div>
        `;
  }

  firstUpdated(changedProperties: PropertyValues) {
    this.resizeObserver.observe(this);
    this.resizeObserver.observe(this.transform);
  }

  updated(changedProperties: PropertyValues) {
    if ((changedProperties.has('centerX') ||
         changedProperties.has('centerY')) &&
        !isNaN(this.centerX) && !isNaN(this.centerY)) {
      this.frameConstrainer.requestCenter({x: this.centerX, y: this.centerY});
      this.updateImageStyles();
    }

    if (changedProperties.has('scale') && this.scale > 0) {
      this.frameConstrainer.setScale(this.scale);
      this.updateImageStyles();
    }
  }

  private updateImageStyles() {
    const {x, y} = this.frameConstrainer.getFrame().origin;
    this.imageStyles = {
      'image-rendering': this.scale >= 1 ? 'pixelated' : 'auto',
      'transform': `translate(${- x}px, ${- y}px) scale(${this.scale})`,
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-zoomed-image': GrZoomedImage;
  }
}
