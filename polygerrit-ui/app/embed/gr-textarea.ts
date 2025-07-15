/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {
  customElement,
  property,
  query,
  queryAsync,
  state,
} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {
  CursorPositionChangeEventDetail,
  GrTextarea as GrTextareaApi,
  HintAppliedEventDetail,
  HintDismissedEventDetail,
  HintShownEventDetail,
} from '../api/embed';

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

/** Class for autocomplete hint */
export const AUTOCOMPLETE_HINT_CLASS = 'autocomplete-hint';

const ACCEPT_PLACEHOLDER_HINT_LABEL =
  'Press TAB to accept the placeholder hint.';

/**
 * A custom textarea component which allows autocomplete functionality.
 *
 * Example usage:
 * <gr-textarea></gr-textarea>
 */
@customElement('gr-textarea')
export class GrTextarea extends LitElement implements GrTextareaApi {
  // editableTextAreaElement is available right away where it may be undefined. This
  // is used for calls for scrollTop as if it is undefined then we can fallback
  // to 0. For other usecases use editableTextArea.
  @query('.editableTextArea')
  private readonly editableTextAreaElement?: HTMLTextAreaElement;

  @queryAsync('.editableTextArea')
  private readonly editableTextArea?: Promise<HTMLTextAreaElement>;

  @queryAsync('.hintOverlay')
  private readonly hintOverlay?: Promise<HTMLDivElement>;

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

  @property({type: String}) value: string = '';

  @property({type: Number}) rows = 1;

  @property({type: Number}) maxRows = 0;

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

  @property({type: String}) autocomplete = 'off';

  @property({type: Boolean}) autoFocus = false;

  @property({type: String}) autoCapitalize = 'none';

  @property({type: String}) inputmode?: string;

  @property({type: String}) readonly?: string;

  @property({type: Boolean}) required = false;

  @property({type: Number}) minlength?: number;

  @property({type: Number}) maxlength?: number;

  @property({type: String}) label?: string;

  @state() showPlaceholder = true;

  /*
   * Is textarea focused. This is a readonly property.
   */
  get isFocused(): boolean {
    return !!this.focused;
  }

  /**
   * Native element for editable div.
   */
  get nativeElement() {
    return this.editableTextAreaElement;
  }

  /**
   * Scroll Top for editable div.
   */
  override get scrollTop() {
    return this.editableTextAreaElement?.scrollTop ?? 0;
  }

  private innerHint: string | undefined;

  private focused = false;

  private currentCursorPosition = -1;

  @state() private tokens: string[] = [];

