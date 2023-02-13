import {LitElement, html, css, PropertyValueMap} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import * as d3 from 'd3';
import {getAppContext} from '../../../services/app-context';
import {ChangeInfo} from '../../../api/rest-api';
import {
  dashboardViewModelToken,
  DashboardViewState,
} from '../../../models/views/dashboard';

function formatEntry(datum: [Date, number]) {
  return `${datum[0].toDateString()} - ${datum[1]}`;
}
@customElement('gr-dashboard-view')
export class GrProfileView extends LitElement {
  @state()
  viewState?: DashboardViewState;

  @query('#svg')
  svg?: SVGElement;

  @query('.tooltip')
  tooltip?: HTMLDivElement;

  @state()
  changes: ChangeInfo[] = [];

  private readonly getViewModel = resolve(this, dashboardViewModelToken);
  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getViewModel().state$,
      x => {
        this.viewState = x;
        this.reload();
      }
    );
  }

  static override get styles() {
    return css`
      .container {
        min-height: 100vh; /* minimum height = screen height */
        display: flex;
        justify-content: center;
        align-items: center;
        background-color: white;
      }
      .tooltip {
        position: absolute;
        text-align: center;
        padding: 10px;
        color: #005050;
        background-color: #ffffff80;
        border-width: 1px;
        border-color: #005050;
        border-radius: 8px;
        pointer-events: none;
        visibility: hidden;
      }
    `;
  }

  async reload() {
    if (!this.viewState) return Promise.resolve();
    const changes = await this.restApiService.getChanges(
      1000,
      'owner:self status:merge'
    );
    if (!changes) return;
    this.changes = changes.filter(c => !!c.submitted);
  }

  override render() {
    return html`<div class="container">
      <svg id="svg"></svg>
      <div class="tooltip">hello</div>
    </div>`;
  }

  protected override updated(_changedProperties: PropertyValueMap<this>): void {
    this.renderSvg();
  }

  private renderSvg() {
    if (!this.svg) return;
    var svg = d3.select(this.svg);
    const data = d3.rollups(
      this.changes,
      v => v.length,
      c => new Date(c.submitted ?? '')
    );

    const years = d3.rollups(
      data,
      v => v.length,
      d => d[0].getUTCFullYear()
    );

    const cellSize = 17;
    const yearHeight = cellSize * 7 + 25;
    const formatDay = (d: Date) =>
      ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'][d.getUTCDay()];
    const countDay = (d: Date) => d.getUTCDay();
    const countYear = (d: Date) => years[0][0] - d.getUTCFullYear();
    const timeWeek = d3.utcSunday;
    svg.attr('width', 1000);
    svg.attr('height', yearHeight * years.length);
    const year = svg
      .selectAll('g')
      .data(years)
      .join('g')
      .attr(
        'transform',
        (_d, i) => `translate(40, ${yearHeight * i + cellSize * 1.5})`
      );
    year
      .append('text')
      .attr('x', 0)
      .attr('y', -30)
      .attr('text-anchor', 'end')
      .attr('font-size', 16)
      .attr('font-weight', 550)
      .attr('transform', 'rotate(270)')
      .text(d => d[0]);
    year
      .append('g')
      .attr('text-anchor', 'end')
      .selectAll('text')
      .data(d3.range(7).map(i => new Date(2022, 0, i)))
      .join('text')
      .attr('x', -5)
      .attr('y', d => (countDay(d) + 0.5) * cellSize)
      .attr('dy', '0.31em')
      .text(formatDay);
    const max =
      d3.quantile(
        data.map(d => d[1]),
        1,
        Math.abs
      ) ?? 0;
    const colorFn = d3
      .scaleSequential(d3.interpolateGreens)
      .domain([0, Math.ceil(max)]);

    const tooltip = this.tooltip;
    year
      .append('g')
      .selectAll('rect')
      .data(data)
      .join('rect')
      .attr('width', cellSize - 1.5)
      .attr('height', cellSize - 1.5)
      .attr(
        'x',
        (d, _i) => timeWeek.count(d3.utcYear(d[0]), d[0]) * cellSize + 10
      )
      .attr('fill', d => colorFn(d[1]))
      .attr(
        'y',
        d => countDay(d[0]) * cellSize + 0.5 + countYear(d[0]) * yearHeight
      )
      .on('mouseover', function (_event, datum) {
        tooltip!.style['visibility'] = 'visible';
        tooltip!.innerText = formatEntry(datum);
      })
      .on('mousemove', function (event, _datum) {
        console.log(event);
        tooltip!.style['top'] = `${event.clientY - 40}px`;
        tooltip!.style['left'] = `${event.clientX}px`;
      })
      .on('mouseout', function (_event, _datum) {
        tooltip!.style['visibility'] = 'hidden';
      });
  }
}
