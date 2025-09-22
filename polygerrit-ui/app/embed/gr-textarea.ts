/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property, query, queryAsync} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {
  CursorPositionChangeEventDetail,
  GrTextarea as GrTextareaApi,
  HintAppliedEventDetail,
  HintDismissedEventDetail,
  HintShownEventDetail,
} from '../api/embed';
import {isFirefox, isSafari} from '../utils/dom-util';

/**
 * Waits for the next animation frame.
 */
async function animationFrame(): Promise<void> {
  return new Promise(resolve => {
    requestAnimationFrame(() => {
      resolve();
    });
  });
}

/**
 * Whether the current browser supports `plaintext-only` for contenteditable
 * https://caniuse.com/mdn-html_global_attributes_contenteditable_plaintext-only
 */
function supportsPlainTextEditing() {
  const div = document.createElement('div');
  try {
    div.contentEditable = 'PLAINTEXT-ONLY';
    return div.contentEditable === 'plaintext-only';
  } catch (e) {
    return false;
  }
}

/** Class for autocomplete hint */
export const AUTOCOMPLETE_HINT_CLASS = 'autocomplete-hint';

const ACCEPT_PLACEHOLDER_HINT_LABEL =
  'Press TAB to accept the placeholder hint.';

/**
 * A custom textarea component which allows autocomplete functionality.
 * This component is only supported in Chrome. Other browsers are not supported.
 *
 * Example usage:
 * <gr-textarea></gr-textarea>
 */
@customElement('gr-textarea')
export class GrTextarea extends LitElement implements GrTextareaApi {
  // editableDivElement is available right away where it may be undefined. This
  // is used for calls for scrollTop as if it is undefined then we can fallback
  // to 0. For other usecases use editableDiv.
  @query('.editableDiv')
  private readonly editableDivElement?: HTMLDivElement;

  @queryAsync('.editableDiv')
  private readonly editableDiv?: Promise<HTMLDivElement>;

  @property({type: Boolean, reflect: true}) disabled = false;

  @property({type: String, reflect: true}) placeholder: string | undefined;

  /**
   * The hint is shown as a autocomplete string which can be added by pressing
   * TAB.
   *
   * The hint is shown
   *  1. At the cursor position, only when cursor position is at the end of
   *     textarea content.
   *  2. When textarea has focus.
   *  3. When selection inside the textarea is collapsed.
   *
   * When hint is applied listen for hintApplied event and remove the hint
   * as component property to avoid showing the hint again.
   */
  @property({type: String})
  set hint(newHint) {
    if (this.hint !== newHint) {
      this.innerHint = newHint;
      this.updateHintInDomIfRendered();
    }
  }

  get hint() {
    return this.innerHint;
  }

  /**
   * Show hint is shown as placeholder which people can autocomplete to.
   *
   * This takes precedence over hint property.
   * It is shown even when textarea has no focus.
   * This is shown only when textarea is blank.
   */
  @property({type: String}) placeholderHint: string | undefined;

  /**
   * Sets the value for textarea and also renders it in dom if it is different
   * from last rendered value.
   *
   * To prevent cursor position from jumping to front of text even when value
   * remains same, Check existing value before triggering the update and only
   * update when there is a change.
   *
   * Also .innerText binding can't be used for security reasons.
   */
  @property({type: String})
  set value(newValue) {
    if (this.ignoreValue && this.ignoreValue === newValue) {
      return;
    }
    const oldVal = this.value;
    if (oldVal !== newValue) {
      this.innerValue = newValue;
      this.updateValueInDom();
    }
  }

  get value() {
    return this.innerValue;
  }

  /**
   * This value will be ignored by textarea and is not set.
   */
  @property({type: String}) ignoreValue: string | undefined;

  /**
   * Sets cursor at the end of content on focus.
   */
  @property({type: Boolean}) putCursorAtEndOnFocus = false;

  /**
   * Enables save shortcut.
   *
   * On S key down with control or meta key enabled is exposed with output event
   * 'saveShortcut'.
   */
  @property({type: Boolean}) enableSaveShortcut = false;

  /*
   * Is textarea focused. This is a readonly property.
   */
  get isFocused(): boolean {
    return this.focused;
  }

  /**
   * Native element for editable div.
   */
  get nativeElement() {
    return this.editableDivElement;
  }

  /**
   * Scroll Top for editable div.
   */
  override get scrollTop() {
    return this.editableDivElement?.scrollTop ?? 0;
  }

  private innerValue: string | undefined;

  private innerHint: string | undefined;

  private focused = false;

  private currentCursorPosition = -1;

  private readonly isPlaintextOnlySupported = supportsPlainTextEditing();

