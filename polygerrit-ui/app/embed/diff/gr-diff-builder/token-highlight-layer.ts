/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {DiffLayer, DiffLayerListener} from '../../../types/types';
import {GrDiffLine, Side, TokenHighlightListener} from '../../../api/diff';
import {assertIsDefined} from '../../../utils/common-util';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {debounce, DelayedTask} from '../../../utils/async-util';

import {getLineElByChild, getSideByLineEl} from '../gr-diff/gr-diff-utils';

import {
  getLineNumberByChild,
  lineNumberToNumber,
} from '../gr-diff/gr-diff-utils';

const tokenMatcher = new RegExp(/[\w]+/g);

/** CSS class for all tokens. */
const CSS_TOKEN = 'token';

/** CSS class for the currently hovered token. */
const CSS_HIGHLIGHT = 'token-highlight';

/** CSS class marking which text value each token corresponds */
const TOKEN_TEXT_PREFIX = 'tk-text-';

/**
 * CSS class marking which index (column) where token starts within a line of code.
 * The assumption is that we can only have a single token per column start per line.
 */
const TOKEN_INDEX_PREFIX = 'tk-index-';

export const HOVER_DELAY_MS = 200;

const LINE_LENGTH_LIMIT = 500;

const TOKEN_LENGTH_LIMIT = 100;

const TOKEN_COUNT_LIMIT = 10000;

const TOKEN_OCCURRENCES_LIMIT = 1000;

/**
 * Token highlighting is only useful for code on-screen, so we only highlight
 * the nearest set of tokens up to this limit.
 */
const TOKEN_HIGHLIGHT_LIMIT = 100;

/**
 * When a user hovers over a token in the diff, then this layer makes sure that
 * all occurrences of this token are annotated with the 'token-highlight' css
 * class. And removes that class when the user moves the mouse away from the
 * token.
 *
 * The layer does not react to mouse events directly by adding a css class to
 * the appropriate elements, but instead it just sets the currently highlighted
 * token and notifies the diff renderer that certain lines must be re-rendered.
 * And when that re-rendering happens the appropriate css class is added.
 */
export class TokenHighlightLayer implements DiffLayer {
  /** The only listener is typically the renderer of gr-diff. */
  private listeners: DiffLayerListener[] = [];

  /** The currently highlighted token. */
  private currentHighlight?: string;

  /** Trigger when a new token starts or stoped being highlighted.*/
  private readonly tokenHighlightListener?: TokenHighlightListener;

  /**
   * The line of the currently highlighted token. We store this in order to
   * re-render only relevant lines of the diff. Only lines visible on the screen
   * need a highlight. For example in a file with 10,000 lines it is sufficient
   * to just re-render the ~100 lines that are visible to the user.
   *
   * It is a known issue that we are only storing the line number on the side of
   * where the user is hovering and we use that also to determine which line
   * numbers to re-render on the other side, but it is non-trivial to look up or
   * store a reliable mapping of line numbers, so we just accept this
   * shortcoming with the reasoning that the user is mostly interested in the
   * tokens on the side where they are hovering anyway.
   *
   * Another known issue is that we are not able to see past collapsed lines
   * with the current implementation.
   */
  private currentHighlightLineNumber = 0;

  /**
   * Keeps track of where tokens occur in a file during rendering, so that it is
   * easy to look up when processing mouse events.
   */
  private tokenToLinesLeft = new Map<string, Set<number>>();

  private tokenToLinesRight = new Map<string, Set<number>>();

  private hoveredElement?: Element;

  private updateTokenTask?: DelayedTask;

  constructor(
    container: HTMLElement = document.documentElement,
    tokenHighlightListener?: TokenHighlightListener
  ) {
    this.tokenHighlightListener = tokenHighlightListener;
    container.addEventListener('click', e => {
      this.handleContainerClick(e);
    });
  }

