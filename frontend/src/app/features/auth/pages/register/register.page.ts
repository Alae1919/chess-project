import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { FloatyPiecesComponent } from '../../../../shared/components/floaty-pieces/floaty-pieces.component';
import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-register-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, FloatyPiecesComponent],
  templateUrl: './register.page.html',
  styleUrls: ['../login/login.page.scss'], // Reusing the exact same SCSS from login!
})
export class RegisterPage {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  registerForm = this.fb.group({
    // Same rule as the backend (AuthDto.USERNAME_PATTERN): no '@', so it can't pass for an email
    username: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9_-]{3,30}$/)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  isLoading = false;
  errorMessage = '';

  onSubmit(): void {
    if (this.registerForm.invalid) return;

    this.isLoading = true;
    this.errorMessage = '';

    const req = {
      username: this.registerForm.value.username!,
      email: this.registerForm.value.email!,
      password: this.registerForm.value.password!,
    };

    this.authService.register(req).subscribe({
      next: () => {
        this.router.navigate(['/home']);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.detail || 'Erreur lors de la création du compte.';
      },
    });
  }
}