  static override get styles() {
    return [
      css`
        :host {
          display: inline-block;
          position: relative;
          width: 100%;
        }

        :host([disabled]) {
          .editableDiv {
            background-color: var(--input-field-disabled-bg, lightgrey);
            color: var(--text-disabled, black);
            cursor: default;
          }
        }

        .editableDiv {
          background-color: var(--input-field-bg, white);
          border: var(--gr-textarea-border-width, 2px) solid
            var(--gr-textarea-border-color, white);
          border-radius: 4px;
          box-sizing: border-box;
          color: var(--text-default, black);
          max-height: var(--gr-textarea-max-height, 16em);
          min-height: var(--gr-textarea-min-height, 4em);
          overflow-x: auto;
          padding: var(--gr-textarea-padding, 12px);
          white-space: pre-wrap;
          width: 100%;

          &:focus-visible {
            border-color: var(--gr-textarea-focus-outline-color, black);
            outline: none;
          }

          &:empty::before {
            content: attr(data-placeholder);
            color: var(--text-secondary, lightgrey);
            display: inline;
            pointer-events: none;
          }

          &.hintShown:empty::after,
          .autocomplete-hint:empty::after {
            background-color: var(--secondary-bg-color, white);
            border: 1px solid var(--text-secondary, lightgrey);
            border-radius: 2px;
            content: 'tab';
            color: var(--text-secondary, lightgrey);
            display: inline;
            pointer-events: none;
            font-size: 10px;
            line-height: 10px;
            margin-left: 4px;
            padding: 1px 3px;
          }

          .autocomplete-hint {
            &:empty::before {
              content: attr(data-hint);
              color: var(--text-secondary, lightgrey);
            }
          }
        }
      `,
    ];
  }

  override render() {
    const isHintShownAsPlaceholder =
      (!this.disabled && this.placeholderHint) ?? false;

    const placeholder = isHintShownAsPlaceholder
      ? this.placeholderHint
      : this.placeholder;
    const ariaPlaceholder = isHintShownAsPlaceholder
      ? (this.placeholderHint ?? '') + ACCEPT_PLACEHOLDER_HINT_LABEL
      : placeholder;

    const classes = classMap({
      editableDiv: true,
      hintShown: isHintShownAsPlaceholder,
    });

    // Chrome supports non-standard "contenteditable=plaintext-only",
    // which prevents HTML from being inserted into a contenteditable element.
    // https://github.com/w3c/editing/issues/162
    return html`<div
      aria-disabled=${this.disabled}
      aria-multiline="true"
      aria-placeholder=${ifDefined(ariaPlaceholder)}
      data-placeholder=${ifDefined(placeholder)}
      class=${classes}
      contenteditable=${this.contentEditableAttributeValue}
      dir="ltr"
      role="textbox"
      spellcheck="true"
      @input=${this.onInput}
      @focus=${this.onFocus}
      @blur=${this.onBlur}
      @keydown=${this.handleKeyDown}
      @keyup=${this.handleKeyUp}
      @mouseup=${this.handleMouseUp}
      @scroll=${this.handleScroll}
    ></div>`;
  }

  /**
   * Focuses the textarea.
   */
  override async focus() {
    const editableDivElement = await this.editableDiv;
    const isFocused = this.isFocused;
    editableDivElement?.focus?.();
    // If already focused, do not change the cursor position.
    if (this.putCursorAtEndOnFocus && !isFocused) {
      await this.putCursorAtEnd();
    }
  }

  /**
   * Puts the cursor at the end of existing content.
   * Scrolls the content of textarea towards the end.
   */
  async putCursorAtEnd() {
    const editableDivElement = await this.editableDiv;
    const selection = this.getSelection();

    if (!editableDivElement || !selection) {
      return;
    }

    const range = document.createRange();
    editableDivElement.focus();
    range.setStart(editableDivElement, editableDivElement.childNodes.length);
    range.collapse(true);
    selection.removeAllRanges();
    selection.addRange(range);

    this.scrollToCursorPosition(range);

    range.detach();

    this.onCursorPositionChange();
  }

  public setCursorPosition(position: number) {
    this.setCursorPositionForDiv(position, this.editableDivElement);
  }

  public async setCursorPositionAsync(position: number) {
    const editableDivElement = await this.editableDiv;
    this.setCursorPositionForDiv(position, editableDivElement);
  }

