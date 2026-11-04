import { ChangeDetectionStrategy, Component, EventEmitter, Output } from '@angular/core';
import { LandingPreviewComponent } from './landing-preview.component';
import { IconComponent } from '../shared/icon.component';
@Component({
  selector: 'app-landing',
  imports: [IconComponent, LandingPreviewComponent],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LandingComponent {
  @Output() accessRequested = new EventEmitter<'login' | 'register'>();
  readonly year = new Date().getFullYear();
}
