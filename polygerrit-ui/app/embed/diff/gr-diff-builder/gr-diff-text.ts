/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, html, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {styleMap} from 'lit/directives/style-map.js';
import {diffClasses} from '../gr-diff/gr-diff-utils';

const SURROGATE_PAIR = /[\uD800-\uDBFF][\uDC00-\uDFFF]/;

const TAB = '\t';

/**
 * Renders one line of code on one side of the diff. It takes care of:
 * - Tabs, see `tabSize` property.
 * - Line Breaks, see `lineLimit` property.
 * - Surrogate Character Pairs.
 *
 * Note that other modifications to the code in a gr-diff is done via diff
 * layers, which manipulate the DOM directly. So `gr-diff-text` is thrown
 * away and re-rendered every time something changes by its parent
 * `gr-diff-row`. So don't bother to optimize this component for re-rendering
 * performance. And be aware that building longer lived local state is not
 * useful here.
 */
@customElement('gr-diff-text')
export class GrDiffText extends LitElement {
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

  @property({type: String})
  text = '';

  @property({type: Boolean})
  isResponsive = false;

  @property({type: Number})
  tabSize = 2;

  @property({type: Number})
  lineLimit = 80;

  /** Temporary state while rendering. */
  private textOffset = 0;

  /** Temporary state while rendering. */
  private columnPos = 0;

  /** Temporary state while rendering. */
  private pieces: (string | TemplateResult)[] = [];

  /** Split up the string into tabs, surrogate pairs and regular segments. */
  override render() {
    this.textOffset = 0;
    this.columnPos = 0;
    this.pieces = [];
    const splitByTab = this.text.split('\t');
    for (let i = 0; i < splitByTab.length; i++) {
      const splitBySurrogate = splitByTab[i].split(SURROGATE_PAIR);
      for (let j = 0; j < splitBySurrogate.length; j++) {
        this.renderSegment(splitBySurrogate[j]);
        if (j < splitBySurrogate.length - 1) {
          this.renderSurrogatePair();
        }
      }
      if (i < splitByTab.length - 1) {
        this.renderTab();
      }
    }
    if (this.textOffset !== this.text.length) throw new Error('unfinished');
    return this.pieces;
  }

  /** Render regular characters, but insert line breaks appropriately. */
  private renderSegment(segment: string) {
    let segmentOffset = 0;
    while (segmentOffset < segment.length) {
      const newOffset = Math.min(
        segment.length,
        segmentOffset + this.lineLimit - this.columnPos
      );
      this.renderString(segment.substring(segmentOffset, newOffset));
      segmentOffset = newOffset;
      if (segmentOffset < segment.length && this.columnPos === this.lineLimit) {
        this.renderLineBreak();
      }
    }
  }

  /** Render regular characters. */
  private renderString(s: string) {
    if (s.length === 0) return;
    this.pieces.push(s);
    this.textOffset += s.length;
    this.columnPos += s.length;
    if (this.columnPos > this.lineLimit) throw new Error('over line limit');
  }

  /** Render a tab character. */
  private renderTab() {
    let tabSize = this.tabSize - (this.columnPos % this.tabSize);
    if (this.columnPos + tabSize > this.lineLimit) {
      this.renderLineBreak();
      tabSize = this.tabSize;
    }
    const piece = html`<span
      class=${diffClasses('tab')}
      style=${styleMap({'tab-size': `${this.tabSize}`})}
      >${TAB}</span
    >`;
    this.pieces.push(piece);
    this.textOffset += 1;
    this.columnPos += tabSize;
  }

  /** Render a surrogate pair: string length is 2, but is just 1 char. */
  private renderSurrogatePair() {
    if (this.columnPos === this.lineLimit) {
      this.renderLineBreak();
    }
    this.pieces.push(this.text.substring(this.textOffset, this.textOffset + 2));
    this.textOffset += 2;
    this.columnPos += 1;
  }

  /** Render a line break, don't advance text offset, reset col position. */
  private renderLineBreak() {
    if (this.isResponsive) {
      this.pieces.push(html`<wbr class=${diffClasses()}></wbr>`);
    } else {
      this.pieces.push(html`<span class=${diffClasses('br')}></span>`);
    }
    // this.textOffset += 0;
    this.columnPos = 0;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-text': GrDiffText;
  }
}