  static override get styles() {
    return [
      css`
        :host {
          display: inline-block;
          position: relative;
          width: 400px;
          border: var(--gr-textarea-border-width, 2px) solid
            var(--gr-textarea-border-color, white);
          border-radius: 4px;
          padding: var(--gr-textarea-padding, 12px);
          -moz-appearance: textarea;
          -webkit-appearance: textarea;
          overflow: hidden;
        }
        :host([disabled]) {
          textarea {
            background-color: var(--input-field-disabled-bg, lightgrey);
            color: var(--text-disabled, black);
            cursor: default;
          }
        }
        .mirror-text {
          visibility: hidden;
          word-wrap: break-word;
        }
        .textarea-container {
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
        }
        textarea {
          position: relative;
          outline: none;
          border: none;
          resize: none;
          background: inherit;
          color: inherit;
          width: 100%;
          height: 100%;
          font-size: inherit;
          font-family: inherit;
          line-height: inherit;
          text-align: inherit;
        }
        textarea:focus-visible {
          border-color: var(--gr-textarea-focus-outline-color, black);
          outline: none;
        }
        .hintOverlay {
          position: absolute;
          z-index: 0;
          top: 0;
          left: 0;
          padding: 2px;
          pointer-events: none;
          width: 100%;
          height: 100%;
          font-size: inherit;
          font-family: inherit;
          text-align: inherit;
          white-space: pre-wrap;
          visibility: hidden;
        }

        .autocomplete-hint {
          margin-left: 1px;
          visibility: visible;
        }
        &.hintShown:empty::after,
        .autocomplete-hint:empty::after {
          background-color: var(--secondary-bg-color, white);
          border: 1px solid var(--text-secondary, lightgrey);
          border-radius: 2px;
          content: 'tab';
          color: var(--text-secondary, lightgrey);
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
      editableTextArea: true,
      hintShown: isHintShownAsPlaceholder,
    });

    return html` <div id="mirror" class="mirror-text" aria-hidden="true">
        ${this.tokens.map(t => html`${t}<br />`)}&#160;
      </div>
      <div class="textarea-container">
        <textarea
          class=${classes}
          .value=${this.value}
          name=${this.label || nothing}
          aria-label=${this.label || nothing}
          aria-disabled=${this.disabled}
          aria-multiline="true"
          aria-placeholder=${ariaPlaceholder ?? nothing}
          autocomplete=${this.autocomplete}
          ?autofocus=${this.autoFocus}
          autocapitalize=${this.autoCapitalize}
          .inputMode=${this.inputmode ?? nothing}
          placeholder=${this.showPlaceholder ? placeholder ?? nothing : nothing}
          ?disabled=${this.disabled}
          .rows=${this.rows}
          .minLength=${ifDefined(this.minlength)}
          .maxLength=${ifDefined(this.maxlength)}
          spellcheck="true"
          @input=${this.onInput}
          @focus=${this.onFocus}
          @blur=${this.onBlur}
          @keydown=${this.handleKeyDown}
          @keyup=${this.handleKeyUp}
          @mouseup=${this.handleMouseUp}
          @scroll=${this.handleScroll}
        ></textarea>
      </div>`;
  }

  override updated(changed: Map<string, unknown>) {
    if (changed.has('value')) {
      if (
        this.editableTextAreaElement &&
        this.editableTextAreaElement.value !== this.value
      ) {
        this.editableTextAreaElement.value = this.value ?? '';
      }
      this.updateMirror();
    }
    if (changed.has('rows') || changed.has('maxRows')) {
      this.updateMirror();
    }
  }

  override async focus() {
    const editableTextAreaElement = await this.editableTextArea;
    const isFocused = this.isFocused;
    editableTextAreaElement?.focus?.();
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
    const editableTextAreaElement = await this.editableTextArea;
    if (!editableTextAreaElement) return;

    const length = this.value.length;
    editableTextAreaElement.selectionStart = length;
    editableTextAreaElement.selectionEnd = length;
    editableTextAreaElement.focus();
  }

  /**
   * Sets cursor position to given position and scrolls the content to cursor
   * position.
   *
   * If position is out of bounds of value of textarea then cursor is places at
   * end of content of textarea.
   */
  public setCursorPosition(position: number) {
    if (!this.editableTextAreaElement) return;

    this.editableTextAreaElement.selectionStart = position;
    this.editableTextAreaElement.selectionEnd = position;

    this.onCursorPositionChange();
  }

  /**
   * Replaces text from start and end cursor position.
   */
  async setRangeText(replacement: string, start: number, end: number) {
    const pre = this.value?.substring(0, start) ?? '';
    const post = this.value?.substring(end, this.value?.length ?? 0) ?? '';
    this.value = pre + replacement + post;

    // We have to use requestAnimationFrame due to problems
    // with pressing tab doesn't go right to the end of the text.
    // e.g. you do \`\`\` \`\`\` and you pressed tab in the centre between
    // both. It would go to the end e.g. \`.
    await animationFrame();

    this.setCursorPosition(pre.length + replacement.length);
  }

  private onInput(event: Event) {
    event.preventDefault();
    event.stopImmediatePropagation();

    const target = event.target as HTMLTextAreaElement;
    this.value = target.value;

    this.toggleHintVisibilityIfAny();

    this.fire('input', {value: this.value});
  }

  private onFocus() {
    this.focused = true;
    this.onCursorPositionChange();
  }

  private async onBlur() {
    this.focused = false;
    this.removeHintSpanIfShown(await this.hintOverlay);
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

  private async handleScroll() {
    this.fire('scroll');

    const hintOverlay = await this.hintOverlay;
    const editableTextAreaElement = await this.editableTextArea;
    if (hintOverlay && editableTextAreaElement && this.maxRows > 0) {
      hintOverlay.style.transform = `translateY(-${editableTextAreaElement.scrollTop}px)`;
    }
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
      const textValue = this.value;

      const startCodeSnippet = textValue.lastIndexOf('```', cursorPosition - 1);
      const endCodeSnippet = textValue.indexOf('```', cursorPosition);

      if (
        startCodeSnippet !== -1 &&
        endCodeSnippet !== -1 &&
        endCodeSnippet > startCodeSnippet
      ) {
        event.preventDefault();
        await this.setRangeText('\t', cursorPosition, cursorPosition);
      }
    }
  }

