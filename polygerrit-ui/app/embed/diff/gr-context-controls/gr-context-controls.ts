/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
import {of, EMPTY, Subject} from 'rxjs';
import {switchMap, delay} from 'rxjs/operators';
import '../../../elements/shared/gr-button/gr-button';
import {pluralize} from '../../../utils/string-util';
import {fire} from '../../../utils/event-util';
import {assertIsDefined} from '../../../utils/common-util';
import {
  css,
  html,
  LitElement,
  nothing,
  PropertyValues,
  TemplateResult,
} from 'lit';
import {property, state} from 'lit/decorators.js';
import {subscribe} from '../../../elements/lit/subscription-controller';
import {
  ContextButtonType,
  DiffContextButtonHoveredDetail,
  RenderPreferences,
  SyntaxBlock,
} from '../../../api/diff';
import {
  GrDiffGroup,
  GrDiffGroupType,
  hideInContextControl,
} from '../gr-diff/gr-diff-group';
import {resolve} from '../../../models/dependency';
import {diffModelToken} from '../gr-diff-model/gr-diff-model';

declare global {
  interface HTMLElementEventMap {
    'diff-context-button-hovered': CustomEvent<DiffContextButtonHoveredDetail>;
  }
}

const PARTIAL_CONTEXT_AMOUNT = 10;

/**
 * Traverses a hierarchical structure of syntax blocks and
 * finds the most local/nested block that can be associated line.
 * It finds the closest block that contains the whole line and
 * returns the whole path from the syntax layer (blocks) sent as parameter
 * to the most nested block - the complete path from the top to bottom layer of
 * a syntax tree. Example: [myNamespace, MyClass, myMethod1, aLocalFunctionInsideMethod1]
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

/**
 * 'above': Typically only for the context controls at the end of a file. So
 *          only show buttons "above" the middle line of the context control
 *          section.
 * 'below': Typically only for the context controls at the beginning of a file.
 *          So only show buttons "below" the middle line of the context control
 *          section.
 * 'both': Typically for the context controls in the middle of a file. So show
 *         two buttons, one for expanding from the top and one for expanding
 *         from the bottom.
 */
export type GrContextControlsShowConfig = 'above' | 'below' | 'both';

export function getShowConfig(group?: GrDiffGroup, lineCountLeft = 0) {
  const above = showAbove(group, lineCountLeft);
  const below = showBelow(group, lineCountLeft);

  if (above && !below) return 'above';
  if (!above && below) return 'below';

  // Note that !showAbove && !showBelow also intentionally returns 'both'.
  // This means the file is completely collapsed, which is unusual, but at least
  // happens in one test.
  return 'both';
}

/** See GrContextControlsShowConfig for explanation of "above". */
export function showAbove(group?: GrDiffGroup, lineCountLeft = 0) {
  if (group?.type !== GrDiffGroupType.CONTEXT_CONTROL) return false;

  // Note that we could as well use `right.start_line` here. And below we only
  // use `left`, because we are comparing with `lineCountLeft`. But that is
  // just an arbitrary choice.
  const leftStart = group.lineRange.left.start_line;
  const firstGroupIsSkipped = !!group.contextGroups[0].skip;
  if (leftStart > 1 && !firstGroupIsSkipped) return true;

  const leftEnd = group.lineRange.left.end_line;
  const containsWholeFile = lineCountLeft === leftEnd - leftStart + 1;
  return containsWholeFile;
}

/** See GrContextControlsShowConfig for explanation of "below". */
export function showBelow(group?: GrDiffGroup, lineCountLeft = 0) {
  if (group?.type !== GrDiffGroupType.CONTEXT_CONTROL) return false;

  // Note that we could as well use `right.start_line` here. But we would then
  // require a `lineCountRight` parameter for making the comparison.
  const leftEnd = group.lineRange.left.end_line;
  const lastGroupIsSkipped =
    !!group.contextGroups[group.contextGroups.length - 1].skip;

  return leftEnd < lineCountLeft && !lastGroupIsSkipped;
}

