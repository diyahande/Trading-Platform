import { ConsoleLogger, Injectable, LoggerService } from '@nestjs/common';

import { redactSensitiveData } from './redaction';

@Injectable()
export class AppLogger implements LoggerService {
  private readonly logger = new ConsoleLogger('AppLogger');

  log(message: unknown, ...optionalParams: unknown[]): void {
    this.logger.log(
      redactSensitiveData(message),
      ...optionalParams.map((item) => redactSensitiveData(item)),
    );
  }

  error(message: unknown, ...optionalParams: unknown[]): void {
    this.logger.error(
      redactSensitiveData(message),
      ...optionalParams.map((item) => redactSensitiveData(item)),
    );
  }

  warn(message: unknown, ...optionalParams: unknown[]): void {
    this.logger.warn(
      redactSensitiveData(message),
      ...optionalParams.map((item) => redactSensitiveData(item)),
    );
  }

  debug(message: unknown, ...optionalParams: unknown[]): void {
    this.logger.debug(
      redactSensitiveData(message),
      ...optionalParams.map((item) => redactSensitiveData(item)),
    );
  }

  verbose(message: unknown, ...optionalParams: unknown[]): void {
    this.logger.verbose(
      redactSensitiveData(message),
      ...optionalParams.map((item) => redactSensitiveData(item)),
    );
  }
}
