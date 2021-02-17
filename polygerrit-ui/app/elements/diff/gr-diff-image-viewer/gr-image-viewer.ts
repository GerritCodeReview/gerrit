import '@polymer/paper-button';
import '@polymer/paper-card';
import '@polymer/paper-checkbox';
import '@polymer/paper-dropdown-menu';
import '@polymer/paper-item';
import '@polymer/paper-listbox';
import '@polymer/paper-styles';
import '@polymer/paper-tooltip';

import {objectProperty} from 'goog:goog.reflect';
import {css, customElement, html, internalProperty, LitElement, property, PropertyValues, query} from 'lit-element';
import {classMap} from 'lit-html/directives/class-map';
import {StyleInfo, styleMap} from 'lit-html/directives/style-map';

import {Dimensions, fitToFrame} from './util';

@customElement('gr-image-viewer')
export class GrImageViewer extends LitElement {
  // URL for the image to use as base.
  @property({type: String}) base = '';
  // URL for the image to use as revision.
  @property({type: String}) revision = '';

  @internalProperty() protected baseImage?: HTMLImageElement;
  @internalProperty() protected revisionImage?: HTMLImageElement;
  @internalProperty() protected baseSelected: boolean = true;
  @internalProperty() protected scaledSelected: boolean = true;
  @internalProperty() protected followMouse: boolean = false;
  @internalProperty() protected scale: number = 1;
  @internalProperty() protected checkerboardSelected: boolean = true;

  @internalProperty() protected frameWidth = 0;
  @internalProperty() protected frameHeight = 0;
  @internalProperty() protected backgroundSize = '';
  @internalProperty() protected backgroundPosition = '';
  @internalProperty() protected zoomedCenterX = 0;
  @internalProperty() protected zoomedCenterY = 0;
  @internalProperty() protected overviewCenterX = 0;
  @internalProperty() protected overviewCenterY = 0;

  @internalProperty() protected zoomedImageStyle: StyleInfo = {};

  @query('.imageArea') protected imageArea!: HTMLDivElement;
  @query('gr-zoomed-image') protected zoomedImage!: Element;
  @query('#source-image') protected sourceImage!: HTMLImageElement;

  private imageSize: Dimensions = {width: 0, height: 0};

  @internalProperty()
  protected magnifierSize: Dimensions = {width: 0, height: 0};

  private readonly resizeObserver =
      new ResizeObserver((entries: ResizeObserverEntry[]) => {
        for (const entry of entries) {
          if (entry.target === this.imageArea) {
            this.magnifierSize = {
              width: entry.contentRect.width,
              height: entry.contentRect.height
            };
          }
        }
      });

  static styles = css`
    :host {
      display: flex;
      width: 100%;
      height: 100%;
      --border-color: #dadce0;
      box-sizing: border-box;
    }
    .imageArea {
      box-sizing: border-box;
      flex-grow: 1;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 2px;
      max-height: 100%;
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
      border-color: rgba(255, 0, 0, 1);
    }
    gr-zoomed-image.revision {
      border-color: rgba(0, 255, 0, 1);
    }
    .checkerboard {
      --square-size: 10px;
      --square-color: #808080;
      background-color: #aaaaaa;
      background-image:
          linear-gradient(45deg, var(--square-color) 25%, transparent 25%),
          linear-gradient(-45deg, var(--square-color) 25%, transparent 25%),
          linear-gradient(45deg, transparent 75%, var(--square-color) 75%),
          linear-gradient(-45deg, transparent 75%, var(--square-color) 75%);
      background-size:
          calc(var(--square-size) * 2) calc(var(--square-size) * 2);
      background-position:
          0 0,
          0 var(--square-size),
          var(--square-size) calc(-1 * var(--square-size)),
          calc(-1 * var(--square-size)) 0;
    }
    .controls {
      flex-grow: 0;
      display: flex;
      flex-direction: column;
      align-self: flex-start;
      margin-left: 16px;
      padding-bottom: 8px;
    }
    .version-switcher {
      border: 1px solid var(--border-color);
      border-radius: 3px;
      display: flex;
      align-self: center;
      margin: 8px 10%;
      padding: 4px 0;
    }
    .version-switcher paper-button {
      flex-basis: 0;
      flex-grow: 1;
    }
    .version-switcher paper-button[active] {
      color: var(--paper-blue-900);
      font-weight: bold;
      background-color: var(--paper-blue-100);
    }
    gr-overview-image {
      min-width: 200px;
      min-height: 150px;
    }
    #zoom-control {
      margin: 8px 16px;
    }
    #follow-mouse {
      margin: 8px 16px;
    }
  `;

