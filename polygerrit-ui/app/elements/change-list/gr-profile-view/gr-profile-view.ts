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

function average(numbers: Array<number>) {
  if (numbers.length === 0) return 0;
  let sum = 0;
  for (const n of numbers) {
    sum += n;
  }
  return sum / numbers.length;
}

function median(numbers: Array<number>) {
  if (numbers.length === 0) return 0;
  const sorted = Array.from(numbers).sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);

  if (sorted.length % 2 === 0) {
    return (sorted[middle - 1] + sorted[middle]) / 2;
  }

  return sorted[middle];
}

function formatDuration(secs_left: number) {
  const days = Math.floor(secs_left / 3600 / 24);
  secs_left = secs_left - days * 3600 * 24;
  const hours = Math.floor(secs_left / 3600);
  secs_left = secs_left - hours * 3600;
  const minutes = Math.floor(secs_left / 60);
  // const seconds = Math.floor(secs_left - minutes * 60);
  let string = '';
  if (days > 0) string += `${days}d`;
  if (hours > 0) string += `${hours}h`;
  if (minutes > 0) string += `${minutes}m`;
  // if (seconds > 0) string += `${seconds}s`;
  return string;
}

function round2Digits(value: number) {
  return Math.floor((value + Number.EPSILON) * 100) / 100;
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
        flex-direction: column;
        justify-content: center;
        align-items: center;
        background-color: white;
      }
      .overview {
        display: flex;
        justify-content: center;
        align-items: center;
        padding: 20px;
      }
      .card {
        padding: 20px;
        border-radius: 12px;
        border-width: 1px;
        border-style: solid;
        border-color: black;
        padding: 30px;
        margin: 10px;
        background-color: lightgreen;
        width: 200px;
      }
      .title {
        text-align: center;
        padding-bottom: 20px;
      }
      .average {
        font-size: xx-large;
        text-align: center;
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
    // TODO: Handle pagination.
    const changes = await this.restApiService.getChanges(
      1000,
      `owner:${this.viewState.user ?? 'self'} status:merged`
    );
    if (!changes) return;
    this.changes = changes.filter(c => !!c.submitted);
  }

  override render() {
    if (!this.changes.length) return html`Loading...`;
    const tts = this.computeTTS();
    const comments = this.computeComments();
    return html`<div class="container">
      <div class="overview">
        <div class="card">
          <div class="title">Average Time to Submit</div>
          <div class="average">${formatDuration(average(tts))}</div>
        </div>
        <div class="card">
          <div class="title">Median Time to Submit</div>
          <div class="average">${formatDuration(median(tts))}</div>
        </div>
        <div class="card">
          <div class="title">Changes with comments</div>
          <div class="average">${comments.length} / ${this.changes.length}</div>
        </div>
        <div class="card">
          <div class="title">Average #comments</div>
          <div class="average">${round2Digits(average(comments))}</div>
        </div>
        <div class="card">
          <div class="title">Median #comments</div>
          <div class="average">${round2Digits(median(comments))}</div>
        </div>
      </div>
      <svg id="svg"></svg>
      <div class="tooltip"></div>
    </div>`;
  }

  protected override updated(_changedProperties: PropertyValueMap<this>): void {
    this.renderSvg();
  }

  private computeTTS() {
    const times = [];
    for (const change of this.changes) {
      if (!change.submitted) continue;
      times.push(
        (new Date(change.submitted).getTime() -
          new Date(change.created).getTime()) /
          1000
      );
    }
    return times;
  }

  private computeComments() {
    const comments = [];
    for (const change of this.changes) {
      if (!change.total_comment_count) continue;
      comments.push(change.total_comment_count);
    }
    return comments;
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
    const timeWeek = d3.utcSunday;
    svg.attr('width', 50 + cellSize * 52 + 10);
    svg.attr('height', yearHeight * years.length);
    const year = svg
      .selectAll('g')
      .data(years)
      .join('g')
      .attr(
        'transform',
        (_year, i) => `translate(50, ${yearHeight * i + cellSize * 1.5})`
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
    // Rather than looking at the max in the data-series, we pick an arbitrary max of 5/day.
    // This way different dashboards will have similar contrast.
    const colorFn = d3.scaleSequential(d3.interpolateGreens).domain([0, 5]);

    const tooltip = this.tooltip;
    year
      .append('g')
      .selectAll('rect')
      .data((year, _i) => data.filter(x => x[0].getUTCFullYear() == year[0]))
      .join('rect')
      .attr('width', cellSize - 1.5)
      .attr('height', cellSize - 1.5)
      .attr(
        'x',
        (d, _i) => timeWeek.count(d3.utcYear(d[0]), d[0]) * cellSize + 10
      )
      .attr('fill', d => colorFn(d[1]))
      .attr('y', d => countDay(d[0]) * cellSize + 0.5)
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