  annotate(
    el: HTMLElement,
    _: HTMLElement,
    line: GrDiffLine,
    side: Side
  ): void {
    const text = el.textContent;
    if (!text) return;
    // Binary files encoded as text for example can have super long lines
    // with super long tokens. Let's guard against against this scenario.
    if (text.length > LINE_LENGTH_LIMIT) return;
    let match;
    let atLeastOneTokenMatched = false;
    while ((match = tokenMatcher.exec(text))) {
      const token = match[0];
      const index = match.index;
      const length = token.length;
      // Binary files encoded as text for example can have super long lines
      // with super long tokens. Let's guard against this scenario.
      if (length > TOKEN_LENGTH_LIMIT) continue;
      atLeastOneTokenMatched = true;
      const highlightTypeClass =
        token === this.currentHighlight ? CSS_HIGHLIGHT : CSS_TOKEN;
      const textClass = `${TOKEN_TEXT_PREFIX}${token}`;
      const indexClass = `${TOKEN_INDEX_PREFIX}${index}`;
      // We add the TOKEN_TEXT_PREFIX class so that we can look up the token later easily
      // even if the token element was split up into multiple smaller nodes.
      // All parts of a single token will share a common TOKEN_INDEX_PREFIX class within the line of code.
      GrAnnotation.annotateElement(
        el,
        index,
        length,
        `${textClass} ${indexClass} ${highlightTypeClass}`
      );
      // We could try to detect whether we are re-rendering instead of initially
      // rendering the line. Then we would not have to call storeLineForToken()
      // again. But since the Set swallows the duplicates we don't care.
      this.storeLineForToken(token, line, side);
    }
    if (atLeastOneTokenMatched) {
      // These listeners do not have to be cleaned, because listeners are
      // garbage collected along with the element itself once it is not attached
      // to the DOM anymore and no references exist anymore.
      el.addEventListener('mouseover', e => {
        this.handleTokenMouseOver(e);
      });
      el.addEventListener('mouseout', e => {
        this.handleTokenMouseOut(e);
      });
    }
  }

  private storeLineForToken(token: string, line: GrDiffLine, side: Side) {
    const tokenToLines =
      side === Side.LEFT ? this.tokenToLinesLeft : this.tokenToLinesRight;
    // Just to make sure that we don't break down on large files.
    if (tokenToLines.size > TOKEN_COUNT_LIMIT) return;
    let numbers = tokenToLines.get(token);
    if (!numbers) {
      numbers = new Set<number>();
      tokenToLines.set(token, numbers);
    }
    // Just to make sure that we don't break down on large files.
    if (numbers.size > TOKEN_OCCURRENCES_LIMIT) return;
    const lineNumber =
      side === Side.LEFT ? line.beforeNumber : line.afterNumber;
    numbers.add(Number(lineNumber));
  }

  private handleTokenMouseOut(e: MouseEvent) {
    // If there's no ongoing hover-task, terminate early.
    if (!this.updateTokenTask?.isActive()) return;
    if (e.buttons > 0 || this.interferesWithSelection()) return;
    const {element} = this.findTokenAncestor(e?.target);
    if (!element) return;
    if (element === this.hoveredElement) {
      // If we are moving out of the currently hovered element, cancel the
      // update task.
      this.hoveredElement = undefined;
      if (this.updateTokenTask) this.updateTokenTask.cancel();
    }
  }

  private handleTokenMouseOver(e: MouseEvent) {
    if (e.buttons > 0 || this.interferesWithSelection()) return;
    const {
      line,
      token: newHighlight,
      element,
    } = this.findTokenAncestor(e?.target);
    if (!newHighlight || newHighlight === this.currentHighlight) return;
    this.hoveredElement = element;
    this.updateTokenTask = debounce(
      this.updateTokenTask,
      () => {
        this.updateTokenHighlight(newHighlight, line, element);
      },
      HOVER_DELAY_MS
    );
  }

  private handleContainerClick(e: MouseEvent) {
    if (this.interferesWithSelection()) return;
    // Ignore the click if the click is on a token.
    // We can't use e.target becauses it gets retargetted to the container as
    // it's a shadow dom.
    const {element} = this.findTokenAncestor(e.composedPath()[0]);
    if (element) return;
    this.hoveredElement = undefined;
    this.updateTokenTask?.cancel();
    this.updateTokenHighlight(undefined, 0, undefined);
  }

  private interferesWithSelection() {
    return document.getSelection()?.type === 'Range';
  }

