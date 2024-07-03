/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import '../gr-cursor-manager/gr-cursor-manager';
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../../styles/shared-styles';
import '../../../embed/gr-textarea';
import {getAppContext} from '../../../services/app-context';
import {
  GrAutocompleteDropdown,
  Item,
  ItemSelectedEventDetail,
} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {Key} from '../../../utils/dom-util';
import {fire} from '../../../utils/event-util';
import {LitElement, css, html} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {PropertyValues} from 'lit';
import {classMap} from 'lit/directives/class-map.js';
import {NumericChangeId, ServerInfo} from '../../../api/rest-api';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {assert} from '../../../utils/common-util';
import {ShortcutController} from '../../lit/shortcut-controller';
import {getAccountDisplayName} from '../../../utils/display-name-util';
import {configModelToken} from '../../../models/config/config-model';
import {formStyles} from '../../../styles/form-styles';
import {GrTextarea} from '../../../embed/gr-textarea';

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

export interface EmojiSuggestion extends Item {
  match: string;
}

function isEmojiSuggestion(x: EmojiSuggestion | Item): x is EmojiSuggestion {
  return !!x && !!(x as EmojiSuggestion).match;
}

declare global {
  interface HTMLElementEventMap {
    'item-selected': CustomEvent<ItemSelectedEventDetail>;
  }
}

@customElement('gr-suggestion-textarea')
export class GrSuggestionTextarea extends LitElement {
  /**
   * @event bind-value-changed
   */
  @query('#textarea') textarea?: GrTextarea;

  @query('#emojiSuggestions') emojiSuggestions?: GrAutocompleteDropdown;

  @query('#mentionsSuggestions') mentionsSuggestions?: GrAutocompleteDropdown;

  @query('#caratSpan', true) caratSpan?: HTMLSpanElement;

  @query('#hiddenText') hiddenText?: HTMLDivElement;

  @property() autocomplete?: string;

  @property({type: Boolean}) disabled?: boolean;

  @property({type: Number}) rows?: number;

  @property({type: Number}) maxRows?: number;

  @property({type: String}) placeholder?: string;

  @property({type: String}) text = '';

  @property({type: Boolean, attribute: 'hide-border'}) hideBorder = false;

  /** Text input should be rendered in monospace font.  */
  @property({type: Boolean}) monospace = false;

  /** Text input should be rendered in code font, which is smaller than the
    standard monospace font. */
  @property({type: Boolean}) code = false;

  /**
   * An autocompletion hint that is passed to <gr-textarea>, which will allow\
   * the user to accept it by pressing tab.
   */
  @property({type: String}) autocompleteHint = '';

  @state() suggestions: (Item | EmojiSuggestion)[] = [];

  // Accessed in tests.
  readonly reporting = getAppContext().reportingService;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly restApiService = getAppContext().restApiService;

  private readonly getConfigModel = resolve(this, configModelToken);

  private serverConfig?: ServerInfo;

  private changeNum?: NumericChangeId;

  // Represents the current location of the ':' or '@' that triggered a drop-down.
  // private but used in tests
  specialCharIndex = -1;

  // Represents the current search string being used to query either emoji or mention suggestions.
  // private but used in tests
  currentSearchString?: string;

