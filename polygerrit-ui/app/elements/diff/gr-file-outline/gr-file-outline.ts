/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {changeModelToken} from '../../../models/change/change-model';
import {resolve} from '../../../models/dependency';
import {changeViewModelToken} from '../../../models/views/change';
import {getAppContext} from '../../../services/app-context';
import {
  BasePatchSetNum,
  NumericChangeId,
  RevisionPatchSetNum,
} from '../../../types/common';
import {subscribe} from '../../lit/subscription-controller';
import {SyntaxNode, Tree} from '@lezer/common';
import {parser} from '@lezer/java';

@customElement('gr-file-outline')
export class GrFileOutline extends LitElement {
  private readonly restApiService = getAppContext().restApiService;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getViewModel = resolve(this, changeViewModelToken);

  @property({type: Object})
  changeNum?: NumericChangeId;

  @property({type: Object})
  patchNum?: RevisionPatchSetNum;

  @property({type: Object})
  basePatchNum?: BasePatchSetNum;

  @property({type: Object})
  diffPath?: string;

  @property({type: Object})
  diff?: {original: string; modified: string};

  @state()
  private context = 'Click a line for context...';

  @state()
  private selectedLine = 0;

  private tree?: Tree;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().changeNum$,
      changeNum => {
        this.changeNum = changeNum;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().patchNum$,
      patchNum => {
        this.patchNum = patchNum;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().basePatchNum$,
      basePatchNum => {
        this.basePatchNum = basePatchNum;
      }
    );
    subscribe(
      this,
      () => this.getViewModel().diffPath$,
      diffPath => {
        this.diffPath = diffPath;
      }
    );
  }

  static override styles = css`
    pre {
      margin: 0;
    }
  `;

  override willUpdate() {
    if (
      this.changeNum &&
      this.patchNum &&
      this.basePatchNum &&
      this.diffPath &&
      this.diff === undefined
    ) {
      this.restApiService
        .getDiff(
          this.changeNum,
          this.basePatchNum,
          this.patchNum,
          this.diffPath
        )
        .then(diffInfo => {
          const left = diffInfo!.content
            .flatMap(content => content.a ?? content.ab ?? [])
            .join('\n');
          const right = diffInfo!.content
            .flatMap(content => content.b ?? content.ab ?? [])
            .join('\n');
          this.diff = {original: left, modified: right};
          this.tree = parser.parse(this.diff.modified);
        });
    }
  }

  override render() {
    if (this.diff === undefined) {
      return;
    }
    return html`
      <div style="display: flex; gap: 30px;">
        <div>
          ${this.diff.modified
            .split('\n')
            .map(
              (line, i) =>
                html`
                  <pre
                    @click=${() => this.onLineClick(i + 1)}
                    style=${this.selectedLine === i + 1
                      ? 'background: darkslategray'
                      : ''}
                  >
${i + 1}: ${line}</pre
                  >
                `
            )}
        </div>
        <pre
          style="position: fixed; top: 120px; left: 130ch; white-space: pre-wrap; background: darkslategray"
        >
${this.context}</pre
        >
      </div>
    `;
  }

  private onLineClick(lineNum: number) {
    this.selectedLine = lineNum;
    const relatedBlocks: string[] = [];
    this.tree!.iterate({
      enter: n => {
        if (
          this.lineForPos(n.from) <= lineNum &&
          this.lineForPos(n.to) >= lineNum
        ) {
          const pathParts: string[] = [];
          let node: SyntaxNode | null = n.node;
          while (node !== null) {
            pathParts.push(node.name);
            node = node.parent;
          }
          const path = pathParts.reverse().join(' > ');
          const text = this.diff!.modified.substring(n.from, n.to);
          relatedBlocks.push(
            `lines ${this.lineCharForPos(n.from)}-${this.lineCharForPos(
              n.to
            )}: ${path}\n${text.substring(0, 100)}${
              text.length > 100 ? '...' : ''
            }`
          );
        }
        return true;
      },
    });
    this.context = relatedBlocks.join('\n');
  }

  private lineForPos(pos: number): number {
    const section = this.diff!.modified.substring(0, pos);
    const lines = section.split('\n');
    const line = lines.length;
    return line;
  }

  private lineCharForPos(pos: number): string {
    const section = this.diff!.modified.substring(0, pos);
    const lines = section.split('\n');
    const line = lines.length;
    const col = lines.at(-1)!.length;
    return `${line}:${col}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-outline': GrFileOutline;
  }
}
