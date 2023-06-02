export class SummaryChip {
  private handleClick(event: MouseEvent) {
    event.stopPropagation();
    event.preventDefault();
  }
}