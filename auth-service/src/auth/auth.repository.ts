import { randomUUID } from 'node:crypto';

import { Injectable, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Pool } from 'pg';

import type { AuthUserRecord, RefreshTokenRecord, Role } from './types/auth.types';

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
      `SELECT id, username, password_hash, account_reference, roles, created_on
       FROM auth_users
       WHERE username = $1`,
      [username],
    );

    return this.mapUser(result.rows[0]);
  }

  async findUserById(id: string): Promise<AuthUserRecord | null> {
    const result = await this.pool.query<UserRow>(
      `SELECT id, username, password_hash, account_reference, roles, created_on
       FROM auth_users
       WHERE id = $1`,
      [id],
    );

    return this.mapUser(result.rows[0]);
  }

  async findUserByAccountId(accountId: string): Promise<AuthUserRecord | null> {
    const result = await this.pool.query<UserRow>(
      `SELECT id, username, password_hash, account_reference, roles, created_on
       FROM auth_users
       WHERE account_reference = $1`,
      [accountId],
    );

    return this.mapUser(result.rows[0]);
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
      createdOn: row.created_on,
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
}

interface RefreshRow {
  id: string;
  user_id: string;
  token_hash: string;
  expires_on: Date;
  consumed_on: Date | null;
  revoked_on: Date | null;
}
