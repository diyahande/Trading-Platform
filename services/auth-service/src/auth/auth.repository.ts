import { randomUUID } from 'node:crypto';

import { Injectable, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Pool } from 'pg';

import type {
  AuthUserRecord,
  RefreshTokenRecord,
  Role,
  SignupRequestRecord,
  SignupRequestStatus,
} from './types/auth.types';

@Injectable()
export class AuthRepository implements OnModuleDestroy {
  private readonly pool: Pool;

  constructor(configService: ConfigService) {
    const databaseUrl = configService.get<string>('DATABASE_URL');
    if (!databaseUrl) {
      throw new Error('DATABASE_URL must be provided');
    }

    this.pool = new Pool({
      connectionString: databaseUrl,
    });
  }

  async findUserByUsername(username: string): Promise<AuthUserRecord | null> {
    const result = await this.pool.query<UserRow>(
      `SELECT au.id, au.username, au.password_hash, au.account_reference, au.roles, au.created_on,
              asr.first_name, asr.last_name
       FROM auth_users au
       LEFT JOIN auth_signup_requests asr ON asr.approved_auth_user_id = au.id
       WHERE au.username = $1`,
      [username],
    );

    return this.mapUser(result.rows[0]);
  }

  async findUserById(id: string): Promise<AuthUserRecord | null> {
    const result = await this.pool.query<UserRow>(
      `SELECT au.id, au.username, au.password_hash, au.account_reference, au.roles, au.created_on,
              asr.first_name, asr.last_name
       FROM auth_users au
       LEFT JOIN auth_signup_requests asr ON asr.approved_auth_user_id = au.id
       WHERE au.id = $1`,
      [id],
    );

    return this.mapUser(result.rows[0]);
  }

  async findUserByAccountId(accountId: string): Promise<AuthUserRecord | null> {
    const result = await this.pool.query<UserRow>(
      `SELECT au.id, au.username, au.password_hash, au.account_reference, au.roles, au.created_on,
              asr.first_name, asr.last_name
       FROM auth_users au
       LEFT JOIN auth_signup_requests asr ON asr.approved_auth_user_id = au.id
       WHERE au.account_reference = $1`,
      [accountId],
    );

    return this.mapUser(result.rows[0]);
  }

  async findSignupRequestById(id: string): Promise<SignupRequestRecord | null> {
    const result = await this.pool.query<SignupRequestRow>(
      `SELECT id, first_name, last_name, username, phone, dob, status, review_notes,
              rejection_reason, approved_account_reference, approved_auth_user_id,
              reviewed_by, reviewed_on, created_on, updated_on
       FROM auth_signup_requests
       WHERE id = $1`,
      [id],
    );

    return this.mapSignupRequest(result.rows[0]);
  }

  async findActiveSignupRequestByUsername(username: string): Promise<SignupRequestRecord | null> {
    const result = await this.pool.query<SignupRequestRow>(
      `SELECT id, first_name, last_name, username, phone, dob, status, review_notes,
              rejection_reason, approved_account_reference, approved_auth_user_id,
              reviewed_by, reviewed_on, created_on, updated_on
       FROM auth_signup_requests
       WHERE lower(username) = lower($1)
         AND status IN ('NEW', 'APPROVED')
       ORDER BY created_on DESC
       LIMIT 1`,
      [username],
    );

    return this.mapSignupRequest(result.rows[0]);
  }

  async createSignupRequest(input: {
    firstName: string;
    lastName: string;
    username: string;
    phone: string;
    dob: Date;
  }): Promise<SignupRequestRecord> {
    const result = await this.pool.query<SignupRequestRow>(
      `INSERT INTO auth_signup_requests (id, first_name, last_name, username, phone, dob, status)
       VALUES ($1, $2, $3, $4, $5, $6, 'NEW')
       RETURNING id, first_name, last_name, username, phone, dob, status, review_notes,
                 rejection_reason, approved_account_reference, approved_auth_user_id,
                 reviewed_by, reviewed_on, created_on, updated_on`,
      [randomUUID(), input.firstName, input.lastName, input.username, input.phone, input.dob],
    );

    const created = this.mapSignupRequest(result.rows[0]);
    if (!created) {
      throw new Error('Failed to create signup request');
    }

    return created;
  }

  async listSignupRequests(status?: SignupRequestStatus): Promise<SignupRequestRecord[]> {
    const result = status
      ? await this.pool.query<SignupRequestRow>(
          `SELECT id, first_name, last_name, username, phone, dob, status, review_notes,
                  rejection_reason, approved_account_reference, approved_auth_user_id,
                  reviewed_by, reviewed_on, created_on, updated_on
           FROM auth_signup_requests
           WHERE status = $1
           ORDER BY created_on DESC`,
          [status],
        )
      : await this.pool.query<SignupRequestRow>(
          `SELECT id, first_name, last_name, username, phone, dob, status, review_notes,
                  rejection_reason, approved_account_reference, approved_auth_user_id,
                  reviewed_by, reviewed_on, created_on, updated_on
           FROM auth_signup_requests
           ORDER BY created_on DESC`,
        );

    return result.rows.map((row) => this.mapSignupRequest(row)).filter(Boolean) as SignupRequestRecord[];
  }

  async markSignupRequestApproved(input: {
    id: string;
    reviewNotes: string | null;
    approvedAccountId: string;
    approvedAuthUserId: string;
    reviewedBy: string;
    reviewedOn: Date;
  }): Promise<boolean> {
    const result = await this.pool.query(
      `UPDATE auth_signup_requests
       SET status = 'APPROVED',
           review_notes = $2,
           rejection_reason = NULL,
           approved_account_reference = $3,
           approved_auth_user_id = $4,
           reviewed_by = $5,
           reviewed_on = $6,
           updated_on = CURRENT_TIMESTAMP
       WHERE id = $1
         AND status = 'NEW'`,
      [
        input.id,
        input.reviewNotes,
        input.approvedAccountId,
        input.approvedAuthUserId,
        input.reviewedBy,
        input.reviewedOn,
      ],
    );

    return result.rowCount === 1;
  }

