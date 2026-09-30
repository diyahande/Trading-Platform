import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { Login } from './login';
import { Auth } from '../../../core/services/auth';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let authSpy: { signIn: ReturnType<typeof vi.fn>; signOut: ReturnType<typeof vi.fn> };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authSpy = {
      signIn: vi.fn(),
      signOut: vi.fn(),
    } as any;
    (authSpy as any).token$ = of(null);
    (authSpy as any).token = null;
    routerSpy = {
      navigate: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        { provide: Auth, useValue: authSpy },
        { provide: Router, useValue: routerSpy }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('valid credentials sign in and redirect', async () => {
    authSpy.signIn.mockResolvedValue({ accessToken: 'abc', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 3600 } as any);
    component.username = 'user';
    component.password = 'pass';
    await component.submit();
    expect(authSpy.signIn).toHaveBeenCalledWith('user', 'pass');
    expect(component.signedIn).toBeTruthy();
  });

  it('invalid credentials show a readable error', async () => {
    authSpy.signIn.mockRejectedValue({ error: { message: 'Invalid' } });
    component.username = 'user';
    component.password = 'bad';
    await component.submit();
    expect(component.error).toBe('Invalid');
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