  /**
   * Sets cursor position to given position and scrolls the content to cursor
   * position.
   *
   * If position is out of bounds of value of textarea then cursor is places at
   * end of content of textarea.
   */
  private setCursorPositionForDiv(
    position: number,
    editableDivElement?: HTMLDivElement
  ) {
    // This will keep track of remaining offset to place the cursor.
    let remainingOffset = position;
    let isOnFreshLine = true;
    let nodeToFocusOn: Node | null = null;
    const selection = this.getSelection();

    if (!editableDivElement || !selection) {
      return;
    }
    editableDivElement.focus();
    const findNodeToFocusOn = (childNodes: Node[]) => {
      for (let i = 0; i < childNodes.length; i++) {
        const childNode = childNodes[i];
        let currentNodeLength = 0;

        if (childNode.nodeType === Node.COMMENT_NODE) {
          continue;
        }

        if (childNode.nodeName === 'BR') {
          currentNodeLength++;
          isOnFreshLine = true;
        }

        if (childNode.nodeName === 'DIV' && !isOnFreshLine && i !== 0) {
          currentNodeLength++;
        }

        isOnFreshLine = false;

        if (childNode.nodeType === Node.TEXT_NODE && childNode.textContent) {
          currentNodeLength += childNode.textContent.length;
        }

        if (remainingOffset <= currentNodeLength) {
          nodeToFocusOn = childNode;
          break;
        } else {
          remainingOffset -= currentNodeLength;
        }

        if (childNode.childNodes?.length > 0) {
          findNodeToFocusOn(Array.from(childNode.childNodes));
        }
      }
    };

    findNodeToFocusOn(Array.from(editableDivElement.childNodes));

    this.setFocusOnNode(
      selection,
      editableDivElement,
      nodeToFocusOn,
      remainingOffset
    );
  }

  /**
   * Replaces text from start and end cursor position.
   */
  setRangeText(replacement: string, start: number, end: number) {
    const pre = this.value?.substring(0, start) ?? '';
    const post = this.value?.substring(end, this.value?.length ?? 0) ?? '';

    this.value = pre + replacement + post;
    this.setCursorPosition(pre.length + replacement.length);
  }

  private get contentEditableAttributeValue() {
    return this.disabled
      ? 'false'
      : !isFirefox() && this.isPlaintextOnlySupported
      ? ('plaintext-only' as unknown as 'true')
      : 'true';
  }

  private setFocusOnNode(
    selection: Selection,
    editableDivElement: Node,
    nodeToFocusOn: Node | null,
    remainingOffset: number
  ) {
    const range = document.createRange();
    // If node is null or undefined then fallback to focus event which will put
    // cursor at the end of content.
    if (nodeToFocusOn === null) {
      range.setStart(editableDivElement, editableDivElement.childNodes.length);
    }
    // If node to focus is BR then focus offset is number of nodes.
    else if (nodeToFocusOn.nodeName === 'BR') {
      const nextNode = nodeToFocusOn.nextSibling ?? nodeToFocusOn;
      range.setEnd(nextNode, 0);
    } else {
      range.setStart(nodeToFocusOn, remainingOffset);
    }

    range.collapse(true);
    selection.removeAllRanges();
    selection.addRange(range);

    // Scroll the content to cursor position.
    this.scrollToCursorPosition(range);

    range.detach();

    this.onCursorPositionChange();
  }

  private async onInput(event: Event) {
    event.preventDefault();
    event.stopImmediatePropagation();

    const value = await this.getValue();
    this.innerValue = value;

    this.fire('input', {value: this.value});
  }

  private onFocus() {
    this.focused = true;
    this.onCursorPositionChange();
  }

  private onBlur() {
    this.focused = false;
    this.removeHintSpanIfShown();
    this.onCursorPositionChange();
  }

  private async handleKeyDown(event: KeyboardEvent) {
    if (
      event.key === 'Tab' &&
      !event.shiftKey &&
      !event.ctrlKey &&
      !event.metaKey
    ) {
      await this.handleTabKeyPress(event);
      return;
    }
    if (
      this.enableSaveShortcut &&
      event.key === 's' &&
      (event.ctrlKey || event.metaKey)
    ) {
      event.preventDefault();
      this.fire('saveShortcut');
    }

    if (event.ctrlKey || event.metaKey || event.altKey) {
      // Prevent looping of cursor position when CTRL+ARROW_LEFT/ARROW_RIGHT is
      // pressed.
      if (event.key === 'ArrowLeft' && this.currentCursorPosition === 0) {
        event.preventDefault();
      }
      if (
        event.key === 'ArrowRight' &&
        this.currentCursorPosition === (this.value?.length ?? 0)
      ) {
        event.preventDefault();
      }

      // Prevent Ctrl/Alt+Backspace from deleting entire content when at start
      if (event.key === 'Backspace' && this.currentCursorPosition === 0) {
        event.preventDefault();
      }
    }

    await this.toggleHintVisibilityIfAny();
  }

