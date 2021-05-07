/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import '@polymer/paper-button/paper-button';
import '@polymer/paper-card/paper-card';
import '@polymer/paper-checkbox/paper-checkbox';
import '@polymer/paper-dropdown-menu/paper-dropdown-menu';
import '@polymer/paper-fab/paper-fab';
import '@polymer/paper-icon-button/paper-icon-button';
import '@polymer/paper-item/paper-item';
import '@polymer/paper-listbox/paper-listbox';

import '../../shared/gr-button/gr-button';
import {pluralize} from '../../../utils/string-util';
import {fire} from '../../../utils/event-util';
import {DiffInfo} from '../../../types/diff';
import {css, customElement, html, LitElement, property} from 'lit-element';

import {
  ContextButtonType,
  RenderPreferences,
  SyntaxBlock,
} from '../../../api/diff';

import {GrDiffGroup, hideInContextControl} from '../gr-diff/gr-diff-group';

const PARTIAL_CONTEXT_AMOUNT = 10;

function findMostNestedContainingBlock(
  lineNum: number,
  blocks?: SyntaxBlock[]
): SyntaxBlock | undefined {
  const containingBlock = blocks?.find(
    ({range}) => range.start_line < lineNum && range.end_line > lineNum
  );
  const containingChildBlock = containingBlock
    ? findMostNestedContainingBlock(lineNum, containingBlock?.children)
    : undefined;
  return containingChildBlock || containingBlock;
}

@customElement('gr-context-controls')
export class GrContextControls extends LitElement {
  @property({type: Object}) renderPreferences?: RenderPreferences;

  @property({type: Object}) diff!: DiffInfo;

  @property({type: Object}) section!: HTMLElement;

  @property({type: Object}) contextGroups!: GrDiffGroup[];

  @property({type: Boolean}) showAbove = false;

  @property({type: Boolean}) showBelow = false;

  static styles = css`
    :host {
      display: flex;
      width: 100%;
      height: 100%;
      justify-content: center;
      position: absolute;
    }
  `;

  static customStyles = html`
    <custom-style>
      <style>
        .contextControlButton {
          background-color: var(--default-button-background-color);
          font: var(--context-control-button-font, inherit);
          /* All position is relative to container, so ignore sibling buttons. */
          position: absolute;
        }
        .contextControlButton:first-child {
          /* First button needs to claim width to display without text wrapping. */
          position: relative;
        }
        .centeredButton {
          /* Center over divider. */
          top: 50%;
          transform: translateY(-50%);

          --gr-button: {
            color: var(--diff-context-control-color);
            border-style: solid;
            border-color: var(--border-color);
            border-top-width: 1px;
            border-right-width: 1px;
            border-bottom-width: 1px;
            border-left-width: 1px;

            border-top-left-radius: var(--border-radius);
            border-top-right-radius: var(--border-radius);
            border-bottom-right-radius: var(--border-radius);
            border-bottom-left-radius: var(--border-radius);
            padding: var(--spacing-s) var(--spacing-l);
          }
        }
        .aboveBelowButtons {
          display: flex;
          flex-direction: column;
          margin-left: var(--spacing-m);
          position: relative;
        }
        .aboveBelowButtons:first-child {
          margin-left: 0;
        }

        .aboveButton {
          /* Display over preceding content / background placeholder. */
          transform: translateY(-100%);
          --gr-button: {
            color: var(--diff-context-control-color);
            border-style: solid;
            border-color: var(--border-color);
            border-top-width: 1px;
            border-right-width: 1px;
            border-bottom-width: 0;
            border-left-width: 1px;

            border-top-left-radius: var(--border-radius);
            border-top-right-radius: var(--border-radius);
            border-bottom-right-radius: 0;
            border-bottom-left-radius: var(--border-radius);
            padding: var(--spacing-xxs) var(--spacing-l);
          }
        }
        .belowButton {
          top: calc(100% + var(--divider-border));
          --gr-button: {
            color: var(--diff-context-control-color);
            border-style: solid;
            border-color: var(--border-color);
            border-top-width: 0;
            border-right-width: 1px;
            border-bottom-width: 1px;
            border-left-width: 1px;

            border-top-left-radius: 0;
            border-top-right-radius: 0;
            border-bottom-right-radius: var(--border-radius);
            border-bottom-left-radius: var(--border-radius);
            padding: var(--spacing-xxs) var(--spacing-l);
          }
        }
      </style>
    </custom-style>
  `;

  get numLines() {
    const {leftStart, leftEnd} = this.contextRange;
    return leftEnd - leftStart + 1;
  }

  private createExpandAllButtonContainer() {
    return html` <div
      class="style-scope gr-diff aboveBelowButtons fullExpansion"
    >
      ${this.createContextButton(ContextButtonType.ALL, this.numLines)}
    </div>`;
  }