  private async appendHint(hint: string, event: Event) {
    const oldValue = this.value ?? '';
    const newValue = oldValue + hint;

    this.value = newValue;
    await this.putCursorAtEnd();
    this.onInput(event);

    this.fire('hintApplied', {hint, oldValue});
  }

  private async toggleHintVisibilityIfAny() {
    // Wait for the next animation frame so that entered key is processed and
    // available in dom.
    await animationFrame();

    const hintOverlay = await this.hintOverlay;
    const editableTextAreaElement = await this.editableTextArea;
    const currentValue = this.value;
    const cursorPosition = await this.getCursorPositionAsync();
    if (
      !editableTextAreaElement ||
      (this.placeholderHint && !currentValue) ||
      !this.hint ||
      !this.isFocused ||
      cursorPosition !== currentValue.length
    ) {
      this.removeHintSpanIfShown(hintOverlay);
      return;
    }

    this.removeHintSpanIfShown(hintOverlay);
    this.addHintSpanAtEndOfContent(editableTextAreaElement, this.hint || '');
  }

  private addHintSpanAtEndOfContent(
    editableTextAreaElement: HTMLTextAreaElement,
    hint: string
  ) {
    const oldValue = this.value ?? '';
    if (oldValue === '') {
      this.showPlaceholder = false;
    }
    const hintOverlay = document.createElement('div');
    hintOverlay.classList.add('hintOverlay');
    if (this.maxRows > 0) {
      hintOverlay.style.transform = `translateY(-${editableTextAreaElement.scrollTop}px)`;
    }
    const hintSpan = document.createElement('span');
    hintSpan.classList.add(AUTOCOMPLETE_HINT_CLASS);
    hintSpan.setAttribute('role', 'alert');
    hintSpan.setAttribute(
      'aria-label',
      'Suggestion: ' + hint + ' Press TAB to accept it.'
    );
    hintSpan.dataset['hint'] = hint;
    const pos = editableTextAreaElement?.selectionStart || 0;
    const before = this.value.slice(0, pos);
    const newText = document.createTextNode(before);
    hintOverlay.appendChild(newText);
    hintOverlay.appendChild(hintSpan);
    editableTextAreaElement.after(hintOverlay);
    this.fire('hintShown', {hint, oldValue});
  }

  private removeHintSpanIfShown(hintOverlay?: HTMLDivElement) {
    const hintSpan = this.hintSpan();
    if (hintSpan && hintOverlay) {
      this.showPlaceholder = true;
      hintOverlay.remove();
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

  private async updateHintInDomIfRendered() {
    // Wait for editable textarea to render then process the hint.
    await this.editableTextArea;
    await this.toggleHintVisibilityIfAny();
  }

  public getCursorPosition() {
    return this.editableTextAreaElement?.selectionStart ?? -1;
  }

  public async getCursorPositionAsync() {
    const editableTextAreaElement = await this.editableTextArea;
    return editableTextAreaElement?.selectionStart ?? -1;
  }

  private updateMirror() {
    if (!this.editableTextAreaElement) return;
    this.tokens = this.constrain(
      this.tokenize(this.editableTextAreaElement.value)
    );
  }

  private tokenize(val: string): string[] {
    return val
      ? val
          .replace(/&/g, '&amp;')
          .replace(/"/g, '&quot;')
          .replace(/'/g, '&#39;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .split('\n')
      : [''];
  }

  private constrain(tokens: string[]): string[] {
    let result = tokens.slice();
    if (this.maxRows > 0 && result.length > this.maxRows) {
      result = result.slice(0, this.maxRows);
    }
    while (this.rows > 0 && result.length < this.rows) {
      result.push('');
    }
    return result;
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
