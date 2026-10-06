// src/app/shared/directives/sheet-drag.directive.ts
import { Directive, ElementRef, EventEmitter, HostListener, Output, inject } from '@angular/core';

/** How far, or how fast, a sheet must be pulled down to close */
const CLOSE_DISTANCE = 90;     // px
const CLOSE_VELOCITY = 0.6;    // px per ms

/**
 * Lets a bottom sheet be pulled down to close, like a native one. The drag starts on the
 * sheet's grab handle or head (`.sheet__grab`, `.sheet__head`), so its scrolling content and
 * its buttons keep working; let go short of the threshold and it springs back.
 */
@Directive({
  selector: '[appSheetDrag]',
  standalone: true,
})
export class SheetDragDirective {
  /** The sheet was pulled far or fast enough: close it */
  @Output() dismiss = new EventEmitter<void>();

  private el = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
  private startY: number | null = null;
  private startT = 0;
  private dy = 0;

  @HostListener('pointerdown', ['$event'])
  onDown(e: PointerEvent): void {
    const target = e.target as HTMLElement;
    if (!target.closest('.sheet__grab, .sheet__head') || target.closest('button, a, input')) return;
    this.startY = e.clientY;
    this.startT = e.timeStamp;
    this.dy = 0;
    this.el.setPointerCapture?.(e.pointerId);
    this.el.style.transition = 'none';
    this.el.style.animation = 'none';
  }

  @HostListener('pointermove', ['$event'])
  onMove(e: PointerEvent): void {
    if (this.startY === null) return;
    this.dy = Math.max(0, e.clientY - this.startY);
    this.el.style.transform = `translateY(${this.dy}px)`;
  }

  @HostListener('pointerup', ['$event'])
  @HostListener('pointercancel', ['$event'])
  onUp(e: PointerEvent): void {
    if (this.startY === null) return;
    const velocity = this.dy / Math.max(1, e.timeStamp - this.startT);
    this.startY = null;
    this.el.style.transition = 'transform .32s cubic-bezier(.22, 1, .36, 1)';
    if (this.dy > CLOSE_DISTANCE || (this.dy > 20 && velocity > CLOSE_VELOCITY)) {
      this.el.style.transform = 'translateY(100%)';
      setTimeout(() => this.dismiss.emit(), 180);
    } else {
      this.el.style.transform = '';
    }
  }
}
