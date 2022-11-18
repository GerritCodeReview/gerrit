/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import '../gr-cursor-manager/gr-cursor-manager';
import '../gr-overlay/gr-overlay';
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../../styles/shared-styles';
import {getAppContext} from '../../../services/app-context';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {
  GrAutocompleteDropdown,
  Item,
  ItemSelectedEvent,
} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {Key} from '../../../utils/dom-util';
import {ValueChangedEvent} from '../../../types/events';
import {fire} from '../../../utils/event-util';
import {LitElement, css, html, nothing} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {PropertyValues} from 'lit';
import {classMap} from 'lit/directives/class-map.js';
import {KnownExperimentId} from '../../../services/flags/flags';
import {NumericChangeId, ServerInfo} from '../../../api/rest-api';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {assert} from '../../../utils/common-util';
import {ShortcutController} from '../../lit/shortcut-controller';
import {getAccountDisplayName} from '../../../utils/display-name-util';
import {configModelToken} from '../../../models/config/config-model';

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
    'item-selected': CustomEvent<ItemSelectedEvent>;
  }
}

@customElement('gr-textarea')
export class GrTextarea extends LitElement {
  /**
   * @event bind-value-changed
   */
  @query('#textarea') textarea?: IronAutogrowTextareaElement;

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

  @state() suggestions: (Item | EmojiSuggestion)[] = [];

  // Accessed in tests.
  readonly reporting = getAppContext().reportingService;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly flagsService = getAppContext().flagsService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getConfigModel = resolve(this, configModelToken);

  private serverConfig?: ServerInfo;

  private changeNum?: NumericChangeId;

  // private but used in tests
  specialCharIndex = -1;

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

