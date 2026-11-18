import { inject } from '@angular/core';
import { LanguageService } from '../core/i18n/language.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { ChangeDetectionStrategy, Component, EventEmitter, Output } from '@angular/core';
import { LandingPreviewComponent } from './landing-preview.component';
import { IconComponent } from '../shared/icon.component';
@Component({
  selector: 'app-landing',
  imports: [TranslatePipe, IconComponent, LandingPreviewComponent],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LandingComponent {
 readonly lang = inject(LanguageService);

  @Output() accessRequested = new EventEmitter<'login' | 'register'>();
  readonly year = new Date().getFullYear();
}
