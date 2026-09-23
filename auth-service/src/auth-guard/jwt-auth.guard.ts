import { CanActivate, ExecutionContext, Injectable, UnauthorizedException, Logger } from '@nestjs/common';
import { JwtService } from './jwt.service';

@Injectable()
export class JwtAuthGuard implements CanActivate {
  private readonly logger = new Logger(JwtAuthGuard.name);

  constructor(private readonly jwtService: JwtService) {}

  // Verify Authorization header and attach `user` to request
  canActivate(context: ExecutionContext): boolean {
    const req = context.switchToHttp().getRequest();
    const auth = req.headers['authorization'];
    // header presence check
    if (!auth || Array.isArray(auth)) {
      this.logger.warn('Missing Authorization header');
      throw new UnauthorizedException();
    }
    const parts = auth.split(' ');
    // header format check
    if (parts.length !== 2 || parts[0] !== 'Bearer') {
      this.logger.warn('Malformed Authorization header');
      throw new UnauthorizedException();
    }
    const token = parts[1];
    try {
      const payload = this.jwtService.verify(token) as any;
      // attach only the contract claims to request.user
      req.user = {
        sub: payload.sub,
        accountId: payload.accountId,
        roles: payload.roles,
        iat: payload.iat,
        exp: payload.exp,
        iss: payload.iss,
      };
      this.logger.log(`JWT verified for sub=${payload.sub} accountId=${payload.accountId}`);
      return true; // allow through
    } catch (err: any) {
      this.logger.warn(`JWT verification failed: ${err?.name || 'UnknownError'}`);
      throw new UnauthorizedException();
    }
  }
}