  findTokenAncestor(el?: EventTarget | Element | null): {
    token?: string;
    line: number;
    element?: Element;
  } {
    if (!(el instanceof Element))
      return {line: 0, token: undefined, element: undefined};
    if (
      el.classList.contains(CSS_TOKEN) ||
      el.classList.contains(CSS_HIGHLIGHT)
    ) {
      const tkTextClass = [...el.classList].find(c =>
        c.startsWith(TOKEN_TEXT_PREFIX)
      );
      const line = lineNumberToNumber(getLineNumberByChild(el));
      if (!line || !tkTextClass)
        return {line: 0, token: undefined, element: undefined};
      return {
        line,
        token: tkTextClass.substring(TOKEN_TEXT_PREFIX.length),
        element: el,
      };
    }
    if (el.tagName === 'TD')
      return {line: 0, token: undefined, element: undefined};
    return this.findTokenAncestor(el.parentElement);
  }

  private updateTokenHighlight(
    newHighlight: string | undefined,
    newLineNumber: number,
    newHoveredElement: Element | undefined
  ) {
    if (
      this.currentHighlight === newHighlight &&
      this.currentHighlightLineNumber === newLineNumber
    )
      return;
    const oldHighlight = this.currentHighlight;
    const oldLineNumber = this.currentHighlightLineNumber;
    this.currentHighlight = newHighlight;
    this.currentHighlightLineNumber = newLineNumber;
    this.triggerTokenHighlightEvent(
      newHighlight,
      newLineNumber,
      newHoveredElement
    );
    this.notifyForToken(oldHighlight, oldLineNumber);
    this.notifyForToken(newHighlight, newLineNumber);
  }

  triggerTokenHighlightEvent(
    token: string | undefined,
    line: number,
    element: Element | undefined
  ) {
    if (!this.tokenHighlightListener) {
      return;
    }
    if (!token || !element) {
      this.tokenHighlightListener(undefined);
      return;
    }
    const lineEl = getLineElByChild(element);
    assertIsDefined(lineEl, 'Line element should be found!');
    const tokenIndexStr = [...element.classList]
      .find(c => c.startsWith(TOKEN_INDEX_PREFIX))
      ?.substring(TOKEN_INDEX_PREFIX.length);
    assertIsDefined(tokenIndexStr, 'Index class should be found!');
    const index = Number(tokenIndexStr);
    const side = getSideByLineEl(lineEl);
    const range = {
      start_line: line,
      start_column: index + 1, // 1-based inclusive
      end_line: line,
      end_column: index + token.length, // 1-based inclusive
    };
    this.tokenHighlightListener({token, element, side, range});
  }

  getSortedLinesForSide(
    lineMapping: Map<string, Set<number>>,
    token: string | undefined,
    lineNumber: number
  ): Array<number> {
    if (!token) return [];
    const lineSet = lineMapping.get(token);
    if (!lineSet) return [];
    const lines = [...lineSet];
    lines.sort((a, b) => {
      const da = Math.abs(a - lineNumber);
      const db = Math.abs(b - lineNumber);
      // For equal distance, prefer lines later in the file over earlier in the
      // file. This ensures total ordering.
      if (da === db) return b - a;
      // Compare the distance to lineNumber.
      return da - db;
    });
    return lines.slice(0, TOKEN_HIGHLIGHT_LIMIT);
  }

  notifyForToken(token: string | undefined, lineNumber: number) {
    const leftLines = this.getSortedLinesForSide(
      this.tokenToLinesLeft,
      token,
      lineNumber
    );
    for (const line of leftLines) {
      this.notifyListeners(line, Side.LEFT);
    }
    const rightLines = this.getSortedLinesForSide(
      this.tokenToLinesRight,
      token,
      lineNumber
    );
    for (const line of rightLines) {
      this.notifyListeners(line, Side.RIGHT);
    }
  }

  addListener(listener: DiffLayerListener) {
    this.listeners.push(listener);
  }

  removeListener(listener: DiffLayerListener) {
    this.listeners = this.listeners.filter(f => f !== listener);
  }

  notifyListeners(line: number, side: Side) {
    for (const listener of this.listeners) {
      listener(line, line, side);
    }
  }
}
