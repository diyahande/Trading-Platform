import { createParamDecorator, ExecutionContext } from '@nestjs/common';

import type { AccessTokenClaims } from '../types/auth.types';

export const AuthUser = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext): AccessTokenClaims => {
    const request = ctx.switchToHttp().getRequest<{ user: AccessTokenClaims }>();
    return request.user;
  },
);