  private readonly shortcuts = new ShortcutController(this);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().changeNum$,
      x => (this.changeNum = x)
    );
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        this.serverConfig = config;
      }
    );
    this.shortcuts.addLocal({key: Key.UP}, e => this.handleUpKey(e), {
      preventDefault: false,
    });
    this.shortcuts.addLocal({key: Key.DOWN}, e => this.handleDownKey(e), {
      preventDefault: false,
    });
    this.shortcuts.addLocal({key: Key.TAB}, e => this.handleTabKey(e), {
      preventDefault: false,
    });
    this.shortcuts.addLocal({key: Key.ENTER}, e => this.handleEnterByKey(e), {
      preventDefault: false,
    });
    this.shortcuts.addLocal({key: Key.ESC}, e => this.handleEscKey(e), {
      preventDefault: false,
    });
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
  }

  override connectedCallback() {
    super.connectedCallback();

    if (this.monospace) {
      this.classList.add('monospace');
    }
    if (this.code) {
      this.classList.add('code');
    }
  }

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      css`
        :host {
          display: flex;
          position: relative;
        }
        :host(.monospace) {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          font-weight: var(--font-weight-normal);
        }
        :host(.code) {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-code);
          /* usually 16px = 12px + 4px */
          line-height: calc(var(--font-size-code) + var(--spacing-s));
          font-weight: var(--font-weight-normal);
        }
        #emojiSuggestions {
          font-family: var(--font-family);
        }
        #textarea {
          background-color: var(--view-background-color);
          width: 100%;
          color: var(--primary-text-color);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          padding: 0;
          box-sizing: border-box;
          position: relative;
          --gr-textarea-padding: var(--spacing-s);
          --gr-textarea-border-width: 0px;
          --gr-textarea-border-color: var(--border-color);
          --input-field-bg: var(--view-background-color);
          --input-field-disabled-bg: var(--view-background-color);
          --secondary-bg-color: var(--background-color-secondary);
          --text-default: var(--primary-text-color);
          --text-disabled: var(--deemphasized-text-color);
          --text-secondary: var(--deemphasized-text-color);
          --iron-autogrow-textarea_-_padding: var(--spacing-s);
        }
        #hiddenText #emojiSuggestions {
          visibility: visible;
          white-space: normal;
        }
        #textarea.noBorder {
          border: none;
        }
        #hiddenText {
          display: block;
          float: left;
          position: absolute;
          visibility: hidden;
          width: 100%;
          white-space: pre-wrap;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div id="hiddenText"></div>
      <!-- When the autocomplete is open, the span is moved at the end of
      hiddenText in order to correctly position the dropdown. After being moved,
      it is set as the positionTarget for the emojiSuggestions dropdown. -->
      <span id="caratSpan"></span>
      ${this.renderEmojiDropdown()} ${this.renderMentionsDropdown()}
      ${this.renderTextarea()}
    `;
  }

  private renderTextarea() {
    return html`<gr-textarea
      id="textarea"
      putCursorAtEndOnFocus
      class=${classMap({noBorder: this.hideBorder})}
      .placeholder=${this.placeholder}
      ?disabled=${this.disabled}
      .value=${this.text}
      .hint=${this.autocompleteHint}
      @input=${(e: InputEvent) => {
        const value = (e.target as GrTextarea).value;
        this.text = value ?? '';
      }}
    ></gr-textarea>`;
  }

  private renderEmojiDropdown() {
    return html`
      <gr-autocomplete-dropdown
        id="emojiSuggestions"
        .suggestions=${this.suggestions}
        .horizontalOffset=${20}
        .verticalOffset=${20}
        @dropdown-closed=${this.resetDropdown}
        @item-selected=${this.handleDropdownItemSelect}
      >
      </gr-autocomplete-dropdown>
    `;
  }

  private renderMentionsDropdown() {
    return html` <gr-autocomplete-dropdown
      id="mentionsSuggestions"
      .suggestions=${this.suggestions}
      @dropdown-closed=${this.resetDropdown}
      @item-selected=${this.handleDropdownItemSelect}
      .horizontalOffset=${20}
      .verticalOffset=${20}
      role="listbox"
    ></gr-autocomplete-dropdown>`;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('text')) {
      this.fireChangedEvents();
      this.handleTextChanged();
    }
  }

  // private but used in test
  closeDropdown() {
    this.mentionsSuggestions?.close();
    this.emojiSuggestions?.close();
  }

  // Note that this may not work as intended, because the textarea is not
  // rendered yet.
  override focus() {
    this.textarea?.focus();
  }

  putCursorAtEnd() {
    this.textarea?.putCursorAtEnd();
  }

  private getVisibleDropdown() {
    if (this.emojiSuggestions && !this.emojiSuggestions.isHidden)
      return this.emojiSuggestions;
    if (this.mentionsSuggestions && !this.mentionsSuggestions.isHidden)
      return this.mentionsSuggestions;
    throw new Error('no dropdown visible');
  }

  private isDropdownVisible() {
    return (
      (this.emojiSuggestions && !this.emojiSuggestions.isHidden) ||
      (this.mentionsSuggestions && !this.mentionsSuggestions.isHidden)
    );
  }

  private handleEscKey(e: KeyboardEvent) {
    // Esc should have normal behavior if the picker is closed, or "open" but
    // with no results.
    if (
      !this.isDropdownVisible() ||
      this.getVisibleDropdown().getCurrentText() === ''
    ) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.resetDropdown();
  }

  private handleUpKey(e: KeyboardEvent) {
    // Up should have normal behavior if the picker is closed, or "open" but
    // with no results.
    if (
      !this.isDropdownVisible() ||
      this.getVisibleDropdown().getCurrentText() === ''
    ) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.getVisibleDropdown().cursorUp();
    this.focus();
  }

  private handleDownKey(e: KeyboardEvent) {
    // Down should have normal behavior if the picker is closed, or "open" but
    // with no results.
    if (
      !this.isDropdownVisible() ||
      this.getVisibleDropdown().getCurrentText() === ''
    ) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.getVisibleDropdown().cursorDown();
    this.focus();
  }

  private handleTabKey(e: KeyboardEvent) {
    // Tab should have normal behavior if the picker is closed, or "open" but
    // with no results.
    if (
      !this.isDropdownVisible() ||
      this.getVisibleDropdown().getCurrentText() === ''
    ) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.setValue(this.getVisibleDropdown().getCurrentText());
  }

  // private but used in test
  handleEnterByKey(e: KeyboardEvent) {
    // Enter should have newline behavior if the picker is closed. Also make
    // sure that shortcuts aren't clobbered.
    if (!this.isDropdownVisible()) {
      this.indent(e);
      return;
    }

    const selection = this.getVisibleDropdown().getCurrentText();
    if (selection === '') {
      // Nothing was selected, so treat this like a newline and reset the dropdown.
      this.indent(e);
      this.resetDropdown();
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.setValue(this.getVisibleDropdown().getCurrentText());
  }

  // private but used in test
  handleDropdownItemSelect(e: CustomEvent<ItemSelectedEventDetail>) {
    if (e.detail.selected?.dataset['value']) {
      this.setValue(e.detail.selected?.dataset['value']);
    }
  }

  private async setValue(text: string) {
    if (this.specialCharIndex === -1) {
      return;
    }
    const specialCharIndex = this.specialCharIndex;
    let move = 0;
    if (this.isEmojiDropdownActive()) {
      this.text = this.addValueToText(text);
      this.reporting.reportInteraction('select-emoji', {type: text});
    } else {
      this.text = this.addValueToText('@' + text);
      this.reporting.reportInteraction('select-mention', {type: text});
      move = 1;
    }
    // iron-autogrow-textarea unfortunately sets the cursor at the end when
    // it's value is changed, which means the setting of selectionStart
    // below needs to happen after iron-autogrow-textarea has set the
    // incorrect value.
    await this.updateComplete;
    this.setCursorPosition(specialCharIndex + text.length + move);
    this.resetDropdown();
  }

  setCursorPosition(pos: number) {
    this.textarea?.setCursorPosition(pos);
  }

  private addValueToText(value: string) {
    if (!this.text) return '';
    const specialCharIndex = this.specialCharIndex ?? 0;
    const beforeSearchString = this.text.substring(0, specialCharIndex);
    const afterSearchString = this.text.substring(
      specialCharIndex + 1 + (this.currentSearchString?.length ?? 0)
    );
    return beforeSearchString + value + afterSearchString;
  }

  /**
   * Uses a hidden element with the same width and styling of the textarea and
   * the text up until the point of interest. Then caratSpan element is added
   * to the end and is set to be the positionTarget for the dropdown. Together
   * this allows the dropdown to appear near where the user is typing.
   * private but used in test
   */
  updateCaratPosition() {
    let position = this.textarea?.getCursorPosition() ?? -1;
    if (position === -1) {
      position = this.text.length;
    }
    this.hiddenText!.textContent = this.text.substring(0, position);

    const caratSpan = this.caratSpan!;
    this.hiddenText!.appendChild(caratSpan);
    return caratSpan;
  }

  private shouldResetDropdown(text: string, charIndex: number, char?: string) {
    // Under any of the following conditions, close and reset the dropdown:
    // - The cursor is no longer at the end of the current search string
    // - The search string is an space or new line
    // - The colon has been removed
    // - There are no suggestions that match the search string
    const position = this.textarea?.getCursorPosition() ?? -1;
    return (
      position !== (this.currentSearchString ?? '').length + charIndex + 1 ||
      this.currentSearchString === ' ' ||
      this.currentSearchString === '\n' ||
      !(text[charIndex] === char)
    );
  }

  private async computeSuggestions() {
    this.suggestions = [];
    if (this.currentSearchString === undefined) {
      return;
    }
    const searchString = this.currentSearchString;
    let suggestions: (Item | EmojiSuggestion)[] = [];
    if (this.isEmojiDropdownActive()) {
      suggestions = this.computeEmojiSuggestions(this.currentSearchString);
    } else if (this.isMentionsDropdownActive()) {
      suggestions = await this.computeReviewerSuggestions();
    }
    if (searchString === this.currentSearchString) {
      this.suggestions = suggestions;
    }
  }

  private openOrResetDropdown() {
    let activeDropdown: GrAutocompleteDropdown;
    let activate: () => void;
    if (this.isEmojiDropdownActive()) {
      activeDropdown = this.emojiSuggestions!;
      activate = () => this.openEmojiDropdown();
    } else if (this.isMentionsDropdownActive()) {
      activeDropdown = this.mentionsSuggestions!;
      activate = () => this.openMentionsDropdown();
    } else {
      this.resetDropdown();
      return;
    }

    if (
      this.shouldResetDropdown(
        this.text,
        this.specialCharIndex,
        this.text[this.specialCharIndex]
      )
    ) {
      this.resetDropdown();
    } else if (activeDropdown!.isHidden && this.isTextareaFocused()) {
      // Otherwise open the dropdown and set the position to be just below the
      // cursor.
      // Do not open dropdown if textarea is not focused
      activeDropdown.setPositionTarget(this.updateCaratPosition());
      activate();
    }
  }

  private isMentionsDropdownActive() {
    return (
      this.specialCharIndex !== -1 && this.text[this.specialCharIndex] === '@'
    );
  }

  private isEmojiDropdownActive() {
    return (
      this.specialCharIndex !== -1 && this.text[this.specialCharIndex] === ':'
    );
  }

  public computeIndexAndSearchString() {
    let currentCarat = this.textarea?.getCursorPosition() ?? -1;
    if (currentCarat === -1) {
      currentCarat = this.text.length;
    }
    const m = this.text
      .substring(0, currentCarat)
      .match(/(?:^|\s)([:@][\S]*)$/);
    if (!m) {
      this.specialCharIndex = -1;
      this.currentSearchString = undefined;
      return;
    }
    this.currentSearchString = m[1].substring(1);
    if (this.specialCharIndex !== -1) return;

    this.specialCharIndex = currentCarat - m[1].length;
  }

  // Private but used in tests.
  async handleTextChanged() {
    this.computeIndexAndSearchString();
    await this.computeSuggestions();
    this.openOrResetDropdown();
    this.focus();
  }

  private openEmojiDropdown() {
    this.emojiSuggestions!.open();
    this.reporting.reportInteraction('open-emoji-dropdown');
  }

  private openMentionsDropdown() {
    this.mentionsSuggestions!.open();
    this.reporting.reportInteraction('open-mentions-dropdown');
  }

  // private but used in test
  formatSuggestions(matchedSuggestions: EmojiSuggestion[]): EmojiSuggestion[] {
    const suggestions = [];
    for (const suggestion of matchedSuggestions) {
      assert(isEmojiSuggestion(suggestion), 'malformed suggestion');
      suggestion.dataValue = suggestion.value;
      suggestion.text = `${suggestion.value} ${suggestion.match}`;
      suggestions.push(suggestion);
    }
    return suggestions;
  }

  // private but used in test
  computeEmojiSuggestions(suggestionsText?: string): EmojiSuggestion[] {
    if (suggestionsText === undefined) {
      return [];
    }
    if (!suggestionsText.length) {
      return this.formatSuggestions(ALL_SUGGESTIONS);
    } else {
      const matches = ALL_SUGGESTIONS.filter(suggestion =>
        suggestion.match.includes(suggestionsText)
      ).slice(0, MAX_ITEMS_DROPDOWN);
      return this.formatSuggestions(matches);
    }
  }

  // TODO(dhruvsri): merge with getAccountSuggestions in account-util
  async computeReviewerSuggestions(): Promise<Item[]> {
    return (
      (await this.restApiService.queryAccounts(
        this.currentSearchString ?? '',
        /* number= */ 15,
        this.changeNum,
        /* filterActive= */ true
      )) ?? []
    )
      .filter(account => account.email)
      .map(account => {
        return {
          text: `${getAccountDisplayName(this.serverConfig, account)}`,
          dataValue: account.email,
        };
      });
  }

  // private but used in test
  resetDropdown() {
    // hide and reset the autocomplete dropdown.
    this.requestUpdate();
    this.currentSearchString = '';
    this.closeDropdown();
    this.specialCharIndex = -1;
    this.focus();
  }

  private fireChangedEvents() {
    fire(this, 'text-changed', {value: this.text});
  }

  private indent(e: KeyboardEvent): void {
    if (!document.queryCommandSupported('insertText')) {
      return;
    }
    // When nothing is selected, selectionStart is the caret position. We want
    // the indentation level of the current line, not the end of the text which
    // may be different.
    const currentLine = this.text
      .substring(0, this.textarea?.getCursorPosition() ?? -1)
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

  isTextareaFocused() {
    return !!this.textarea?.isFocused;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-suggestion-textarea': GrSuggestionTextarea;
  }
}
