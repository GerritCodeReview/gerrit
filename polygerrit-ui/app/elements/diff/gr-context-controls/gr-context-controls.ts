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
import '@polymer/paper-tooltip/paper-tooltip';

import '../../shared/gr-button/gr-button';
import {pluralize} from '../../../utils/string-util';
import {fire} from '../../../utils/event-util';
import {DiffInfo} from '../../../types/diff';
import {assertIsDefined} from '../../../utils/common-util';
import {
  css,
  customElement,
  html,
  LitElement,
  property,
  TemplateResult,
} from 'lit-element';

import {
  ContextButtonType,
  RenderPreferences,
  SyntaxBlock,
} from '../../../api/diff';

import {GrDiffGroup, hideInContextControl} from '../gr-diff/gr-diff-group';

const PARTIAL_CONTEXT_AMOUNT = 10;

/**
 * Traverses a hierarchical structure of syntax blocks and
 * finds the most local/nested block that can be associated line.
 * It finds the closest block that contains the whole line and
 * returns the whole path from the syntax layer (blocks) sent as parameter
 * to the most nested block - the complete path from the top to bottom layer of
 * a syntax tree. Example: [myNamepace, MyClass, myMethod1, aLocalFunctionInsideMethod1]
 *
 * @param lineNum line number for the targeted line.
 * @param blocks Blocks for a specific syntax level in the file (to allow recursive calls)
 */
function findBlockTreePathForLine(
  lineNum: number,
  blocks?: SyntaxBlock[]
): SyntaxBlock[] {
  const containingBlock = blocks?.find(
    ({range}) => range.start_line < lineNum && range.end_line > lineNum
  );
  if (!containingBlock) return [];
  const innerPathInChild = findBlockTreePathForLine(
    lineNum,
    containingBlock?.children
  );
  return [containingBlock].concat(innerPathInChild);
}

@customElement('gr-context-controls')
export class GrContextControls extends LitElement {
  @property({type: Object}) renderPreferences?: RenderPreferences;

  @property({type: Object}) diff?: DiffInfo;

  @property({type: Object}) section?: HTMLElement;

