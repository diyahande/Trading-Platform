import { Injectable } from '@nestjs/common';
import * as nodemailer from 'nodemailer';

@Injectable()
export class EmailService {

  private transporter = nodemailer.createTransport({
    host: process.env.MAIL_HOST,
    port: Number(process.env.MAIL_PORT),
    secure: false,
    auth: {
      user: process.env.MAIL_USER,
      pass: process.env.MAIL_PASSWORD,
    },
  });

  async sendTemporaryPassword(
    email: string,
    username: string,
    temporaryPassword: string,
  ) {

    await this.transporter.sendMail({
      from: `"Trading Platform" <${process.env.MAIL_FROM}>`,
      to: email,
      subject: 'Your Trading Platform Account',
      html: `
        <h2>Account Approved</h2>

        <p>Your account has been approved by the administrator.</p>

        <p><strong>Username:</strong> ${username}</p>

        <p><strong>Temporary Password:</strong> ${temporaryPassword}</p>

        <p>
          Please log in using these credentials and change your
          password immediately.
        </p>

        <p>
          If you did not request this account, please contact the administrator.
        </p>
      `,
    });
  }

  async sendRejectionEmail(email: string, firstName: string, reason: string): Promise<void> {
    await this.transporter.sendMail({
      from: `"Trading Platform" <${process.env.MAIL_FROM}>`,
      to: email,
      subject: 'Your account request status',
      html: `
        <h2>Account Request Update</h2>

        <p>Hello ${firstName},</p>

        <p>Your account request has been reviewed and was not approved at this time.</p>

        <p><strong>Reason:</strong> ${reason}</p>

        <p>You may submit a fresh request with corrected details.</p>
      `,
    });
  }

  async sendOrderPlacedConfirmationEmail(
    email: string,
    details: {
      orderId: string;
      accountReference: string;
      symbol: string;
      side: 'BUY' | 'SELL';
      quantity: number;
      limitPrice: number;
      currency: string;
      status: 'NEW';
      createdOn: string;
    },
  ): Promise<void> {
    const escapedSymbol = this.escapeHtml(details.symbol);
    const escapedSide = this.escapeHtml(details.side);
    const escapedStatus = this.escapeHtml(details.status);
    const escapedAccountReference = this.escapeHtml(details.accountReference);
    const escapedCurrency = this.escapeHtml(details.currency);
    const escapedOrderId = this.escapeHtml(`ORD-${details.orderId}`);

    const submittedAt = new Date(details.createdOn);
    const submittedAtDisplay = Number.isNaN(submittedAt.getTime())
      ? this.escapeHtml(details.createdOn)
      : submittedAt.toUTCString();

    const limitPriceDisplay = `${details.limitPrice.toFixed(2)} ${escapedCurrency}`;

    await this.transporter.sendMail({
      from: `"Trading Platform" <${process.env.MAIL_FROM}>`,
      to: email,
      subject: `TradeX Order Confirmation - ${escapedOrderId}`,
      html: `
        <div style="font-family: Arial, Helvetica, sans-serif; line-height: 1.5; color: #1f2937;">
          <h2 style="margin-bottom: 8px;">Order Submitted Successfully</h2>
          <p style="margin-top: 0;">Your order has been accepted by TradeX and queued for execution.</p>

          <table style="border-collapse: collapse; width: 100%; max-width: 560px; margin-top: 16px;">
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Order ID</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${escapedOrderId}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Account Reference</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${escapedAccountReference}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Symbol</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${escapedSymbol}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Side</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${escapedSide}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Quantity</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${details.quantity}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Limit Price</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${limitPriceDisplay}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Status</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${escapedStatus}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Submitted At (UTC)</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${submittedAtDisplay}</td></tr>
          </table>

          <p style="margin-top: 16px;">You can monitor this order from the TradeX order blotter.</p>
          <p style="color: #6b7280; font-size: 12px; margin-top: 20px;">This is an automated notification from TradeX. Please do not reply to this email.</p>
        </div>
      `,
    });
  }

  async sendPriceAlertEmail(
    email: string,
    details: {
      alertId: number;
      accountReference: string;
      symbol: string;
      currentPrice: number;
      threshold: number;
      direction: 'ABOVE' | 'BELOW';
      triggeredAt: string;
    },
  ): Promise<void> {
    const escapedSymbol = this.escapeHtml(details.symbol);
    const escapedDirection = this.escapeHtml(details.direction);
    const escapedAccountReference = this.escapeHtml(details.accountReference);
    const escapedAlertId = this.escapeHtml(`ALR-${details.alertId}`);

    const triggeredAt = new Date(details.triggeredAt);
    const triggeredAtDisplay = Number.isNaN(triggeredAt.getTime())
      ? this.escapeHtml(details.triggeredAt)
      : triggeredAt.toUTCString();

    await this.transporter.sendMail({
      from: `"Trading Platform" <${process.env.MAIL_FROM}>`,
      to: email,
      subject: `TradeX Price Alert Triggered - ${escapedSymbol}`,
      html: `
        <div style="font-family: Arial, Helvetica, sans-serif; line-height: 1.5; color: #1f2937;">
          <h2 style="margin-bottom: 8px;">Price Alert Triggered</h2>
          <p style="margin-top: 0;">One of your configured price alerts has been triggered.</p>

          <table style="border-collapse: collapse; width: 100%; max-width: 560px; margin-top: 16px;">
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Alert ID</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${escapedAlertId}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Account Reference</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${escapedAccountReference}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Symbol</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${escapedSymbol}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Direction</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${escapedDirection}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Threshold</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${details.threshold.toFixed(4)}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Current Price</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${details.currentPrice.toFixed(4)}</td></tr>
            <tr><td style="padding: 8px; border: 1px solid #e5e7eb;"><strong>Triggered At (UTC)</strong></td><td style="padding: 8px; border: 1px solid #e5e7eb;">${triggeredAtDisplay}</td></tr>
          </table>

          <p style="margin-top: 16px;">You can review or deactivate this alert from your TradeX watchlist.</p>
          <p style="color: #6b7280; font-size: 12px; margin-top: 20px;">This is an automated notification from TradeX. Please do not reply to this email.</p>
        </div>
      `,
    });
  }

  private escapeHtml(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
}