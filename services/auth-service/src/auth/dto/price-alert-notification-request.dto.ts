import { ApiProperty } from '@nestjs/swagger';
import { IsIn, IsISO8601, IsNumber, IsString, IsUUID, MaxLength, Min } from 'class-validator';

export class PriceAlertNotificationRequestDto {
  @ApiProperty({ minimum: 1, description: 'Alert identifier from trade-api.' })
  @IsNumber()
  @Min(1)
  alertId!: number;

  @ApiProperty({ format: 'uuid', description: 'Account reference used by auth_users.account_reference.' })
  @IsUUID('4')
  accountReference!: string;

  @ApiProperty({ maxLength: 20 })
  @IsString()
  @MaxLength(20)
  symbol!: string;

  @ApiProperty({ minimum: 0.01 })
  @IsNumber({ maxDecimalPlaces: 4 })
  @Min(0.01)
  currentPrice!: number;

  @ApiProperty({ minimum: 0.01 })
  @IsNumber({ maxDecimalPlaces: 4 })
  @Min(0.01)
  threshold!: number;

  @ApiProperty({ enum: ['ABOVE', 'BELOW'] })
  @IsIn(['ABOVE', 'BELOW'])
  direction!: 'ABOVE' | 'BELOW';

  @ApiProperty({ format: 'date-time' })
  @IsISO8601()
  triggeredAt!: string;
}
