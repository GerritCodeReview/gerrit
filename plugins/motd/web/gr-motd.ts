import {property, customElement} from 'lit/decorators';
import {css, html, LitElement} from 'lit';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';

/** A banner that loads a message from the motd~motd endpoint */
@customElement('gr-motd-banner')
export class MotdBanner extends LitElement {

  /** Guaranteed to be provided by the 'banner' endpoint. */
  @property()
  plugin!: PluginApi;

  @property()
  message = '';

  static override get styles() {
    return css`
      div {
        background-color: var(--warning-background);
        padding: var(--spacing-m) var(--spacing-m);
        text-align: center;
      }
      iron-icon {
        color: var(--warning-foreground);
        width: 20px;
        height: 20px;
      }
    `;
  }

  override render() {
    if (!this.message) {
      return undefined;
    }
    return html`
        <div>
          <iron-icon icon="gr-icons:warning"></iron-icon>
          ${this.message} - edited
        </div>`;
  }

  override connectedCallback() {
    super.connectedCallback();

    this.plugin.restApi()
        .get<string>(`/config/server/${this.plugin.getPluginName()}~motd`)
        .then((motd: string) => {
          if (!motd) {
            motd = '';
          }
          this.message = motd;
        });
  }
}
