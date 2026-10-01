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
}