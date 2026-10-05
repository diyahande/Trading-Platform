import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { vi } from 'vitest';
import { BehaviorSubject } from 'rxjs';

import { Login } from './login';
import { Auth } from '../../../core/services/auth';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let authSpy: { signIn: ReturnType<typeof vi.fn>; signOut: ReturnType<typeof vi.fn> };
  let queryParams: Record<string, string>;

  const makeActivatedRoute = () => ({
    snapshot: {
      queryParamMap: {
        get: (key: string) => queryParams[key] ?? null,
      },
    },
  });

  beforeEach(async () => {
    queryParams = {};
    authSpy = {
      signIn: vi.fn(),
      signOut: vi.fn(),
    } as any;
    // Create a token$ that emits a token after signIn is called
    const tokenSubject = new BehaviorSubject<string | null>(null);
    (authSpy as any).token$ = tokenSubject.asObservable();
    (authSpy as any).token = null;
    (authSpy as any).isAdmin = vi.fn().mockReturnValue(false);
    
    authSpy.signIn.mockImplementation(async () => {
      tokenSubject.next('mock-token');
      return { accessToken: 'abc', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 3600 };
    });

    await TestBed.configureTestingModule({
      imports: [Login, RouterTestingModule.withRoutes([])],
      providers: [
        { provide: Auth, useValue: authSpy },
        { provide: ActivatedRoute, useFactory: makeActivatedRoute },
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('valid credentials sign in and redirect', async () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const navigateByUrlSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true as any);
    component.username = 'user';
    component.password = 'pass';
    
    await component.submit();
    expect(authSpy.signIn).toHaveBeenCalledWith('user', 'pass');
    expect(component.signedIn).toBe(true);
    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
    expect(navigateByUrlSpy).not.toHaveBeenCalled();
  });

  it('after sign in user is redirected to requested in-origin return path', async () => {
    queryParams = { returnUrl: '/admin?tab=users' };
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const navigateByUrlSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true as any);

    component.username = 'user';
    component.password = 'pass';
    await component.submit();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(navigateByUrlSpy).toHaveBeenCalledWith('/admin?tab=users');
    expect(navigateSpy).not.toHaveBeenCalledWith(['/dashboard'], { queryParams: { username: 'user' } });
  });

  it('off-origin return address is refused and falls back to dashboard', async () => {
    queryParams = { returnUrl: 'https://evil.example/phish' };
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const navigateByUrlSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true as any);

    component.username = 'user';
    component.password = 'pass';
    await component.submit();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
    expect(navigateByUrlSpy).not.toHaveBeenCalled();
  });

  it('invalid credentials show a readable error', async () => {
    authSpy.signIn.mockRejectedValue({ error: { message: 'Invalid' } });
    component.username = 'user';
    component.password = 'bad';
    await component.submit();
    expect(component.error).toBe('Invalid');
  });

  it('shows fallback invalid-credentials message for generic http failure responses', async () => {
    authSpy.signIn.mockRejectedValue({ message: 'Http failure response for http://localhost:3000/auth/login: 401 Unauthorized', status: 401 });
    component.username = 'user';
    component.password = 'bad';
    await component.submit();
    expect(component.error).toBe('Invalid username or password');
  });

  it('shows timeout message when sign in request takes too long', async () => {
    authSpy.signIn.mockRejectedValue({ message: 'Timeout has occurred' });
    component.username = 'user';
    component.password = 'bad';
    await component.submit();
    expect(component.error).toBe('Sign in request timed out. Please try again.');
  });

  it('shows backend unreachable message when network status is 0', async () => {
    authSpy.signIn.mockRejectedValue({ status: 0, message: 'Http failure response for http://localhost:3000/auth/login: 0 Unknown Error' });
    component.username = 'user';
    component.password = 'bad';
    await component.submit();
    expect(component.error).toBe('Unable to reach auth service. Please try again.');
  });

  it('empty fields are blocked by validation', async () => {
    component.username = '';
    component.password = '';
    await component.submit();
    expect(component.error).toBe('Username and password are required');
  });

  it('signing out clears the session', () => {
    component.signOut();
    expect(authSpy.signOut).toHaveBeenCalled();
  });
});
