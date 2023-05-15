/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './gr-diff-section';
import '../gr-context-controls/gr-context-controls';
import {
  ContentLoadNeededEventDetail,
  DiffContextExpandedExternalDetail,
  DiffViewMode,
  LineNumber,
  RenderPreferences,
} from '../../../api/diff';
import {GrDiffGroup} from '../gr-diff/gr-diff-group';
import {BlameInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {Side} from '../../../constants/constants';
import {DiffLayer, isDefined} from '../../../types/types';
import {GrDiffRow} from './gr-diff-row';
import {GrDiffSection} from './gr-diff-section';
import {html, render} from 'lit';
import {diffClasses} from '../gr-diff/gr-diff-utils';
import {when} from 'lit/directives/when.js';
import {GrDiffBuilderImage} from './gr-diff-builder-image';
import {GrDiffBuilderBinary} from './gr-diff-builder-binary';

export interface DiffContextExpandedEventDetail
  extends DiffContextExpandedExternalDetail {
  /** The context control group that should be replaced by `groups`. */
  contextGroup: GrDiffGroup;
  groups: GrDiffGroup[];
}

declare global {
  interface HTMLElementEventMap {
    'diff-context-expanded-internal': CustomEvent<DiffContextExpandedEventDetail>;
    'diff-context-expanded': CustomEvent<DiffContextExpandedExternalDetail>;
    'content-load-needed': CustomEvent<ContentLoadNeededEventDetail>;
  }
}

export function isImageDiffBuilder<T extends GrDiffBuilder>(
  x: T | GrDiffBuilderImage | undefined
): x is GrDiffBuilderImage {
  return !!x && !!(x as GrDiffBuilderImage).renderImageDiff;
}

export function isBinaryDiffBuilder<T extends GrDiffBuilder>(
  x: T | GrDiffBuilderBinary | undefined
): x is GrDiffBuilderBinary {
  return !!x && !!(x as GrDiffBuilderBinary).renderBinaryDiff;
}

/**
 * The builder takes GrDiffGroups, and builds the corresponding DOM elements,
 * called sections. Only the builder should add or remove sections from the
 * DOM. Callers can use the ...group() methods to modify groups and thus cause
 * rendering changes.
 */
export class GrDiffBuilder {
  private readonly diff: DiffInfo;

  readonly prefs: DiffPreferencesInfo;

  renderPrefs?: RenderPreferences;

  readonly outputEl: HTMLElement;

  private groups: GrDiffGroup[];

  private readonly layerUpdateListener: (
    start: LineNumber,
    end: LineNumber,
    side: Side
  ) => void;

  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement,
    readonly layers: DiffLayer[] = [],
    renderPrefs?: RenderPreferences
  ) {
    this.diff = diff;
    this.prefs = prefs;
    this.renderPrefs = renderPrefs;
    this.outputEl = outputEl;
    this.groups = [];

    if (isNaN(prefs.tab_size) || prefs.tab_size <= 0) {
      throw Error('Invalid tab size from preferences.');
    }

    if (isNaN(prefs.line_length) || prefs.line_length <= 0) {
      throw Error('Invalid line length from preferences.');
    }

    this.layerUpdateListener = (
      start: LineNumber,
      end: LineNumber,
      side: Side
    ) => this.renderContentByRange(start, end, side);
    this.init();
  }

  getContentTdByLine(
    lineNumber: LineNumber,
    side?: Side
  ): HTMLTableCellElement | undefined {
    if (!side) return undefined;
    const row = this.findRow(lineNumber, side);
    return row?.getContentCell(side);
  }

  getLineElByNumber(
    lineNumber: LineNumber,
    side?: Side
  ): HTMLTableCellElement | undefined {
    if (!side) return undefined;
    const row = this.findRow(lineNumber, side);
    return row?.getLineNumberCell(side);
  }

  private findRow(lineNumber?: LineNumber, side?: Side): GrDiffRow | undefined {
    if (!side || !lineNumber) return undefined;
    const group = this.findGroup(side, lineNumber);
    if (!group) return undefined;
    const section = this.findSection(group);
    if (!section) return undefined;
    return section.findRow(side, lineNumber);
  }

  private getDiffRows() {
    const sections = [
      ...this.outputEl.querySelectorAll<GrDiffSection>('gr-diff-section'),
    ];
    return sections.map(s => s.getDiffRows()).flat();
  }

  getLineNumberRows(): HTMLTableRowElement[] {
    const rows = this.getDiffRows();
    return rows.map(r => r.getTableRow()).filter(isDefined);
  }

  getLineNumEls(side: Side): HTMLTableCellElement[] {
    const rows = this.getDiffRows();
    return rows.map(r => r.getLineNumberCell(side)).filter(isDefined);
  }

  /** This is used when layers initiate an update. */
  renderContentByRange(start: LineNumber, end: LineNumber, side: Side) {
    const groups = this.getGroupsByLineRange(start, end, side);
    for (const group of groups) {
      const section = this.findSection(group);
      for (const row of section?.getDiffRows() ?? []) {
        row.requestUpdate();
      }
    }
  }

  private findSection(group: GrDiffGroup): GrDiffSection | undefined {
    const leftClass = `left-${group.startLine(Side.LEFT)}`;
    const rightClass = `right-${group.startLine(Side.RIGHT)}`;
    return (
      this.outputEl.querySelector<GrDiffSection>(
        `gr-diff-section.${leftClass}.${rightClass}`
      ) ?? undefined
    );
  }

  buildSectionElement(group: GrDiffGroup): HTMLElement {
    const leftCl = `left-${group.startLine(Side.LEFT)}`;
    const rightCl = `right-${group.startLine(Side.RIGHT)}`;
    const section = html`
      <gr-diff-section
        class="${leftCl} ${rightCl}"
        .group=${group}
        .diff=${this.diff}
        .layers=${this.layers}
        .diffPrefs=${this.prefs}
        .renderPrefs=${this.renderPrefs}
      ></gr-diff-section>
    `;
    // When using Lit's `render()` method it wants to be in full control of the
    // element that it renders into, so we let it render into a temp element.
    // Rendering into the diff table directly would interfere with
    // `clearDiffContent()`for example.
    // TODO: Convert <gr-diff> to be fully lit controlled and incorporate this
    // method into Lit's `render()` cycle.
    const tempEl = document.createElement('div');
    render(section, tempEl);
    const sectionEl = tempEl.firstElementChild as GrDiffSection;
    return sectionEl;
  }

  addColumns(outputEl: HTMLElement, lineNumberWidth: number): void {
    const colgroup = html`
      <colgroup>
        <col class=${diffClasses('blame')}></col>
        ${when(
          this.renderPrefs?.view_mode === DiffViewMode.UNIFIED,
          () => html` ${this.renderUnifiedColumns(lineNumberWidth)} `,
          () => html`
            ${this.renderSideBySideColumns(Side.LEFT, lineNumberWidth)}
            ${this.renderSideBySideColumns(Side.RIGHT, lineNumberWidth)}
          `
        )}
      </colgroup>
    `;
    // When using Lit's `render()` method it wants to be in full control of the
    // element that it renders into, so we let it render into a temp element.
    // Rendering into the diff table directly would interfere with
    // `clearDiffContent()`for example.
    // TODO: Convert <gr-diff> to be fully lit controlled and incorporate this
    // method into Lit's `render()` cycle.
    const tempEl = document.createElement('div');
    render(colgroup, tempEl);
    const colgroupEl = tempEl.firstElementChild as HTMLElement;
    outputEl.appendChild(colgroupEl);
  }

  private renderUnifiedColumns(lineNumberWidth: number) {
    return html`
      <col class=${diffClasses()} width=${lineNumberWidth}></col>
      <col class=${diffClasses()} width=${lineNumberWidth}></col>
      <col class=${diffClasses()}></col>
    `;
  }

  private renderSideBySideColumns(side: Side, lineNumberWidth: number) {
    return html`
      <col class=${diffClasses(side)} width=${lineNumberWidth}></col>
      <col class=${diffClasses(side, 'sign')}></col>
      <col class=${diffClasses(side)}></col>
    `;
  }

  /**
   * This is meant to be called when the gr-diff component re-connects, or when
   * the diff is (re-)rendered.
   *
   * Make sure that this method is symmetric with cleanup(), which is called
   * when gr-diff disconnects.
   */
  init() {
    this.cleanup();
    for (const layer of this.layers) {
      if (layer.addListener) {
        layer.addListener(this.layerUpdateListener);
      }
    }
  }

  /**
   * This is meant to be called when the gr-diff component disconnects, or when
   * the diff is (re-)rendered.
   *
   * Make sure that this method is symmetric with init(), which is called when
   * gr-diff re-connects.
   */
  cleanup() {
    for (const layer of this.layers) {
      if (layer.removeListener) {
        layer.removeListener(this.layerUpdateListener);
      }
    }
  }

  addGroups(groups: readonly GrDiffGroup[]) {
    for (const group of groups) {
      this.groups.push(group);
      this.emitGroup(group);
    }
  }

  clearGroups() {
    for (const deletedGroup of this.groups) {
      deletedGroup.element?.remove();
    }
    this.groups = [];
  }

  replaceGroup(contextControl: GrDiffGroup, groups: readonly GrDiffGroup[]) {
    const i = this.groups.indexOf(contextControl);
    if (i === -1) throw new Error('cannot find context control group');

    const contextControlSection = this.groups[i].element;
    if (!contextControlSection) throw new Error('diff group element not set');

    this.groups.splice(i, 1, ...groups);
    for (const group of groups) {
      this.emitGroup(group, contextControlSection);
    }
    if (contextControlSection) contextControlSection.remove();
  }

  findGroup(side: Side, line: LineNumber) {
    return this.groups.find(group => group.containsLine(side, line));
  }

  private emitGroup(group: GrDiffGroup, beforeSection?: HTMLElement) {
    const element = this.buildSectionElement(group);
    this.outputEl.insertBefore(element, beforeSection ?? null);
    group.element = element;
  }

  // visible for testing
  getGroupsByLineRange(
    startLine: LineNumber,
    endLine: LineNumber,
    side: Side
  ): GrDiffGroup[] {
    const startIndex = this.groups.findIndex(group =>
      group.containsLine(side, startLine)
    );
    if (startIndex === -1) return [];
    let endIndex = this.groups.findIndex(group =>
      group.containsLine(side, endLine)
    );
    // Not all groups may have been processed yet (i.e. this.groups is still
    // incomplete). In that case let's just return *all* groups until the end
    // of the array.
    if (endIndex === -1) endIndex = this.groups.length - 1;
    // The filter preserves the legacy behavior to only return non-context
    // groups
    return this.groups
      .slice(startIndex, endIndex + 1)
      .filter(group => group.lines.length > 0);
  }

  /**
   * Set the blame information for the diff. For any already-rendered line,
   * re-render its blame cell content.
   */
  setBlame(blame: BlameInfo[]) {
    for (const blameInfo of blame) {
      for (const range of blameInfo.ranges) {
        for (let line = range.start; line <= range.end; line++) {
          const row = this.findRow(line, Side.LEFT);
          if (row) row.blameInfo = blameInfo;
        }
      }
    }
  }

  /**
   * Only special builders need to implement this. The default is to
   * just ignore it.
   */
  updateRenderPrefs(_: RenderPreferences) {}
}
