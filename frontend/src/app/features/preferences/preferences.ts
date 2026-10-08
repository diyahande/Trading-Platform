import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../core/services/auth';

type UserPreferences = {
  theme: string;
  currency: string;
  emailNotificationsEnabled: boolean;
};

@Component({
  selector: 'app-preferences',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './preferences.html',
  styleUrl: './preferences.css'
})
export class Preferences implements OnInit {

  selectedCurrency = 'USD';
  selectedTheme = 'LIGHT';
  emailNotificationsEnabled = true;

  isSaving = false;
  errorMessage = '';
  successMessage = '';

  constructor(
    private http: HttpClient,
    private auth: Auth,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    void this.loadPreferences();
  }

  private async loadPreferences(): Promise<void> {
    try {
      const prefs = await firstValueFrom(
        this.http.get<UserPreferences>('/api/v1/preferences')
      );
      this.selectedTheme = prefs.theme ?? 'LIGHT';
      this.selectedCurrency = prefs.currency ?? 'USD';
      this.emailNotificationsEnabled = prefs.emailNotificationsEnabled ?? true;

      // Apply persisted theme to DOM
      this.applyTheme(this.selectedTheme);
      this.cdr.markForCheck();
    } catch {
      this.errorMessage = 'Could not load preferences. Showing defaults.';
      this.cdr.markForCheck();
    }
  }

  onCurrencyChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.selectedCurrency = select.value;
  }

  onThemeChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.selectedTheme = select.value;
    // Preview the theme immediately
    this.applyTheme(this.selectedTheme);
  }

  onEmailNotificationsChange(event: Event): void {
    const checkbox = event.target as HTMLInputElement;
    this.emailNotificationsEnabled = checkbox.checked;
  }

  async savePreferences(): Promise<void> {
    if (this.isSaving) return;
    this.isSaving = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.cdr.markForCheck();

    try {
      await firstValueFrom(
        this.http.put<UserPreferences>('/api/v1/preferences', {
          theme: this.selectedTheme,
          currency: this.selectedCurrency,
          emailNotificationsEnabled: this.emailNotificationsEnabled
        })
      );

      // Persist theme in localStorage so other pages pick it up
      this.applyTheme(this.selectedTheme);

      this.successMessage = 'Preferences saved successfully!';
      setTimeout(() => { 
        this.successMessage = ''; 
        this.cdr.markForCheck();
      }, 4000);
    } catch {
      this.errorMessage = 'Failed to save preferences. Please try again.';
    } finally {
      this.isSaving = false;
      this.cdr.markForCheck();
    }
  }

  signOut(): void {
    this.auth.signOut();
    void this.router.navigate(['/login']);
  }

  private applyTheme(theme: string): void {
    const normalized = theme === 'DARK' ? 'dark' : 'light';
    document.documentElement.setAttribute('data-theme', normalized);
    try {
      localStorage.setItem('tradex-theme', normalized);
    } catch {
      // Ignore storage errors in restricted browser contexts.
    }
  }
}