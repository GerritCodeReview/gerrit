import {property, customElement} from 'lit/decorators';
import {css, html, LitElement} from 'lit';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';

@customElement('gr-motd-banner')
export class MotdBanner extends LitElement {
  plugin: PluginApi;

  constructor(plugin: PluginApi) {
    super();
    this.plugin = plugin;
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
    return html`<div ?hidden=${this._hidden}>
<iron-icon icon="gr-icons:warning"></iron-icon>
${this._message}
</div>`;
}

  @property({type: String})
  _message?: string;

  @property({type: Boolean})
  _hidden = false;

  override connectedCallback() {
    super.connectedCallback();

    this.plugin.restApi()
      .get<string>(`/config/server/${this.plugin.getPluginName()}~motd`)
      .then((motd: string) => {
        this._hidden = (motd == null || motd == undefined || motd == "");
        this._message = motd;
      });
  }
}
