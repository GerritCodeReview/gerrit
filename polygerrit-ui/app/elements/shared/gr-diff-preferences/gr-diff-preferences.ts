/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import '../gr-button/gr-button';
import {DiffPreferencesInfo, IgnoreWhitespaceType} from '../../../types/diff';
import {subscribe} from '../../lit/subscription-controller';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {convertToString} from '../../../utils/string-util';
import {fire} from '../../../utils/event-util';
import {ValueChangedEvent} from '../../../types/events';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';
import '@material/web/checkbox/checkbox';
import {MdCheckbox} from '@material/web/checkbox/checkbox';
import '@material/web/select/outlined-select';
import '@material/web/select/select-option';
import {MdOutlinedSelect} from '@material/web/select/outlined-select';

@customElement('gr-diff-preferences')
export class GrDiffPreferences extends LitElement {
  @query('#columnsInput') private columnsInput?: HTMLInputElement;

  @query('#tabSizeInput') private tabSizeInput?: HTMLInputElement;

  @query('#fontSizeInput') private fontSizeInput?: HTMLInputElement;

  @query('#lineWrappingInput') private lineWrappingInput?: MdCheckbox;

  @query('#showTabsInput') private showTabsInput?: MdCheckbox;

  @query('#showTrailingWhitespaceInput')
  private showTrailingWhitespaceInput?: MdCheckbox;

  @query('#automaticReviewInput')
  private automaticReviewInput?: MdCheckbox;

  @query('#syntaxHighlightInput')
  private syntaxHighlightInput?: MdCheckbox;

  // Used in gr-diff-preferences-dialog
  @query('#contextSelect') contextSelect?: MdOutlinedSelect;

  @state() diffPrefs?: DiffPreferencesInfo;

  @state() private originalDiffPrefs?: DiffPreferencesInfo;

  readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().diffPreferences$,
      diffPreferences => {
        if (!diffPreferences) return;
        this.originalDiffPrefs = diffPreferences;
        this.diffPrefs = {...diffPreferences};
      }
    );
  }

  static override get styles() {
    return [
      materialStyles,
      sharedStyles,
      grFormStyles,
      css`
        md-outlined-text-field {
          max-width: 25em;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div id="diffPreferences" class="gr-form-styles">
        <section>
          <label for="contextLineSelect" class="title">Context</label>
          <span class="value">
            <md-outlined-select
              id="contextSelect"
              value=${convertToString(this.diffPrefs?.context)}
              @change=${(e: Event) => {
                const select = e.target as HTMLSelectElement;
                this.diffPrefs!.context = Number(select.value);
                this.requestUpdate();
                fire(this, 'has-unsaved-changes-changed', {
                  value: this.hasUnsavedChanges(),
                });
              }}
            >
              <md-select-option value="3">
                <div slot="headline">3 lines</div>
              </md-select-option>
              <md-select-option value="10">
                <div slot="headline">10 lines</div>
              </md-select-option>
              <md-select-option value="25">
                <div slot="headline">25 lines</div>
              </md-select-option>
              <md-select-option value="50">
                <div slot="headline">50 lines</div>
              </md-select-option>
              <md-select-option value="75">
                <div slot="headline">75 lines</div>
              </md-select-option>
              <md-select-option value="100">
                <div slot="headline">100 lines</div>
              </md-select-option>
              <md-select-option value="-1">
                <div slot="headline">Whole file</div>
              </md-select-option>
            </md-outlined-select>
          </span>
        </section>
        <section>
          <label for="lineWrappingInput" class="title">Fit to screen</label>
          <span class="value">
            <md-checkbox
              id="lineWrappingInput"
              ?checked=${!!this.diffPrefs?.line_wrapping}
              @change=${this.handleLineWrappingTap}
            ></md-checkbox>
          </span>
        </section>
        <section>
          <label for="columnsInput" class="title">Diff width</label>
          <span class="value">
            <md-outlined-text-field
              id="columnsInput"
              class="showBlueFocusBorder"
              type="number"
              step="1"
              .value=${convertToString(this.diffPrefs?.line_length)}
              @input=${this.handleDiffLineLengthInput}
              @beforeinput=${(e: InputEvent) => {
                const data = e.data;
                if (data && !/^[0-9]*$/.test(data)) {
                  e.preventDefault();
                }
              }}
            >
            </md-outlined-text-field>
          </span>
        </section>
        <section>
          <label for="tabSizeInput" class="title">Tab width</label>
          <span class="value">
            <md-outlined-text-field
              id="tabSizeInput"
              class="showBlueFocusBorder"
              type="number"
              step="1"
              .value=${convertToString(this.diffPrefs?.tab_size)}
              @input=${this.handleDiffTabSizeInput}
              @beforeinput=${(e: InputEvent) => {
                const data = e.data;
                if (data && !/^[0-9]*$/.test(data)) {
                  e.preventDefault();
                }
              }}
            >
            </md-outlined-text-field>
          </span>
        </section>
        <section>
          <label for="fontSizeInput" class="title">Font size</label>
          <span class="value">
            <md-outlined-text-field
              id="fontSizeInput"
              class="showBlueFocusBorder"
              type="number"
              step="1"
              .value=${convertToString(this.diffPrefs?.font_size)}
              @input=${this.handleDiffFontSizeInput}
              @beforeinput=${(e: InputEvent) => {
                const data = e.data;
                if (data && !/^[0-9]*$/.test(data)) {
                  e.preventDefault();
                }
              }}
            >
            </md-outlined-text-field>
          </span>
        </section>
        <section>
          <label for="showTabsInput" class="title">Show tabs</label>
          <span class="value">
            <md-checkbox
              id="showTabsInput"
              ?checked=${!!this.diffPrefs?.show_tabs}
              @change=${this.handleShowTabsTap}
            ></md-checkbox>
          </span>
        </section>
        <section>
          <label for="showTrailingWhitespaceInput" class="title"
            >Show trailing whitespace</label
          >
          <span class="value">
            <md-checkbox
              id="showTrailingWhitespaceInput"
              ?checked=${!!this.diffPrefs?.show_whitespace_errors}
              @change=${this.handleShowTrailingWhitespaceTap}
            ></md-checkbox>
          </span>
        </section>
        <section>
          <label for="syntaxHighlightInput" class="title"
            >Syntax highlighting</label
          >
          <span class="value">
            <md-checkbox
              id="syntaxHighlightInput"
              ?checked=${!!this.diffPrefs?.syntax_highlighting}
              @change=${this.handleSyntaxHighlightTap}
            ></md-checkbox>
          </span>
        </section>
        <section>
          <label for="automaticReviewInput" class="title"
            >Automatically mark viewed files reviewed</label
          >
          <span class="value">
            <md-checkbox
              id="automaticReviewInput"
              ?checked=${!this.diffPrefs?.manual_review}
              @change=${this.handleAutomaticReviewTap}
            ></md-checkbox>
          </span>
        </section>
        <section>
          <div class="pref">
            <label for="ignoreWhiteSpace" class="title"
              >Ignore Whitespace</label
            >
            <span class="value">
              <md-outlined-select
                id="contextSelect"
                value=${convertToString(this.diffPrefs?.ignore_whitespace)}
                @change=${(e: Event) => {
                  const select = e.target as HTMLSelectElement;
                  this.diffPrefs!.ignore_whitespace =
                    select.value as IgnoreWhitespaceType;
                  this.requestUpdate();
                  fire(this, 'has-unsaved-changes-changed', {
                    value: this.hasUnsavedChanges(),
                  });
                }}
              >
                <md-select-option value="IGNORE_NONE">
                  <div slot="headline">None</div>
                </md-select-option>
                <md-select-option value="IGNORE_TRAILING">
                  <div slot="headline">Trailing</div>
                </md-select-option>
                <md-select-option value="IGNORE_LEADING_AND_TRAILING">
                  <div slot="headline">Leading &amp; trailing</div>
                </md-select-option>
                <md-select-option value="IGNORE_ALL">
                  <div slot="headline">All</div>
                </md-select-option>
              </md-outlined-select>
            </span>
          </div>
        </section>
      </div>
    `;
  }

  private readonly handleLineWrappingTap = () => {
    this.diffPrefs!.line_wrapping = this.lineWrappingInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleDiffLineLengthInput = () => {
    this.diffPrefs!.line_length = Number(this.columnsInput!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleDiffTabSizeInput = () => {
    this.diffPrefs!.tab_size = Number(this.tabSizeInput!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleDiffFontSizeInput = () => {
    this.diffPrefs!.font_size = Number(this.fontSizeInput!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleShowTabsTap = () => {
    this.diffPrefs!.show_tabs = this.showTabsInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  // private but used in test
  readonly handleShowTrailingWhitespaceTap = () => {
    this.diffPrefs!.show_whitespace_errors =
      this.showTrailingWhitespaceInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleSyntaxHighlightTap = () => {
    this.diffPrefs!.syntax_highlighting = this.syntaxHighlightInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleAutomaticReviewTap = () => {
    this.diffPrefs!.manual_review = !this.automaticReviewInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  hasUnsavedChanges() {
    // We have to wrap boolean values in Boolean() to ensure undefined values
    // use false rather than undefined.
    return (
      Boolean(this.originalDiffPrefs?.syntax_highlighting) !==
        Boolean(this.diffPrefs?.syntax_highlighting) ||
      this.originalDiffPrefs?.context !== this.diffPrefs?.context ||
      Boolean(this.originalDiffPrefs?.line_wrapping) !==
        Boolean(this.diffPrefs?.line_wrapping) ||
      this.originalDiffPrefs?.line_length !== this.diffPrefs?.line_length ||
      this.originalDiffPrefs?.tab_size !== this.diffPrefs?.tab_size ||
      this.originalDiffPrefs?.font_size !== this.diffPrefs?.font_size ||
      this.originalDiffPrefs?.ignore_whitespace !==
        this.diffPrefs?.ignore_whitespace ||
      Boolean(this.originalDiffPrefs?.show_tabs) !==
        Boolean(this.diffPrefs?.show_tabs) ||
      Boolean(this.originalDiffPrefs?.show_whitespace_errors) !==
        Boolean(this.diffPrefs?.show_whitespace_errors) ||
      Boolean(this.originalDiffPrefs?.manual_review) !==
        Boolean(this.diffPrefs?.manual_review)
    );
  }

  async save() {
    if (!this.diffPrefs) return;
    await this.getUserModel().updateDiffPreference(this.diffPrefs);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  }
}

declare global {
  interface HTMLElementEventMap {
    'has-unsaved-changes-changed': ValueChangedEvent<boolean>;
  }
  interface HTMLElementTagNameMap {
    'gr-diff-preferences': GrDiffPreferences;
  }
}
