import { ApiProperty } from '@nestjs/swagger';

export class PriceAlertNotificationResponseDto {
  @ApiProperty({ minimum: 1 })
  alertId!: number;

  @ApiProperty({ format: 'uuid' })
  accountReference!: string;

  @ApiProperty()
  recipient!: string;

  @ApiProperty()
  emailSent!: boolean;
}
