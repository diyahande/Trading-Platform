import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { SignupRequest, SignupRequests } from '../../core/services/signup-requests';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin.html',
  styleUrls: ['./admin.css'],
})
export class Admin implements OnInit {
  requests: SignupRequest[] = [];
  loading = false;
  error: string | null = null;
  statusMessage: string | null = null;

  accountIds: Record<string, string> = {};
  rejectReasons: Record<string, string> = {};
  reviewNotes: Record<string, string> = {};
  processing: Record<string, boolean> = {};

  constructor(
    private readonly signupRequests: SignupRequests,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    void this.loadRequests();
  }

  async loadRequests(): Promise<void> {
    this.loading = true;
    this.error = null;
    this.statusMessage = null;
    try {
      this.requests = await this.signupRequests.list('NEW');
    } catch {
      this.error = 'Unable to load signup requests. Ensure you are logged in as admin.';
    } finally {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }

  async approve(request: SignupRequest): Promise<void> {
    const accountId = (this.accountIds[request.id] ?? '').trim();
    if (!accountId) {
      this.error = 'Account ID is required for approval.';
      this.cdr.detectChanges();
      return;
    }

    this.processing[request.id] = true;
    this.error = null;
    this.statusMessage = null;
    try {
      const result = await this.signupRequests.approveAndRegister(
        request.id,
        accountId,
        this.reviewNotes[request.id],
      );
      this.requests = this.requests.filter((item) => item.id !== request.id);
      this.statusMessage = result.emailSent
        ? `Approved ${request.username} and sent temporary password email.`
        : `Approved ${request.username}, but email delivery failed.`;
    } catch {
      this.error = 'Approval failed. Check account ID format and request state.';
    } finally {
      this.processing[request.id] = false;
      this.cdr.detectChanges();
    }
  }

  async reject(request: SignupRequest): Promise<void> {
    const reason = (this.rejectReasons[request.id] ?? '').trim();
    if (!reason) {
      this.error = 'Rejection reason is required.';
      this.cdr.detectChanges();
      return;
    }

    this.processing[request.id] = true;
    this.error = null;
    this.statusMessage = null;
    try {
      const result = await this.signupRequests.reject(request.id, reason, this.reviewNotes[request.id]);
      this.requests = this.requests.filter((item) => item.id !== request.id);
      this.statusMessage = result.emailSent
        ? `Rejected ${request.username} and sent rejection email.`
        : `Rejected ${request.username}, but rejection email failed.`;
    } catch {
      this.error = 'Rejection failed. The request may have already been processed.';
    } finally {
      this.processing[request.id] = false;
      this.cdr.detectChanges();
    }
  }
}