/**
 * Renders context control buttons such as "+23 lines" or "+Block". It is only
 * meant to be used to be rendered into a diff table cell of its parent
 * component <gr-context-controls-section>.
 */
export class GrContextControls extends LitElement {
  @property({type: Object}) group?: GrDiffGroup;

  // This is just a property (and not a state), because we want to "reflect".
  @property({type: String, reflect: true})
  showConfig: GrContextControlsShowConfig = 'both';

  @state() syntaxTreeRight?: SyntaxBlock[];

  @state() renderPreferences?: RenderPreferences;

  @state() lineCountLeft = 0;

  private readonly getDiffModel = resolve(this, diffModelToken);

  private expandButtonsHover = new Subject<{
    eventType: 'enter' | 'leave';
    buttonType: ContextButtonType;
    linesToExpand: number;
  }>();

  static override get styles() {
    return [
      css`
        :host {
          display: flex;
          justify-content: center;
          flex-direction: column;
          position: relative;
        }

        :host([showConfig='above']) {
          justify-content: flex-end;
          margin-top: calc(-1px - var(--line-height-normal) - var(--spacing-s));
          margin-bottom: var(--gr-context-controls-margin-bottom);
          height: calc(var(--line-height-normal) + var(--spacing-s));
          .horizontalFlex {
            align-items: end;
          }
        }

        :host([showConfig='below']) {
          justify-content: flex-start;
          margin-top: 1px;
          margin-bottom: calc(
            0px - var(--line-height-normal) - var(--spacing-s)
          );
          .horizontalFlex {
            align-items: start;
          }
        }

        :host([showConfig='both']) {
          margin-top: calc(0px - var(--line-height-normal) - var(--spacing-s));
          margin-bottom: calc(
            0px - var(--line-height-normal) - var(--spacing-s)
          );
          height: calc(
            2 * var(--line-height-normal) + 2 * var(--spacing-s) +
              var(--divider-height)
          );
          .horizontalFlex {
            align-items: center;
          }
        }

        .contextControlButton {
          background-color: var(--default-button-background-color);
          font: var(--context-control-button-font, inherit);
        }

        paper-button {
          text-transform: none;
          align-items: center;
          background-color: var(--background-color);
          font-family: inherit;
          margin: var(--margin, 0);
          min-width: var(--border, 0);
          color: var(--diff-context-control-color);
          border: solid var(--border-color);
          border-width: 1px;
          border-radius: var(--border-radius);
          padding: var(--spacing-s) var(--spacing-l);
        }

        paper-button:hover {
          /* same as defined in gr-button */
          background: rgba(0, 0, 0, 0.12);
        }
        paper-button:focus-visible {
          /* paper-button sets this to 0, thus preventing focus-based styling. */
          outline-width: 1px;
        }

        .aboveBelowButtons {
          display: flex;
          flex-direction: column;
          justify-content: center;
          margin-left: var(--spacing-m);
          position: relative;
        }
        .aboveBelowButtons:first-child {
          margin-left: 0;
          /* Places a default background layer behind the "all button" that can have opacity */
          background-color: var(--default-button-background-color);
        }

        .horizontalFlex {
          display: flex;
          justify-content: center;
          align-items: var(
            --gr-context-controls-horizontal-align-items,
            center
          );
        }

        .aboveButton {
          border-bottom-width: 0;
          border-bottom-right-radius: 0;
          border-bottom-left-radius: 0;
          padding: var(--spacing-xxs) var(--spacing-l);
        }
        .belowButton {
          border-top-width: 0;
          border-top-left-radius: 0;
          border-top-right-radius: 0;
          padding: var(--spacing-xxs) var(--spacing-l);
          margin-top: calc(var(--divider-height) + 2 * var(--spacing-xxs));
        }
        .belowButton:first-child {
          margin-top: 0;
        }
        .breadcrumbTooltip {
          white-space: nowrap;
        }
        .unrelatedChanges {
          color: var(--primary-button-text-color);
          background-color: var(--primary-button-background-color);

          &:hover {
            // TODO(anuragpathak): Update hover colors as per specification.
            color: var(--primary-button-text-color);
            background-color: var(--primary-button-background-color);
          }
        }
      `,
    ];
  }

