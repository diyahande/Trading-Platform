
import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../../core/services/auth';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrls: ['./login.css'],
})
export class Login {
  username = '';
  password = '';
  showPassword = false;
  error: string | null = null;
  loading = false;
  signedIn = false;
  private sub: Subscription | null = null;

  constructor(private auth: Auth, private router: Router, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    // Success message is shown only for sign-in performed in this page session.
    this.signedIn = false;
    this.sub = this.auth.token$.subscribe((t) => {
      if (!t) {
        this.signedIn = false;
      }
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  async submit() {
    this.error = null;
    if (!this.username || !this.password) {
      this.error = 'Username and password are required';
      return;
    }
    this.loading = true;
    try {
      console.log('[Login] submitting', this.username);
      const res = await this.auth.signIn(this.username, this.password);
      console.log('[Login] signIn result', res);
      this.signedIn = true;
      void this.router.navigate(['/dashboard'], { queryParams: { username: this.username } });
    } catch (e: any) {
      this.signedIn = false;
      this.error = this.getReadableErrorMessage(e);
      this.cdr.detectChanges();
    } finally {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }

  signOut() {
    this.auth.signOut();
    this.signedIn = false;
  }

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  private getReadableErrorMessage(err: any): string {
    const nestedMessage = err?.error?.message;
    if (Array.isArray(nestedMessage) && nestedMessage.length > 0) {
      return String(nestedMessage[0]);
    }
    if (typeof nestedMessage === 'string' && nestedMessage.trim()) {
      return nestedMessage;
    }

    const statusCode = err?.status;
    if (statusCode === 0) {
      return 'Unable to reach auth service. Please try again.';
    }
    if (statusCode === 401 || statusCode === 403) {
      return 'Invalid username or password';
    }

    const directMessage = err?.message;
    if (typeof directMessage === 'string' && directMessage.trim()) {
      if (directMessage.includes('Timeout has occurred')) {
        return 'Sign in request timed out. Please try again.';
      }
      // Angular HttpErrorResponse often uses this generic text for 4xx/5xx responses.
      if (directMessage.startsWith('Http failure response')) {
        return 'Invalid username or password';
      }
      return directMessage;
    }

    return 'Sign in failed. Please try again.';
  }
}