  private createContextButton(type: ContextButtonType, linesToExpand: number) {
    let text = '';
    let groups: GrDiffGroup[] = []; // The groups that replace this one if tapped.
    let ariaLabel = '';
    let classes = 'contextControlButton showContext ';

    if (type === ContextButtonType.ALL) {
      text = `+${pluralize(linesToExpand, 'common line')}`;
      ariaLabel = `Show ${pluralize(linesToExpand, 'common line')}`;
      classes +=
        this.showAbove && this.showBelow
          ? 'centeredButton'
          : this.showAbove
          ? 'aboveButton'
          : 'belowButton';
      if (this.partialContent) {
        // Expanding content would require load of more data
        text += ' (too large)';
      }
      groups.push(...this.contextGroups);
    } else if (type === ContextButtonType.ABOVE) {
      groups = hideInContextControl(
        this.contextGroups,
        linesToExpand,
        this.numLines
      );
      text = `+${linesToExpand}`;
      classes += 'aboveButton';
      ariaLabel = `Show ${pluralize(linesToExpand, 'line')} above`;
    } else if (type === ContextButtonType.BELOW) {
      groups = hideInContextControl(
        this.contextGroups,
        0,
        this.numLines - linesToExpand
      );
      text = `+${linesToExpand}`;
      classes += 'belowButton';
      ariaLabel = `Show ${pluralize(linesToExpand, 'line')} below`;
    } else if (type === ContextButtonType.BLOCK_ABOVE) {
      groups = hideInContextControl(
        this.contextGroups,
        linesToExpand,
        this.numLines
      );
      text = '+Block';
      classes += 'aboveButton';
      ariaLabel = 'Show block above';
    } else if (type === ContextButtonType.BLOCK_BELOW) {
      groups = hideInContextControl(
        this.contextGroups,
        0,
        this.numLines - linesToExpand
      );
      text = '+Block';
      classes += 'belowButton';
      ariaLabel = 'Show block below';
    }
    const expandHandler = (e: Event) => {
      e.stopPropagation();
      if (type === ContextButtonType.ALL && this.partialContent) {
        const {leftStart, leftEnd, rightStart, rightEnd} = this.contextRange;
        const lineRange = {
          left: {
            start_line: leftStart,
            end_line: leftEnd,
          },
          right: {
            start_line: rightStart,
            end_line: rightEnd,
          },
        };
        fire(this, 'content-load-needed', {
          lineRange,
        });
      } else {
        fire(this, 'diff-context-expanded', {
          groups,
          section: this.section,
          numLines: this.numLines,
          buttonType: type,
          expandedLines: linesToExpand,
        });
      }
    };

    const button = html` <gr-button
      class="${classes}"
      link="true"
      no-uppercase="true"
      aria-label="${ariaLabel}"
      @click="${expandHandler}"
    >
      <span class="showContext">${text}</span>
    </gr-button>`;
    return button;
  }

  private get showPartialLinks() {
    return this.numLines > PARTIAL_CONTEXT_AMOUNT;
  }

  private createPartialExpansionButtons() {
    if (!this.showPartialLinks) {
      return undefined;
    }
    let aboveButton;
    let belowButton;
    if (this.showAbove) {
      aboveButton = this.createContextButton(
        ContextButtonType.ABOVE,
        PARTIAL_CONTEXT_AMOUNT
      );
    }
    if (this.showBelow) {
      belowButton = this.createContextButton(
        ContextButtonType.BELOW,
        PARTIAL_CONTEXT_AMOUNT
      );
    }
    return aboveButton || belowButton
      ? html` <div class="aboveBelowButtons partialExpansion">
          ${aboveButton} ${belowButton}
        </div>`
      : undefined;
  }

  /**
   * Checks if the collapsed section contains unavailable content (skip chunks).
   */
  private get partialContent() {
    return this.contextGroups.find(c => !!c.skip) !== undefined;
  }

  private createBlockExpansionButtons() {
    if (
      !this.showPartialLinks ||
      !this.renderPreferences?.use_block_expansion ||
      this.partialContent
    ) {
      return undefined;
    }
    let aboveBlockButton;
    let belowBlockButton;
    if (this.showAbove) {
      aboveBlockButton = this._createBlockButton(
        ContextButtonType.BLOCK_ABOVE,
        this.numLines,
        this.contextRange.rightStart - 1
      );
    }
    if (this.showBelow) {
      belowBlockButton = this._createBlockButton(
        ContextButtonType.BLOCK_BELOW,
        this.numLines,
        this.contextRange.rightEnd + 1
      );
    }
    if (aboveBlockButton || belowBlockButton) {
      return html` <div class="aboveBelowButtons blockExpansion">
        ${aboveBlockButton} ${belowBlockButton}
      </div>`;
    }
    return undefined;
  }

  private _createBlockButton(
    buttonType: ContextButtonType,
    numLines: number,
    referenceLine: number
  ) {
    const syntaxTree = this.diff.meta_b.syntax_tree;
    const containingBlock = findMostNestedContainingBlock(
      referenceLine,
      syntaxTree
    );
    let linesToExpand = numLines;
    if (containingBlock) {
      const {range} = containingBlock;
      const targetLine =
        buttonType === ContextButtonType.BLOCK_ABOVE
          ? range.end_line
          : range.start_line;
      const distanceToTargetLine = Math.abs(targetLine - referenceLine);
      if (distanceToTargetLine < numLines) {
        linesToExpand = distanceToTargetLine;
      }
    }
    return this.createContextButton(buttonType, linesToExpand);
  }

  private get contextRange() {
    return {
      leftStart: this.contextGroups[0].lineRange.left.start_line,
      leftEnd: this.contextGroups[this.contextGroups.length - 1].lineRange.left
        .end_line,
      rightStart: this.contextGroups[0].lineRange.right.start_line,
      rightEnd: this.contextGroups[this.contextGroups.length - 1].lineRange
        .right.end_line,
    };
  }

  render() {
    if (!this.numLines) console.error('context group without lines');

    return html`
      ${GrContextControls.customStyles} ${this.createExpandAllButtonContainer()}
      ${this.createPartialExpansionButtons()}
      ${this.createBlockExpansionButtons()}
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-context-controls': GrContextControls;
  }
}
