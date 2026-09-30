export type Role = 'CUSTOMER' | 'ADMIN';

export interface AccessTokenClaims {
  sub: string;
  accountId: string;
  roles: Role[];
  iat: number;
  exp: number;
  iss: string;
}

export interface AuthUserRecord {
  id: string;
  username: string;
  passwordHash: string;
  accountId: string;
  roles: Role[];
  createdOn: Date;
}

export interface RefreshTokenRecord {
  id: string;
  userId: string;
  tokenHash: string;
  expiresOn: Date;
  consumedOn: Date | null;
  revokedOn: Date | null;
}
