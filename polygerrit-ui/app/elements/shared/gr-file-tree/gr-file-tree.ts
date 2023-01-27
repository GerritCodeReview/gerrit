/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {changeModelToken} from '../../../models/change/change-model';
import {resolve} from '../../../models/dependency';

interface TreeNode {
  name: string;
  // present for leaf nodes only
  fullPath?: string;
  children: Map<string, TreeNode>;
}

@customElement('gr-file-tree')
export class GrFileTree extends LitElement {
  @property({type: Array})
  files: string[] = [];

  @property({type: String})
  selectedPath = '';

  static override styles = css`
    :host {
      display: block;
      padding: 20px;
    }
  `;

  override render() {
    const tree = constructFileTree(this.files);
    return html`
      <gr-file-tree-node
        .node=${tree}
        .depth=${0}
        .selectedPath=${this.selectedPath}
      ></gr-file-tree-node>
    `;
  }
}

@customElement('gr-file-tree-node')
class GrFileTreeNode extends LitElement {
  @property({type: Object})
  node?: TreeNode;

  @property({type: Number})
  depth = 0;

  @property({type: String})
  selectedPath = '';

  static override styles = css`
    :host {
      display: block;
    }
  `;

  override render() {
    if (this.node === undefined) {
      return nothing;
    }
    if (this.node.fullPath) {
      return html`
        <gr-file-tree-file-node
          .node=${this.node}
          .depth=${this.depth}
          .selectedPath=${this.selectedPath}
        ></gr-file-tree-file-node>
      `;
    } else {
      return html`
        <gr-file-tree-folder-node
          .node=${this.node}
          .depth=${this.depth}
          .selectedPath=${this.selectedPath}
        ></gr-file-tree-folder-node>
      `;
    }
  }
}

@customElement('gr-file-tree-folder-node')
class GrFileTreeFolderNode extends LitElement {
  @property({type: Object})
  node?: TreeNode;

  @property({type: Number})
  depth = 0;

  @property({type: String})
  selectedPath = '';

  @state()
  private expanded = true;

  static override styles = css`
    :host {
      display: block;
      cursor: pointer;
    }
    gr-file-tree-node {
      padding-left: 10px;
    }
  `;

  constructor() {
    super();
    this.addEventListener('click', e => {
      // Don't trigger expand/collapse of higher folders
      e.stopPropagation();
      this.expanded = !this.expanded;
    });
  }

  override render() {
    if (this.node === undefined) {
      return nothing;
    }
    return html`
      ${when(
        this.node.name !== '',
        () => html`<div>${this.expanded ? 'v' : '>'} ${this.node!.name}/</div>`
      )}
      ${when(this.expanded, () =>
        Array.from(this.node!.children.values()).map(
          childNode =>
            html`<gr-file-tree-node
              .node=${childNode}
              .depth=${this.depth + 1}
              .selectedPath=${this.selectedPath}
            ></gr-file-tree-node>`
        )
      )}
    `;
  }
}

@customElement('gr-file-tree-file-node')
class GrFileTreeFileNode extends LitElement {
  @property({type: Object})
  node?: TreeNode;

  @property({type: Number})
  depth = 0;

  @property({type: String})
  selectedPath = '';

  private readonly getChangeModel = resolve(this, changeModelToken);

  static override styles = css`
    :host {
      display: block;
      cursor: pointer;
      padding-left: 10px;
    }
    div.selected {
      background-color: var(--gray-700);
    }
  `;

  constructor() {
    super();
    this.addEventListener('click', e => {
      e.stopPropagation();
      if (this.node?.fullPath === this.selectedPath) return;
      this.getChangeModel().navigateToDiff({path: this.node!.fullPath!});
    });
  }

  override render() {
    if (this.node === undefined) {
      return nothing;
    }
    return html`<div
      class=${this.node.fullPath === this.selectedPath ? 'selected' : ''}
    >
      ${this.node.name}
    </div>`;
  }
}

function constructFileTree(files: string[]): TreeNode {
  const tree: TreeNode = {name: '', children: new Map()};

  // first construct naive tree
  for (const file of files) {
    const pathParts = file.split('/');
    let node = tree;
    for (let partIndex = 0; partIndex < pathParts.length; partIndex++) {
      const pathPart = pathParts[partIndex];
      if (partIndex < pathParts.length - 1) {
        if (!node.children.has(pathPart)) {
          node.children.set(pathPart, {name: pathPart, children: new Map()});
        }
        node = node.children.get(pathPart)!;
      } else {
        node.children.set(pathPart, {
          name: pathPart,
          children: new Map(),
          fullPath: file,
        });
      }
    }
  }

  // then walk the tree to flatten nodes with a single child
  flattenTree(tree);
  return tree;
}

function flattenTree(node: TreeNode): void {
  const childNames = Array.from(node.children.keys());
  if (
    childNames.length === 1 &&
    Array.from(node.children.get(childNames[0])!.children.keys()).length > 0
  ) {
    const onlyChild = node.children.get(childNames[0])!;
    node.name = `${node.name.length > 0 ? `${node.name}/` : ''}${
      onlyChild.name
    }`;
    node.children = onlyChild.children;
    flattenTree(node);
  }
  const updatedChildNames = Array.from(node.children.keys());
  for (const childName of updatedChildNames) {
    flattenTree(node.children.get(childName)!);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-tree': GrFileTree;
    'gr-file-tree-node': GrFileTreeNode;
    'gr-file-tree-folder-node': GrFileTreeFolderNode;
    'gr-file-tree-file-node': GrFileTreeFileNode;
  }
}
