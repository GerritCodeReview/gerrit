/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, property, query, state} from 'lit/decorators.js';
import {css, html, LitElement, nothing} from 'lit';
import {styleMap} from 'lit/directives/style-map.js';

const SIDEBAR_MIN_WIDTH = 250;

/**
 * A component that displays content in a main area and a resizable sidebar.
 * The sidebar can be toggled between hidden and visible and positioned on the left or right.
 *
 * slot main - The content to be displayed in the main area.
 * slot side - The content to be displayed in the sidebar.
 */
@customElement('gr-content-with-sidebar')
export class GrContentWithSidebar extends LitElement {
  @query('.sidebar-wrapper') sidebarWrapper?: HTMLElement;

  @state()
  private sidebarWidthPx = 400;

  @property()
  hideSide = true;

  @property()
  side: 'left' | 'right' = 'right';

  @property({type: Number})
  minWidth = SIDEBAR_MIN_WIDTH;

  private isSidebarResizing = false;

  private sidebarResizingStartPosPx = 0;

  private sidebarResizingStartWidthPx = 0;

  private readonly boundResizeSidebar = (e: MouseEvent) =>
    this.resizeSidebar(e);

  private readonly boundStopSidebarResize = () => this.stopSidebarResize();

  static override get styles() {
    return [
      css`
        :host {
          display: block;
          position: relative;
          --sidebar-height: calc(100vh - var(--sidebar-top, 0px));
        }
        .sidebar-wrapper {
          z-index: 50;
          position: absolute;
          display: flex;
          top: 0;
          bottom: calc(0px - var(--sidebar-bottom-overflow, 0px));
          min-width: 250px;
          max-width: 100%;
          background-color: var(--background-color-secondary);
        }
        .sidebar-wrapper.right {
          right: 0;
          left: auto;
        }
        .sidebar-wrapper.left {
          left: 0;
          right: auto;
        }
        .sidebar {
          position: sticky;
          top: var(--sidebar-top, 0px);
          height: var(--sidebar-height);
          box-sizing: border-box;
          overflow: auto;
          flex-grow: 1;
          font-size: 14px;
        }
        .resizer-wrapper {
          position: sticky;
          top: var(--sidebar-top, 0px);
          height: var(--sidebar-height);
          z-index: 51;
        }
        .resizer {
          background-color: var(--background-color-secondary);
          width: 7px;
          cursor: ew-resize;
          position: absolute;
          top: 0;
          bottom: 0;
          box-sizing: border-box;
        }
        .resizer.right-side {
          left: -7px;
          border-left: 1px solid var(--border-color);
        }
        .resizer.right-side:hover {
          background-color: var(--background-color-tertiary);
          width: 11px;
          left: -9px;
        }
        .resizer.left-side {
          right: -7px;
          border-right: 1px solid var(--border-color);
        }
        .resizer.left-side:hover {
          background-color: var(--background-color-tertiary);
          width: 11px;
          right: -9px;
        }
      `,
    ];
  }

  override render() {
    const widthPx = this.hideSide ? 0 : this.sidebarWidthPx;
    const mainStyle =
      this.side === 'left'
        ? styleMap({
            marginLeft: `${widthPx}px`,
            width: `calc(100% - ${widthPx}px)`,
          })
        : styleMap({
            width: `calc(100% - ${widthPx}px)`,
          });
    return html`
      <div>
        <div style=${mainStyle}>
          <slot name="main"></slot>
        </div>
        ${this.renderSidebar()}
      </div>
    `;
  }

  private renderSidebar() {
    if (this.hideSide) return;
    const sideClass = this.side === 'left' ? 'left' : 'right';
    const resizerClass = this.side === 'left' ? 'left-side' : 'right-side';
    return html`
      <div
        class="sidebar-wrapper ${sideClass}"
        style=${styleMap({width: `${this.sidebarWidthPx}px`})}
      >
        ${this.side === 'right' ? this.renderResizer(resizerClass) : nothing}
        <div class="sidebar">
          <slot name="side"></slot>
        </div>
        ${this.side === 'left' ? this.renderResizer(resizerClass) : nothing}
      </div>
    `;
  }

  private renderResizer(resizerClass: string) {
    return html`
      <div class="resizer-wrapper">
        <div
          class="resizer ${resizerClass}"
          role="separator"
          aria-orientation="vertical"
          aria-valuenow=${this.sidebarWidthPx}
          aria-label="Resize sidebar"
          tabindex="0"
          @mousedown=${this.startSidebarResize}
        ></div>
      </div>
    `;
  }

  private startSidebarResize(event: MouseEvent) {
    if (this.isSidebarResizing) return;

    // Disable user selection while resizing.
    document.body.style.setProperty('user-select', 'none');
    this.isSidebarResizing = true;
    this.sidebarResizingStartPosPx = event.clientX;
    this.sidebarResizingStartWidthPx =
      this.sidebarWrapper!.getBoundingClientRect().width;
    window.addEventListener('mousemove', this.boundResizeSidebar);
    window.addEventListener('mouseup', this.boundStopSidebarResize);
  }

  private stopSidebarResize() {
    if (!this.isSidebarResizing) return;

    // Re-enable user selection when resizing is done.
    document.body.style.setProperty('user-select', 'auto');
    this.isSidebarResizing = false;
    this.sidebarResizingStartPosPx = 0;
    this.sidebarResizingStartWidthPx = 0;
    window.removeEventListener('mousemove', this.boundResizeSidebar);
    window.removeEventListener('mouseup', this.boundStopSidebarResize);
  }

  private resizeSidebar(event: MouseEvent) {
    if (!this.isSidebarResizing || event.buttons === 0) return;

    const widthDiffPx = event.clientX - this.sidebarResizingStartPosPx;
    const rawWidth =
      this.side === 'right'
        ? this.sidebarResizingStartWidthPx - widthDiffPx
        : this.sidebarResizingStartWidthPx + widthDiffPx;
    this.sidebarWidthPx = Math.max(rawWidth, this.minWidth);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-content-with-sidebar': GrContentWithSidebar;
  }
}