  constructor() {
    super();
    this.setupButtonHoverHandler();
    subscribe(
      this,
      () => this.getDiffModel().syntaxTreeRight$,
      syntaxTree => (this.syntaxTreeRight = syntaxTree)
    );
    subscribe(
      this,
      () => this.getDiffModel().renderPrefs$,
      renderPrefs => (this.renderPreferences = renderPrefs)
    );
    subscribe(
      this,
      () => this.getDiffModel().lineCountLeft$,
      lineCountLeft => {
        this.lineCountLeft = lineCountLeft;
        this.updateShowConfig();
      }
    );
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('group')) this.updateShowConfig();
  }

  private updateShowConfig() {
    this.showConfig = getShowConfig(this.group, this.lineCountLeft);
  }

  private showBoth() {
    return this.showConfig === 'both';
  }

  private showAbove() {
    return this.showBoth() || this.showConfig === 'above';
  }

  private showBelow() {
    return this.showBoth() || this.showConfig === 'below';
  }

  private setupButtonHoverHandler() {
    subscribe(
      this,
      () =>
        this.expandButtonsHover.pipe(
          switchMap(e => {
            if (e.eventType === 'leave') {
              // cancel any previous delay
              // for mouse enter
              return EMPTY;
            }
            return of(e).pipe(delay(500));
          })
        ),
      ({buttonType, linesToExpand}) => {
        fire(this, 'diff-context-button-hovered', {
          buttonType,
          linesToExpand,
        });
      }
    );
  }

  private numLines() {
    assertIsDefined(this.group);
    // In context groups, there is the same number of lines left and right
    const left = this.group.lineRange.left;
    // Both start and end inclusive, so we need to add 1.
    return left.end_line - left.start_line + 1;
  }

  private createExpandAllButtonContainer() {
    return html` <div class="aboveBelowButtons fullExpansion">
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
    if (!this.group) return;
    let text = '';
    let groups: GrDiffGroup[] = []; // The groups that replace this one if tapped.
    let ariaLabel = '';
    let classes = 'contextControlButton showContext ';

    if (type === ContextButtonType.ALL) {
      if (this.group.hasDeltaGroup()) {
        text = '+ Unrelated changes';
        ariaLabel = 'Show unrelated changes';
        classes += ' unrelatedChanges ';
      } else {
        text = `+${pluralize(linesToExpand, 'common line')}`;
        ariaLabel = `Show ${pluralize(linesToExpand, 'common line')}`;
      }
      classes += this.showBoth()
        ? 'centeredButton'
        : this.showAbove()
        ? 'aboveButton'
        : 'belowButton';
      if (this.group?.hasSkipGroup()) {
        // Expanding content would require load of more data
        text += ' (too large)';
      }
      groups.push(...this.group.contextGroups);
    } else if (type === ContextButtonType.ABOVE) {
      groups = hideInContextControl(
        this.group.contextGroups,
        linesToExpand,
        this.numLines()
      );
      text = `+${linesToExpand}`;
      classes += 'aboveButton';
      ariaLabel = `Show ${pluralize(linesToExpand, 'line')} above`;
    } else if (type === ContextButtonType.BELOW) {
      groups = hideInContextControl(
        this.group.contextGroups,
        0,
        this.numLines() - linesToExpand
      );
      text = `+${linesToExpand}`;
      classes += 'belowButton';
      ariaLabel = `Show ${pluralize(linesToExpand, 'line')} below`;
    } else if (type === ContextButtonType.BLOCK_ABOVE) {
      groups = hideInContextControl(
        this.group.contextGroups,
        linesToExpand,
        this.numLines()
      );
      text = '+Block';
      classes += 'aboveButton';
      ariaLabel = 'Show block above';
    } else if (type === ContextButtonType.BLOCK_BELOW) {
      groups = hideInContextControl(
        this.group.contextGroups,
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

    const mouseHandler = (eventType: 'enter' | 'leave') => {
      this.expandButtonsHover.next({
        eventType,
        buttonType: type,
        linesToExpand,
      });
    };

    const button = html` <paper-button
      class=${classes}
      aria-label=${ariaLabel}
      @click=${expandHandler}
      @mouseenter=${() => mouseHandler('enter')}
      @mouseleave=${() => mouseHandler('leave')}
    >
      <span class="showContext">${text}</span>
      ${tooltip}
    </paper-button>`;
    return button;
  }

  private createExpansionHandler(
    linesToExpand: number,
    type: ContextButtonType,
    groups: GrDiffGroup[]
  ) {
    return (e: Event) => {
      assertIsDefined(this.group);
      e.stopPropagation();
      if (type === ContextButtonType.ALL && this.group?.hasSkipGroup()) {
        fire(this, 'content-load-needed', {
          lineRange: this.group.lineRange,
        });
      } else {
        fire(this, 'diff-context-expanded', {
          numLines: this.numLines(),
          buttonType: type,
          expandedLines: linesToExpand,
        });
        fire(this, 'diff-context-expanded-internal-new', {
          contextGroup: this.group,
          groups,
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
    if (!this.showPartialLinks() || this.group?.hasDeltaGroup()) {
      return undefined;
    }
    let aboveButton;
    let belowButton;
    if (this.showAbove()) {
      aboveButton = this.createContextButton(
        ContextButtonType.ABOVE,
        PARTIAL_CONTEXT_AMOUNT
      );
    }
    if (this.showBelow()) {
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
   * Creates a container div with block expansion buttons (above and/or below).
   */
  private createBlockExpansionButtons() {
    assertIsDefined(this.group, 'group');
    if (
      !this.showPartialLinks() ||
      !this.renderPreferences?.use_block_expansion ||
      this.group?.hasSkipGroup() ||
      this.group?.hasDeltaGroup()
    ) {
      return undefined;
    }
    let aboveBlockButton;
    let belowBlockButton;
    if (this.showAbove()) {
      aboveBlockButton = this.createBlockButton(
        ContextButtonType.BLOCK_ABOVE,
        this.group.lineRange.right.start_line - 1
      );
    }
    if (this.showBelow()) {
      belowBlockButton = this.createBlockButton(
        ContextButtonType.BLOCK_BELOW,
        this.group.lineRange.right.end_line + 1
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
    // myNamespace > MyClass > myMethod1 > aLocalFunctionInsideMethod1 > (anonymous)
    const tooltipText = syntaxPath.length
      ? syntaxPath.map(b => b.name || '(anonymous)').join(' > ')
      : `${linesToExpand} common lines`;

    const position =
      buttonType === ContextButtonType.BLOCK_ABOVE ? 'top' : 'bottom';
    return html`<paper-tooltip offset="10" position=${position}
      ><div class="breadcrumbTooltip">${tooltipText}</div></paper-tooltip
    >`;
  }

  /**
   * Creates a "expand until end of block" button. This is based on syntax tree
   * information for the *right* side of the diff.
   */
  private createBlockButton(
    buttonType: ContextButtonType,
    referenceLineRight: number
  ) {
    if (this.syntaxTreeRight === undefined) return;
    const outlineSyntaxPath = findBlockTreePathForLine(
      referenceLineRight,
      this.syntaxTreeRight
    );
    let linesToExpand = this.numLines();
    if (outlineSyntaxPath.length) {
      const {range} = outlineSyntaxPath[outlineSyntaxPath.length - 1];
      const targetLine =
        buttonType === ContextButtonType.BLOCK_ABOVE
          ? range.end_line
          : range.start_line;
      const distanceToTargetLine = Math.abs(targetLine - referenceLineRight);
      if (distanceToTargetLine < this.numLines()) {
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

  override render() {
    if (!this.group) return nothing;
    return html`
      <div class="horizontalFlex">
        ${this.createExpandAllButtonContainer()}
        ${this.createPartialExpansionButtons()}
        ${this.createBlockExpansionButtons()}
      </div>
    `;
  }
}

customElements.define('gr-context-controls', GrContextControls);

declare global {
  interface HTMLElementTagNameMap {
    'gr-context-controls': GrContextControls;
  }
}
