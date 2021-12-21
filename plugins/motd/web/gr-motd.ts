import {property, customElement} from 'lit/decorators';
import {css, html, LitElement} from 'lit';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';

/** A banner that loads a message from the motd~motd endpoint */
@customElement('gr-motd-banner')
export class MotdBanner extends LitElement {
  constructor(private pluginApi: PluginApi) {
    super();
    this.message = '';
  }
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
    if (this.message === '') {
      return undefined;
    }
    return html`
        <div>
          <iron-icon icon="gr-icons:warning"></iron-icon>
          ${this.message}
        </div>`;
  }

  @property({type: String}) message: string;

  override connectedCallback() {
    super.connectedCallback();

    this.pluginApi.restApi()
        .get<string>(`/config/server/${this.pluginApi.getPluginName()}~motd`)
        .then((motd: string) => {
          if (motd === null || motd === undefined) {
            motd = '';
          }
          this.message = motd;
        });
  }
}
