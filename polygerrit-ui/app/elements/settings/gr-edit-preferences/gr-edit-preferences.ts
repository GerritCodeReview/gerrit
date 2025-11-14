/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import {EditPreferencesInfo} from '../../../types/common';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {convertToString} from '../../../utils/string-util';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import {fire} from '../../../utils/event-util';
import {ValueChangedEvent} from '../../../types/events';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';
import '@material/web/checkbox/checkbox';
import {MdCheckbox} from '@material/web/checkbox/checkbox';

@customElement('gr-edit-preferences')
export class GrEditPreferences extends LitElement {
  @query('#editTabWidth') private editTabWidth?: HTMLInputElement;

  @query('#editColumns') private editColumns?: HTMLInputElement;

  @query('#editIndentUnit') private editIndentUnit?: HTMLInputElement;

  @query('#editSyntaxHighlighting')
  private editSyntaxHighlighting?: MdCheckbox;

  @query('#showAutoCloseBrackets')
  private showAutoCloseBrackets?: MdCheckbox;

  @query('#showIndentWithTabs') private showIndentWithTabs?: MdCheckbox;

  @query('#showMatchBrackets') private showMatchBrackets?: MdCheckbox;

  @query('#editShowLineWrapping')
  private editShowLineWrapping?: MdCheckbox;

  @query('#editShowTabs') private editShowTabs?: MdCheckbox;

  @query('#editShowTrailingWhitespaceInput')
  private editShowTrailingWhitespaceInput?: MdCheckbox;

  @state() editPrefs?: EditPreferencesInfo;

  @state() private originalEditPrefs?: EditPreferencesInfo;

  readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().editPreferences$,
      editPreferences => {
        this.originalEditPrefs = editPreferences;
        this.editPrefs = {...editPreferences};
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
      <div id="editPreferences" class="gr-form-styles">
        <section>
          <label for="editTabWidth" class="title">Tab width</label>
          <span class="value">
            <md-outlined-text-field
              id="editTabWidth"
              class="showBlueFocusBorder"
              type="number"
              step="1"
              .value=${convertToString(this.editPrefs?.tab_size)}
              @input=${this.handleEditTabWidthInput}
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
          <label for="editColumns" class="title">Columns</label>
          <span class="value">
            <md-outlined-text-field
              id="editColumns"
              class="showBlueFocusBorder"
              type="number"
              step="1"
              .value=${convertToString(this.editPrefs?.line_length)}
              @input=${this.handleEditLineLengthInput}
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
          <label for="editIndentUnit" class="title">Indent unit</label>
          <span class="value">
            <md-outlined-text-field
              id="editIndentUnit"
              class="showBlueFocusBorder"
              type="number"
              step="1"
              .value=${convertToString(this.editPrefs?.indent_unit)}
              @input=${this.handleEditIndentUnitInput}
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
          <label for="editSyntaxHighlighting" class="title"
            >Syntax highlighting</label
          >
          <span class="value">
            <md-checkbox
              id="editSyntaxHighlighting"
              ?checked=${!!this.editPrefs?.syntax_highlighting}
              @change=${this.handleEditSyntaxHighlightingChanged}
            ></md-checkbox>
          </span>
        </section>
        <section>
          <label for="editShowTabs" class="title">Show tabs</label>
          <span class="value">
            <md-checkbox
              id="editShowTabs"
              ?checked=${!!this.editPrefs?.show_tabs}
              @change=${this.handleEditShowTabsChanged}
            ></md-checkbox>
          </span>
        </section>
        <section>
          <label for="showTrailingWhitespaceInput" class="title"
            >Show trailing whitespace</label
          >
          <span class="value">
            <md-checkbox
              id="editShowTrailingWhitespaceInput"
              ?checked=${!!this.editPrefs?.show_whitespace_errors}
              @change=${this.handleEditShowTrailingWhitespaceTap}
            ></md-checkbox>
          </span>
        </section>
        <section>
          <label for="showMatchBrackets" class="title">Match brackets</label>
          <span class="value">
            <md-checkbox
              id="showMatchBrackets"
              ?checked=${!!this.editPrefs?.match_brackets}
              @change=${this.handleMatchBracketsChanged}
            ></md-checkbox>
          </span>
        </section>
        <section>
          <label for="editShowLineWrapping" class="title">Line wrapping</label>
          <span class="value">
            <md-checkbox
              id="editShowLineWrapping"
              ?checked=${!!this.editPrefs?.line_wrapping}
              @change=${this.handleEditLineWrappingChanged}
            ></md-checkbox>
          </span>
        </section>
        <section>
          <label for="showIndentWithTabs" class="title">Indent with tabs</label>
          <span class="value">
            <md-checkbox
              id="showIndentWithTabs"
              ?checked=${!!this.editPrefs?.indent_with_tabs}
              @change=${this.handleIndentWithTabsChanged}
            ></md-checkbox>
          </span>
        </section>
        <section>
          <label for="showAutoCloseBrackets" class="title"
            >Auto close brackets</label
          >
          <span class="value">
            <md-checkbox
              id="showAutoCloseBrackets"
              ?checked=${!!this.editPrefs?.auto_close_brackets}
              @change=${this.handleAutoCloseBracketsChanged}
            ></md-checkbox>
          </span>
        </section>
      </div>
    `;
  }

  private readonly handleEditTabWidthInput = () => {
    this.editPrefs!.tab_size = Number(this.editTabWidth!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleEditLineLengthInput = () => {
    this.editPrefs!.line_length = Number(this.editColumns!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleEditIndentUnitInput = () => {
    this.editPrefs!.indent_unit = Number(this.editIndentUnit!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleEditSyntaxHighlightingChanged = () => {
    this.editPrefs!.syntax_highlighting = this.editSyntaxHighlighting!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  // private but used in test
  readonly handleEditShowTabsChanged = () => {
    this.editPrefs!.show_tabs = this.editShowTabs!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleEditShowTrailingWhitespaceTap = () => {
    this.editPrefs!.show_whitespace_errors =
      this.editShowTrailingWhitespaceInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleMatchBracketsChanged = () => {
    this.editPrefs!.match_brackets = this.showMatchBrackets!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleEditLineWrappingChanged = () => {
    this.editPrefs!.line_wrapping = this.editShowLineWrapping!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleIndentWithTabsChanged = () => {
    this.editPrefs!.indent_with_tabs = this.showIndentWithTabs!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleAutoCloseBracketsChanged = () => {
    this.editPrefs!.auto_close_brackets = this.showAutoCloseBrackets!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  // private but used in test
  hasUnsavedChanges() {
    // We have to wrap boolean values in Boolean() to ensure undefined values
    // use false rather than undefined.
    return (
      this.originalEditPrefs?.tab_size !== this.editPrefs?.tab_size ||
      this.originalEditPrefs?.line_length !== this.editPrefs?.line_length ||
      this.originalEditPrefs?.indent_unit !== this.editPrefs?.indent_unit ||
      Boolean(this.originalEditPrefs?.syntax_highlighting) !==
        Boolean(this.editPrefs?.syntax_highlighting) ||
      Boolean(this.originalEditPrefs?.show_tabs) !==
        Boolean(this.editPrefs?.show_tabs) ||
      Boolean(this.originalEditPrefs?.show_whitespace_errors) !==
        Boolean(this.editPrefs?.show_whitespace_errors) ||
      Boolean(this.originalEditPrefs?.match_brackets) !==
        Boolean(this.editPrefs?.match_brackets) ||
      Boolean(this.originalEditPrefs?.line_wrapping) !==
        Boolean(this.editPrefs?.line_wrapping) ||
      Boolean(this.originalEditPrefs?.indent_with_tabs) !==
        Boolean(this.editPrefs?.indent_with_tabs) ||
      Boolean(this.originalEditPrefs?.auto_close_brackets) !==
        Boolean(this.editPrefs?.auto_close_brackets)
    );
  }

  async save() {
    if (!this.editPrefs) return;
    await this.getUserModel().updateEditPreference(this.editPrefs);
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
    'gr-edit-preferences': GrEditPreferences;
  }
}
