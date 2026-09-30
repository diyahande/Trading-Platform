import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { AUTH_HEADER_POLICY, bearerTokenInterceptor } from './core/interceptors/bearer-token.interceptor';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(withInterceptors([bearerTokenInterceptor])),
        {
          provide: AUTH_HEADER_POLICY,
          useValue: {
            allowedOrigins: ['http://localhost:3000', 'http://localhost:8080'],
            unauthenticatedAuthPaths: ['/auth/login', '/auth/register', '/auth/refresh'],
          },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render login component', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-login')).toBeTruthy();
  });
});
