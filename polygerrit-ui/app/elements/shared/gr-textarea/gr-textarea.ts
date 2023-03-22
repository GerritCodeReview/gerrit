/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import '../gr-cursor-manager/gr-cursor-manager';
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../../styles/shared-styles';
import {getAppContext} from '../../../services/app-context';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {
  GrAutocompleteDropdown,
  Item,
  ItemSelectedEventDetail,
} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {Key} from '../../../utils/dom-util';
import {ValueChangedEvent} from '../../../types/events';
import { Observable } from 'rxjs';
import {fire} from '../../../utils/event-util';
import {LitElement, css, html} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {Subject} from 'rxjs';
import {sharedStyles} from '../../../styles/shared-styles';
import {PropertyValues} from 'lit';
import {classMap} from 'lit/directives/class-map.js';
import {NumericChangeId, ServerInfo} from '../../../api/rest-api';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {assert} from '../../../utils/common-util';
import {ShortcutController} from '../../lit/shortcut-controller';
import {debounceTime, map} from 'rxjs/operators';
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
    'item-selected': CustomEvent<ItemSelectedEventDetail>;
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

  @property() selectedCode?: string;

  @property({type: Boolean}) disabled?: boolean;

  @property({type: Number}) rows?: number;

  @property() observable?: Observable;


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

    this.observable = new Observable();

    const result = this.mlTrigger!.pipe(debounceTime(1500));
    result.subscribe(x => this.mlMagic());
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
        padding: 5px;
        float: left;
        position: absolute;
        visibility: visible;
        width: 100%;
        white-space: pre-wrap;
      }
    `,
  ];

  override render() {
    return html`
      <div id="hiddenText">
            <span id="smartcompose" style="margin: visibility: visible;">FOO BAR!</span>
            </div>
      <!-- When the autocomplete is open, the span is moved at the end of
      hiddenText in order to correctly position the dropdown. After being moved,
      it is set as the positionTarget for the emojiSuggestions dropdown. -->
      <span id="caratSpan" style="color: #949494;"></span>

      ${this.renderEmojiDropdown()} ${this.renderMentionsDropdown()}
      <iron-autogrow-textarea
        id="textarea"
        style="background: none;"
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
  //console.log(changedProperties);
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

  test(t : String) {
    console.log("test" + t);
    this.selectedCode = t;
  }

  override focus() {
    this.textarea?.textarea.focus();
  }

  putCursorAtEnd() {
    const textarea = this.getNativeTextarea();
    // Put the cursor at the end always.
    textarea.selectionStart = textarea.value.length;
    textarea.selectionEnd = textarea.selectionStart;
    textarea.focus();
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
    console.log('handleEscKey!');
    if (!this.isDropdownVisible()) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.resetDropdown();
  }

  private handleUpKey(e: KeyboardEvent) {
    console.log('handleUpKey!');
    if (!this.isDropdownVisible()) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.getVisibleDropdown().cursorUp();
    this.focus();
  }

  private handleDownKey(e: KeyboardEvent) {
    console.log('handleDownKey!');
    if (!this.isDropdownVisible()) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.getVisibleDropdown().cursorDown();
    this.focus();
  }

  private handleTabKey(e: KeyboardEvent) {
    console.log('handleTabKey!');
    e.preventDefault();
    e.stopPropagation();

    this.text = this.text + this.caratSpan!.innerHTML;
    this.caratSpan!.innerHTML = '';
  }

  // private but used in test
  handleEnterByKey(e: KeyboardEvent) {
    console.log('handleEnterByKey!');
    // Enter should have newline behavior if the picker is closed. Also make
    // sure that shortcuts aren't clobbered.
    if (!this.isDropdownVisible()) {
      this.indent(e);
      return;
    }

    e.preventDefault();
    e.stopPropagation();
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
      this.hiddenText!.innerHTML = '<span style="visibility: hidden;">' + this.textarea!.value + '</span>';
    }

    const caratSpan = this.caratSpan!;
    this.hiddenText!.appendChild(caratSpan);
    //console.log(caratSpan);
    //caratSpan.innerHTML = "foo!!!"
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

    if (charAtCursor === '@' && this.specialCharIndex === -1) {
      this.specialCharIndex = this.getSpecialCharIndex(this.text);
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
    this.updateCaratPosition();
    this.focus();





    this.caratSpan!.innerHTML = '';
    this.mlTrigger.next();
  }

  private mlTrigger = new Subject();

  async mlMagic() {
      // ML MAGIC !!!!!.
      // if%20(!keyedValues.containsKey(key))%20%7B%0A%20%20%20keyedValues.put(key%2C%20value)%3B%0A%7D%0A---%0AYou%20can
      console.log("selected code" + this.selectedCode!);
      const inputStr =`if (!keyedValues.containsKey(key)) {
         keyedValues.put(key, value);
      }
      ---
      You can`;
      const mlURL = 'https://llamas-endpoint-server-prod.corp.google.com/api/1675733561542?id=%2Fsax%2Fllmau%2Fulm_340b_q&temperature=0.1&input_string=' + encodeURIComponent(inputStr);
      console.log(mlURL);
      const response = await fetch(mlURL, {
        credentials: 'include',
        method: 'GET'
      }).then(res => res.json());
      const mlAuto = response.messages[0].text.split('\n')[0];
      console.log(mlAuto);
      this.caratSpan!.innerHTML = mlAuto;
      console.log(response);
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
    fire(this, 'text-changed', {value: this.text});
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
