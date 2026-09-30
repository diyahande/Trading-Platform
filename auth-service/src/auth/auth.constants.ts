export const ACCESS_TOKEN_TTL_SECONDS = 900;
export const REFRESH_TOKEN_TTL_SECONDS = 60 * 60 * 24 * 7;

export const ERROR_UNAUTHORISED = {
  errorCode: 'AUTH-401',
  message: 'Unauthorised',
} as const;

export const ERROR_REGISTRATION_FAILED = {
  errorCode: 'AUTH-409',
  message: 'Registration failed',
} as const;

export const ERROR_INVALID_INPUT = {
  errorCode: 'VAL-422',
  message: 'Invalid input',
} as const;

export const DUMMY_PASSWORD_HASH =
  '$2b$12$C6UzMDM.H6dfI/f/IKcEe.5R7M3fNfX0Jf7oOJPfm6D/Ebhj116HC';
