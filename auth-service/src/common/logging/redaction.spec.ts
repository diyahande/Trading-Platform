import { redactSensitiveData } from './redaction';

describe('redactSensitiveData', () => {
  it('redacts sensitive keys at any depth', () => {
    const input = {
      password: 'top-secret',
      profile: {
        nested: {
          refreshToken: 'abc123',
          auth: {
            authorization: 'Bearer value',
          },
        },
      },
      events: [{ token: 't1' }, { safe: true }],
    };

    const redacted = redactSensitiveData(input) as {
      password: string;
      profile: { nested: { refreshToken: string; auth: { authorization: string } } };
      events: Array<{ token?: string; safe?: boolean }>;
    };

    expect(redacted.password).toBe('[REDACTED]');
    expect(redacted.profile.nested.refreshToken).toBe('[REDACTED]');
    expect(redacted.profile.nested.auth.authorization).toBe('[REDACTED]');
    expect(redacted.events[0].token).toBe('[REDACTED]');
    expect(redacted.events[1].safe).toBe(true);
  });

  it('does not mutate the original object', () => {
    const input = {
      credentials: {
        passwordHash: 'hash-value',
      },
    };

    const redacted = redactSensitiveData(input) as {
      credentials: { passwordHash: string };
    };

    expect(input.credentials.passwordHash).toBe('hash-value');
    expect(redacted.credentials.passwordHash).toBe('[REDACTED]');
  });
});
