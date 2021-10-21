/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import '../gr-cursor-manager/gr-cursor-manager';
import '../gr-overlay/gr-overlay';
import '@polymer/iron-a11y-keys-behavior/iron-a11y-keys-behavior';
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../../styles/shared-styles';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-textarea_html';
import {appContext} from '../../../services/app-context';
import {customElement, property} from '@polymer/decorators';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {
  GrAutocompleteDropdown,
  Item,
  ItemSelectedEvent,
} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {addShortcut, Key} from '../../../utils/dom-util';

const MAX_ITEMS_DROPDOWN = 10;

const ALL_SUGGESTIONS: EmojiSuggestion[] = [
  {value: '😊', match: 'smile :)'},
  {value: '👍', match: 'thumbs up'},
  {value: '😄', match: 'laugh :D'},
  {value: '❤️', match: 'heart <3'},
  {value: '😂', match: "tears :')"},
  {value: '🎉', match: 'party'},
  {value: '😎', match: 'cool |;)'},
  {value: '😞', match: 'sad :('},
  {value: '😐', match: 'neutral :|'},
  {value: '😮', match: 'shock :O'},
  {value: '🙏', match: 'pray'},
  {value: '😕', match: 'confused'},
  {value: '👌', match: 'ok'},
  {value: '🔥', match: 'fire'},
  {value: '💯', match: '100'},
  {value: '✔', match: 'check'},
  {value: '😋', match: 'tongue'},
  {value: '😭', match: "crying :'("},
  {value: '🤓', match: 'glasses'},
  {value: '😢', match: 'tear'},
  {value: '😜', match: 'winking tongue ;)'},
];

interface EmojiSuggestion extends Item {
  match: string;
}

interface ValueChangeEvent {
  value: string;
}

export interface GrTextarea {
  $: {
    textarea: IronAutogrowTextareaElement;
    emojiSuggestions: GrAutocompleteDropdown;
    caratSpan: HTMLSpanElement;
    hiddenText: HTMLDivElement;
  };
}

declare global {
  interface HTMLElementEventMap {
    'item-selected': CustomEvent<ItemSelectedEvent>;
    'bind-value-changed': CustomEvent<ValueChangeEvent>;
  }
}

