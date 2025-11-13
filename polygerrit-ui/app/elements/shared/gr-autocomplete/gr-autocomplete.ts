/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import '../gr-cursor-manager/gr-cursor-manager';
import '../../../styles/shared-styles';
import {
  AutocompleteQueryStatus,
  AutocompleteQueryStatusType,
  GrAutocompleteDropdown,
  ItemSelectedEventDetail,
} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {fire} from '../../../utils/event-util';
import {
  debounce,
  DelayedTask,
  ResolvedDelayedTaskStatus,
} from '../../../utils/async-util';
import {PropertyType} from '../../../types/common';
import {modifierPressed} from '../../../utils/dom-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {ValueChangedEvent} from '../../../types/events';
import '@material/web/textfield/outlined-text-field';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';
import {AutocompleteSuggestion} from '../../../utils/autocomplete-util';

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

  @query('#input') input?: MdOutlinedTextField;

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

  @property({type: Boolean, attribute: 'show-blue-focus-border'})
  showBlueFocusBorder = false;

  /**
   * When true, the selection of the item will not trigger a commit.
   * When used by GrSearchAutocomplete for example, we don't want the user to navigate to the results page after selecting an item.
   */

  @property({type: Boolean, attribute: 'skip-commit-on-item-select'})
  skipCommitOnItemSelect = false;

  /**
   * Debounce wait time in milliseconds for updating suggestions.
   * Default is 200ms.
   */
  @property({type: Number})
  debounceWait = DEBOUNCE_WAIT_MS;

  @property({type: String})
  label? = '';

  @state() suggestions: AutocompleteSuggestion[] = [];

  @state() queryStatus?: AutocompleteQueryStatus;

  @state() index: number | null = null;

  // Enabled to suppress showing/updating suggestions when changing properties
  // that would normally trigger the update.
  disableDisplayingSuggestions = false;

  // private but used in tests
  focused = false;

  @state() selected: HTMLElement | null = null;

  /**
   * The query id that status or suggestions correspond to.
   */
  private activeQueryId = 0;

  /**
   * Last scheduled update suggestions task.
   */
  private updateSuggestionsTask?: DelayedTask;

  // Generate ids for scheduled suggestion queries to easily distinguish them.
  private static NEXT_QUERY_ID = 1;

  private static getNextQueryId() {
    return GrAutocomplete.NEXT_QUERY_ID++;
  }

  /**
   * @return Promise that resolves when suggestions are update.
   */
  get latestSuggestionUpdateComplete():
    | Promise<ResolvedDelayedTaskStatus>
    | undefined {
    return this.updateSuggestionsTask?.promise;
  }

  get nativeInput() {
    return this.input!.shadowRoot!.querySelector('input')!;
  }

  static override get styles() {
    return [
      materialStyles,
      sharedStyles,
      css`
        md-outlined-text-field {
          height: var(--gr-autocomplete-height);
          width: 100%;
          border-radius: var(
            --gr-autocomplete-text-field-border-radius,
            var(--border-radius)
          );
          --md-outlined-text-field-container-shape: var(
            --gr-autocomplete-text-field-border-radius,
            var(--border-radius)
          );
        }
        md-outlined-text-field.borderless {
          --md-outlined-text-field-outline-width: 0;
          --md-outlined-text-field-hover-outline-width: 0;
          --md-outlined-text-field-focus-outline-width: 0;
          border: none;
          padding: 0;
        }
        md-outlined-text-field.warnUncommitted {
          --md-outlined-text-field-input-text-size: 16px;
          --md-sys-color-primary: var(--error-text-color);
          --md-sys-color-on-surface: var(--error-text-color);
        }
      `,
    ];
  }

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
    if (changedProperties.has('text') || changedProperties.has('threshold')) {
      this.updateSuggestions();
    }
    if (
      changedProperties.has('suggestions') ||
      changedProperties.has('queryStatus')
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

  /**
   * There's an issue where if an element doesn't use leading-icon/trailing-icon
   * then a blank space is inserted where such icon should be.
   * This function checks that an element contains either leading-icon or trailing-icon.
   * @param name string
   * @return bool
   */
  private hasNamedSlot(name: string): boolean {
    const slot = this.querySelectorAll(`[slot="${name}"]`);
    return !!slot && slot.length > 0;
  }

  override render() {
    return html`
      <md-outlined-text-field
        id="input"
        class=${this.computeClass()}
        ?disabled=${this.disabled}
        .value=${this.text}
        @input=${(e: InputEvent) => {
          const target = e.target as HTMLInputElement;
          this.text = target.value;
        }}
        .placeholder=${this.placeholder}
        @keydown=${this.handleKeydown}
        @focus=${this.onInputFocus}
        @blur=${this.onInputBlur}
        autocomplete="off"
        .label=${this.label}
        aria-label=${this.label || this.placeholder || nothing}
      >
        ${this.hasNamedSlot('leading-icon')
          ? html`
              <div slot="leading-icon">
                <slot name="leading-icon"></slot>
              </div>
            `
          : nothing}
        ${this.hasNamedSlot('trailing-icon')
          ? html`
              <div slot="trailing-icon">
                <slot name="trailing-icon"></slot>
              </div>
            `
          : nothing}
      </md-outlined-text-field>
      <gr-autocomplete-dropdown
        .verticalOffset=${this.verticalOffset}
        id="suggestions"
        @item-selected=${this.handleItemSelect}
        @dropdown-closed=${this.focusWithoutDisplayingSuggestions}
        .suggestions=${this.suggestions}
        .queryStatus=${this.queryStatus}
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

  private handleItemSelectEnter(
    e: CustomEvent<ItemSelectedEventDetail> | KeyboardEvent
  ) {
    this.handleInputCommit(this.skipCommitOnItemSelect);
    e.stopPropagation();
    e.preventDefault();
    this.focusWithoutDisplayingSuggestions();
  }

  handleItemSelect(e: CustomEvent<ItemSelectedEventDetail>) {
    if (e.detail.trigger === 'click') {
      this.selected = e.detail.selected;
      this.commit();
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
  }

  onInputBlur() {
    this.input?.classList.toggle(
      'warnUncommitted',
      this.warnUncommitted && !!this.text.length && !this.focused
    );
  }

  updateSuggestions() {
    if (this.text === undefined || this.threshold === undefined) return;

    // Reset suggestions for every update
    // This will also prevent from carrying over suggestions:
    // @see Issue 12039
    this.resetQueryOutput();

    // TODO(taoalpha): Also skip if text has not changed

    if (this.disableDisplayingSuggestions) {
      return;
    }

    if (!this.query) {
      return;
    }

    if (this.text.length < this.threshold) {
      this.value = '';
      return;
    }

    if (!this.focused) {
      return;
    }

    const queryId = GrAutocomplete.getNextQueryId();
    this.activeQueryId = queryId;
    this.setQueryStatus({
      type: AutocompleteQueryStatusType.LOADING,
      message: 'Loading...',
    });
    this.updateSuggestionsTask = debounce(
      this.updateSuggestionsTask,
      this.createUpdateTask(queryId, this.query, this.text),
      this.debounceWait
    );
  }

  private createUpdateTask(
    queryId: number,
    query: AutocompleteQuery,
    text: string
  ): () => Promise<void> {
    return async () => {
      let suggestions: AutocompleteSuggestion[];
      try {
        suggestions = await query(text);
      } catch (e) {
        this.value = '';
        if (typeof e === 'string') {
          this.setQueryStatus({
            type: AutocompleteQueryStatusType.ERROR,
            message: e,
          });
        } else if (e instanceof Error) {
          this.setQueryStatus({
            type: AutocompleteQueryStatusType.ERROR,
            message: e.message,
          });
        }
        return;
      }
      if (queryId !== this.activeQueryId) {
        // Late response.
        return;
      }
      for (const suggestion of suggestions) {
        suggestion.text = suggestion?.name ?? '';
      }
      this.setSuggestions(suggestions);
      if (this.index === -1) {
        this.value = '';
      }
    };
  }

  setFocus(focused: boolean) {
    if (focused === this.focused) return;
    this.focused = focused;
    this.updateDropdownVisibility();
  }

  private shouldShowDropdown() {
    return (this.suggestions.length > 0 || this.queryStatus) && this.focused;
  }

  updateDropdownVisibility() {
    if (this.shouldShowDropdown()) {
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
        if (
          this.queryStatus?.type === AutocompleteQueryStatusType.LOADING &&
          this.tabComplete
        ) {
          e.preventDefault();
          // Queue tab on load.
          this.queryStatus = {
            type: AutocompleteQueryStatusType.LOADING,
            message: 'Loading... (Handle Tab on load)',
          };
          const queryId = this.activeQueryId;
          this.latestSuggestionUpdateComplete?.then(() => {
            if (queryId === this.activeQueryId) {
              this.handleInputCommit(/* tabComplete= */ true);
            }
          });
        } else if (this.suggestions.length > 0 && this.tabComplete) {
          e.preventDefault();
          this.handleInputCommit(/* tabComplete= */ true);
          this.focus();
        } else {
          this.setFocus(false);
        }
        break;
      case 'Enter':
        if (modifierPressed(e)) {
          break;
        }
        e.preventDefault();
        if (this.queryStatus?.type === AutocompleteQueryStatusType.LOADING) {
          // Queue enter on load.
          this.queryStatus = {
            type: AutocompleteQueryStatusType.LOADING,
            message: 'Loading... (Handle Enter on load)',
          };
          const queryId = this.activeQueryId;
          this.latestSuggestionUpdateComplete?.then(() => {
            if (queryId === this.activeQueryId) {
              this.handleItemSelectEnter(e);
            }
          });
        } else if (this.suggestions.length > 0) {
          // If suggestions are shown, act as if the keypress is in dropdown.
          // suggestions length is 0 if error is shown.
          this.handleItemSelectEnter(e);
        } else {
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
        this.resetQueryOutput();
        this.activeQueryId = 0;
    }
  }

  cancel() {
    if (this.shouldShowDropdown()) {
      this.resetQueryOutput();
      // If query is in flight by setting id to 0 we indicate that the results
      // are outdated.
      this.activeQueryId = 0;
      this.requestUpdate();
    } else {
      fire(this, 'cancel', {});
    }
  }

  handleInputCommit(tabComplete?: boolean) {
    // Nothing to do if no suggestions.
    if (
      !this.allowNonSuggestedValues &&
      (this.suggestionsDropdown?.isHidden || this.suggestions.length === 0)
    ) {
      return;
    }

    this.selected = this.suggestionsDropdown?.getCursorTarget() ?? null;
    this.commit(tabComplete);
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
    this.activeQueryId = 0;
  };

  /**
   * Commits the suggestion, optionally firing the commit event.
   *
   * @param silent Allows for silent committing of an
   * autocomplete suggestion in order to handle cases like tab-to-complete
   * without firing the commit event.
   *
   * Private but used in tests.
   */
  async commit(silent?: boolean) {
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

    this.resetQueryOutput();
    // we need willUpdate to send text-changed event before we can send the
    // 'commit' event
    await this.updateComplete;
    if (!silent) {
      fire(this, 'commit', {value});
    }
  }

  // resetQueryOutput, setSuggestions and setQueryStatus insure that suggestions
  // and queryStatus are never set at the same time.
  private resetQueryOutput() {
    this.suggestions = [];
    this.queryStatus = undefined;
  }

  private setSuggestions(suggestions: AutocompleteSuggestion[]) {
    this.suggestions = suggestions;
    this.queryStatus = undefined;
  }

  private setQueryStatus(queryStatus: AutocompleteQueryStatus) {
    this.suggestions = [];
    this.queryStatus = queryStatus;
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
