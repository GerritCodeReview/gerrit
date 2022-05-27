/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/paper-button/paper-button';
import '@polymer/paper-card/paper-card';
import '@polymer/paper-checkbox/paper-checkbox';
import '@polymer/paper-dropdown-menu/paper-dropdown-menu';
import '@polymer/paper-fab/paper-fab';
import '@polymer/paper-icon-button/paper-icon-button';
import '@polymer/paper-item/paper-item';
import '@polymer/paper-listbox/paper-listbox';
import './gr-overview-image';
import './gr-zoomed-image';

import {GrLibLoader} from '../../../elements/shared/gr-lib-loader/gr-lib-loader';
import {RESEMBLEJS_LIBRARY_CONFIG} from '../../../elements/shared/gr-lib-loader/resemblejs_config';

import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {ifDefined} from 'lit/directives/if-defined';
import {classMap} from 'lit/directives/class-map';
import {StyleInfo, styleMap} from 'lit/directives/style-map';

import {
  createEvent,
  Dimensions,
  fitToFrame,
  FrameConstrainer,
  Point,
  Rect,
} from './util';

const DRAG_DEAD_ZONE_PIXELS = 5;

const DEFAULT_AUTOMATIC_BLINK_TIME_MS = 1000;

const AUTOMATIC_BLINK_BUTTON_ACTIVE_AREA_PIXELS = 350;

/**
 * This components allows the user to rapidly switch between two given images
 * rendered in the same location, to make subtle differences more noticeable.
 * Images can be magnified to compare details.
 */
@customElement('gr-image-viewer')
export class GrImageViewer extends LitElement {
  /** URL for the image to use as base. */
  @property({type: String}) baseUrl = '';

  /** URL for the image to use as revision. */
  @property({type: String}) revisionUrl = '';

  /**
   * When true, cycle automatically between base and revision image, if both
   * are available.
   */
  @property({type: Boolean}) automaticBlink = false;

  @state() protected baseSelected = false;

  @state() protected scaledSelected = true;

  @state() protected followMouse = false;

  @state() protected scale = 1;

  @state() protected checkerboardSelected = true;

  @state() protected backgroundColor = '';

  @state() protected automaticBlinkShown = false;

  @state() protected zoomedImageStyle: StyleInfo = {};

  @query('.imageArea') protected imageArea!: HTMLDivElement;

  @query('gr-zoomed-image') protected zoomedImage!: Element;

  @query('#source-image') protected sourceImage!: HTMLImageElement;

  @query('#automatic-blink-button') protected automaticBlinkButton?: Element;

  private imageSize: Dimensions = {width: 0, height: 0};

  @state()
  protected magnifierSize: Dimensions = {width: 0, height: 0};

  @state()
  protected magnifierFrame: Rect = {
    origin: {x: 0, y: 0},
    dimensions: {width: 0, height: 0},
  };

  @state()
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

  @state() protected grabbing = false;

  @state() protected canHighlightDiffs = false;

  @state() protected diffHighlightSrc?: string;

  @state() protected showHighlight = false;

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

  // Ensure constant function references, so that render() does not bind a new
  // event listener on every call, as it would with lambdas.
  private createColorPickerCallback(color: string) {
    return {color, callback: () => this.pickColor(color)};
  }

  private readonly colorPickerCallbacks = [
    this.createColorPickerCallback('#fff'),
    this.createColorPickerCallback('#000'),
    this.createColorPickerCallback('#aaa'),
  ];

  private automaticBlinkTimer?: ReturnType<typeof setInterval>;

  // TODO(hermannloose): Make GrLibLoader a singleton.
  private static readonly libLoader = new GrLibLoader();

