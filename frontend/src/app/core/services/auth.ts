import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, firstValueFrom } from 'rxjs';
import { TokenResponseDto } from '../../generated/api-client/model/tokenResponseDto';
import { AuthService as ApiAuthService } from '../../generated/api-client/api/auth.service';

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';
const ACCESS_TOKEN_EXPIRES_AT_KEY = 'accessTokenExpiresAt';
const EXPIRY_SKEW_MS = 30_000;

@Injectable({
  providedIn: 'root',
})
export class Auth {
  private _token$ = new BehaviorSubject<string | null>(this.getStoredAccessToken());
  private refreshInFlight: Promise<TokenResponseDto | null> | null = null;

  constructor(private apiAuth: ApiAuthService) {}

  get token$(): Observable<string | null> {
    return this._token$.asObservable();
  }

  public get token(): string | null {
    return this._token$.value;
  }

  public async getValidAccessToken(): Promise<string | null> {
    const accessToken = this.getStoredAccessToken();
    if (!accessToken) {
      return null;
    }

    if (this.isAccessTokenValid()) {
      return accessToken;
    }

    const refreshed = await this.refreshTokens();
    return refreshed?.accessToken ?? null;
  }

  public async signIn(username: string, password: string): Promise<TokenResponseDto> {
    const payload = { username, password } as any;
    try {
      console.log('[Auth] signIn request for', username);
      const res = await firstValueFrom(this.apiAuth.authControllerLogin(payload));
      console.log('[Auth] signIn response', res);
      if (!res) throw new Error('No token returned');
      this.storeTokens(res);
      this._token$.next(res.accessToken);
      return res;
    } catch (err) {
      console.error('[Auth] signIn error', err);
      throw err;
    }
  }

  public signOut(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_KEY);
    this._token$.next(null);
  }

  private storeTokens(tokens: TokenResponseDto) {
    if (tokens?.accessToken) {
      localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    }
    if (tokens?.refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    }
    if (typeof tokens?.expiresIn === 'number' && tokens.expiresIn > 0) {
      const expiresAtMs = Date.now() + tokens.expiresIn * 1000;
      localStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_KEY, String(expiresAtMs));
    }
  }

  private getStoredAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  private getStoredRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  private getStoredExpiryMs(): number | null {
    const raw = localStorage.getItem(ACCESS_TOKEN_EXPIRES_AT_KEY);
    if (!raw) {
      return null;
    }
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private isAccessTokenValid(): boolean {
    const expiresAt = this.getStoredExpiryMs();
    if (!expiresAt) {
      return false;
    }
    return Date.now() + EXPIRY_SKEW_MS < expiresAt;
  }

  private async refreshTokens(): Promise<TokenResponseDto | null> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }

    const refreshToken = this.getStoredRefreshToken();
    if (!refreshToken) {
      this.signOut();
      return null;
    }

    this.refreshInFlight = (async () => {
      try {
        const res = await firstValueFrom(this.apiAuth.authControllerRefresh({ refreshToken }));
        if (!res?.accessToken) {
          throw new Error('Refresh did not return a valid access token');
        }
        this.storeTokens(res);
        this._token$.next(res.accessToken);
        return res;
      } catch {
        this.signOut();
        return null;
      } finally {
        this.refreshInFlight = null;
      }
    })();

    return this.refreshInFlight;
  }
}