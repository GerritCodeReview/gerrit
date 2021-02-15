/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '@polymer/paper-input/paper-input';
import '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import '../gr-cursor-manager/gr-cursor-manager';
import '../gr-icons/gr-icons';
import '../../../styles/shared-styles';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-autocomplete_html';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {property, customElement, observe} from '@polymer/decorators';
import {GrAutocompleteDropdown} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {GrCursorManager} from '../gr-cursor-manager/gr-cursor-manager';
import {PaperInputElementExt} from '../../../types/types';
import {CustomKeyboardEvent} from '../../../types/events';
import {fireEvent} from '../../../utils/event-util';

const TOKENIZE_REGEX = /(?:[^\s"]+|"[^"]*")+/g;
const DEBOUNCE_WAIT_MS = 200;

export interface GrAutocomplete {
  $: {
    input: PaperInputElementExt;
    suggestions: GrAutocompleteDropdown;
    cursor: GrCursorManager;
  };
}

export type AutocompleteQuery = (
  text: string
) => Promise<AutocompleteSuggestion[]>;

declare global {
  interface HTMLElementTagNameMap {
    'gr-autocomplete': GrAutocomplete;
  }
}

export interface AutocompleteSuggestion {
  name?: string;
  label?: string;
  value?: string;
  text?: string;
}

export interface AutocompleteCommitEventDetail {
  value: string;
}

export type AutocompleteCommitEvent = CustomEvent<
  AutocompleteCommitEventDetail
>;

const DEBOUNCER_UPDATE_SUGGESTIONS = 'update-suggestions';

@customElement('gr-autocomplete')
export class GrAutocomplete extends KeyboardShortcutMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }
  /**
   * Fired when a value is chosen.
   *
   * @event commit
   */

  /**
   * Fired when the user cancels.
   *
   * @event cancel
   */

  /**
   * Fired on keydown to allow for custom hooks into autocomplete textbox
   * behavior.
   *
   * @event input-keydown
   */

  /**
   * Query for requesting autocomplete suggestions. The function should
   * accept the input as a string parameter and return a promise. The
   * promise yields an array of suggestion objects with "name", "label",
   * "value" properties. The "name" property will be displayed in the
   * suggestion entry. The "label" property will, when specified, appear
   * next to the "name" as label text. The "value" property will be emitted
   * if that suggestion is selected.
   *
   */
  @property({type: Object})
  query: AutocompleteQuery = () => Promise.resolve([]);

  /**
   * The number of characters that must be typed before suggestions are
   * made. If threshold is zero, default suggestions are enabled.
   */
  @property({type: Number})
  threshold = 1;

  @property({type: Boolean})
  allowNonSuggestedValues = false;

  @property({type: Boolean})
  borderless = false;

  @property({type: Boolean})
  disabled = false;

  @property({type: Boolean})
  showSearchIcon = false;

  /**
   * Vertical offset needed for an element with 20px line-height, 4px
   * padding and 1px border (30px height total). Plus 1px spacing between
   * input and dropdown. Inputs with different line-height or padding will
   * need to tweak vertical offset.
   */
  @property({type: Number})
  verticalOffset = 31;

  @property({type: String, notify: true})
  text = '';

  @property({type: String})
  placeholder = '';

  @property({type: Boolean})
  clearOnCommit = false;

  /**
   * When true, tab key autocompletes but does not fire the commit event.
   * When false, tab key not caught, and focus is removed from the element.
   * See Issue 4556, Issue 6645.
   */
  @property({type: Boolean})
  tabComplete = false;

  @property({type: String, notify: true})
  value = '';

  /**
   * Multi mode appends autocompleted entries to the value.
   * If false, autocompleted entries replace value.
   */
  @property({type: Boolean})
  multi = false;

  /**
   * When true and uncommitted text is left in the autocomplete input after
   * blurring, the text will appear red.
   */
  @property({type: Boolean})
  warnUncommitted = false;

  /**
   * When true, querying for suggestions is not debounced w/r/t keypresses
   */
  @property({type: Boolean})
  noDebounce = false;

  @property({type: Array})
  _suggestions: AutocompleteSuggestion[] = [];

  @property({type: Array})
  _suggestionEls = [];

  @property({type: Number})
  _index?: number;

  @property({type: Boolean})
  _disableSuggestions = false;

  @property({type: Boolean})
  _focused = false;

  /**
   * Invisible label for input element. This label is exposed to
   * screen readers by paper-input
   */
  @property({type: String})
  label = '';

  /** The DOM element of the selected suggestion. */
  @property({type: Object})
  _selected: HTMLElement | null = null;

  get _nativeInput() {
    // In Polymer 2 inputElement isn't nativeInput anymore
    return (this.$.input.$.nativeInput ||
      this.$.input.inputElement) as HTMLInputElement;
  }

  /** @override */
  attached() {
    super.attached();
    this.listen(document.body, 'click', '_handleBodyClick');
  }

  /** @override */
  detached() {
    super.detached();
    this.unlisten(document.body, 'click', '_handleBodyClick');
    this.cancelDebouncer(DEBOUNCER_UPDATE_SUGGESTIONS);
  }

  get focusStart() {
    return this.$.input;
  }

  focus() {
    this._nativeInput.focus();
  }

  selectAll() {
    const nativeInputElement = this._nativeInput;
    if (!this.$.input.value) {
      return;
    }
    nativeInputElement.setSelectionRange(0, this.$.input.value.length);
  }

  clear() {
    this.text = '';
  }

  _handleItemSelect(e: CustomEvent) {
    // Let _handleKeydown deal with keyboard interaction.
    if (e.detail.trigger !== 'click') {
      return;
    }
    this._selected = e.detail.selected;
    this._commit();
  }

  get _inputElement() {
    // Polymer2: this.$ can be undefined when this is first evaluated.
    return this.$ && this.$.input;
  }

  /**
   * Set the text of the input without triggering the suggestion dropdown.
   *
   * @param text The new text for the input.
   */
  setText(text: string) {
    this._disableSuggestions = true;
    this.text = text;
    this._disableSuggestions = false;
  }

  _onInputFocus() {
    this._focused = true;
    this._updateSuggestions(this.text, this.threshold, this.noDebounce);
    this.$.input.classList.remove('warnUncommitted');
    // Needed so that --paper-input-container-input updated style is applied.
    this.updateStyles();
  }

  _onInputBlur() {
    this.$.input.classList.toggle(
      'warnUncommitted',
      this.warnUncommitted && !!this.text.length && !this._focused
    );
    // Needed so that --paper-input-container-input updated style is applied.
    this.updateStyles();
  }

  @observe('text', 'threshold', 'noDebounce')
  _updateSuggestions(text?: string, threshold?: number, noDebounce?: boolean) {
    if (
      text === undefined ||
      threshold === undefined ||
      noDebounce === undefined
    )
      return;

    // Reset _suggestions for every update
    // This will also prevent from carrying over suggestions:
    // @see Issue 12039
    this._suggestions = [];

    // TODO(taoalpha): Also skip if text has not changed

    if (this._disableSuggestions) {
      return;
    }
    if (text.length < threshold) {
      this.value = '';
      return;
    }

    if (!this._focused) {
      return;
    }

    const update = () => {
      this.query(text).then(suggestions => {
        if (text !== this.text) {
          // Late response.
          return;
        }
        for (const suggestion of suggestions) {
          suggestion.text = suggestion.name;
        }
        this._suggestions = suggestions;
        flush();
        if (this._index === -1) {
          this.value = '';
        }
      });
    };

    if (noDebounce) {
      update();
    } else {
      this.debounce(DEBOUNCER_UPDATE_SUGGESTIONS, update, DEBOUNCE_WAIT_MS);
    }
  }

  @observe('_suggestions', '_focused')
  _maybeOpenDropdown(suggestions: AutocompleteSuggestion[], focused: boolean) {
    if (suggestions.length > 0 && focused) {
      return this.$.suggestions.open();
    }
    return this.$.suggestions.close();
  }

  _computeClass(borderless: boolean) {
    return borderless ? 'borderless' : '';
  }

  /**
   * _handleKeydown used for key handling in the this.$.input AND all child
   * autocomplete options.
   */
  _handleKeydown(e: CustomKeyboardEvent) {
    this._focused = true;
    switch (e.keyCode) {
      case 38: // Up
        e.preventDefault();
        this.$.suggestions.cursorUp();
        break;
      case 40: // Down
        e.preventDefault();
        this.$.suggestions.cursorDown();
        break;
      case 27: // Escape
        e.preventDefault();
        this._cancel();
        break;
      case 9: // Tab
        if (this._suggestions.length > 0 && this.tabComplete) {
          e.preventDefault();
          this._handleInputCommit(true);
          this.focus();
        } else {
          this._focused = false;
        }
        break;
      case 13: // Enter
        if (this.modifierPressed(e)) {
          break;
        }
        e.preventDefault();
        this._handleInputCommit();
        break;
      default:
        // For any normal keypress, return focus to the input to allow for
        // unbroken user input.
        this.focus();

        // Since this has been a normal keypress, the suggestions will have
        // been based on a previous input. Clear them. This prevents an
        // outdated suggestion from being used if the input keystroke is
        // immediately followed by a commit keystroke. @see Issue 8655
        this._suggestions = [];
    }
    this.dispatchEvent(
      new CustomEvent('input-keydown', {
        detail: {keyCode: e.keyCode, input: this.$.input},
        composed: true,
        bubbles: true,
      })
    );
  }

  _cancel() {
    if (this._suggestions.length) {
      this.set('_suggestions', []);
    } else {
      fireEvent(this, 'cancel');
    }
  }

  _handleInputCommit(_tabComplete?: boolean) {
    // Nothing to do if the dropdown is not open.
    if (!this.allowNonSuggestedValues && this.$.suggestions.isHidden) {
      return;
    }

    this._selected = this.$.suggestions.getCursorTarget();
    this._commit(_tabComplete);
  }

  _updateValue(
    suggestion: HTMLElement | null,
    suggestions: AutocompleteSuggestion[]
  ) {
    if (!suggestion) {
      return;
    }
    const index = Number(suggestion.dataset['index']!);
    if (isNaN(index)) return;
    const completed = suggestions[index].value;
    if (completed === undefined || completed === null) return;
    if (this.multi) {
      // Append the completed text to the end of the string.
      // Allow spaces within quoted terms.
      const tokens = this.text.match(TOKENIZE_REGEX);
      if (tokens?.length) {
        tokens[tokens.length - 1] = completed;
        this.value = tokens.join(' ');
      }
    } else {
      this.value = completed;
    }
  }

  _handleBodyClick(e: Event) {
    const eventPath = e.composedPath();
    if (!eventPath) return;
    for (let i = 0; i < eventPath.length; i++) {
      if (eventPath[i] === this) {
        return;
      }
    }
    this._focused = false;
  }

  /**
   * Commits the suggestion, optionally firing the commit event.
   *
   * @param silent Allows for silent committing of an
   * autocomplete suggestion in order to handle cases like tab-to-complete
   * without firing the commit event.
   */
  _commit(silent?: boolean) {
    // Allow values that are not in suggestion list iff suggestions are empty.
    if (this._suggestions.length > 0) {
      this._updateValue(this._selected, this._suggestions);
    } else {
      this.value = this.text || '';
    }

    const value = this.value;

    // Value and text are mirrors of each other in multi mode.
    if (this.multi) {
      this.setText(this.value);
    } else {
      if (!this.clearOnCommit && this._selected) {
        const dataSet = this._selected.dataset;
        // index property cannot be null for the data-set
        if (dataSet) {
          const index = Number(dataSet['index']!);
          if (isNaN(index)) return;
          this.setText(this._suggestions[index].name || '');
        }
      } else {
        this.clear();
      }
    }

    this._suggestions = [];
    if (!silent) {
      this.dispatchEvent(
        new CustomEvent('commit', {
          detail: {value} as AutocompleteCommitEventDetail,
          composed: true,
          bubbles: true,
        })
      );
    }
  }

  _computeShowSearchIconClass(showSearchIcon: boolean) {
    return showSearchIcon ? 'showSearchIcon' : '';
  }
}
