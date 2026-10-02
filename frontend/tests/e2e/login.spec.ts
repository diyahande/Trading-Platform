import { expect, test, type Page } from '@playwright/test';

function mustReadEnv(name: string): string {
	const value = process.env[name];
	if (!value || !value.trim()) {
		throw new Error(`Missing required environment variable: ${name}`);
	}
	return value.trim();
}

function appBaseUrl(): string {
	return mustReadEnv('E2E_FRONTEND_BASE_URL');
}

function loginUrl(): string {
	return `${appBaseUrl()}/login`;
}

function protectedPath(): string {
	return process.env['E2E_PROTECTED_PATH']?.trim() || '/dashboard';
}

function protectedUrl(): string {
	return `${appBaseUrl()}${protectedPath()}`;
}

async function submitSignIn(page: Page, username: string, password: string): Promise<void> {
	await page.getByTestId('signin-username').fill(username);
	await page.getByTestId('signin-password').fill(password);
	await page.getByTestId('signin-submit').click();
}

test.describe('Sign-in journeys against real running stack', () => {
	test('guard redirect sends signed-out user to sign-in carrying return address', async ({ page }) => {
		await page.goto(protectedUrl());

		await expect(page).toHaveURL(new RegExp('/login\\?returnUrl='));
		await expect(page.getByTestId('signin-username')).toBeVisible();
		await expect(page.getByTestId('signin-password')).toBeVisible();
		await expect(page.getByTestId('signin-submit')).toBeVisible();
	});

	test('refused sign-in shows a readable error', async ({ page }) => {
		const invalidUsername = mustReadEnv('E2E_INVALID_USERNAME');
		const invalidPassword = mustReadEnv('E2E_INVALID_PASSWORD');

		await page.goto(loginUrl());
		await submitSignIn(page, invalidUsername, invalidPassword);

		await expect(page).toHaveURL(/\/login/);
		await expect(page.getByTestId('signin-error')).toBeVisible();
		await expect(page.getByTestId('signin-error')).toContainText(/invalid|failed|unauthori|unable|timeout/i);
	});

	test('successful sign-in from login page lands on dashboard', async ({ page }) => {
		const username = mustReadEnv('E2E_USERNAME');
		const password = mustReadEnv('E2E_PASSWORD');

		await page.goto(loginUrl());
		await submitSignIn(page, username, password);

		await expect(page).toHaveURL(/\/dashboard$/);
		await expect(page.getByText('Welcome')).toBeVisible();
	});

	test('after successful sign-in, user lands where they were going', async ({ page }) => {
		const username = mustReadEnv('E2E_USERNAME');
		const password = mustReadEnv('E2E_PASSWORD');
		const targetPath = protectedPath();

		await page.goto(protectedUrl());
		await expect(page).toHaveURL(new RegExp('/login\\?returnUrl='));

		await submitSignIn(page, username, password);

		const escapedTargetPath = targetPath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
		await expect(page).toHaveURL(new RegExp(`${escapedTargetPath}$`));
	});
});
