import { Component, Input, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * A one-line notice under the board, optionally counting down to a deadline
 * ("Votre adversaire s'est déconnecté … 42 s").
 */
@Component({
  selector: 'app-game-notice-banner',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="notice" role="status" aria-live="polite">
      <span>{{ message }}</span>
      <strong *ngIf="secondsLeft() !== null">{{ secondsLeft() }} s</strong>
    </div>
  `,
  styles: [`
    .notice {
      margin-top: 14px; padding: 7px 16px; border-radius: 20px; display: flex; align-items: center;
      gap: 8px; justify-content: center; text-align: center;
      background: var(--bg2); border: 1px solid var(--borderh); color: var(--textd);
      font: 500 .8rem var(--font);
    }
    strong { color: var(--gold); font-variant-numeric: tabular-nums; }
  `],
})
export class GameNoticeBannerComponent implements OnInit, OnDestroy {
  @Input({ required: true }) message!: string;
  /** Epoch milliseconds to count down to; omit for a plain notice. */
  @Input() until?: number;

  private now = signal(Date.now());
  private timer?: ReturnType<typeof setInterval>;

  secondsLeft = computed(() =>
    this.until === undefined ? null : Math.max(0, Math.ceil((this.until - this.now()) / 1000)));

  ngOnInit(): void {
    if (this.until !== undefined) this.timer = setInterval(() => this.now.set(Date.now()), 500);
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }
}