  private handleKeyUp() {
    this.onCursorPositionChange();
  }

  private async handleMouseUp() {
    this.onCursorPositionChange();
    await this.toggleHintVisibilityIfAny();
  }

  private handleScroll() {
    this.fire('scroll');
  }

  private fire<T>(type: string, detail?: T) {
    this.dispatchEvent(
      new CustomEvent(type, {detail, bubbles: true, composed: true})
    );
  }

  private async handleTabKeyPress(event: KeyboardEvent) {
    const oldValue = this.value;
    if (this.placeholderHint && !oldValue) {
      event.preventDefault();
      await this.appendHint(this.placeholderHint, event);
    } else if (this.hasHintSpan()) {
      event.preventDefault();
      await this.appendHint(this.hint!, event);
    } else {
      // Add tab \t to cursor position if inside a code snippet ```
      const cursorPosition = await this.getCursorPositionAsync();
      const textValue = await this.getValue();

      const startCodeSnippet = textValue.lastIndexOf('```', cursorPosition - 1);
      const endCodeSnippet = textValue.indexOf('```', cursorPosition);

      if (
        startCodeSnippet !== -1 &&
        endCodeSnippet !== -1 &&
        endCodeSnippet > startCodeSnippet
      ) {
        event.preventDefault();
        this.setRangeText('\t', cursorPosition, cursorPosition);
      }
    }
  }

  private async appendHint(hint: string, event: Event) {
    const oldValue = this.value ?? '';
    const newValue = oldValue + hint;

    this.value = newValue;
    await this.putCursorAtEnd();
    await this.onInput(event);

    this.fire('hintApplied', {hint, oldValue});
  }

  private async toggleHintVisibilityIfAny() {
    // Wait for the next animation frame so that entered key is processed and
    // available in dom.
    await animationFrame();

    const editableDivElement = await this.editableDiv;
    const currentValue = (await this.getValue()) ?? '';
    const cursorPosition = await this.getCursorPositionAsync();
    if (
      !editableDivElement ||
      (this.placeholderHint && !currentValue) ||
      !this.hint ||
      !this.isFocused ||
      cursorPosition !== currentValue.length
    ) {
      this.removeHintSpanIfShown();
      return;
    }

    const hintSpan = this.hintSpan();
    if (!hintSpan) {
      this.addHintSpanAtEndOfContent(editableDivElement, this.hint || '');
      return;
    }

    const oldHint = (hintSpan as HTMLElement).dataset['hint'];
    if (oldHint !== this.hint) {
      this.removeHintSpanIfShown();
      this.addHintSpanAtEndOfContent(editableDivElement, this.hint || '');
    }
  }

  private addHintSpanAtEndOfContent(editableDivElement: Node, hint: string) {
    const oldValue = this.value ?? '';
    const hintSpan = document.createElement('span');
    hintSpan.classList.add(AUTOCOMPLETE_HINT_CLASS);
    hintSpan.setAttribute('role', 'alert');
    hintSpan.setAttribute(
      'aria-label',
      'Suggestion: ' + hint + ' Press TAB to accept it.'
    );
    hintSpan.dataset['hint'] = hint;
    editableDivElement.appendChild(hintSpan);
    this.fire('hintShown', {hint, oldValue});
  }

  private removeHintSpanIfShown() {
    const hintSpan = this.hintSpan();
    if (hintSpan) {
      hintSpan.remove();
      this.fire('hintDismissed', {
        hint: (hintSpan as HTMLElement).dataset['hint'],
      });
    }
  }

  private hasHintSpan() {
    return !!this.hintSpan();
  }

  private hintSpan() {
    return this.shadowRoot?.querySelector('.' + AUTOCOMPLETE_HINT_CLASS);
  }

  private onCursorPositionChange() {
    const cursorPosition = this.getCursorPosition();
    this.fire('cursorPositionChange', {position: cursorPosition});
    this.currentCursorPosition = cursorPosition;
  }

  private async updateValueInDom() {
    const editableDivElement =
      this.editableDivElement ?? (await this.editableDiv);
    if (editableDivElement) {
      editableDivElement.textContent = this.value || '';
    }
  }

  private async updateHintInDomIfRendered() {
    // Wait for editable div to render then process the hint.
    await this.editableDiv;
    await this.toggleHintVisibilityIfAny();
  }

  private async getValue() {
    const editableDivElement = await this.editableDiv;
    if (editableDivElement) {
      const [output] = this.parseText(editableDivElement, false, true);
      return output;
    }
    return '';
  }