  static override styles = css`
    :host {
      display: grid;
      grid-template-rows: 1fr auto;
      grid-template-columns: 1fr auto;
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      text-align: initial !important;
      font-size: var(--font-size-normal);
      --image-border-width: 2px;
    }
    .imageArea {
      grid-row-start: 1;
      grid-column-start: 1;
      box-sizing: border-box;
      flex-grow: 1;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      align-items: center;
      margin: var(--spacing-m);
      padding: var(--image-border-width);
      max-height: 100%;
      position: relative;
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
    #automatic-blink-button {
      position: absolute;
      right: var(--spacing-xl);
      bottom: var(--spacing-xl);
      opacity: 0;
      transition: opacity 200ms ease;
      --paper-fab-background: var(--primary-button-background-color);
      --paper-fab-keyboard-focus-background: var(
        --primary-button-background-color
      );
    }
    #automatic-blink-button.show,
    #automatic-blink-button:focus-visible {
      opacity: 1;
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
    .dimensions {
      grid-row-start: 2;
      justify-self: center;
      align-self: center;
      background: var(--primary-button-background-color);
      color: var(--primary-button-text-color);
      font-family: var(--font-family);
      font-size: var(--font-size-small);
      line-height: var(--line-height-small);
      border-radius: var(--border-radius, 4px);
      margin: var(--spacing-s);
      padding: var(--spacing-xxs) var(--spacing-s);
    }
    .controls {
      grid-column-start: 2;
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
      outline: 1px solid transparent;
      border: 1px solid var(--primary-button-background-color);
    }
    paper-button.unelevated {
      color: var(--primary-button-text-color);
      background-color: var(--primary-button-background-color);
    }
    paper-button.outlined {
      color: var(--primary-button-background-color);
    }
    #version-switcher {
      display: flex;
      align-items: center;
      margin: var(--spacing-xl) var(--spacing-xl) var(--spacing-m);
      /* Start a stacking context to contain FAB below. */
      z-index: 0;
    }
    #version-switcher paper-button {
      flex-grow: 1;
      margin: 0;
      /*
        The floating action button below overlaps part of the version buttons.
        This min-width ensures the button text still appears somewhat balanced.
        */
      min-width: 7rem;
    }
    #version-switcher paper-fab {
      /* Round button overlaps Base and Revision buttons. */
      z-index: 1;
      margin: 0 -12px;
      /* Styled as an outlined button. */
      color: var(--primary-button-background-color);
      border: 1px solid var(--primary-button-background-color);
      --paper-fab-background: var(--primary-background-color);
      --paper-fab-keyboard-focus-background: var(--primary-background-color);
    }
    #version-explanation {
      color: var(--deemphasized-text-color);
      text-align: center;
      margin: var(--spacing-xl) var(--spacing-xl) var(--spacing-m);
    }
    #highlight-changes {
      margin: var(--spacing-m) var(--spacing-xl);
    }
    gr-overview-image {
      min-width: 200px;
      min-height: 150px;
      margin-top: var(--spacing-m);
    }
    #zoom-control {
      margin: 0 var(--spacing-xl);
    }
    paper-item {
      cursor: pointer;
    }
    paper-item:hover {
      background-color: var(--hover-background-color);
    }
    #follow-mouse {
      margin: var(--spacing-m) var(--spacing-xl);
    }
    .color-picker {
      margin: var(--spacing-m) var(--spacing-xl) 0;
    }
    .color-picker .label {
      margin-bottom: var(--spacing-s);
    }
    .color-picker .options {
      display: flex;
      /* Ignore selection border for alignment, for visual balance. */
      margin-left: -3px;
    }
    .color-picker-button {
      border-width: 2px;
      border-style: solid;
      border-color: transparent;
      border-radius: 50%;
      width: 24px;
      height: 24px;
      padding: 1px;
    }
    .color-picker-button.selected {
      border-color: var(--primary-button-background-color);
    }
    .color-picker-button:focus-within:not(.selected) {
      /* Not an actual outline, as those do not follow border-radius. */
      border-color: var(--outline-color-focus);
    }
    .color-picker-button .color {
      border: 1px solid var(--border-color);
      border-radius: 50%;
      width: 100%;
      height: 100%;
      box-sizing: border-box;
    }
    #source-plus-highlight-container {
      position: relative;
    }
    #source-plus-highlight-container img {
      position: absolute;
      top: 0;
      left: 0;
    }
  `;

  private renderColorPickerButton(color: string, colorPicked: () => void) {
    const selected =
      color === this.backgroundColor && !this.checkerboardSelected;
    return html`
      <div
        class=${classMap({
          'color-picker-button': true,
          selected,
        })}
      >
        <paper-icon-button
          class="color"
          style=${styleMap({backgroundColor: color})}
          @click=${colorPicked}
        ></paper-icon-button>
      </div>
    `;
  }

