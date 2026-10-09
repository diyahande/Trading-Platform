import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { APP_ENV } from '../config/app-env.generated';

export type SignupRequestStatus = 'NEW' | 'APPROVED' | 'REJECTED';

export interface SignupRequest {
  id: string;
  firstName: string;
  lastName: string;
  username: string;
  phone: string;
  dob: string;
  status: SignupRequestStatus;
  reviewNotes?: string | null;
  rejectionReason?: string | null;
  approvedAccountId?: string | null;
  approvedAuthUserId?: string | null;
  reviewedBy?: string | null;
  reviewedOn?: string | null;
  createdOn: string;
  updatedOn: string;
}

@Injectable({
  providedIn: 'root',
})
export class SignupRequests {
  private readonly baseUrl = `${APP_ENV.authApiUrl}/auth`;

  constructor(private readonly http: HttpClient) {}

  submit(request: {
    firstName: string;
    lastName: string;
    username: string;
    phone: string;
    dob: string;
  }): Promise<SignupRequest> {
    return firstValueFrom(this.http.post<SignupRequest>(`${this.baseUrl}/signup-request`, request));
  }

  list(status?: SignupRequestStatus): Promise<SignupRequest[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return firstValueFrom(this.http.get<SignupRequest[]>(`${this.baseUrl}/admin/signup-requests`, { params }));
  }

  approveAndRegister(id: string, accountId: string, reviewNotes?: string): Promise<{
    requestId: string;
    authUserId: string;
    accountId: string;
    emailSent: boolean;
  }> {
    return firstValueFrom(
      this.http.patch<{
        requestId: string;
        authUserId: string;
        accountId: string;
        emailSent: boolean;
      }>(`${this.baseUrl}/admin/signup-requests/${id}/approve-and-register`, {
        accountId,
        reviewNotes: reviewNotes ?? '',
      }),
    );
  }

  reject(id: string, rejectionReason: string, reviewNotes?: string): Promise<{ requestId: string; emailSent: boolean }> {
    return firstValueFrom(
      this.http.patch<{ requestId: string; emailSent: boolean }>(
        `${this.baseUrl}/admin/signup-requests/${id}/reject`,
        {
          rejectionReason,
          reviewNotes: reviewNotes ?? '',
        },
      ),
    );
  }
}
