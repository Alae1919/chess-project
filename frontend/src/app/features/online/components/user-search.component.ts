import { Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, filter, switchMap, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { InvitationService } from '../../../core/services/invitation.service';
import { UserSummary } from '../../../core/models';

@Component({
  selector: 'app-user-search',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="user-search">
      <svg class="search-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.3-4.3"/></svg>
      <input
        [id]="inputId"
        [formControl]="searchControl"
        type="search"
        placeholder="Pseudo…"
        class="search-input"
        autocomplete="off"
        enterkeyhint="search"
      />
      <ul class="search-results" *ngIf="results.length > 0">
        <li *ngFor="let user of results">
          <button type="button" class="search-result-item" (click)="selectUser(user)">
            <img *ngIf="user.avatarUrl" [src]="user.avatarUrl" class="avatar" alt="" />
            <span *ngIf="!user.avatarUrl" class="avatar-placeholder">{{ user.username.slice(0, 2).toUpperCase() }}</span>
            <span class="username">{{ user.username }}</span>
            <span class="elo">Elo {{ user.elo }}</span>
          </button>
        </li>
      </ul>
      <p class="no-results" *ngIf="searched && results.length === 0">
        Aucun joueur trouvé.
      </p>
    </div>
  `,
  styles: [`
    .user-search { position: relative; width: 100%; }
    .search-icon {
      position: absolute; left: 14px; top: 15px; width: 20px; height: 20px; pointer-events: none;
      fill: none; stroke: var(--textc); stroke-width: 1.7; stroke-linecap: round;
    }
    .search-input {
      width: 100%; height: 50px; padding: 0 14px 0 44px; box-sizing: border-box;
      border-radius: 14px; border: 1px solid rgba(201, 164, 92, .35); background: rgba(255, 255, 255, .04);
      color: var(--text); font: 400 16px var(--font); outline: none; transition: border-color .2s, box-shadow .2s;
    }
    .search-input:focus { border-color: var(--p); box-shadow: 0 0 0 3px rgba(201, 164, 92, .12); }
    .search-input::placeholder { color: #958873; }
    .search-results {
      position: absolute; top: 100%; left: 0; right: 0; z-index: 100;
      margin: 6px 0 0; padding: 6px; list-style: none; max-height: 240px; overflow-y: auto;
      background: #1c1611; border: 1px solid var(--borderh); border-radius: 14px;
      box-shadow: 0 20px 40px rgba(0, 0, 0, .5);
    }
    .search-result-item {
      width: 100%; min-height: 52px; display: flex; align-items: center; gap: 12px; padding: 6px 10px;
      background: none; border: 0; border-radius: 10px; color: var(--text); text-align: left; cursor: pointer;
      transition: background .15s;
    }
    .search-result-item:hover, .search-result-item:focus-visible { background: rgba(230, 194, 122, .1); }
    .avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; }
    .avatar-placeholder {
      width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
      display: flex; align-items: center; justify-content: center;
      font: 600 12px var(--font); color: var(--gold); background: #1f1812; border: 1px solid rgba(201, 164, 92, .3);
    }
    .username { flex: 1; font: 500 15px var(--font); }
    .elo { font-size: 12px; color: var(--textc); }
    .no-results { color: var(--textc); font-size: 13px; margin-top: 8px; }
  `],
})
export class UserSearchComponent {
  @Output() userSelected = new EventEmitter<UserSummary>();
  /** For a <label for> outside */
  @Input() inputId = 'user-search';

  private invitationService = inject(InvitationService);

  searchControl = new FormControl('');
  results: UserSummary[] = [];
  searched = false;

  constructor() {
    this.searchControl.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      filter((v): v is string => !!v && v.length >= 2),
      switchMap((q) =>
        this.invitationService.searchUsers(q).pipe(
          catchError(() => of([] as UserSummary[]))
        )
      )
    ).subscribe((results) => {
      this.results = results;
      this.searched = true;
    });
  }

  selectUser(user: UserSummary): void {
    this.userSelected.emit(user);
    this.searchControl.setValue(user.username, { emitEvent: false });
    this.results = [];
    this.searched = false; // a pick is not an empty search
  }
}
