/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, property, query, state} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {styleMap} from 'lit/directives/style-map.js';

const SIDEBAR_MIN_WIDTH = 300;

/**
 * A component that displays content in a main area and a resizable sidebar.
 * The sidebar can be toggled between hidden and visible.
 *
 * slot main - The content to be displayed in the main area.
 * slot side - The content to be displayed in the sidebar.
 */
@customElement('gr-content-with-sidebar')
export class GrContentWithSidebar extends LitElement {
  @query('.sidebar-wrapper') sidebarWrapper?: HTMLElement;

  @state()
  private sidebarWidthPx = SIDEBAR_MIN_WIDTH;

  @property()
  hideSide = true;

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
          --sidebar-height: calc(100vh - var(--sidebar-top));
          --sidebar-top: var(--sidebar-top, 0px);
          --sidebar-bottom-overflow: var(--sidebar-bottom-overflow, 0px);
        }
        .sidebar-wrapper {
          z-index: 50;
          position: absolute;
          display: flex;
          top: 0;
          bottom: calc(0px - var(--sidebar-bottom-overflow));
          right: 0;
          min-width: 300px;
          max-width: 100%;
          background-color: var(--background-color-secondary);
        }
        .sidebar {
          position: sticky;
          top: var(--sidebar-top);
          height: var(--sidebar-height);
          box-sizing: border-box;
          overflow: auto;
          flex-grow: 1;
          padding: var(--spacing-l);
          font-size: 14px;
        }
        .resizer-wrapper {
          position: sticky;
          top: var(--sidebar-top);
          height: var(--sidebar-height);
          z-index: 51;
        }
        .resizer {
          background-color: var(--background-color-secondary);
          width: 7px;
          border-left: 1px solid var(--border-color);
          cursor: ew-resize;
          position: absolute;
          top: 0;
          bottom: 0;
          left: -7px;
          box-sizing: border-box;
        }
        .resizer:hover {
          background-color: var(--background-color-tertiary);
          width: 11px;
          left: -9px;
        }
      `,
    ];
  }

  override render() {
    const widthPx = this.hideSide ? 0 : this.sidebarWidthPx;
    return html`
      <div>
        <div style=${styleMap({width: `calc(100% - ${widthPx}px)`})}>
          <slot name="main"></slot>
        </div>
        ${this.renderSidebar()}
      </div>
    `;
  }

  private renderSidebar() {
    if (this.hideSide) return;
    return html`
      <div
        class="sidebar-wrapper"
        style=${styleMap({width: `${this.sidebarWidthPx}px`})}
      >
        <div class="resizer-wrapper">
          <div
            class="resizer"
            role="separator"
            aria-orientation="vertical"
            aria-valuenow=${this.sidebarWidthPx}
            aria-label="Resize sidebar"
            tabindex="0"
            @mousedown=${this.startSidebarResize}
          ></div>
        </div>
        <div class="sidebar">
          <slot name="side"></slot>
        </div>
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
    this.sidebarWidthPx = Math.max(
      this.sidebarResizingStartWidthPx - widthDiffPx,
      SIDEBAR_MIN_WIDTH
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-content-with-sidebar': GrContentWithSidebar;
  }
}
