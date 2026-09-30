import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { AUTH_HEADER_POLICY, bearerTokenInterceptor } from './bearer-token.interceptor';
import { Auth } from '../services/auth';

describe('bearerTokenInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authSpy: { getValidAccessToken: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorage.clear();
    authSpy = {
      getValidAccessToken: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([bearerTokenInterceptor])),
        provideHttpClientTesting(),
        { provide: Auth, useValue: authSpy },
        {
          provide: AUTH_HEADER_POLICY,
          useValue: {
            allowedOrigins: ['http://localhost:3000', 'http://localhost:8080'],
            unauthenticatedAuthPaths: ['/auth/login', '/auth/register', '/auth/refresh'],
          },
        },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('a request to a platform API carries the bearer token', async () => {
    authSpy.getValidAccessToken.mockResolvedValue('test-token');

    http.get('http://localhost:8080/api/accounts/123').subscribe();
    await Promise.resolve();

    const req = httpMock.expectOne('http://localhost:8080/api/accounts/123');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush({});
  });

  it('a request to a third-party origin does not carry the bearer token', async () => {
    authSpy.getValidAccessToken.mockResolvedValue('test-token');

    http.get('https://example.com/analytics').subscribe();
    await Promise.resolve();

    const req = httpMock.expectOne('https://example.com/analytics');
    expect(req.request.headers.has('Authorization')).toBeFalsy();
    req.flush({});
  });

  it('the unauthenticated auth routes are called without a header', async () => {
    authSpy.getValidAccessToken.mockResolvedValue('test-token');

    http.post('http://localhost:3000/auth/login', { username: 'u', password: 'p' }).subscribe();
    await Promise.resolve();

    const req = httpMock.expectOne('http://localhost:3000/auth/login');
    expect(req.request.headers.has('Authorization')).toBeFalsy();
    req.flush({});
  });

  it('attaches refreshed token when auth returns a new valid token', async () => {
    authSpy.getValidAccessToken.mockResolvedValue('refreshed-token');

    http.get('http://localhost:3000/auth/me').subscribe();
    await Promise.resolve();

    const req = httpMock.expectOne('http://localhost:3000/auth/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer refreshed-token');
    req.flush({});
  });
});