  render() {
    const src = this.baseSelected ? this.base : this.revision;

    const sourceImage = html`
        <img
            id="source-image"
            src="${src}"
            class="${classMap({
      'checkerboard': this.checkerboardSelected
    })}"
            @load="${this.updateSizes}"/>
        `;

    const versionSwitcher = html`
        <div class="version-switcher">
          <paper-button
            toggles
            ?active="${this.baseSelected}"
            ?disabled="${this.baseSelected}"
            @click="${this.selectBase}">Base</paper-button>
          <paper-button
            toggles
            ?active="${!this.baseSelected}"
            ?disabled="${!this.baseSelected}"
            @click="${this.selectRevision}">Revision</paper-button>
        </div>
        `;

    const overviewImage = html`
        <gr-overview-image
            centerX="${this.overviewCenterX}"
            centerY="${this.overviewCenterY}"
            frameWidth="${this.frameWidth}"
            frameHeight="${this.frameHeight}"
            @center-updated="${this.onOverviewCenterUpdated}">
          <img src="${src}" class="checkerboard" />
        </gr-overview-image>
        `;

    const zoomControl = html`
        <paper-dropdown-menu
            label="Zoom"
            id="zoom-control">
          <paper-listbox
              slot="dropdown-content"
              selected="fit"
              attr-for-selected="value"
              @selected-changed="${this.zoomControlChanged}">
            <paper-item value="fit">Fit</paper-item>
            <paper-item value="1">100%</paper-item>
            <paper-item value="1.25">125%</paper-item>
            <paper-item value="1.5">150%</paper-item>
            <paper-item value="1.75">175%</paper-item>
            <paper-item value="2">200%</paper-item>
          </paper-listbox>
        </paper-dropdown-menu>
        `;

    const followMouse = html`
        <paper-checkbox
            id="follow-mouse"
            ?checked="${this.followMouse}"
            @click="${this.followMouseChanged}">
          Magnifier follows mouse
        </paper-checkbox>

        <paper-tooltip for="follow-mouse">
          Moving the mouse over the enlarged image will move the part the image that is being shown.
        </paper-tooltip>
        `;

    return html`
      <div
          class="imageArea"
          @mousemove="${this.mousemoveMagnifier}">
        <gr-zoomed-image
            class="${classMap({
      'base': this.baseSelected,
      'revision': !this.baseSelected
    })}"
            style="${styleMap(this.zoomedImageStyle)}"
            centerX="${this.zoomedCenterX}"
            centerY="${this.zoomedCenterY}"
            scale="${this.scale}"
            @click="${this.toggleImage}"
            @mousemove="${this.mousemoveMagnifier}">
          ${sourceImage}
        </gr-zoomed-image>
      </div>

      <paper-card class="controls">
        ${versionSwitcher}
        ${overviewImage}
        ${zoomControl}
        ${!this.scaledSelected ? followMouse : ''}
      </paper-card>
    `;
  }

  firstUpdated(changedProperties: PropertyValues) {
    this.resizeObserver.observe(this.imageArea, {box: 'content-box'});
  }

  updated(changedProperties: PropertyValues) {
    if (changedProperties.has(objectProperty('magnifierSize', this)) ||
        changedProperties.has(objectProperty('baseSelected', this)) ||
        changedProperties.has(objectProperty('scaledSelected', this)) ||
        changedProperties.has(objectProperty('scale', this))) {
      this.updateSizes();
    }

    if ((changedProperties.has('base') && this.baseSelected) ||
        changedProperties.has('revision') && !this.baseSelected) {
      setTimeout(() => {
        this.zoomedCenterX = 0;
        this.zoomedCenterY = 0;
        this.overviewCenterX = 0;
        this.overviewCenterY = 0;
      }, 1);
    }
  }

  selectBase() {
    this.baseSelected = true;
  }

  selectRevision() {
    this.baseSelected = false;
  }

  toggleImage() {
    this.baseSelected = !this.baseSelected;
  }

  zoomControlChanged(event: CustomEvent) {
    const value = event.detail.value;
    if (!value) return;
    if (value === 'fit') {
      this.scaledSelected = true;
    }
    if (value > 0) {
      this.scaledSelected = false;
      this.scale = value;
    }
  }

  followMouseChanged(event: Event) {
    this.followMouse = !this.followMouse;
  }

  mousemoveMagnifier(event: MouseEvent) {
    if (!this.followMouse) return;
    const rect = this.imageArea!.getBoundingClientRect();
    // TODO: might need to handle scrolled page
    const offsetX = event.clientX - rect.left;
    const offsetY = event.clientY - rect.top;
    const fractionX = offsetX / rect.width;
    const fractionY = offsetY / rect.height;
    this.zoomedCenterX = this.imageSize.width * fractionX;
    this.zoomedCenterY = this.imageSize.height * fractionY;
    this.overviewCenterX = this.imageSize.width * fractionX;
    this.overviewCenterY = this.imageSize.height * fractionY;
  }

  onOverviewCenterUpdated(event: CustomEvent) {
    this.zoomedCenterX = event.detail.x;
    this.zoomedCenterY = event.detail.y;
  }

  updateSizes() {
    if (!this.sourceImage.complete) return;

    this.imageSize = {
      width: this.sourceImage.naturalWidth || 0,
      height: this.sourceImage.naturalHeight || 0
    };

    if (this.scaledSelected) {
      const fittedImage = fitToFrame(this.imageSize, this.magnifierSize);
      this.scale = Math.min(fittedImage.scale, 1);
    }

    const scaledImageSize = {
      width: this.imageSize.width * this.scale,
      height: this.imageSize.height * this.scale
    };

    const width = Math.min(this.magnifierSize.width, scaledImageSize.width);
    const height = Math.min(this.magnifierSize.height, scaledImageSize.height);

    this.frameWidth = width / this.scale;
    this.frameHeight = height / this.scale;

    this.zoomedImageStyle = {
      ...this.zoomedImageStyle,
      'width': `${width}px`,
      'height': `${height}px`,
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-image-viewer': GrImageViewer;
  }
}
