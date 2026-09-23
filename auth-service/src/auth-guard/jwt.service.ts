import { Injectable } from '@nestjs/common';
import * as jwt from 'jsonwebtoken';

@Injectable()
export class JwtService {
  private readonly secret = process.env.JWT_SECRET;

  sign(payload: Record<string, any>, expiresInSeconds: number) {
    return jwt.sign(payload, this.secret, {
      algorithm: 'HS256',
      expiresIn: expiresInSeconds,
    });
  }

  verify(token: string) {
    return jwt.verify(token, this.secret, {
      algorithms: ['HS256'],
      issuer: 'auth-service',
    });
  }
}