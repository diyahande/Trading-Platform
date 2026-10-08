import { ChangeDetectorRef, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../../core/services/auth';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './change-password.html',
  styleUrls: ['./change-password.css'],
})
export class ChangePassword {
  oldPassword = '';
  newPassword = '';
  confirmNewPassword = '';
  showOldPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;
  loading = false;
  error: string | null = null;

  constructor(
    private readonly auth: Auth,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  async submit(): Promise<void> {
    this.error = null;

    if (!this.oldPassword || !this.newPassword || !this.confirmNewPassword) {
      this.error = 'All password fields are required.';
      return;
    }

    if (this.newPassword !== this.confirmNewPassword) {
      this.error = 'New password and confirm password must match.';
      return;
    }

    this.loading = true;
    try {
      await this.auth.changePassword(this.oldPassword, this.newPassword, this.confirmNewPassword);
      this.auth.signOut();
      void this.router.navigate(['/login'], { queryParams: { passwordChanged: '1' } });
    } catch (err: any) {
      if (err?.status === 401) {
        this.error = 'Current password is incorrect. Please try again.';
      } else if (err?.status === 422) {
        this.error = 'Invalid password input. Use at least 12 characters and confirm correctly.';
      } else if (err?.status === 0) {
        this.error = 'Unable to reach auth service. Please try again.';
      } else {
        this.error = 'Unable to change password right now. Please try again.';
      }
    } finally {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }

  toggleOldPasswordVisibility(): void {
    this.showOldPassword = !this.showOldPassword;
  }

  toggleNewPasswordVisibility(): void {
    this.showNewPassword = !this.showNewPassword;
  }

  toggleConfirmPasswordVisibility(): void {
    this.showConfirmPassword = !this.showConfirmPassword;
  }
}
