import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ChangeDetectorRef } from '@angular/core';

import { SignupRequests } from '../../../core/services/signup-requests';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './signup.html',
  styleUrls: ['./signup.css'],
})
export class Signup {
  firstName = '';
  lastName = '';
  email = '';
  phone = '';
  dob = '';
  loading = false;
  error: string | null = null;
  success: string | null = null;

  constructor(
    private readonly signupRequests: SignupRequests,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  async submit(): Promise<void> {
    this.error = null;
    this.success = null;

    if (!this.firstName || !this.lastName || !this.email || !this.phone || !this.dob) {
      this.error = 'Please fill all required fields.';
      return;
    }

    this.loading = true;
    try {
      await this.signupRequests.submit({
        firstName: this.firstName,
        lastName: this.lastName,
        username: this.email,
        phone: this.phone,
        dob: this.dob,
      });
      this.success = 'Request submitted. An admin will review your application.';
      this.firstName = '';
      this.lastName = '';
      this.email = '';
      this.phone = '';
      this.dob = '';
    } catch {
      this.error = 'Unable to submit request. If a pending or approved request exists, use a different email.';
    } finally {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }
}
