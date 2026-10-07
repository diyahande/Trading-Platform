import { ApiProperty } from '@nestjs/swagger';

export class OrderPlacedNotificationResponseDto {
  @ApiProperty({ format: 'uuid' })
  orderId!: string;

  @ApiProperty({ format: 'uuid' })
  accountReference!: string;

  @ApiProperty()
  recipient!: string;

  @ApiProperty()
  emailSent!: boolean;
}
