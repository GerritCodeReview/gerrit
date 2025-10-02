import {customElement, property, query, state} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {styleMap} from 'lit/directives/style-map.js';

const SIDEBAR_MIN_WIDTH = 300;

@customElement('gr-sidebar')
export class GrSidebar extends LitElement {
  @query('.sidebar-wrapper') sidebarWrapper?: HTMLElement;

  @state()
  private sidebarWidthPx = SIDEBAR_MIN_WIDTH;

  @property()
  hideSide = true;

  private isSidebarResizing = false;

  private sidebarResizingStartPosPx = 0;

  private sidebarResizingStartWidthPx = 0;

  static override get styles() {
    return [
      css`
        :host {
          display: block;
          --sidebar-height: calc(100vh - var(--sidebar-top));
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

  constructor() {
    super();
    this.addEventListener('mousemove', e => this.resizeSidebar(e));
    this.addEventListener('mouseup', () => this.stopSidebarResize());
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
            @mousedown=${this.startSidebarResize}
            @mouseup=${this.stopSidebarResize}
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
  }

  private stopSidebarResize() {
    if (!this.isSidebarResizing) return;

    // Re-enable user selection when resizing is done.
    document.body.style.setProperty('user-select', 'auto');
    this.isSidebarResizing = false;
    this.sidebarResizingStartPosPx = 0;
    this.sidebarResizingStartWidthPx = 0;
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
    'gr-sidebar': GrSidebar;
  }
}