  private parseText(
    node: Node,
    isLastBr: boolean,
    isFirst: boolean
  ): [string, boolean] {
    let textValue = '';
    let output = '';
    if (node.nodeName === 'BR') {
      return ['\n', true];
    }

    if (node.nodeType === Node.TEXT_NODE && node.textContent) {
      return [node.textContent, false];
    }

    if (node.nodeName === 'DIV' && !isLastBr && !isFirst) {
      textValue = '\n';
    }

    isLastBr = false;

    for (let i = 0; i < node.childNodes?.length; i++) {
      [output, isLastBr] = this.parseText(
        node.childNodes[i],
        isLastBr,
        i === 0
      );
      textValue += output;
    }
    return [textValue, isLastBr];
  }

  public getCursorPosition() {
    return this.getCursorPositionForDiv(this.editableDivElement);
  }

  public async getCursorPositionAsync() {
    const editableDivElement = await this.editableDiv;
    return this.getCursorPositionForDiv(editableDivElement);
  }

  private getCursorPositionForDiv(editableDivElement?: HTMLDivElement) {
    const selection = this.getSelection();

    // Cursor position is -1 (not available) if
    //
    // If textarea is not rendered.
    // If textarea is not focused
    // There is no accessible selection object.
    // This is not a collapsed selection.
    if (
      !editableDivElement ||
      !this.focused ||
      !selection ||
      selection.focusNode === null ||
      !selection.isCollapsed
    ) {
      return -1;
    }

    let cursorPosition = 0;
    let isOnFreshLine = true;

    const findCursorPosition = (childNodes: Node[]) => {
      for (let i = 0; i < childNodes.length; i++) {
        const childNode = childNodes[i];

        if (childNode.nodeName === 'BR') {
          cursorPosition++;
          isOnFreshLine = true;
          continue;
        }

        if (childNode.nodeName === 'DIV' && !isOnFreshLine && i !== 0) {
          cursorPosition++;
        }

        isOnFreshLine = false;

        if (childNode === selection.focusNode) {
          cursorPosition += selection.focusOffset;
          break;
        } else if (childNode.nodeType === 3 && childNode.textContent) {
          cursorPosition += childNode.textContent.length;
        }

        if (childNode.childNodes?.length > 0) {
          findCursorPosition(Array.from(childNode.childNodes));
        }
      }
    };

    if (editableDivElement === selection.focusNode) {
      // If focus node is the top textarea then focusOffset is the number of
      // child nodes before the cursor position.
      const partOfNodes = Array.from(editableDivElement.childNodes).slice(
        0,
        selection.focusOffset
      );
      findCursorPosition(partOfNodes);
    } else {
      findCursorPosition(Array.from(editableDivElement.childNodes));
    }

    return cursorPosition;
  }

  /** Gets the current selection, preferring the shadow DOM selection. */
  private getSelection(): Selection | null {
    const selection =
      this.shadowRoot?.getSelection?.() ?? document.getSelection?.();
    if (!selection) return null;

    // For safari 17+.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    if (isSafari() && (selection as any).getComposedRanges) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const composedRanges = (selection as any).getComposedRanges(
        this.shadowRoot
      );
      if (composedRanges.length === 0) return null;

      const r = composedRanges[0];
      const range = new Range();
      range.setStart(r.startContainer, r.startOffset);
      range.setEnd(r.endContainer, r.endOffset);

      return {
        isCollapsed: range.collapsed,
        focusNode: range.endContainer,
        focusOffset: range.endOffset,
        removeAllRanges: () => selection.removeAllRanges(),
        addRange: (newRange: Range) => selection.addRange(newRange),
        getRangeAt: (_: number) => range,
        rangeCount: 1,
      } as unknown as Selection;
    }

    return selection;
  }

  private scrollToCursorPosition(range: Range) {
    const tempAnchorEl = document.createElement('br');
    range.insertNode(tempAnchorEl);

    tempAnchorEl.scrollIntoView({behavior: 'smooth', block: 'nearest'});

    tempAnchorEl.remove();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-textarea': GrTextarea;
  }
  interface HTMLElementEventMap {
    // prettier-ignore
    'saveShortcut': CustomEvent<{}>;
    // prettier-ignore
    'hintApplied': CustomEvent<HintAppliedEventDetail>;
    // prettier-ignore
    'hintShown': CustomEvent<HintShownEventDetail>;
    // prettier-ignore
    'hintDismissed': CustomEvent<HintDismissedEventDetail>;
    // prettier-ignore
    'cursorPositionChange': CustomEvent<CursorPositionChangeEventDetail>;
  }
}
