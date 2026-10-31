import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
const paths = {
  grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  wallet: 'M20 7V5a2 2 0 0 0-2-2H6a3 3 0 0 0 0 6h14v11H6a3 3 0 0 1-3-3V6 M20 12h-5v5h5 M16.5 14.5h.01',
  rows: 'M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01',
  mail: 'M3 5h18v14H3z M3 6l9 7 9-7',
  message: 'M21 11a9 9 0 0 1-9 9H3l1.5-4.5A9 9 0 1 1 21 11Z M8 9h8 M8 13h5',
  arrow: 'M5 12h14 M13 6l6 6-6 6', down: 'M7 10l5 5 5-5',
  logout: 'M9 4H4v16h5 M10 12h11 M17 8l4 4-4 4', plus: 'M12 5v14 M5 12h14',
  trend: 'M3 17l6-6 4 4 8-10 M15 5h6v6',
  spark: 'M12 3l2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5Z',
  shield: 'M12 3l8 3v6c0 5-8 9-8 9s-8-4-8-9V6z M8 12l3 3 5-6',
  calendar: 'M4 5h16v16H4z M8 3v4 M16 3v4 M4 10h16 M8 14h2 M14 14h2',
  check: 'M20 6L9 17l-5-5', close: 'M6 6l12 12 M18 6L6 18',
  refresh: 'M20 8a8 8 0 1 0 .5 7 M20 3v5h-5',
  clock: 'M12 8v5l3 2 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  info: 'M12 11v6 M12 7h.01 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  search: 'M21 21l-5-5 M18 10.5a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0',
  upRight: 'M7 17L17 7 M7 7h10v10', downLeft: 'M17 7L7 17 M7 7v10h10',
  repeat: 'M4 8h16l-4-4 M20 16H4l4 4 M20 8v3 M4 16v-3',
  lock: 'M5 10h14v11H5z M8 10V7a4 4 0 0 1 8 0v3 M12 14v3'
};
export type IconName = keyof typeof paths;
@Component({ selector: 'app-icon', changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg [attr.width]="size" [attr.height]="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path [attr.d]="path" /></svg>`,
  styles: [':host { display: inline-flex; flex: 0 0 auto; vertical-align: middle; }'] })
export class IconComponent {
  @Input() name: IconName = 'grid';
  @Input() size = 20;
  get path(): string { return paths[this.name]; }
}
