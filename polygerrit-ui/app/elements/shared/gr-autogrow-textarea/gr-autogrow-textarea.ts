/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {
  customElement,
  property,
  query,
  queryAsync,
  state,
} from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {
  CursorPositionChangeEventDetail,
  GrAutogrowTextarea as GrAutogrowTextareaApi,
} from '../../../api/embed';

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
 * A custom textarea component which allows autocomplete functionality.
 *
 * Example usage:
 * <gr-autogrow-textarea></gr-autogrow-textarea>
 */
@customElement('gr-autogrow-textarea')
export class GrAutogrowTextarea
  extends LitElement
  implements GrAutogrowTextareaApi
{
  // editableTextAreaElement is available right away where it may be undefined. This
  // is used for calls for scrollTop as if it is undefined then we can fallback
  // to 0. For other usecases use editableTextArea.
  @query('.editableTextArea')
  private readonly editableTextAreaElement?: HTMLTextAreaElement;

  @queryAsync('.editableTextArea')
  private readonly editableTextArea?: Promise<HTMLTextAreaElement>;

  @property({type: Boolean, reflect: true}) disabled = false;

  @property({type: String, reflect: true}) placeholder: string | undefined;

  @property({type: String}) value: string = '';

  @property({type: Number}) rows = 1;

  @property({type: Number}) maxRows = 0;

  /**
   * Sets cursor at the end of content on focus.
   */
  @property({type: Boolean}) putCursorAtEndOnFocus = false;

  @property({type: String}) autocomplete = 'off';

  @property({type: String}) inputmode?: string;

  @property({type: String}) readonly?: string;

  @property({type: Boolean}) required = false;

  @property({type: Number}) minlength?: number;

  @property({type: Number}) maxlength?: number;

  @property({type: String}) label?: string;

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

  private focused = false;

  @state() private tokens: string[] = [];

  static override get styles() {
    return [
      css`
        :host {
          display: inline-block;
          position: relative;
          width: 400px;
          border: var(--gr-autogrow-textarea-border-width, 2px) solid
            var(--gr-textarea-border-color, white);
          border-radius: 4px;
          padding: 2px;
          -moz-appearance: textarea;
          -webkit-appearance: textarea;
          appearance: textarea;
          overflow: hidden;
        }
        :host([disabled]) {
          textarea {
            background-color: var(--gr-autogrow-textarea-disabled-color);
            color: var(--text-disabled, black);
            cursor: default;
          }
        }
        .mirror-text {
          visibility: hidden;
          word-wrap: break-word;
          padding: var(--gr-autogrow-textarea-padding);
          box-sizing: var(--gr-autogrow-textarea-box-sizing);
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
          padding: var(--gr-autogrow-textarea-padding);
          box-sizing: var(--gr-autogrow-textarea-box-sizing);
        }
        textarea::placeholder {
          color: var(--deemphasized-text-color);
        }
        textarea:focus-visible {
          border-color: var(--gr-textarea-focus-outline-color, black);
          outline: none;
        }
      `,
    ];
  }

  override render() {
    return html` <div id="mirror" class="mirror-text" aria-hidden="true">
        ${this.tokens.length === 1 && this.tokens[0] === ''
          ? html`&#160;`
          : this.tokens.map((t, i) =>
              i === this.tokens.length - 1 ? html`${t}&#160;` : html`${t}<br />`
            )}
      </div>
      <div class="textarea-container">
        <textarea
          class="editableTextArea"
          .value=${this.value}
          aria-label=${ifDefined(this.label)}
          aria-disabled=${this.disabled}
          aria-multiline="true"
          aria-placeholder=${ifDefined(this.placeholder)}
          autocomplete=${this.autocomplete}
          ?autofocus=${this.autofocus}
          autocapitalize=${this.autocapitalize}
          inputmode=${ifDefined(this.inputmode)}
          placeholder=${ifDefined(this.placeholder)}
          ?disabled=${this.disabled}
          rows=${this.rows}
          minlength=${ifDefined(this.minlength)}
          maxlength=${ifDefined(this.maxlength)}
          spellcheck=${this.spellcheck}
          @input=${this.onInput}
          @focus=${this.onFocus}
          @blur=${this.onBlur}
          @keyup=${this.handleKeyUp}
          @mouseup=${this.handleMouseUp}
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

    await animationFrame();

    this.setCursorPosition(pre.length + replacement.length);
  }

  private onInput(event: Event) {
    event.preventDefault();
    event.stopImmediatePropagation();

    const target = event.target as HTMLTextAreaElement;
    this.value = target.value;

    this.fire('input', {value: this.value});
  }

  private onFocus() {
    this.focused = true;
    this.onCursorPositionChange();
  }

  private onBlur() {
    this.focused = false;
    this.onCursorPositionChange();
  }

  private handleKeyUp() {
    this.onCursorPositionChange();
  }

  private handleMouseUp() {
    this.onCursorPositionChange();
  }

  private fire<T>(type: string, detail?: T) {
    this.dispatchEvent(
      new CustomEvent(type, {detail, bubbles: true, composed: true})
    );
  }

  private onCursorPositionChange() {
    const cursorPosition = this.getCursorPosition();
    this.fire('cursorPositionChange', {position: cursorPosition});
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
    'gr-autogrow-textarea': GrAutogrowTextarea;
  }
  interface HTMLElementEventMap {
    // prettier-ignore
    'cursorPositionChange': CustomEvent<CursorPositionChangeEventDetail>;
  }
}
