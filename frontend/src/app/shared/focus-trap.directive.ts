import { AfterViewInit, Directive, ElementRef, HostListener, OnDestroy } from '@angular/core';
@Directive({ selector: '[appFocusTrap]' })
export class FocusTrapDirective implements AfterViewInit, OnDestroy {
  private previous: HTMLElement | null;
  constructor(private readonly host: ElementRef<HTMLElement>) { this.previous = host.nativeElement.ownerDocument.activeElement as HTMLElement | null; }
  private controls(): HTMLElement[] {
    return Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]'));
  }
  ngAfterViewInit(): void { this.controls()[0]?.focus(); }
  @HostListener('keydown', ['$event']) trap(event: KeyboardEvent): void {
    if (event.key !== 'Tab') return;
    const controls = this.controls();
    const first = controls[0], last = controls[controls.length - 1];
    if (!first) { event.preventDefault(); return; }
    if (event.shiftKey && this.host.nativeElement.ownerDocument.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && this.host.nativeElement.ownerDocument.activeElement === last) { event.preventDefault(); first.focus(); }
  }
  ngOnDestroy(): void { if (this.previous?.isConnected) this.previous.focus(); }
}