  private renderCheckerboardButton() {
    return html`
      <div
        class=${classMap({
          'color-picker-button': true,
          selected: this.checkerboardSelected,
        })}
      >
        <paper-icon-button
          class="color checkerboard"
          @click=${this.pickCheckerboard}
        >
        </paper-icon-button>
      </div>
    `;
  }

  override render() {
    const src = this.baseSelected ? this.baseUrl : this.revisionUrl;

    const sourceImage = html`
      <img
        id="source-image"
        src=${src}
        class=${classMap({checkerboard: this.checkerboardSelected})}
        style=${styleMap({
          backgroundColor: this.checkerboardSelected
            ? ''
            : this.backgroundColor,
        })}
        @load=${this.updateSizes}
      />
    `;

    const sourceImageWithHighlight = html`
      <div id="source-plus-highlight-container">
        ${sourceImage}
        <img
          id="highlight-image"
          style=${styleMap({
            opacity: this.showHighlight ? '1' : '0',
            // When the highlight layer is not being shown, saving the image or
            // opening it in a new tab from the context menu, e.g. for external
            // comparison, should give back the source image, not the highlight
            // layer.
            'pointer-events': this.showHighlight ? 'auto' : 'none',
          })}
          src=${ifDefined(this.diffHighlightSrc)}
        />
      </div>
    `;

    const versionExplanation = html`
      <div id="version-explanation">
        This file is being ${this.revisionUrl ? 'added' : 'deleted'}.
      </div>
    `;

    // This uses the unelevated and outlined attributes from mwc-button with
    // manual styling, for a more seamless transition later.
    const leftClasses = {
      left: true,
      unelevated: this.baseSelected,
      outlined: !this.baseSelected,
    };
    const rightClasses = {
      right: true,
      unelevated: !this.baseSelected,
      outlined: this.baseSelected,
    };
    const versionToggle = html`
      <div id="version-switcher">
        <paper-button class=${classMap(leftClasses)} @click=${this.selectBase}>
          Base
        </paper-button>
        <paper-fab mini icon="gr-icons:swapHoriz" @click=${this.manualBlink}>
        </paper-fab>
        <paper-button
          class=${classMap(rightClasses)}
          @click=${this.selectRevision}
        >
          Revision
        </paper-button>
      </div>
    `;

    const versionSwitcher = html`
      ${this.baseUrl && this.revisionUrl ? versionToggle : versionExplanation}
    `;

    const highlightSwitcher = this.diffHighlightSrc
      ? html`
          <paper-checkbox
            id="highlight-changes"
            ?checked=${this.showHighlight}
            @change=${this.showHighlightChanged}
          >
            Highlight differences
          </paper-checkbox>
        `
      : '';

    const overviewImage = html`
      <gr-overview-image
        .frameRect=${this.overviewFrame}
        @center-updated=${this.onOverviewCenterUpdated}
      >
        <img
          src=${src}
          class=${classMap({checkerboard: this.checkerboardSelected})}
          style=${styleMap({
            backgroundColor: this.checkerboardSelected
              ? ''
              : this.backgroundColor,
          })}
        />
      </gr-overview-image>
    `;

    const zoomControl = html`
      <paper-dropdown-menu id="zoom-control" label="Zoom">
        <paper-listbox
          slot="dropdown-content"
          selected="fit"
          .attrForSelected=${'value'}
          @selected-changed=${this.zoomControlChanged}
        >
          ${this.zoomLevels.map(
            zoomLevel => html`
              <paper-item value=${zoomLevel}>
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
        ?checked=${this.followMouse}
        @change=${this.followMouseChanged}
      >
        Magnifier follows mouse
      </paper-checkbox>
    `;

    const backgroundPicker = html`
      <div class="color-picker">
        <div class="label">Background</div>
        <div class="options">
          ${this.renderCheckerboardButton()}
          ${this.colorPickerCallbacks.map(({color, callback}) =>
            this.renderColorPickerButton(color, callback)
          )}
        </div>
      </div>
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
        style=${styleMap({
          width: `${spacerWidth}px`,
          height: `${spacerHeight}px`,
        })}
      ></div>
    `;

    const automaticBlink = html`
      <paper-fab
        id="automatic-blink-button"
        class=${classMap({show: this.automaticBlinkShown})}
        title="Automatic blink"
        icon="gr-icons:${this.automaticBlink ? 'pause' : 'playArrow'}"
        @click=${this.toggleAutomaticBlink}
      >
      </paper-fab>
    `;

    // To pass CSS mixins for @apply to Polymer components, they need to appear
    // in <style> inside the template.
    /* eslint-disable lit/prefer-static-styles */
    const customStyle = html`
      <style>
        /* prettier formatter removes semi-colons after css mixins. */
        /* prettier-ignore */
        paper-item {
          --paper-item-min-height: 48;
          --paper-item: {
            min-height: 48px;
            padding: 0 var(--spacing-xl);
          };
          --paper-item-focused-before: {
            background-color: var(--selection-background-color);
          };
          --paper-item-focused: {
            background-color: var(--selection-background-color);
          };
        }
      </style>
    `;

    return html`
      ${customStyle}
      <div
        class="imageArea"
        @mousemove=${this.mousemoveImageArea}
        @mouseleave=${this.mouseleaveImageArea}
      >
        <gr-zoomed-image
          class=${classMap({
            base: this.baseSelected,
            revision: !this.baseSelected,
          })}
          style=${styleMap({
            ...this.zoomedImageStyle,
            cursor: this.grabbing ? 'grabbing' : 'pointer',
          })}
          .scale=${this.scale}
          .frameRect=${this.magnifierFrame}
          @mousedown=${this.mousedownMagnifier}
          @mouseup=${this.mouseupMagnifier}
          @mousemove=${this.mousemoveMagnifier}
          @mouseleave=${this.mouseleaveMagnifier}
          @dragstart=${this.dragstartMagnifier}
        >
          ${sourceImageWithHighlight}
        </gr-zoomed-image>
        ${this.baseUrl && this.revisionUrl ? automaticBlink : ''} ${spacer}
      </div>

      <div class="dimensions">
        ${this.imageSize.width} x ${this.imageSize.height}
      </div>

      <paper-card class="controls">
        ${versionSwitcher} ${highlightSwitcher} ${overviewImage} ${zoomControl}
        ${!this.scaledSelected ? followMouse : ''} ${backgroundPicker}
      </paper-card>
    `;
  }

  override firstUpdated() {
    this.resizeObserver.observe(this.imageArea, {box: 'content-box'});
    GrImageViewer.libLoader.getLibrary(RESEMBLEJS_LIBRARY_CONFIG).then(() => {
      this.canHighlightDiffs = true;
      this.computeDiffImage();
    });
  }

  // We don't want property changes in updateSizes() to trigger infinite update
  // loops, so we perform this in update() instead of updated().
  override update(changedProperties: PropertyValues) {
    // eslint-disable-next-line lit/no-property-change-update
    if (!this.baseUrl) this.baseSelected = false;
    // eslint-disable-next-line lit/no-property-change-update
    if (!this.revisionUrl) this.baseSelected = true;
    this.updateSizes();
    super.update(changedProperties);
  }

  override updated(changedProperties: PropertyValues) {
    if (
      (changedProperties.has('baseUrl') && this.baseSelected) ||
      (changedProperties.has('revisionUrl') && !this.baseSelected)
    ) {
      this.frameConstrainer.requestCenter({x: 0, y: 0});
    }
    if (changedProperties.has('automaticBlink')) {
      this.updateAutomaticBlink();
    }
    if (
      this.canHighlightDiffs &&
      (changedProperties.has('baseUrl') || changedProperties.has('revisionUrl'))
    ) {
      this.computeDiffImage();
    }
  }

  private computeDiffImage() {
    if (!(this.baseUrl && this.revisionUrl)) return;
    window
      .resemble(this.baseUrl)
      .compareTo(this.revisionUrl)
      // By default Resemble.js applies some color / alpha tolerance as well as
      // min / max brightness beyond which to ignore changes. Until we have
      // controls to let the user affect these options, always highlight all
      // changed pixels.
      .ignoreNothing()
      .onComplete(result => {
        this.diffHighlightSrc = result.getImageDataUrl();
      });
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

  manualBlink() {
    this.toggleImage();
    this.dispatchEvent(
      createEvent({type: 'version-switcher-clicked', button: 'switch'})
    );
  }

  private toggleImage() {
    if (this.baseUrl && this.revisionUrl) {
      this.baseSelected = !this.baseSelected;
    }
  }

  toggleAutomaticBlink() {
    this.automaticBlink = !this.automaticBlink;
    this.dispatchEvent(
      createEvent({type: 'automatic-blink-changed', value: this.automaticBlink})
    );
  }

  private updateAutomaticBlink() {
    if (this.automaticBlink) {
      this.toggleImage();
      this.setBlinkInterval();
    } else {
      this.clearBlinkInterval();
    }
  }

  private setBlinkInterval() {
    this.clearBlinkInterval();
    this.automaticBlinkTimer = setInterval(() => {
      this.toggleImage();
    }, DEFAULT_AUTOMATIC_BLINK_TIME_MS);
  }

  private clearBlinkInterval() {
    if (this.automaticBlinkTimer) {
      clearInterval(this.automaticBlinkTimer);
      this.automaticBlinkTimer = undefined;
    }
  }

  showHighlightChanged() {
    this.toggleHighlight('controls');
  }

  private toggleHighlight(source: 'controls' | 'magnifier') {
    this.showHighlight = !this.showHighlight;
    this.dispatchEvent(
      createEvent({
        type: 'highlight-changes-changed',
        value: this.showHighlight,
        source,
      })
    );
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

  pickColor(value: string) {
    this.checkerboardSelected = false;
    this.backgroundColor = value;
    this.dispatchEvent(createEvent({type: 'background-color-changed', value}));
  }

  pickCheckerboard() {
    this.checkerboardSelected = true;
    this.dispatchEvent(
      createEvent({type: 'background-color-changed', value: 'checkerboard'})
    );
  }

  mousemoveImageArea(event: MouseEvent) {
    if (this.automaticBlinkButton) {
      this.updateAutomaticBlinkVisibility(event);
    }
    this.mousemoveMagnifier(event);
  }

  private updateAutomaticBlinkVisibility(event: MouseEvent) {
    const rect = this.automaticBlinkButton!.getBoundingClientRect();
    const centerX = rect.left + (rect.right - rect.left) / 2;
    const centerY = rect.top + (rect.bottom - rect.top) / 2;
    const distX = Math.abs(centerX - event.clientX);
    const distY = Math.abs(centerY - event.clientY);
    this.automaticBlinkShown =
      distX < AUTOMATIC_BLINK_BUTTON_ACTIVE_AREA_PIXELS &&
      distY < AUTOMATIC_BLINK_BUTTON_ACTIVE_AREA_PIXELS;
  }

  mouseleaveImageArea() {
    this.automaticBlinkShown = false;
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
    if (!this.ownsMouseDown) return;
    this.grabbing = false;
    this.ownsMouseDown = false;

    if (event.shiftKey && this.diffHighlightSrc) {
      this.toggleHighlight('magnifier');
      return;
    }

    const offsetX = event.clientX - this.pointerOnDown.x;
    const offsetY = event.clientY - this.pointerOnDown.y;
    const distance = Math.max(Math.abs(offsetX), Math.abs(offsetY));
    // Consider very short drags as clicks. These tend to happen more often on
    // external mice.
    if (distance < DRAG_DEAD_ZONE_PIXELS) {
      this.toggleImage();
      this.dispatchEvent(createEvent({type: 'magnifier-clicked'}));
    } else {
      this.dispatchEvent(createEvent({type: 'magnifier-dragged'}));
    }
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
    const rect = this.imageArea.getBoundingClientRect();
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
    if (!this.ownsMouseDown) return;
    this.grabbing = false;
    this.ownsMouseDown = false;
    this.dispatchEvent(createEvent({type: 'magnifier-dragged'}));
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
