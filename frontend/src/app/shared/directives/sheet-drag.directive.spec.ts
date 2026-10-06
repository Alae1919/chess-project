import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SheetDragDirective } from './sheet-drag.directive';

@Component({
  standalone: true,
  imports: [SheetDragDirective],
  template: `
    <section class="sheet" appSheetDrag (dismiss)="dismissed = dismissed + 1">
      <span class="sheet__grab"></span>
      <div class="sheet__head"><button type="button">Fermer</button></div>
      <p class="body">contenu</p>
    </section>
  `,
})
class HostComponent {
  dismissed = 0;
}

describe('SheetDragDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let sheet: HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    sheet = fixture.nativeElement.querySelector('.sheet');
  });

  /** Drag from `from` down by `dy` px over `ms` ms */
  function drag(from: string, dy: number, ms = 300) {
    const target = sheet.querySelector(from)!;
    const down = new PointerEvent('pointerdown', { bubbles: true, clientY: 100, pointerId: 1 });
    target.dispatchEvent(down);
    sheet.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientY: 100 + dy, pointerId: 1 }));
    // the event timestamps decide the speed of the flick
    const up = new PointerEvent('pointerup', { bubbles: true, clientY: 100 + dy, pointerId: 1 });
    Object.defineProperty(up, 'timeStamp', { value: down.timeStamp + ms });
    sheet.dispatchEvent(up);
  }

  it('closes when pulled down far enough by its handle', fakeAsync(() => {
    drag('.sheet__grab', 140);
    tick(200);

    expect(fixture.componentInstance.dismissed).toBe(1);
  }));

  it('closes on a quick flick, even a short one', fakeAsync(() => {
    drag('.sheet__head', 40, 30);
    tick(200);

    expect(fixture.componentInstance.dismissed).toBe(1);
  }));

  it('springs back when let go too soon', fakeAsync(() => {
    drag('.sheet__grab', 50);
    tick(200);

    expect(fixture.componentInstance.dismissed).toBe(0);
    expect(sheet.style.transform).toBe('');
  }));

  it('leaves its content and its buttons alone: they scroll and click as usual', fakeAsync(() => {
    drag('.body', 200);
    drag('.sheet__head button', 200);
    tick(200);

    expect(fixture.componentInstance.dismissed).toBe(0);
  }));
});
