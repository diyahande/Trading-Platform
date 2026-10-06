import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';

import { ERROR_UNAUTHORISED } from '../auth.constants';
import type { AccessTokenClaims } from '../types/auth.types';
import { TokenService } from '../token.service';

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(private readonly tokenService: TokenService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<{
      headers: Record<string, string | string[] | undefined>;
      user?: AccessTokenClaims;
    }>();

    const rawHeader = request.headers.authorization;
    if (!rawHeader || Array.isArray(rawHeader)) {
      throw new UnauthorizedException(ERROR_UNAUTHORISED);
    }

    const [scheme, token] = rawHeader.split(' ');
    if (scheme !== 'Bearer' || !token) {
      throw new UnauthorizedException(ERROR_UNAUTHORISED);
    }

    try {
      request.user = this.tokenService.verifyAccessToken(token);
      return true;
    } catch {
      throw new UnauthorizedException(ERROR_UNAUTHORISED);
    }
  }
}