  async markSignupRequestRejected(input: {
    id: string;
    rejectionReason: string;
    reviewNotes: string | null;
    reviewedBy: string;
    reviewedOn: Date;
  }): Promise<boolean> {
    const result = await this.pool.query(
      `UPDATE auth_signup_requests
       SET status = 'REJECTED',
           review_notes = $2,
           rejection_reason = $3,
           approved_account_reference = NULL,
           approved_auth_user_id = NULL,
           reviewed_by = $4,
           reviewed_on = $5,
           updated_on = CURRENT_TIMESTAMP
       WHERE id = $1
         AND status = 'NEW'`,
      [input.id, input.reviewNotes, input.rejectionReason, input.reviewedBy, input.reviewedOn],
    );

    return result.rowCount === 1;
  }

  async createUser(input: {
    username: string;
    passwordHash: string;
    accountId: string;
    roles: Role[];
  }): Promise<AuthUserRecord> {
    const id = randomUUID();

    const result = await this.pool.query<UserRow>(
      `INSERT INTO auth_users (id, username, password_hash, account_reference, roles)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING id, username, password_hash, account_reference, roles, created_on`,
      [id, input.username, input.passwordHash, input.accountId, input.roles],
    );

    const user = this.mapUser(result.rows[0]);
    if (!user) {
      throw new Error('Failed to create user');
    }

    return user;
  }

  async updateUserPasswordHash(userId: string, passwordHash: string): Promise<boolean> {
    const result = await this.pool.query(
      `UPDATE auth_users
       SET password_hash = $2
       WHERE id = $1`,
      [userId, passwordHash],
    );

    return result.rowCount === 1;
  }

  async storeRefreshToken(input: {
    userId: string;
    tokenHash: string;
    expiresOn: Date;
  }): Promise<void> {
    await this.pool.query(
      `INSERT INTO auth_refresh_tokens (id, user_id, token_hash, expires_on)
       VALUES ($1, $2, $3, $4)`,
      [randomUUID(), input.userId, input.tokenHash, input.expiresOn],
    );
  }

  async findRefreshTokenByHash(tokenHash: string): Promise<RefreshTokenRecord | null> {
    const result = await this.pool.query<RefreshRow>(
      `SELECT id, user_id, token_hash, expires_on, consumed_on, revoked_on
       FROM auth_refresh_tokens
       WHERE token_hash = $1`,
      [tokenHash],
    );

    const row = result.rows[0];
    if (!row) {
      return null;
    }

    return {
      id: row.id,
      userId: row.user_id,
      tokenHash: row.token_hash,
      expiresOn: row.expires_on,
      consumedOn: row.consumed_on,
      revokedOn: row.revoked_on,
    };
  }

  async consumeRefreshToken(id: string, consumedOn: Date): Promise<boolean> {
    const result = await this.pool.query(
      `UPDATE auth_refresh_tokens
       SET consumed_on = $2
       WHERE id = $1
         AND consumed_on IS NULL
         AND revoked_on IS NULL`,
      [id, consumedOn],
    );

    return result.rowCount === 1;
  }

  async revokeAllRefreshTokensForUser(userId: string, revokedOn: Date): Promise<void> {
    await this.pool.query(
      `UPDATE auth_refresh_tokens
       SET revoked_on = $2
       WHERE user_id = $1
         AND revoked_on IS NULL`,
      [userId, revokedOn],
    );
  }

  async onModuleDestroy(): Promise<void> {
    await this.pool.end();
  }

  private mapUser(row?: UserRow): AuthUserRecord | null {
    if (!row) {
      return null;
    }

    return {
      id: row.id,
      username: row.username,
      passwordHash: row.password_hash,
      accountId: row.account_reference,
      roles: row.roles,
      firstName: row.first_name,
      lastName: row.last_name,
      createdOn: row.created_on,
    };
  }

  private mapSignupRequest(row?: SignupRequestRow): SignupRequestRecord | null {
    if (!row) {
      return null;
    }

    return {
      id: row.id,
      firstName: row.first_name,
      lastName: row.last_name,
      username: row.username,
      phone: row.phone,
      dob: row.dob,
      status: row.status,
      reviewNotes: row.review_notes,
      rejectionReason: row.rejection_reason,
      approvedAccountId: row.approved_account_reference,
      approvedAuthUserId: row.approved_auth_user_id,
      reviewedBy: row.reviewed_by,
      reviewedOn: row.reviewed_on,
      createdOn: row.created_on,
      updatedOn: row.updated_on,
    };
  }
}

interface UserRow {
  id: string;
  username: string;
  password_hash: string;
  account_reference: string;
  roles: Role[];
  created_on: Date;
  first_name?: string;
  last_name?: string;
}

interface RefreshRow {
  id: string;
  user_id: string;
  token_hash: string;
  expires_on: Date;
  consumed_on: Date | null;
  revoked_on: Date | null;
}

interface SignupRequestRow {
  id: string;
  first_name: string;
  last_name: string;
  username: string;
  phone: string;
  dob: Date;
  status: SignupRequestStatus;
  review_notes: string | null;
  rejection_reason: string | null;
  approved_account_reference: string | null;
  approved_auth_user_id: string | null;
  reviewed_by: string | null;
  reviewed_on: Date | null;
  created_on: Date;
  updated_on: Date;
}
