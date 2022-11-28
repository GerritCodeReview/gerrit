/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/paper-input/paper-input';
import '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import '../gr-cursor-manager/gr-cursor-manager';
import '../gr-icon/gr-icon';
import '../../../styles/shared-styles';
import {GrAutocompleteDropdown} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {fire, fireEvent} from '../../../utils/event-util';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {PropertyType} from '../../../types/common';
import {modifierPressed} from '../../../utils/dom-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {ValueChangedEvent} from '../../../types/events';
import {PaperInputElement} from '@polymer/paper-input/paper-input';
import {IronInputElement} from '@polymer/iron-input';

const TOKENIZE_REGEX = /(?:[^\s"]+|"[^"]*")+/g;
const DEBOUNCE_WAIT_MS = 200;

export type AutocompleteQuery<T = string> = (
  text: string
) => Promise<Array<AutocompleteSuggestion<T>>>;

declare global {
  interface HTMLElementTagNameMap {
    'gr-autocomplete': GrAutocomplete;
  }
  interface HTMLElementEventMap {
    'text-changed': ValueChangedEvent<string>;
    'value-changed': ValueChangedEvent<string>;
  }
}

export interface AutocompleteSuggestion<T = string> {
  name?: string;
  label?: string;
  value?: T;
  text?: string;
}

export interface AutocompleteCommitEventDetail {
  value: string;
}

export type AutocompleteCommitEvent =
  CustomEvent<AutocompleteCommitEventDetail>;

@customElement('gr-autocomplete')
export class GrAutocomplete extends LitElement {
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
   * If query fails, the function should return rejected promise containing
   * an Error. The "message" property will be shown in a dropdown instead of
   * rendering suggestions.
   */
  @property({type: Object})
  query?: AutocompleteQuery = () => Promise.resolve([]);

  @query('#input') input?: PaperInputElement;

  @query('#suggestions') suggestionsDropdown?: GrAutocompleteDropdown;

  /**
   * The number of characters that must be typed before suggestions are
   * made. If threshold is zero, default suggestions are enabled.
   */
  @property({type: Number})
  threshold = 1;

  @property({type: Boolean, attribute: 'allow-non-suggested-values'})
  allowNonSuggestedValues = false;

  @property({type: Boolean})
  borderless = false;

  @property({type: Boolean})
  disabled = false;

  @property({type: Boolean, attribute: 'show-search-icon'})
  showSearchIcon = false;

  /**
   * Vertical offset needed for an element with 20px line-height, 4px
   * padding and 1px border (30px height total). Plus 1px spacing between
   * input and dropdown. Inputs with different line-height or padding will
   * need to tweak vertical offset.
   */
  @property({type: Number})
  verticalOffset = 31;

  @property({type: String})
  text = '';

  @property({type: String})
  placeholder = '';

  @property({type: Boolean, attribute: 'clear-on-commit'})
  clearOnCommit = false;

  /**
   * When true, tab key autocompletes but does not fire the commit event.
   * When false, tab key not caught, and focus is removed from the element.
   * See Issue 4556, Issue 6645.
   */
  @property({type: Boolean, attribute: 'tab-complete'})
  tabComplete = false;

  @property({type: String})
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
  @property({type: Boolean, attribute: 'warn-uncommitted'})
  warnUncommitted = false;

  /**
   * When true, querying for suggestions is not debounced w/r/t keypresses
   */
  @property({type: Boolean, attribute: 'no-debounce'})
  noDebounce = false;

  @property({type: Boolean, attribute: 'show-blue-focus-border'})
  showBlueFocusBorder = false;

  /**
   * Invisible label for input element. This label is exposed to
   * screen readers by paper-input
   */
  @property({type: String})
  label = '';

  @state() suggestions: AutocompleteSuggestion[] = [];

  @state() queryErrorMessage?: string;

  @state() index: number | null = null;

  // Enabled to suppress showing/updating suggestions when changing properties
  // that would normally trigger the update.
  disableDisplayingSuggestions = false;

  // private but used in tests
  focused = false;

  @state() selected: HTMLElement | null = null;

  private updateSuggestionsTask?: DelayedTask;

  get nativeInput() {
    return (this.input!.inputElement as IronInputElement)
      .inputElement as HTMLInputElement;
  }

  static override styles = [
    sharedStyles,
    css`
      .searchIcon {
        display: none;
      }
      .searchIcon.showSearchIcon {
        display: inline-block;
      }
      gr-icon {
        margin: 0 var(--spacing-xs);
      }
      paper-input.borderless {
        border: none;
        padding: 0;
      }
      paper-input {
        background-color: var(--view-background-color);
        color: var(--primary-text-color);
        border: 1px solid var(--prominent-border-color, var(--border-color));
        border-radius: var(--border-radius);
        padding: var(--spacing-s);
        --paper-input-container_-_padding: 0;
        --paper-input-container-input_-_font-size: var(--font-size-normal);
        --paper-input-container-input_-_line-height: var(--line-height-normal);
        /* This is a hack for not being able to set height:0 on the underline
            of a paper-input 2.2.3 element. All the underline fixes below only
            actually work in 3.x.x, so the height must be adjusted directly as
            a workaround until we are on Polymer 3. */
        height: var(--line-height-normal);
        --paper-input-container-underline-height: 0;
        --paper-input-container-underline-wrapper-height: 0;
        --paper-input-container-underline-focus-height: 0;
        --paper-input-container-underline-legacy-height: 0;
        --paper-input-container-underline_-_height: 0;
        --paper-input-container-underline_-_display: none;
        --paper-input-container-underline-focus_-_height: 0;
        --paper-input-container-underline-focus_-_display: none;
        --paper-input-container-underline-disabled_-_height: 0;
        --paper-input-container-underline-disabled_-_display: none;
        /* Hide label for input. The label is still visible for
           screen readers. Workaround found at:
           https://github.com/PolymerElements/paper-input/issues/478 */
        --paper-input-container-label_-_display: none;
      }
      paper-input.showBlueFocusBorder:focus {
        border: 2px solid var(--input-focus-border-color);
        /*
         * The goal is to have a thicker blue border when focused and a thinner
         * gray border when blurred. To avoid shifting neighboring elements
         * around when the border size changes, a negative margin is added to
         * compensate. box-sizing: border-box; will not work since there is
         * important padding to add around the content.
         */
        margin: -1px;
      }
      paper-input.warnUncommitted {
        --paper-input-container-input_-_color: var(--error-text-color);
        --paper-input-container-input_-_font-size: inherit;
      }
    `,
  ];

  override connectedCallback() {
    super.connectedCallback();
    document.addEventListener('click', this.handleBodyClick);
  }

  override disconnectedCallback() {
    document.removeEventListener('click', this.handleBodyClick);
    this.updateSuggestionsTask?.cancel();
    super.disconnectedCallback();
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (
      changedProperties.has('text') ||
      changedProperties.has('threshold') ||
      changedProperties.has('noDebounce')
    ) {
      this.updateSuggestions();
    }
    if (
      changedProperties.has('suggestions') ||
      changedProperties.has('queryErrorMessage')
    ) {
      this.updateDropdownVisibility();
    }
    if (changedProperties.has('text')) {
      fire(this, 'text-changed', {value: this.text});
    }
    if (changedProperties.has('value')) {
      fire(this, 'value-changed', {value: this.value});
    }
  }

  override render() {
    return html`
      <paper-input
        .noLabelFloat=${true}
        id="input"
        class=${this.computeClass()}
        ?disabled=${this.disabled}
        .value=${this.text}
        @value-changed=${(e: CustomEvent) => {
          this.text = e.detail.value;
        }}
        .placeholder=${this.placeholder}
        @keydown=${this.handleKeydown}
        @focus=${this.onInputFocus}
        @blur=${this.onInputBlur}
        autocomplete="off"
        .label=${this.label}
      >
        <div slot="prefix">
          <gr-icon
            icon="search"
            class="searchIcon ${this.computeShowSearchIconClass(
              this.showSearchIcon
            )}"
          ></gr-icon>
        </div>

        <div slot="suffix">
          <slot name="suffix"></slot>
        </div>
      </paper-input>
      <gr-autocomplete-dropdown
        .verticalOffset=${this.verticalOffset}
        id="suggestions"
        @item-selected=${this.handleItemSelect}
        @dropdown-closed=${this.focusWithoutDisplayingSuggestions}
        .suggestions=${this.suggestions}
        .errorMessage=${this.queryErrorMessage}
        role="listbox"
        .index=${this.index}
      >
      </gr-autocomplete-dropdown>
    `;
  }

  get focusStart() {
    return this.input;
  }

  override focus() {
    this.nativeInput.focus();
  }

  private focusWithoutDisplayingSuggestions() {
    this.disableDisplayingSuggestions = true;
    this.focus();

    this.updateComplete.then(() => {
      this.disableDisplayingSuggestions = false;
    });
  }

  selectAll() {
    const nativeInputElement = this.nativeInput;
    if (!this.input?.value) {
      return;
    }
    nativeInputElement.setSelectionRange(0, this.input?.value.length);
  }

  clear() {
    this.text = '';
  }

  private handleItemSelectEnter(e: CustomEvent | KeyboardEvent) {
    this.handleInputCommit();
    e.stopPropagation();
    e.preventDefault();
    this.focusWithoutDisplayingSuggestions();
  }

  handleItemSelect(e: CustomEvent) {
    if (e.detail.trigger === 'click') {
      this.selected = e.detail.selected;
      this._commit();
      e.stopPropagation();
      e.preventDefault();
      this.focusWithoutDisplayingSuggestions();
    } else if (e.detail.trigger === 'enter') {
      this.handleItemSelectEnter(e);
    } else if (e.detail.trigger === 'tab') {
      if (this.tabComplete) {
        this.handleInputCommit(true);
        e.stopPropagation();
        e.preventDefault();
        this.focus();
      } else {
        this.setFocus(false);
      }
    }
  }

  /**
   * Set the text of the input without triggering the suggestion dropdown.
   *
   * @param text The new text for the input.
   */
  setText(text: string) {
    this.disableDisplayingSuggestions = true;
    this.text = text;

    this.updateComplete.then(() => {
      this.disableDisplayingSuggestions = false;
    });
  }

  onInputFocus() {
    this.setFocus(true);
    this.updateSuggestions();
    this.input?.classList.remove('warnUncommitted');
    // Needed so that --paper-input-container-input updated style is applied.
    this.requestUpdate();
  }

  onInputBlur() {
    this.input?.classList.toggle(
      'warnUncommitted',
      this.warnUncommitted && !!this.text.length && !this.focused
    );
    // Needed so that --paper-input-container-input updated style is applied.
    this.requestUpdate();
  }

  updateSuggestions() {
    if (
      this.text === undefined ||
      this.threshold === undefined ||
      this.noDebounce === undefined
    )
      return;

    // Reset suggestions for every update
    // This will also prevent from carrying over suggestions:
    // @see Issue 12039
    this.suggestions = [];
    this.queryErrorMessage = undefined;

    // TODO(taoalpha): Also skip if text has not changed

    if (this.disableDisplayingSuggestions) {
      return;
    }

    const query = this.query;
    if (!query) {
      return;
    }

    if (this.text.length < this.threshold) {
      this.value = '';
      return;
    }

    if (!this.focused) {
      return;
    }

    const update = () => {
      query(this.text)
        .then(suggestions => {
          if (this.text !== this.text) {
            // Late response.
            return;
          }
          for (const suggestion of suggestions) {
            suggestion.text = suggestion?.name ?? '';
          }
          this.suggestions = suggestions;
          if (this.index === -1) {
            this.value = '';
          }
        })
        .catch(e => {
          debugger;
          this.value = '';
          if (typeof e === 'string') {
            this.queryErrorMessage = e;
          } else if (e instanceof Error) {
            this.queryErrorMessage = e.message;
          }
        });
    };

    if (this.noDebounce) {
      update();
    } else {
      this.updateSuggestionsTask = debounce(
        this.updateSuggestionsTask,
        update,
        DEBOUNCE_WAIT_MS
      );
    }
  }

  setFocus(focused: boolean) {
    if (focused === this.focused) return;
    this.focused = focused;
    this.updateDropdownVisibility();
  }

  updateDropdownVisibility() {
    if (
      (this.suggestions.length > 0 || this.queryErrorMessage) &&
      this.focused
    ) {
      this.suggestionsDropdown?.open();
      return;
    }
    this.suggestionsDropdown?.close();
  }

  computeClass() {
    const classes = [];
    if (this.borderless) classes.push('borderless');
    if (this.showBlueFocusBorder) classes.push('showBlueFocusBorder');
    return classes.join(' ');
  }

  /**
   * handleKeydown used for key handling in the this.input?.
   */
  handleKeydown(e: KeyboardEvent) {
    this.setFocus(true);
    switch (e.key) {
      case 'ArrowUp':
        e.preventDefault();
        this.suggestionsDropdown?.cursorUp();
        break;
      case 'ArrowDown':
        e.preventDefault();
        this.suggestionsDropdown?.cursorDown();
        break;
      case 'Escape':
        e.preventDefault();
        this.cancel();
        break;
      case 'Tab':
        if (this.suggestions.length > 0 && this.tabComplete) {
          e.preventDefault();
          this.focus();
          this.handleInputCommit(true);
        } else {
          this.setFocus(false);
        }
        break;
      case 'Enter':
        if (modifierPressed(e)) {
          break;
        }
        if (this.suggestions.length > 0) {
          // If suggestions are shown, act as if the keypress is in dropdown.
          // suggestions length is 0 if error is shown.
          this.handleItemSelectEnter(e);
        } else {
          e.preventDefault();
          this.handleInputCommit();
        }
        break;
      default:
        // For any normal keypress, return focus to the input to allow for
        // unbroken user input.
        this.focus();

        // Since this has been a normal keypress, the suggestions will have
        // been based on a previous input. Clear them. This prevents an
        // outdated suggestion from being used if the input keystroke is
        // immediately followed by a commit keystroke. @see Issue 8655
        this.suggestions = [];
    }
    this.dispatchEvent(
      new CustomEvent('input-keydown', {
        detail: {key: e.key, input: this.input},
        composed: true,
        bubbles: true,
      })
    );
  }

  cancel() {
    if (this.suggestions.length || this.queryErrorMessage) {
      this.suggestions = [];
      this.queryErrorMessage = undefined;
      this.requestUpdate();
    } else {
      fireEvent(this, 'cancel');
    }
  }

  handleInputCommit(_tabComplete?: boolean) {
    // Nothing to do if no suggestions.
    if (
      !this.allowNonSuggestedValues &&
      (this.suggestionsDropdown?.isHidden || this.queryErrorMessage)
    ) {
      return;
    }

    this.selected = this.suggestionsDropdown?.getCursorTarget() ?? null;
    this._commit(_tabComplete);
  }

  updateValue(
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

  private readonly handleBodyClick = (e: Event) => {
    const eventPath = e.composedPath();
    if (!eventPath) return;
    for (let i = 0; i < eventPath.length; i++) {
      if (eventPath[i] === this) {
        return;
      }
    }
    this.setFocus(false);
  };

  /**
   * Commits the suggestion, optionally firing the commit event.
   *
   * @param silent Allows for silent committing of an
   * autocomplete suggestion in order to handle cases like tab-to-complete
   * without firing the commit event.
   */
  async _commit(silent?: boolean) {
    // Allow values that are not in suggestion list iff suggestions are empty.
    if (this.suggestions.length > 0) {
      this.updateValue(this.selected, this.suggestions);
    } else {
      this.value = this.text || '';
    }

    const value = this.value;

    // Value and text are mirrors of each other in multi mode.
    if (this.multi) {
      this.setText(this.value);
    } else {
      if (!this.clearOnCommit && this.selected) {
        const dataSet = this.selected.dataset;
        // index property cannot be null for the data-set
        if (dataSet) {
          const index = Number(dataSet['index']!);
          if (isNaN(index)) return;
          this.setText(this.suggestions[index]?.name || '');
        }
      } else {
        this.clear();
      }
    }

    this.suggestions = [];
    this.queryErrorMessage = undefined;
    // we need willUpdate to send text-changed event before we can send the
    // 'commit' event
    await this.updateComplete;
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

  computeShowSearchIconClass(showSearchIcon: boolean) {
    return showSearchIcon ? 'showSearchIcon' : '';
  }
}

/**
 * Often gr-autocomplete is used for BranchName, RepoName, etc...
 * GrTypedAutocomplete allows to define more precise typing in templates.
 * For example, instead of
 * $: {
 *   branchSelect: GrAutocomplete
 * }
 * you can write
 * $: {
 *   branchSelect: GrTypedAutocomplete<BranchName>
 * }
 * And later user $.branchSelect.text without type conversion to BranchName.
 */
export interface GrTypedAutocomplete<
  T extends PropertyType<GrAutocomplete, 'text'>
> extends GrAutocomplete {
  text: T;
  value: T;
  query?: AutocompleteQuery<T>;
}
