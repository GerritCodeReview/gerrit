/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {DiffLayer} from '../../../types/types';
import {GrDiffLine, Side, TokenHighlightListener} from '../../../api/diff';
import {assertIsDefined} from '../../../utils/common-util';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {debounce, DelayedTask} from '../../../utils/async-util';

import {getLineElByChild, getSideByLineEl} from '../gr-diff/gr-diff-utils';

import {
  getLineNumberByChild,
  lineNumberToNumber,
} from '../gr-diff/gr-diff-utils';
import {GrDiff} from '../gr-diff/gr-diff';

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
  private tokenToElements = new Map<string, Set<HTMLElement>>();

  private hoveredElement?: Element;

  private updateTokenTask?: DelayedTask;

  /**
   * Container that contains all annotated tokens and contains no shadow root
   * elements that would prevent tokens to be queryable by querySelectorAll.
   */
  private getTokenQueryContainer?: () => HTMLElement;

  /**
   * @param container for registering "deselect" click
   * @param tokenHighlightListener method that is called,
   *   when token is highlighted.
   * @param getTokenQueryContainer if specified, list of tokens to be
   *   highlighted are recalculated every time using querySelectorAll inside
   *   this element. Otherwise, the pointers calculated once at annotate() time
   *   and are reused.
   */
  constructor(
    container: HTMLElement,
    tokenHighlightListener?: TokenHighlightListener,
    getTokenQueryContainer?: () => HTMLElement
  ) {
    this.tokenHighlightListener = tokenHighlightListener;
    container.addEventListener('click', e => {
      this.handleContainerClick(e);
    });
    this.getTokenQueryContainer = getTokenQueryContainer;
  }

  static createTokenHighlightContainer(
    container: HTMLElement,
    getGrDiff: () => GrDiff,
    tokenHighlightListener?: TokenHighlightListener
  ): TokenHighlightLayer {
    return new TokenHighlightLayer(
      container,
      tokenHighlightListener,
      () => getGrDiff().diffTable!
    );
  }

  annotate(el: HTMLElement, _1: HTMLElement, _2: GrDiffLine, _3: Side): void {
    const text = el.textContent;
    if (!text) return;
    // Binary files encoded as text for example can have super long lines
    // with super long tokens. Let's guard against against this scenario.
    if (text.length > LINE_LENGTH_LIMIT) return;
    let match;
    let atLeastOneTokenMatched = false;
    while ((match = tokenMatcher.exec(text))) {
      const token = match[0];

      // Binary files encoded as text for example can have super long lines
      // with super long tokens. Let's guard against this scenario.
      if (token.length > TOKEN_LENGTH_LIMIT) continue;

      // This is to correctly count surrogate pairs in text and token.
      // If the index calculation becomes a hotspot, we could precompute a code
      // unit to code point index map for text before iterating over the results
      const index = GrAnnotation.getStringLength(text.slice(0, match.index));
      const length = GrAnnotation.getStringLength(token);

      atLeastOneTokenMatched = true;
      const highlightTypeClass =
        token === this.currentHighlight ? CSS_HIGHLIGHT : '';
      const textClass = `${TOKEN_TEXT_PREFIX}${token}`;
      const indexClass = `${TOKEN_INDEX_PREFIX}${index}`;
      // We add the TOKEN_TEXT_PREFIX class so that we can look up the token later easily
      // even if the token element was split up into multiple smaller nodes.
      // All parts of a single token will share a common TOKEN_INDEX_PREFIX class within the line of code.
      GrAnnotation.annotateElement(
        el,
        index,
        length,
        `${textClass} ${indexClass} ${CSS_TOKEN} ${highlightTypeClass}`
      );
      // We could try to detect whether we are re-rendering instead of initially
      // rendering the line. Then we would not have to call storeLineForToken()
      // again. But since the Set swallows the duplicates we don't care.
      this.storeElementsForToken(token, el, textClass);
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

  private storeElementsForToken(
    token: string,
    lineEl: HTMLElement,
    cssClass: string
  ) {
    for (const el of lineEl.querySelectorAll(`.${cssClass}`)) {
      let tokenEls = this.tokenToElements.get(token);
      if (!tokenEls) {
        // Just to make sure that we don't break down on large files.
        if (this.tokenToElements.size > TOKEN_COUNT_LIMIT) return;
        tokenEls = new Set<HTMLElement>();
        this.tokenToElements.set(token, tokenEls);
      }
      // Just to make sure that we don't break down on large files.
      if (tokenEls.size > TOKEN_OCCURRENCES_LIMIT) return;
      tokenEls.add(el as HTMLElement);
    }
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
    if (el.classList.contains(CSS_TOKEN)) {
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
    this.currentHighlight = newHighlight;
    this.currentHighlightLineNumber = newLineNumber;
    this.triggerTokenHighlightEvent(
      newHighlight,
      newLineNumber,
      newHoveredElement
    );
    this.toggleTokenHighlight(oldHighlight, CSS_HIGHLIGHT);
    this.toggleTokenHighlight(newHighlight, CSS_HIGHLIGHT);
  }

  private toggleTokenHighlight(token: string | undefined, cssClass: string) {
    if (!token) {
      return;
    }
    let tokenEls;
    let tokenElsLength;
    if (this.getTokenQueryContainer) {
      tokenEls = this.getTokenQueryContainer().querySelectorAll(
        `.${TOKEN_TEXT_PREFIX}${token}`
      );
      tokenElsLength = tokenEls.length;
    } else {
      tokenEls = this.tokenToElements.get(token);
      tokenElsLength = tokenEls?.size;
    }
    if (!tokenEls || tokenElsLength === 0) {
      console.warn(`No tokens have been found for '${token}'`);
      return;
    }
    for (const el of tokenEls) {
      el.classList.toggle(cssClass);
    }
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
      end_column: index + GrAnnotation.getStringLength(token), // 1-based inclusive
    };
    this.tokenHighlightListener({token, element, side, range});
  }
}