@customElement('gr-textarea')
export class GrTextarea extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * @event bind-value-changed
   */
  @property({type: String})
  autocomplete?: string;

  @property({type: Boolean})
  disabled?: boolean;

  @property({type: Number})
  rows?: number;

  @property({type: Number})
  maxRows?: number;

  @property({type: String})
  placeholder?: string;

  @property({type: String, notify: true, observer: '_handleTextChanged'})
  text = '';

  @property({type: Boolean})
  hideBorder = false;

  /** Text input should be rendered in monospace font.  */
  @property({type: Boolean})
  monospace = false;

  /** Text input should be rendered in code font, which is smaller than the
    standard monospace font. */
  @property({type: Boolean})
  code = false;

  @property({type: Number})
  _colonIndex: number | null = null;

  @property({type: String, observer: '_determineSuggestions'})
  _currentSearchString?: string;

  @property({type: Boolean})
  _hideEmojiAutocomplete = true;

  @property({type: Number})
  _index: number | null = null;

  @property({type: Array})
  _suggestions: EmojiSuggestion[] = [];

  @property({type: Number})
  readonly _verticalOffset = 20;
  // Offset makes dropdown appear below text.

  reporting: ReportingService;

  disableEnterKeyForSelectingEmoji = false;

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
  }

  override connectedCallback() {
    super.connectedCallback();
    this.cleanups.push(
      addShortcut(this, {key: Key.UP}, e => this._handleUpKey(e))
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.DOWN}, e => this._handleDownKey(e))
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.TAB}, e => this._handleTabKey(e))
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.ENTER}, e => this._handleEnterByKey(e))
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.ESC}, e => this._handleEscKey(e))
    );
  }

  override ready() {
    super.ready();
    if (this.monospace) {
      this.classList.add('monospace');
    }
    if (this.code) {
      this.classList.add('code');
    }
    if (this.hideBorder) {
      this.$.textarea.classList.add('noBorder');
    }
  }

  closeDropdown() {
    return this.$.emojiSuggestions.close();
  }

  getNativeTextarea() {
    return this.$.textarea.textarea;
  }

  putCursorAtEnd() {
    const textarea = this.getNativeTextarea();
    // Put the cursor at the end always.
    textarea.selectionStart = textarea.value.length;
    textarea.selectionEnd = textarea.selectionStart;
    setTimeout(() => {
      textarea.focus();
    });
  }

  _handleEscKey(e: KeyboardEvent) {
    if (this._hideEmojiAutocomplete) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this._resetEmojiDropdown();
  }

  _handleUpKey(e: KeyboardEvent) {
    if (this._hideEmojiAutocomplete) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.$.emojiSuggestions.cursorUp();
    this.$.textarea.textarea.focus();
    this.disableEnterKeyForSelectingEmoji = false;
  }

  _handleDownKey(e: KeyboardEvent) {
    if (this._hideEmojiAutocomplete) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.$.emojiSuggestions.cursorDown();
    this.$.textarea.textarea.focus();
    this.disableEnterKeyForSelectingEmoji = false;
  }

  _handleTabKey(e: KeyboardEvent) {
    // Tab should have normal behavior if the picker is closed or if the user
    // has only typed ':'.
    if (this._hideEmojiAutocomplete || this.disableEnterKeyForSelectingEmoji) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this._setEmoji(this.$.emojiSuggestions.getCurrentText());
  }

  _handleEnterByKey(e: KeyboardEvent) {
    // Enter should have newline behavior if the picker is closed or if the user
    // has only typed ':'. Also make sure that shortcuts aren't clobbered.
    if (this._hideEmojiAutocomplete || this.disableEnterKeyForSelectingEmoji) {
      this.indent(e);
      return;
    }

    e.preventDefault();
    e.stopPropagation();
    this._setEmoji(this.$.emojiSuggestions.getCurrentText());
  }

  _handleEmojiSelect(e: CustomEvent<ItemSelectedEvent>) {
    if (e.detail.selected?.dataset['value']) {
      this._setEmoji(e.detail.selected?.dataset['value']);
    }
  }

  _setEmoji(text: string) {
    if (this._colonIndex === null) {
      return;
    }
    const colonIndex = this._colonIndex;
    this.text = this._getText(text);
    this.$.textarea.selectionStart = colonIndex + 1;
    this.$.textarea.selectionEnd = colonIndex + 1;
    this.reporting.reportInteraction('select-emoji', {type: text});
    this._resetEmojiDropdown();
  }

  _getText(value: string) {
    if (!this.text) return '';
    return (
      this.text.substr(0, this._colonIndex || 0) +
      value +
      this.text.substr(this.$.textarea.selectionStart)
    );
  }

  /**
   * Uses a hidden element with the same width and styling of the textarea and
   * the text up until the point of interest. Then caratSpan element is added
   * to the end and is set to be the positionTarget for the dropdown. Together
   * this allows the dropdown to appear near where the user is typing.
   */
  _updateCaratPosition() {
    this._hideEmojiAutocomplete = false;
    if (typeof this.$.textarea.value === 'string') {
      this.$.hiddenText.textContent = this.$.textarea.value.substr(
        0,
        this.$.textarea.selectionStart
      );
    }

    const caratSpan = this.$.caratSpan;
    this.$.hiddenText.appendChild(caratSpan);
    this.$.emojiSuggestions.positionTarget = caratSpan;
    this._openEmojiDropdown();
  }

  /**
   * _handleKeydown used for key handling in the this.$.textarea AND all child
   * autocomplete options.
   */
  _onValueChanged(e: CustomEvent<ValueChangeEvent>) {
    // Relay the event.
    this.dispatchEvent(
      new CustomEvent('bind-value-changed', {
        detail: e,
        composed: true,
        bubbles: true,
      })
    );

    // If cursor is not in textarea (just opened with colon as last char),
    // Don't do anything.
    if (
      e.currentTarget === null ||
      !(e.currentTarget as IronAutogrowTextareaElement).focused
    ) {
      return;
    }

    const charAtCursor =
      e.detail && e.detail.value
        ? e.detail.value[this.$.textarea.selectionStart - 1]
        : '';
    if (charAtCursor !== ':' && this._colonIndex === null) {
      return;
    }

    // When a colon is detected, set a colon index. We are interested only on
    // colons after space or in beginning of textarea
    if (charAtCursor === ':') {
      if (
        this.$.textarea.selectionStart < 2 ||
        e.detail.value[this.$.textarea.selectionStart - 2] === ' '
      ) {
        this._colonIndex = this.$.textarea.selectionStart - 1;
      }
    }
    if (this._colonIndex === null) {
      return;
    }

    this._currentSearchString = e.detail.value.substr(
      this._colonIndex + 1,
      this.$.textarea.selectionStart - this._colonIndex - 1
    );
    // Under the following conditions, close and reset the dropdown:
    // - The cursor is no longer at the end of the current search string
    // - The search string is an space or new line
    // - The colon has been removed
    // - There are no suggestions that match the search string
    if (
      this.$.textarea.selectionStart !==
        this._currentSearchString.length + this._colonIndex + 1 ||
      this._currentSearchString === ' ' ||
      this._currentSearchString === '\n' ||
      !(e.detail.value[this._colonIndex] === ':') ||
      !this._suggestions ||
      !this._suggestions.length
    ) {
      this._resetEmojiDropdown();
      // Otherwise open the dropdown and set the position to be just below the
      // cursor.
    } else if (this.$.emojiSuggestions.isHidden) {
      this._updateCaratPosition();
    }
    this.$.textarea.textarea.focus();
  }

  _openEmojiDropdown() {
    this.$.emojiSuggestions.open();
    this.reporting.reportInteraction('open-emoji-dropdown');
  }

  _formatSuggestions(matchedSuggestions: EmojiSuggestion[]) {
    const suggestions = [];
    for (const suggestion of matchedSuggestions) {
      suggestion.dataValue = suggestion.value;
      suggestion.text = `${suggestion.value} ${suggestion.match}`;
      suggestions.push(suggestion);
    }
    this.set('_suggestions', suggestions);
  }

  _determineSuggestions(emojiText: string) {
    if (!emojiText.length) {
      this._formatSuggestions(ALL_SUGGESTIONS);
      this.disableEnterKeyForSelectingEmoji = true;
    } else {
      const matches = ALL_SUGGESTIONS.filter(suggestion =>
        suggestion.match.includes(emojiText)
      ).slice(0, MAX_ITEMS_DROPDOWN);
      this._formatSuggestions(matches);
      this.disableEnterKeyForSelectingEmoji = false;
    }
  }

  _resetEmojiDropdown() {
    // hide and reset the autocomplete dropdown.
    flush();
    this._currentSearchString = '';
    this._hideEmojiAutocomplete = true;
    this.closeDropdown();
    this._colonIndex = null;
    this.$.textarea.textarea.focus();
  }

  _handleTextChanged(text: string) {
    this.dispatchEvent(
      new CustomEvent('value-changed', {detail: {value: text}})
    );
  }

  private indent(e: KeyboardEvent): void {
    if (!document.queryCommandSupported('insertText')) {
      return;
    }
    // When nothing is selected, selectionStart is the caret position. We want
    // the indentation level of the current line, not the end of the text which
    // may be different.
    const currentLine = this.$.textarea.textarea.value
      .substr(0, this.$.textarea.selectionStart)
      .split('\n')
      .pop();
    const currentLineIndentation = currentLine?.match(/^\s*/)?.[0];
    if (!currentLineIndentation) {
      return;
    }

    // Stops the normal newline being added afterwards since we are adding it
    // ourselves.
    e.preventDefault();

    // MDN says that execCommand is deprecated, but the replacements are still
    // WIP (Input Events Level 2). The queryCommandSupported check should ensure
    // that entering newlines will work even if this indent feature breaks.
    // Directly replacing the text is possible, but would destroy the undo/redo
    // queue.
    document.execCommand('insertText', false, '\n' + currentLineIndentation);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-textarea': GrTextarea;
  }
}
