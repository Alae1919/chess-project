import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FloatyPiecesComponent } from '../../../../shared/components/floaty-pieces/floaty-pieces.component';
import { AuthService } from '../../../../core/services/auth.service';

/** Where to go after logging in: the page that sent us here, if it is one of ours. */
function safeReturnUrl(url: string | null): string {
  return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\') ? url : '/home';
}

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, FloatyPiecesComponent],
  templateUrl: './login.page.html',
  styleUrls: ['./login.page.scss'],
})
export class LoginPage {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  isLoading = false;
  errorMessage = '';

  onSubmit(): void {
    if (this.loginForm.invalid) return;

    this.isLoading = true;
    this.errorMessage = '';

    const req = {
      email: this.loginForm.value.email!,
      password: this.loginForm.value.password!,
    };

    this.authService.login(req).subscribe({
      next: () => {
        this.router.navigateByUrl(safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')));
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.detail || 'Identifiants incorrects. Veuillez réessayer.';
      },
    });
  }
}