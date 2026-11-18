import { Pipe, PipeTransform, inject } from '@angular/core';
import { LanguageService } from './language.service';

@Pipe({ name: 't', standalone: true, pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly language = inject(LanguageService);
  transform(value: unknown, params?: readonly unknown[]): string {
    return params ? this.language.t(String(value ?? ''), params) : this.language.text(value);
  }
}