  static override styles = [
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
      }
      #hiddenText #emojiSuggestions {
        visibility: visible;
        white-space: normal;
      }
      iron-autogrow-textarea {
        position: relative;
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

  override render() {
    return html`
      <div id="hiddenText"></div>
      <!-- When the autocomplete is open, the span is moved at the end of
      hiddenText in order to correctly position the dropdown. After being moved,
      it is set as the positionTarget for the emojiSuggestions dropdown. -->
      <span id="caratSpan"></span>
      ${this.renderEmojiDropdown()}
      ${this.renderMentionsDropdown()}
      </gr-autocomplete-dropdown>
      <iron-autogrow-textarea
        id="textarea"
        class=${classMap({noBorder: this.hideBorder})}
        .autocomplete=${this.autocomplete}
        .placeholder=${this.placeholder}
        ?disabled=${this.disabled}
        .rows=${this.rows}
        .maxRows=${this.maxRows}
        .value=${this.text}
        @value-changed=${(e: ValueChangedEvent) => {
          this.text = e.detail.value;
        }}
      ></iron-autogrow-textarea>
    `;
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
    if (!this.flagsService.isEnabled(KnownExperimentId.MENTION_USERS))
      return nothing;
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
      // Add to updated because we want this.textarea.selectionStart and
      // this.textarea is null in the willUpdate lifecycle
      this.computeSpecialCharIndex();
      this.computeCurrentSearchString();
      this.handleTextChanged();
    }
  }

  // private but used in test
  closeDropdown() {
    this.mentionsSuggestions?.close();
    this.emojiSuggestions?.close();
  }

  getNativeTextarea() {
    return this.textarea!.textarea;
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
    if (!this.isDropdownVisible()) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.resetDropdown();
  }

  private handleUpKey(e: KeyboardEvent) {
    if (!this.isDropdownVisible()) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.getVisibleDropdown().cursorUp();
    this.textarea!.textarea.focus();
  }

  private handleDownKey(e: KeyboardEvent) {
    if (!this.isDropdownVisible()) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.getVisibleDropdown().cursorDown();
    this.textarea!.textarea.focus();
  }

  private handleTabKey(e: KeyboardEvent) {
    // Tab should have normal behavior if the picker is closed or if the user
    // has only typed ':'.
    if (
      !this.isDropdownVisible() ||
      (this.isEmojiDropdownActive() && this.currentSearchString === '')
    ) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.setValue(this.getVisibleDropdown().getCurrentText());
  }

  // private but used in test
  handleEnterByKey(e: KeyboardEvent) {
    // Enter should have newline behavior if the picker is closed or if the user
    // has only typed ':'. Also make sure that shortcuts aren't clobbered.
    if (
      !this.isDropdownVisible() ||
      (this.isEmojiDropdownActive() && this.currentSearchString === '')
    ) {
      this.indent(e);
      return;
    }

    e.preventDefault();
    e.stopPropagation();
    this.setValue(this.getVisibleDropdown().getCurrentText());
  }

  // private but used in test
  handleDropdownItemSelect(e: CustomEvent<ItemSelectedEvent>) {
    if (e.detail.selected?.dataset['value']) {
      this.setValue(e.detail.selected?.dataset['value']);
    }
  }

  private async setValue(text: string) {
    if (this.specialCharIndex === -1) {
      return;
    }
    const specialCharIndex = this.specialCharIndex;
    if (this.isEmojiDropdownActive()) {
      this.text = this.addValueToText(text);
      this.reporting.reportInteraction('select-emoji', {type: text});
    } else {
      this.text = this.addValueToText('@' + text);
      this.reporting.reportInteraction('select-mention', {type: text});
    }
    // iron-autogrow-textarea unfortunately sets the cursor at the end when
    // it's value is changed, which means the setting of selectionStart
    // below needs to happen after iron-autogrow-textarea has set the
    // incorrect value.
    await this.updateComplete;
    this.textarea!.selectionStart = specialCharIndex + 1;
    this.textarea!.selectionEnd = specialCharIndex + 1;
    this.resetDropdown();
  }

  private addValueToText(value: string) {
    if (!this.text) return '';
    return (
      this.text.substr(0, this.specialCharIndex ?? 0) +
      value +
      this.text.substr(this.textarea!.selectionStart)
    );
  }

  /**
   * Uses a hidden element with the same width and styling of the textarea and
   * the text up until the point of interest. Then caratSpan element is added
   * to the end and is set to be the positionTarget for the dropdown. Together
   * this allows the dropdown to appear near where the user is typing.
   * private but used in test
   */
  updateCaratPosition() {
    if (typeof this.textarea!.value === 'string') {
      this.hiddenText!.textContent = this.textarea!.value.substr(
        0,
        this.textarea!.selectionStart
      );
    }

    const caratSpan = this.caratSpan!;
    this.hiddenText!.appendChild(caratSpan);
    return caratSpan;
  }

  private shouldResetDropdown(
    text: string,
    charIndex: number,
    suggestions?: Item[],
    char?: string
  ) {
    // Under any of the following conditions, close and reset the dropdown:
    // - The cursor is no longer at the end of the current search string
    // - The search string is an space or new line
    // - The colon has been removed
    // - There are no suggestions that match the search string
    return (
      this.textarea!.selectionStart !==
        (this.currentSearchString ?? '').length + charIndex + 1 ||
      this.currentSearchString === ' ' ||
      this.currentSearchString === '\n' ||
      !(text[charIndex] === char) ||
      !suggestions ||
      !suggestions.length
    );
  }

  // When special char is detected, set index. We are interested only on
  // special char after space or in beginning of textarea
  // In case of mentions we are interested if previous char is '\n' as well
  private getSpecialCharIndex(text: string) {
    const charAtCursor = text[this.textarea!.selectionStart - 1];
    if (
      this.textarea!.selectionStart < 2 ||
      text[this.textarea!.selectionStart - 2] === ' '
    ) {
      return this.textarea!.selectionStart - 1;
    }
    if (
      charAtCursor === '@' &&
      text[this.textarea!.selectionStart - 2] === '\n'
    ) {
      return this.textarea!.selectionStart - 1;
    }
    return -1;
  }

  private async computeSuggestions() {
    if (this.currentSearchString === undefined) {
      this.suggestions = [];
      return;
    }
    if (this.isEmojiDropdownActive()) {
      this.computeEmojiSuggestions(this.currentSearchString);
    } else if (this.isMentionsDropdownActive()) {
      await this.computeReviewerSuggestions();
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
        this.suggestions,
        this.text[this.specialCharIndex]
      )
    ) {
      this.resetDropdown();
    } else if (activeDropdown!.isHidden && this.textarea!.focused) {
      // Otherwise open the dropdown and set the position to be just below the
      // cursor.
      // Do not open dropdown if textarea is not focused
      activeDropdown.setPositionTarget(this.updateCaratPosition());
      activate();
    }
  }

  private isMentionsDropdownActive() {
    if (!this.flagsService.isEnabled(KnownExperimentId.MENTION_USERS))
      return false;
    return (
      this.specialCharIndex !== -1 && this.text[this.specialCharIndex] === '@'
    );
  }

  private isEmojiDropdownActive() {
    return (
      this.specialCharIndex !== -1 && this.text[this.specialCharIndex] === ':'
    );
  }

  private computeSpecialCharIndex() {
    const charAtCursor = this.text[this.textarea!.selectionStart - 1];

    if (this.flagsService.isEnabled(KnownExperimentId.MENTION_USERS)) {
      if (charAtCursor === '@' && this.specialCharIndex === -1) {
        this.specialCharIndex = this.getSpecialCharIndex(this.text);
      }
    }
    if (charAtCursor === ':' && this.specialCharIndex === -1) {
      this.specialCharIndex = this.getSpecialCharIndex(this.text);
    }
  }

  private computeCurrentSearchString() {
    if (this.specialCharIndex === -1) {
      this.currentSearchString = undefined;
      return;
    }
    this.currentSearchString = this.text.substr(
      this.specialCharIndex + 1,
      this.textarea!.selectionStart - this.specialCharIndex - 1
    );
  }

  // Private but used in tests.
  async handleTextChanged() {
    await this.computeSuggestions();
    this.openOrResetDropdown();
    this.textarea!.textarea.focus();
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
  formatSuggestions(matchedSuggestions: EmojiSuggestion[]) {
    const suggestions = [];
    for (const suggestion of matchedSuggestions) {
      assert(isEmojiSuggestion(suggestion), 'malformed suggestion');
      suggestion.dataValue = suggestion.value;
      suggestion.text = `${suggestion.value} ${suggestion.match}`;
      suggestions.push(suggestion);
    }
    this.suggestions = suggestions;
  }

  // private but used in test
  computeEmojiSuggestions(suggestionsText?: string) {
    if (suggestionsText === undefined) {
      this.suggestions = [];
      return;
    }
    if (!suggestionsText.length) {
      this.formatSuggestions(ALL_SUGGESTIONS);
    } else {
      const matches = ALL_SUGGESTIONS.filter(suggestion =>
        suggestion.match.includes(suggestionsText)
      ).slice(0, MAX_ITEMS_DROPDOWN);
      this.formatSuggestions(matches);
    }
  }

  // TODO(dhruvsri): merge with getAccountSuggestions in account-util
  async computeReviewerSuggestions() {
    this.suggestions = (
      (await this.restApiService.getSuggestedAccounts(
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
    this.textarea?.textarea.focus();
  }

  private fireChangedEvents() {
    // This is a bit redundant, because the `text` property has `notify:true`,
    // so whenever the `text` changes the component fires two identical events
    // `text-changed` and `value-changed`.
    fire(this, 'value-changed', {value: this.text});
    fire(this, 'text-changed', {value: this.text});
    // Relay the event.
    fire(this, 'bind-value-changed', {value: this.text});
  }

  private indent(e: KeyboardEvent): void {
    if (!document.queryCommandSupported('insertText')) {
      return;
    }
    // When nothing is selected, selectionStart is the caret position. We want
    // the indentation level of the current line, not the end of the text which
    // may be different.
    const currentLine = this.textarea!.textarea.value.substr(
      0,
      this.textarea!.selectionStart
    )
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
