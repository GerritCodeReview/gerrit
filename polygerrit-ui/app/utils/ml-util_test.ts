/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../test/common-test-setup';
import {
  C2CSuggestion,
  transformC2CSuggestionsToProviderSuggestions,
} from './ml-util';
import {SuggestCodeRequest, Suggestion} from '../api/suggestions';
import {NumericChangeId, RevisionPatchSetNum} from '../api/rest-api';

suite('ml-util tests', () => {
  test('transformC2CSuggestionsToProviderSuggestions same range', () => {
    const c2cSuggestions: C2CSuggestion[] = [
      {
        path: 'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
        comment_uuid: '',
        replacements: [
          {
            patch_set_number: 0,
            path: 'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
            range: {
              start_line: 128,
              start_char: -1,
              end_line: 131,
              end_char: 0,
            },
            new_content:
              '  private handleClick(gerrit: MouseEvent) {\n    gerrit.stopPropagation();\n    gerrit.preventDefault();\n',
            operation: 'MODIFY_FILE',
          },
        ],
        log_probability: -0.13675694,
        new_content:
          "/**\n * @license\n * Copyright 2022 Google LLC\n * SPDX-License-Identifier: Apache-2.0\n */\nimport '../../shared/gr-icon/gr-icon';\nimport {LitElement, css, html} from 'lit';\nimport {customElement, property} from 'lit/decorators.js';\nimport {sharedStyles} from '../../../styles/shared-styles';\nimport {getAppContext} from '../../../services/app-context';\nimport {fireShowTab} from '../../../utils/event-util';\nimport {Tab} from '../../../constants/constants';\nimport {CommentTabState} from '../../../types/events';\nimport {fontStyles} from '../../../styles/gr-font-styles';\n\nexport enum SummaryChipStyles {\n  INFO = 'info',\n  WARNING = 'warning',\n  CHECK = 'check',\n  UNDEFINED = '',\n}\n\n@customElement('gr-summary-chip')\nexport class GrSummaryChip extends LitElement {\n  @property()\n  icon = '';\n\n  @property({type: Boolean})\n  iconFilled = false;\n\n  @property()\n  styleType = SummaryChipStyles.UNDEFINED;\n\n  @property()\n  category?: CommentTabState;\n\n  @property({type: Boolean})\n  clickable?: boolean;\n\n  private readonly reporting = getAppContext().reportingService;\n\n  static override get styles() {\n    return [\n      sharedStyles,\n      fontStyles,\n      css`\n        .summaryChip {\n          color: var(--chip-color);\n          cursor: pointer;\n          display: inline-block;\n          padding: var(--spacing-xxs) var(--spacing-m) var(--spacing-xxs)\n            var(--spacing-s);\n          margin-right: var(--spacing-s);\n          border-radius: 12px;\n          border: 1px solid gray;\n          vertical-align: top;\n          /* centered position of 20px chips in 24px line-height inline flow */\n          vertical-align: top;\n          position: relative;\n          top: 2px;\n        }\n        gr-icon {\n          font-size: var(--line-height-small);\n        }\n        .summaryChip.info {\n          border-color: var(--info-foreground);\n          background: var(--info-background);\n        }\n        button.summaryChip.info:hover {\n          background: var(--info-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.info:focus-within {\n          background: var(--info-background-focus);\n        }\n        .summaryChip.info gr-icon {\n          color: var(--info-foreground);\n        }\n        .summaryChip.warning {\n          border-color: var(--warning-foreground);\n          background: var(--warning-background);\n        }\n        button.summaryChip.warning:hover {\n          background: var(--warning-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.warning:focus-within {\n          background: var(--warning-background-focus);\n        }\n        .summaryChip.warning gr-icon {\n          color: var(--warning-foreground);\n        }\n        .summaryChip.check {\n          border-color: var(--gray-foreground);\n          background: var(--gray-background);\n        }\n        button.summaryChip.check:hover {\n          background: var(--gray-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.check:focus-within {\n          background: var(--gray-background-focus);\n        }\n        .summaryChip.check gr-icon {\n          color: var(--gray-foreground);\n        }\n      `,\n    ];\n  }\n\n  override render() {\n    const chipClass = `summaryChip font-small ${this.styleType}`;\n    if (this.clickable) {\n      return html`<button class=${chipClass} @click=${this.handleClick}>\n        ${this.renderIconAndSlot()}\n      </button>`;\n    } else {\n      return html`<span class=${chipClass}>${this.renderIconAndSlot()}</span>`;\n    }\n  }\n\n  renderIconAndSlot() {\n    return html` ${this.icon &&\n      html`<gr-icon ?filled=${this.iconFilled} icon=${this.icon}></gr-icon>`}\n      <slot></slot>`;\n  }\n\n  private handleClick(gerrit: MouseEvent) {\n    gerrit.stopPropagation();\n    gerrit.preventDefault();\n    this.reporting.reportInteraction('comment chip click', {\n      category: this.category,\n    });\n    fireShowTab(this, Tab.COMMENT_THREADS, true, {\n      commentTab: this.category,\n    });\n  }\n}\n\ndeclare global {\n  interface HTMLElementTagNameMap {\n    'gr-summary-chip': GrSummaryChip;\n  }\n}\n",
      },
    ];
    const suggestCodeRequest: SuggestCodeRequest = {
      prompt: 'Rename event to gerrit',
      changeNumber: 375726 as NumericChangeId,
      patchsetNumber: 1 as RevisionPatchSetNum,
      filePath:
        'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
      range: {
        start_line: 128,
        start_character: 0,
        end_line: 130,
        end_character: 27,
      },
      lineNumber: 130,
    };
    // TODO(milutin): Why there is \n at the end?
    const expectedResult: Suggestion[] = [
      {
        replacement:
          '  private handleClick(gerrit: MouseEvent) {\n    gerrit.stopPropagation();\n    gerrit.preventDefault();\n',
      },
    ];
    assert.deepEqual(
      transformC2CSuggestionsToProviderSuggestions(
        c2cSuggestions,
        suggestCodeRequest
      ),
      expectedResult
    );
  });

  test('transformC2CSuggestionsToProviderSuggestions 1 line vs 4 lines range', () => {
    const c2cSuggestions: C2CSuggestion[] = [
      {
        path: 'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
        comment_uuid: '',
        replacements: [
          {
            patch_set_number: 0,
            path: 'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
            range: {
              start_line: 128,
              start_char: -1,
              end_line: 131,
              end_char: 0,
            },
            new_content:
              '  private handleClick(gerrit: MouseEvent) {\n    gerrit.stopPropagation();\n    gerrit.preventDefault();\n',
            operation: 'MODIFY_FILE',
          },
        ],
        log_probability: -0.13675694,
        new_content:
          "/**\n * @license\n * Copyright 2022 Google LLC\n * SPDX-License-Identifier: Apache-2.0\n */\nimport '../../shared/gr-icon/gr-icon';\nimport {LitElement, css, html} from 'lit';\nimport {customElement, property} from 'lit/decorators.js';\nimport {sharedStyles} from '../../../styles/shared-styles';\nimport {getAppContext} from '../../../services/app-context';\nimport {fireShowTab} from '../../../utils/event-util';\nimport {Tab} from '../../../constants/constants';\nimport {CommentTabState} from '../../../types/events';\nimport {fontStyles} from '../../../styles/gr-font-styles';\n\nexport enum SummaryChipStyles {\n  INFO = 'info',\n  WARNING = 'warning',\n  CHECK = 'check',\n  UNDEFINED = '',\n}\n\n@customElement('gr-summary-chip')\nexport class GrSummaryChip extends LitElement {\n  @property()\n  icon = '';\n\n  @property({type: Boolean})\n  iconFilled = false;\n\n  @property()\n  styleType = SummaryChipStyles.UNDEFINED;\n\n  @property()\n  category?: CommentTabState;\n\n  @property({type: Boolean})\n  clickable?: boolean;\n\n  private readonly reporting = getAppContext().reportingService;\n\n  static override get styles() {\n    return [\n      sharedStyles,\n      fontStyles,\n      css`\n        .summaryChip {\n          color: var(--chip-color);\n          cursor: pointer;\n          display: inline-block;\n          padding: var(--spacing-xxs) var(--spacing-m) var(--spacing-xxs)\n            var(--spacing-s);\n          margin-right: var(--spacing-s);\n          border-radius: 12px;\n          border: 1px solid gray;\n          vertical-align: top;\n          /* centered position of 20px chips in 24px line-height inline flow */\n          vertical-align: top;\n          position: relative;\n          top: 2px;\n        }\n        gr-icon {\n          font-size: var(--line-height-small);\n        }\n        .summaryChip.info {\n          border-color: var(--info-foreground);\n          background: var(--info-background);\n        }\n        button.summaryChip.info:hover {\n          background: var(--info-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.info:focus-within {\n          background: var(--info-background-focus);\n        }\n        .summaryChip.info gr-icon {\n          color: var(--info-foreground);\n        }\n        .summaryChip.warning {\n          border-color: var(--warning-foreground);\n          background: var(--warning-background);\n        }\n        button.summaryChip.warning:hover {\n          background: var(--warning-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.warning:focus-within {\n          background: var(--warning-background-focus);\n        }\n        .summaryChip.warning gr-icon {\n          color: var(--warning-foreground);\n        }\n        .summaryChip.check {\n          border-color: var(--gray-foreground);\n          background: var(--gray-background);\n        }\n        button.summaryChip.check:hover {\n          background: var(--gray-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.check:focus-within {\n          background: var(--gray-background-focus);\n        }\n        .summaryChip.check gr-icon {\n          color: var(--gray-foreground);\n        }\n      `,\n    ];\n  }\n\n  override render() {\n    const chipClass = `summaryChip font-small ${this.styleType}`;\n    if (this.clickable) {\n      return html`<button class=${chipClass} @click=${this.handleClick}>\n        ${this.renderIconAndSlot()}\n      </button>`;\n    } else {\n      return html`<span class=${chipClass}>${this.renderIconAndSlot()}</span>`;\n    }\n  }\n\n  renderIconAndSlot() {\n    return html` ${this.icon &&\n      html`<gr-icon ?filled=${this.iconFilled} icon=${this.icon}></gr-icon>`}\n      <slot></slot>`;\n  }\n\n  private handleClick(gerrit: MouseEvent) {\n    gerrit.stopPropagation();\n    gerrit.preventDefault();\n    this.reporting.reportInteraction('comment chip click', {\n      category: this.category,\n    });\n    fireShowTab(this, Tab.COMMENT_THREADS, true, {\n      commentTab: this.category,\n    });\n  }\n}\n\ndeclare global {\n  interface HTMLElementTagNameMap {\n    'gr-summary-chip': GrSummaryChip;\n  }\n}\n",
      },
    ];
    const suggestCodeRequest: SuggestCodeRequest = {
      prompt: 'Rename event to gerrit',
      changeNumber: 375726 as NumericChangeId,
      patchsetNumber: 1 as RevisionPatchSetNum,
      filePath:
        'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
      range: {
        start_line: 128,
        start_character: 22,
        end_line: 128,
        end_character: 27,
      },
      lineNumber: 128,
    };
    const expectedResult: Suggestion[] = [
      {
        replacement:
          '  private handleClick(gerrit: MouseEvent) {\n    gerrit.stopPropagation();\n    gerrit.preventDefault();\n',
      },
    ];
    assert.deepEqual(
      transformC2CSuggestionsToProviderSuggestions(
        c2cSuggestions,
        suggestCodeRequest
      ),
      expectedResult
    );
  });

  test('transformC2CSuggestionsToProviderSuggestions 8 line vs 4 lines range', () => {
    const c2cSuggestions: C2CSuggestion[] = [
      {
        path: 'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
        comment_uuid: '',
        replacements: [
          {
            patch_set_number: 0,
            path: 'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
            range: {
              start_line: 128,
              start_char: -1,
              end_line: 131,
              end_char: 0,
            },
            new_content:
              '  private handleClick(gerrit: MouseEvent) {\n    gerrit.stopPropagation();\n    gerrit.preventDefault();\n',
            operation: 'MODIFY_FILE',
          },
        ],
        log_probability: -0.13675694,
        new_content:
          "/**\n * @license\n * Copyright 2022 Google LLC\n * SPDX-License-Identifier: Apache-2.0\n */\nimport '../../shared/gr-icon/gr-icon';\nimport {LitElement, css, html} from 'lit';\nimport {customElement, property} from 'lit/decorators.js';\nimport {sharedStyles} from '../../../styles/shared-styles';\nimport {getAppContext} from '../../../services/app-context';\nimport {fireShowTab} from '../../../utils/event-util';\nimport {Tab} from '../../../constants/constants';\nimport {CommentTabState} from '../../../types/events';\nimport {fontStyles} from '../../../styles/gr-font-styles';\n\nexport enum SummaryChipStyles {\n  INFO = 'info',\n  WARNING = 'warning',\n  CHECK = 'check',\n  UNDEFINED = '',\n}\n\n@customElement('gr-summary-chip')\nexport class GrSummaryChip extends LitElement {\n  @property()\n  icon = '';\n\n  @property({type: Boolean})\n  iconFilled = false;\n\n  @property()\n  styleType = SummaryChipStyles.UNDEFINED;\n\n  @property()\n  category?: CommentTabState;\n\n  @property({type: Boolean})\n  clickable?: boolean;\n\n  private readonly reporting = getAppContext().reportingService;\n\n  static override get styles() {\n    return [\n      sharedStyles,\n      fontStyles,\n      css`\n        .summaryChip {\n          color: var(--chip-color);\n          cursor: pointer;\n          display: inline-block;\n          padding: var(--spacing-xxs) var(--spacing-m) var(--spacing-xxs)\n            var(--spacing-s);\n          margin-right: var(--spacing-s);\n          border-radius: 12px;\n          border: 1px solid gray;\n          vertical-align: top;\n          /* centered position of 20px chips in 24px line-height inline flow */\n          vertical-align: top;\n          position: relative;\n          top: 2px;\n        }\n        gr-icon {\n          font-size: var(--line-height-small);\n        }\n        .summaryChip.info {\n          border-color: var(--info-foreground);\n          background: var(--info-background);\n        }\n        button.summaryChip.info:hover {\n          background: var(--info-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.info:focus-within {\n          background: var(--info-background-focus);\n        }\n        .summaryChip.info gr-icon {\n          color: var(--info-foreground);\n        }\n        .summaryChip.warning {\n          border-color: var(--warning-foreground);\n          background: var(--warning-background);\n        }\n        button.summaryChip.warning:hover {\n          background: var(--warning-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.warning:focus-within {\n          background: var(--warning-background-focus);\n        }\n        .summaryChip.warning gr-icon {\n          color: var(--warning-foreground);\n        }\n        .summaryChip.check {\n          border-color: var(--gray-foreground);\n          background: var(--gray-background);\n        }\n        button.summaryChip.check:hover {\n          background: var(--gray-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.check:focus-within {\n          background: var(--gray-background-focus);\n        }\n        .summaryChip.check gr-icon {\n          color: var(--gray-foreground);\n        }\n      `,\n    ];\n  }\n\n  override render() {\n    const chipClass = `summaryChip font-small ${this.styleType}`;\n    if (this.clickable) {\n      return html`<button class=${chipClass} @click=${this.handleClick}>\n        ${this.renderIconAndSlot()}\n      </button>`;\n    } else {\n      return html`<span class=${chipClass}>${this.renderIconAndSlot()}</span>`;\n    }\n  }\n\n  renderIconAndSlot() {\n    return html` ${this.icon &&\n      html`<gr-icon ?filled=${this.iconFilled} icon=${this.icon}></gr-icon>`}\n      <slot></slot>`;\n  }\n\n  private handleClick(gerrit: MouseEvent) {\n    gerrit.stopPropagation();\n    gerrit.preventDefault();\n    this.reporting.reportInteraction('comment chip click', {\n      category: this.category,\n    });\n    fireShowTab(this, Tab.COMMENT_THREADS, true, {\n      commentTab: this.category,\n    });\n  }\n}\n\ndeclare global {\n  interface HTMLElementTagNameMap {\n    'gr-summary-chip': GrSummaryChip;\n  }\n}\n",
      },
    ];
    const suggestCodeRequest: SuggestCodeRequest = {
      prompt: 'Rename event to gerrit',
      changeNumber: 375726 as NumericChangeId,
      patchsetNumber: 1 as RevisionPatchSetNum,
      filePath:
        'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
      range: {
        start_line: 128,
        start_character: 0,
        end_line: 138,
        end_character: 1,
      },
      lineNumber: 138,
    };
    // TODO(milutin): Change range
    const expectedResult: Suggestion[] = [
      {
        replacement:
          '  private handleClick(gerrit: MouseEvent) {\n    gerrit.stopPropagation();\n    gerrit.preventDefault();\n',
      },
    ];
    assert.deepEqual(
      transformC2CSuggestionsToProviderSuggestions(
        c2cSuggestions,
        suggestCodeRequest
      ),
      expectedResult
    );
  });

  test('transformC2CSuggestionsToProviderSuggestions import', () => {
    const c2cSuggestions: C2CSuggestion[] = [
      {
        path: 'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
        comment_uuid: '',
        replacements: [
          {
            patch_set_number: 0,
            path: 'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
            range: {
              start_line: 15,
              start_char: -1,
              end_line: 15,
              end_char: 0,
            },
            new_content:
              "import {diffStyles} from '../../../utils/diff-util';\n",
            operation: 'MODIFY_FILE',
          },
          // TODO(milutin): Why it's not 'fontStyles\n diffStyles" since new_content is addition.
          // Hint is in range, this is line 46
          {
            patch_set_number: 0,
            path: 'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
            range: {
              start_line: 46,
              start_char: -1,
              end_line: 46,
              end_char: 0,
            },
            new_content: '      diffStyles,\n',
            operation: 'MODIFY_FILE',
          },
        ],
        log_probability: -0.8626439,
        new_content:
          "/**\n * @license\n * Copyright 2022 Google LLC\n * SPDX-License-Identifier: Apache-2.0\n */\nimport '../../shared/gr-icon/gr-icon';\nimport {LitElement, css, html} from 'lit';\nimport {customElement, property} from 'lit/decorators.js';\nimport {sharedStyles} from '../../../styles/shared-styles';\nimport {getAppContext} from '../../../services/app-context';\nimport {fireShowTab} from '../../../utils/event-util';\nimport {Tab} from '../../../constants/constants';\nimport {CommentTabState} from '../../../types/events';\nimport {fontStyles} from '../../../styles/gr-font-styles';\nimport {diffStyles} from '../../../utils/diff-util';\n\nexport enum SummaryChipStyles {\n  INFO = 'info',\n  WARNING = 'warning',\n  CHECK = 'check',\n  UNDEFINED = '',\n}\n\n@customElement('gr-summary-chip')\nexport class GrSummaryChip extends LitElement {\n  @property()\n  icon = '';\n\n  @property({type: Boolean})\n  iconFilled = false;\n\n  @property()\n  styleType = SummaryChipStyles.UNDEFINED;\n\n  @property()\n  category?: CommentTabState;\n\n  @property({type: Boolean})\n  clickable?: boolean;\n\n  private readonly reporting = getAppContext().reportingService;\n\n  static override get styles() {\n    return [\n      sharedStyles,\n      fontStyles,\n      diffStyles,\n      css`\n        .summaryChip {\n          color: var(--chip-color);\n          cursor: pointer;\n          display: inline-block;\n          padding: var(--spacing-xxs) var(--spacing-m) var(--spacing-xxs)\n            var(--spacing-s);\n          margin-right: var(--spacing-s);\n          border-radius: 12px;\n          border: 1px solid gray;\n          vertical-align: top;\n          /* centered position of 20px chips in 24px line-height inline flow */\n          vertical-align: top;\n          position: relative;\n          top: 2px;\n        }\n        gr-icon {\n          font-size: var(--line-height-small);\n        }\n        .summaryChip.info {\n          border-color: var(--info-foreground);\n          background: var(--info-background);\n        }\n        button.summaryChip.info:hover {\n          background: var(--info-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.info:focus-within {\n          background: var(--info-background-focus);\n        }\n        .summaryChip.info gr-icon {\n          color: var(--info-foreground);\n        }\n        .summaryChip.warning {\n          border-color: var(--warning-foreground);\n          background: var(--warning-background);\n        }\n        button.summaryChip.warning:hover {\n          background: var(--warning-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.warning:focus-within {\n          background: var(--warning-background-focus);\n        }\n        .summaryChip.warning gr-icon {\n          color: var(--warning-foreground);\n        }\n        .summaryChip.check {\n          border-color: var(--gray-foreground);\n          background: var(--gray-background);\n        }\n        button.summaryChip.check:hover {\n          background: var(--gray-background-hover);\n          box-shadow: var(--elevation-level-1);\n        }\n        .summaryChip.check:focus-within {\n          background: var(--gray-background-focus);\n        }\n        .summaryChip.check gr-icon {\n          color: var(--gray-foreground);\n        }\n      `,\n    ];\n  }\n\n  override render() {\n    const chipClass = `summaryChip font-small ${this.styleType}`;\n    if (this.clickable) {\n      return html`<button class=${chipClass} @click=${this.handleClick}>\n        ${this.renderIconAndSlot()}\n      </button>`;\n    } else {\n      return html`<span class=${chipClass}>${this.renderIconAndSlot()}</span>`;\n    }\n  }\n\n  renderIconAndSlot() {\n    return html` ${this.icon &&\n      html`<gr-icon ?filled=${this.iconFilled} icon=${this.icon}></gr-icon>`}\n      <slot></slot>`;\n  }\n\n  private handleClick(event: MouseEvent) {\n    event.stopPropagation();\n    event.preventDefault();\n    this.reporting.reportInteraction('comment chip click', {\n      category: this.category,\n    });\n    fireShowTab(this, Tab.COMMENT_THREADS, true, {\n      commentTab: this.category,\n    });\n  }\n}\n\ndeclare global {\n  interface HTMLElementTagNameMap {\n    'gr-summary-chip': GrSummaryChip;\n  }\n}\n",
      },
    ];
    const suggestCodeRequest: SuggestCodeRequest = {
      prompt: 'Add diffStyles',
      changeNumber: 375726 as NumericChangeId,
      patchsetNumber: 1 as RevisionPatchSetNum,
      filePath:
        'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip.ts',
      range: {
        start_line: 45,
        start_character: 6,
        end_line: 45,
        end_character: 16,
      },
      lineNumber: 45,
    };
    const expectedResult: Suggestion[] = [
      {
        replacement: '      fontStyles,\n      diffStyles,\n',
      },
    ];
    assert.deepEqual(
      transformC2CSuggestionsToProviderSuggestions(
        c2cSuggestions,
        suggestCodeRequest
      ),
      expectedResult
    );
  });
});
