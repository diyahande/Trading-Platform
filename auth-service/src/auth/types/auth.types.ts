export type Role = 'CUSTOMER' | 'ADMIN';

export type SignupRequestStatus = 'NEW' | 'APPROVED' | 'REJECTED';

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

export interface SignupRequestRecord {
  id: string;
  firstName: string;
  lastName: string;
  username: string;
  phone: string;
  dob: Date;
  status: SignupRequestStatus;
  reviewNotes: string | null;
  rejectionReason: string | null;
  approvedAccountId: string | null;
  approvedAuthUserId: string | null;
  reviewedBy: string | null;
  reviewedOn: Date | null;
  createdOn: Date;
  updatedOn: Date;
}
