/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RenderPreferences} from '../../../api/diff';
import {LineNumber} from '../gr-diff/gr-diff-line';
import {GrDiffGroup} from '../gr-diff/gr-diff-group';
import '../gr-context-controls/gr-context-controls';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {Side} from '../../../constants/constants';
import {DiffLayer} from '../../../types/types';
import {diffClasses} from '../gr-diff/gr-diff-utils';
import {GrDiffBuilder} from './gr-diff-builder';
import {BlameInfo} from '../../../types/common';
import {html, render} from 'lit';

/**
 * Base class for builders that are creating the diff using Lit elements.
 */
export class GrDiffBuilderLit extends GrDiffBuilder {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement,
    layers: DiffLayer[] = [],
    renderPrefs?: RenderPreferences
  ) {
    super(diff, prefs, outputEl, layers, renderPrefs);
  }

  override getContentTdByLine(
    _lineNumber: LineNumber,
    _side?: Side,
    _root: Element = this.outputEl
  ): Element | null {
    // TODO: Implement method.
    console.warn('unimplemented method getContentTdByLine() called');
    return null;
  }

  override getBlameTdByLine(_lineNum: number): Element | undefined {
    // TODO: Implement method.
    console.warn('unimplemented method getBlameTdByLine() called');
    return undefined;
  }

  override getContentByLine(
    _lineNumber: LineNumber,
    _side?: Side,
    _root?: HTMLElement
  ): HTMLElement | null {
    // TODO: Implement method.
    console.warn('unimplemented method getContentByLine() called');
    return null;
  }

  override renderContentByRange(
    _start: LineNumber,
    _end: LineNumber,
    _side: Side
  ) {
    // TODO: Implement method.
    console.warn('unimplemented method renderContentByRange() called');
  }

  override renderBlameByRange(_blame: BlameInfo, _start: number, _end: number) {
    // TODO: Implement method.
    console.warn('unimplemented method renderBlameByRange() called');
  }

  protected override getMoveControlsConfig() {
    return {
      numberOfCells: 4,
      movedOutIndex: 1,
      movedInIndex: 3,
      lineNumberCols: [0, 2],
    };
  }

  protected override buildSectionElement(group: GrDiffGroup) {
    const section = html`
      <gr-diff-section
        .group=${group}
        .diff=${this._diff}
        .layers=${this.layers}
        .renderPrefs=${this.renderPrefs}
      >
      </gr-diff-section>
    `;
    const tempContainer = document.createElement('div');
    render(section, tempContainer);
    return tempContainer.firstChild as HTMLElement;
  }

  override addColumns(outputEl: HTMLElement, lineNumberWidth: number): void {
    render(
      html`
        <colgroup>
         <col ${diffClasses('blame')}></col>
         <col ${diffClasses(Side.LEFT)} width="${lineNumberWidth}"></col>
         <col ${diffClasses(Side.LEFT)}></col>
         <col ${diffClasses(Side.RIGHT)} width="${lineNumberWidth}"></col>
         <col ${diffClasses(Side.RIGHT)}></col>
        </colgroup>
      `,
      outputEl
    );
  }

  protected override getNextContentOnSide(
    _content: HTMLElement,
    _side: Side
  ): HTMLElement | null {
    // TODO: Implement method.
    console.warn('unimplemented method getNextContentOnSide() called');
    return null;
  }
}