  @property({type: Object}) contextGroups: GrDiffGroup[] = [];

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
    }
    .belowButton {
      top: calc(100% + var(--divider-border));
    }
    .breadcrumbTooltip {
      white-space: nowrap;
    }
  `;

  // To pass CSS mixins for @apply to Polymer components, they need to be
  // wrapped in a <custom-style>.
  static customStyles = html`
    <custom-style>
      <style>
        .centeredButton {
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
        .aboveButton {
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

  private numLines() {
    const {leftStart, leftEnd} = this.contextRange();
    return leftEnd - leftStart + 1;
  }

  private createExpandAllButtonContainer() {
    return html` <div
      class="style-scope gr-diff aboveBelowButtons fullExpansion"
    >
      ${this.createContextButton(ContextButtonType.ALL, this.numLines())}
    </div>`;
  }

  /**
   * Creates a specific expansion button (e.g. +X common lines, +10, +Block).
   */
  private createContextButton(
    type: ContextButtonType,
    linesToExpand: number,
    tooltip?: TemplateResult
  ) {
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
        this.numLines()
      );
      text = `+${linesToExpand}`;
      classes += 'aboveButton';
      ariaLabel = `Show ${pluralize(linesToExpand, 'line')} above`;
    } else if (type === ContextButtonType.BELOW) {
      groups = hideInContextControl(
        this.contextGroups,
        0,
        this.numLines() - linesToExpand
      );
      text = `+${linesToExpand}`;
      classes += 'belowButton';
      ariaLabel = `Show ${pluralize(linesToExpand, 'line')} below`;
    } else if (type === ContextButtonType.BLOCK_ABOVE) {
      groups = hideInContextControl(
        this.contextGroups,
        linesToExpand,
        this.numLines()
      );
      text = '+Block';
      classes += 'aboveButton';
      ariaLabel = 'Show block above';
    } else if (type === ContextButtonType.BLOCK_BELOW) {
      groups = hideInContextControl(
        this.contextGroups,
        0,
        this.numLines() - linesToExpand
      );
      text = '+Block';
      classes += 'belowButton';
      ariaLabel = 'Show block below';
    }
    const expandHandler = this.createExpansionHandler(
      linesToExpand,
      type,
      groups
    );

    const button = html` <gr-button
      class="${classes}"
      link="true"
      no-uppercase="true"
      aria-label="${ariaLabel}"
      @click="${expandHandler}"
    >
      <span class="showContext">${text}</span>
      ${tooltip}
    </gr-button>`;
    return button;
  }

  private createExpansionHandler(
    linesToExpand: number,
    type: ContextButtonType,
    groups: GrDiffGroup[]
  ) {
    return (e: Event) => {
      e.stopPropagation();
      if (type === ContextButtonType.ALL && this.partialContent) {
        const {leftStart, leftEnd, rightStart, rightEnd} = this.contextRange();
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
        assertIsDefined(this.section, 'section');
        fire(this, 'diff-context-expanded', {
          groups,
          section: this.section!,
          numLines: this.numLines(),
          buttonType: type,
          expandedLines: linesToExpand,
        });
      }
    };
  }

  private showPartialLinks() {
    return this.numLines() > PARTIAL_CONTEXT_AMOUNT;
  }

  /**
   * Creates a container div with partial (+10) expansion buttons (above and/or below).
   */
  private createPartialExpansionButtons() {
    if (!this.showPartialLinks()) {
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
    return this.contextGroups.some(c => !!c.skip);
  }

  /**
   * Creates a container div with block expansion buttons (above and/or below).
   */
  private createBlockExpansionButtons() {
    if (
      !this.showPartialLinks() ||
      !this.renderPreferences?.use_block_expansion ||
      this.partialContent
    ) {
      return undefined;
    }
    let aboveBlockButton;
    let belowBlockButton;
    if (this.showAbove) {
      aboveBlockButton = this.createBlockButton(
        ContextButtonType.BLOCK_ABOVE,
        this.numLines(),
        this.contextRange().rightStart - 1
      );
    }
    if (this.showBelow) {
      belowBlockButton = this.createBlockButton(
        ContextButtonType.BLOCK_BELOW,
        this.numLines(),
        this.contextRange().rightEnd + 1
      );
    }
    if (aboveBlockButton || belowBlockButton) {
      return html` <div class="aboveBelowButtons blockExpansion">
        ${aboveBlockButton} ${belowBlockButton}
      </div>`;
    }
    return undefined;
  }

  private createBlockButtonTooltip(
    buttonType: ContextButtonType,
    syntaxPath: SyntaxBlock[],
    linesToExpand: number
  ) {
    // Create breadcrumb string:
    // myNamepace > MyClass > myMethod1 > aLocalFunctionInsideMethod1 > (anonymous)
    const tooltipText = syntaxPath.length
      ? syntaxPath.map(b => b.name || '(anonymous)').join(' > ')
      : `${linesToExpand} common lines`;

    const position =
      buttonType === ContextButtonType.BLOCK_ABOVE ? 'top' : 'bottom';
    return html`<paper-tooltip offset="10" position="${position}"
      ><div class="breadcrumbTooltip">${tooltipText}</div></paper-tooltip
    >`;
  }

  private createBlockButton(
    buttonType: ContextButtonType,
    numLines: number,
    referenceLine: number
  ) {
    assertIsDefined(this.diff, 'diff');
    const syntaxTree = this.diff!.meta_b.syntax_tree;
    const outlineSyntaxPath = findBlockTreePathForLine(
      referenceLine,
      syntaxTree
    );
    let linesToExpand = numLines;
    if (outlineSyntaxPath.length) {
      const {range} = outlineSyntaxPath[outlineSyntaxPath.length - 1];
      const targetLine =
        buttonType === ContextButtonType.BLOCK_ABOVE
          ? range.end_line
          : range.start_line;
      const distanceToTargetLine = Math.abs(targetLine - referenceLine);
      if (distanceToTargetLine < numLines) {
        linesToExpand = distanceToTargetLine;
      }
    }
    const tooltip = this.createBlockButtonTooltip(
      buttonType,
      outlineSyntaxPath,
      linesToExpand
    );
    return this.createContextButton(buttonType, linesToExpand, tooltip);
  }

  private contextRange() {
    return {
      leftStart: this.contextGroups[0].lineRange.left.start_line,
      leftEnd: this.contextGroups[this.contextGroups.length - 1].lineRange.left
        .end_line,
      rightStart: this.contextGroups[0].lineRange.right.start_line,
      rightEnd: this.contextGroups[this.contextGroups.length - 1].lineRange
        .right.end_line,
    };
  }

  private hasValidProperties() {
    return !!(this.diff && this.section && this.contextGroups?.length);
  }

  render() {
    if (!this.hasValidProperties()) {
      console.error('Invalid properties for gr-context-controls!');
      return html`<p>invalid properties</p>`;
    }
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
