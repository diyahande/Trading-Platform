
import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Auth } from '../../../core/services/auth';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.html',
  styleUrls: ['./login.css'],
})
export class Login {
  username = '';
  password = '';
  error: string | null = null;
  loading = false;
  signedIn = false;
  private sub: Subscription | null = null;

  constructor(private auth: Auth) {}

  ngOnInit() {
    // reflect current auth state and keep UI in sync when token changes
    this.signedIn = !!this.auth.token;
    this.sub = this.auth.token$.subscribe((t) => {
      this.signedIn = !!t;
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
    } catch (e: any) {
      this.error = e?.error?.message || e?.message || 'Sign-in failed';
    } finally {
      this.loading = false;
    }
  }

  signOut() {
    this.auth.signOut();
    this.signedIn = false;
  }
}
