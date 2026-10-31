import { ChangeDetectionStrategy, Component, EventEmitter, Output } from '@angular/core';
import { IconComponent } from '../shared/icon.component';
@Component({
  selector: 'app-landing',
  imports: [IconComponent],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LandingComponent {
  @Output() accessRequested = new EventEmitter<'login' | 'register'>();
  readonly year = new Date().getFullYear();
}
