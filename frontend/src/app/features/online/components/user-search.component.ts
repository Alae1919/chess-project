import { Component, EventEmitter, inject, Output } from '@angular/core';
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
      <input
        [formControl]="searchControl"
        type="text"
        placeholder="Search by username..."
        class="search-input"
        autocomplete="off"
      />
      <ul class="search-results" *ngIf="results.length > 0">
        <li
          *ngFor="let user of results"
          class="search-result-item"
          (click)="selectUser(user)"
        >
          <img
            *ngIf="user.avatarUrl"
            [src]="user.avatarUrl"
            class="avatar"
            alt=""
          />
          <span *ngIf="!user.avatarUrl" class="avatar-placeholder">
            {{ user.username[0].toUpperCase() }}
          </span>
          <span class="username">{{ user.username }}</span>
          <span class="elo">{{ user.elo }}</span>
        </li>
      </ul>
      <p class="no-results" *ngIf="searched && results.length === 0">
        No players found.
      </p>
    </div>
  `,
  styles: [`
    .user-search { position: relative; width: 100%; }
    .search-input {
      width: 100%; padding: 10px 14px; border-radius: 8px;
      border: 1px solid #3a3f52; background: #1e2130; color: #e0e6f0;
      font-size: 14px; box-sizing: border-box;
    }
    .search-results {
      position: absolute; top: 100%; left: 0; right: 0; z-index: 100;
      background: #252a3d; border: 1px solid #3a3f52; border-radius: 8px;
      list-style: none; margin: 4px 0 0; padding: 4px 0; max-height: 200px;
      overflow-y: auto;
    }
    .search-result-item {
      display: flex; align-items: center; gap: 10px; padding: 8px 14px;
      cursor: pointer; transition: background 0.15s;
    }
    .search-result-item:hover { background: #303654; }
    .avatar { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; }
    .avatar-placeholder {
      width: 28px; height: 28px; border-radius: 50%; background: #4a6fa5;
      display: flex; align-items: center; justify-content: center;
      font-size: 12px; color: #fff; font-weight: 600; flex-shrink: 0;
    }
    .username { flex: 1; font-size: 14px; color: #e0e6f0; }
    .elo { font-size: 12px; color: #8892a4; }
    .no-results { color: #8892a4; font-size: 13px; margin-top: 8px; }
  `],
})
export class UserSearchComponent {
  @Output() userSelected = new EventEmitter<UserSummary>();

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
